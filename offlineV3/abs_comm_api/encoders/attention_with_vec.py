import keras.backend as K
from keras.layers import Layer
from keras import initializers, regularizers, constraints


class AttentionWithVec(Layer):
    def __init__(
            self, attend_mode='sum', bias=True, return_coefficients=False,
            W_regularizer=None, M_regularizer=None, u_regularizer=None, b_regularizer=None,
            W_constraint=None, M_constraint=None, u_constraint=None, b_constraint=None, **kwargs
    ):
        self.supports_masking = True

        self.attend_mode = attend_mode
        self.bias = bias
        self.return_coefficients = return_coefficients

        self.init = initializers.get('glorot_uniform')
        
        self.W_regularizer = regularizers.get(W_regularizer)
        self.M_regularizer = regularizers.get(M_regularizer)
        self.u_regularizer = regularizers.get(u_regularizer)
        self.b_regularizer = regularizers.get(b_regularizer)
        
        self.W_constraint = constraints.get(W_constraint)
        self.M_constraint = constraints.get(M_constraint)
        self.u_constraint = constraints.get(u_constraint)
        self.b_constraint = constraints.get(b_constraint)

        super(AttentionWithVec, self).__init__(**kwargs)
    
    def build(self, input_shape):
        assert len(input_shape) == 2
        assert input_shape[0][-1] == input_shape[1][-1]

        if self.attend_mode == 'concat':
            dim = input_shape[0][-1] + input_shape[1][-1]
        if self.attend_mode == 'sum':
            dim = input_shape[0][-1]
            self.M = self.add_weight(
                (dim, dim,),
                initializer=self.init,
                name='{}_M'.format(self.name),
                regularizer=self.M_regularizer,
                constraint=self.M_constraint
            )

        self.W = self.add_weight(
            (dim, dim,),
            initializer=self.init,
            name='{}_W'.format(self.name),
            regularizer=self.W_regularizer,
            constraint=self.W_constraint
        )

        if self.bias:
            self.b = self.add_weight(
                (dim,),
                initializer='zero',
                name='{}_b'.format(self.name),
                regularizer=self.b_regularizer,
                constraint=self.b_constraint
            )
        
        self.u = self.add_weight(
            (dim,),
            initializer=self.init,
            name='{}_u'.format(self.name),
            regularizer=self.u_regularizer,
            constraint=self.u_constraint
        )
        
        super(AttentionWithVec, self).build(input_shape)
    
    def call(self, inputs, mask=None):
        X, v = inputs
        mask_X, _ = mask

        if self.attend_mode == 'concat':
            concatenated = K.concatenate([X, K.repeat(v, X.shape[1])], axis=-1)
            e = dot_product(concatenated, self.W)
        if self.attend_mode == 'sum':
            e = dot_product(X, self.W) + dot_product(K.expand_dims(v, axis=1), self.M)

        if self.bias:
            e += self.b
        e = K.tanh(e)
        e = dot_product(e, self.u)

        a = K.exp(e)
        if mask_X is not None:
            a *= K.cast(mask_X, K.floatx())
        a /= K.cast(K.sum(a, axis=1, keepdims=True) + K.epsilon(), K.floatx())
        a = K.expand_dims(a)

        weighted_sum = K.sum(X * a, axis=1)
        
        if self.return_coefficients:
            return weighted_sum, a
        else:
            return weighted_sum

    def compute_mask(self, inputs, mask=None):
        return None

    def compute_output_shape(self, input_shape):
        input_shape_X, _ = input_shape

        if self.return_coefficients:
            return [(input_shape_X[0], input_shape_X[-1]), (input_shape_X[0], input_shape_X[-1], 1)]
        else:
            return input_shape_X[0], input_shape_X[-1]


def dot_product(x, kernel):
    if K.backend() == 'tensorflow':
        return K.squeeze(K.dot(x, K.expand_dims(kernel)), axis=-1)
    else:
        return K.dot(x, kernel)