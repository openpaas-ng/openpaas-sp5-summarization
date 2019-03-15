import tensorflow as tf
from keras.layers import Layer


class AttentionWithTimeDecay(Layer):
    def __init__(self, decay_type='universal', reverse_decay=False, **kwargs):
        self.supports_masking = False

        self.decay_type = decay_type
        self.reverse_decay = reverse_decay

        super(AttentionWithTimeDecay, self).__init__(**kwargs)
    
    def build(self, input_shape):
        assert len(input_shape) == 3

        self.a = tf.Variable(1.0)
        self.b = tf.Variable(1.0)
        self.m = tf.Variable(0.2)
        self.k = tf.Variable(0.9)
        self.d = tf.Variable(4.5)
        self.n = tf.Variable(2.5)
        self.W = tf.Variable([0.33, 0.33, 0.33])
        self.trainable_weights = [self.a, self.b, self.m, self.k, self.d, self.n, self.W]

        self.history_length = input_shape[1]
        distances = list(range(1, self.history_length + 1))
        if self.reverse_decay is True:
            distances.reverse()
        self.distances = tf.Variable([distances], dtype='float32')

        super(AttentionWithTimeDecay, self).build(input_shape)
    
    def compute_mask(self, input, input_mask=None):
        # do not pass the mask to the next layers
        return None
    
    def call(self, x, mask=None):
        # convex 1/(a*t^b)
        b = tf.scalar_mul(self.b, tf.ones_like(self.distances))
        linear_mapping_0 = tf.reciprocal(tf.scalar_mul(self.a, tf.pow(self.distances, b)))

        # linear k-m*t
        linear_mapping_1 = tf.subtract(
            tf.scalar_mul(self.k, tf.ones_like(self.distances, dtype=tf.float32)),
            tf.scalar_mul(self.m, self.distances)
        )
        linear_mapping_1 = tf.add(
            tf.fill(tf.shape(linear_mapping_1), 0.0001),
            tf.nn.relu(linear_mapping_1)
        )

        # concave
        n = tf.scalar_mul(self.n, tf.ones_like(self.distances))
        linear_mapping_2 = tf.reciprocal(tf.add(
            tf.ones_like(self.distances),
            tf.pow(tf.divide(self.distances, self.d), n)
        ))

        # merge 3 functions
        combined_weight = tf.scalar_mul(tf.reciprocal(tf.reduce_sum(self.W)), self.W)
        combined_weight = tf.unstack(combined_weight)

        linear_mapping = tf.add(
            tf.scalar_mul(combined_weight[0], linear_mapping_0),
            tf.scalar_mul(combined_weight[1], linear_mapping_1)
        )
        linear_mapping = tf.add(
            linear_mapping,
            tf.scalar_mul(combined_weight[2], linear_mapping_2)
        )

        if self.decay_type == 'convex':
            linear_mapping = linear_mapping_0
        elif self.decay_type == 'linear':
            linear_mapping = linear_mapping_1
        elif self.decay_type == 'concave':
            linear_mapping = linear_mapping_2

        mean = tf.expand_dims(tf.reduce_sum(linear_mapping, axis=1), axis=1)
        mean = tf.matmul(mean, tf.ones([1, self.history_length], dtype=tf.float32))
        att = tf.divide(linear_mapping, mean)
        # x = tf.Print(x, [x], message='\nx: ', summarize=200)
        # att = tf.Print(att, [att], message='\natt: ', summarize=20)
        hist = tf.multiply(x, tf.expand_dims(att, axis=2))
        hist = tf.reduce_sum(hist, axis=1)

        #hist = tf.Print(hist, [hist], message='\nhist: ', summarize=200)
        return hist

    def compute_output_shape(self, input_shape):
        return input_shape[0], input_shape[-1]

