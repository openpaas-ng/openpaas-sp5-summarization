import utlis
import keras.backend as K
import encoders.S2V as S2V
from keras.models import Model
from keras.utils import plot_model
from keras.layers import Layer, Input, Dropout, Lambda, Concatenate, Dense, RepeatVector
from encoders.attention_with_vec import AttentionWithVec
from encoders.attention_with_context import AttentionWithContext
from encoders.attention_with_time_decay import AttentionWithTimeDecay


def get_sub_model(input_shape, n_hidden, dropout_rate, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features):
    if with_extra_features:
        inputs = [
            Input(shape=input_shape, dtype='int32'),
            Input(shape=(21,), dtype='float32')
        ]
    else:
        inputs = Input(shape=input_shape, dtype='int32')

    embdedding_layer = S2V.get_embdedding_layer(
        name=word_vectors_name,
        embedding_matrix=word_vectors,
        max_sequence_length=input_shape[0],
        trainable=fine_tune_word_vectors
    )

    dropout_layer = Dropout(rate=dropout_rate)

    if with_extra_features:
        f_f_layer = Dense(units=n_hidden, activation='tanh')

        outputs = f_f_layer(
            ConcatenateFeatures(axis=-1)([
                dropout_layer(embdedding_layer(inputs[0])),
                RepeatVector(input_shape[0])(inputs[1])
            ])
        )
    else:
        outputs = dropout_layer(embdedding_layer(inputs))

    model = Model(inputs, outputs)

    model.summary()
    return model


def TIXIER(pre_context_size, post_context_size, input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features):
    if pre_context_size <= 0:
        raise ValueError('pre_context_size should greater than 0')

    context_size = pre_context_size + 1 + post_context_size
    if with_extra_features:
        inputs = [
            [Input(shape=input_shape, dtype='int32'),
             Input(shape=(21,), dtype='float32')]
            for _ in range(context_size)
        ]
    else:
        inputs = [
            Input(shape=input_shape, dtype='int32')
            for _ in range(context_size)
        ]

    sub_model = get_sub_model(input_shape, n_hidden, dropout_rate, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features)
    attention_layer = AttentionWithVec(attend_mode='sum')
    # convert list of 2D tensors to a 3D tensor (None, left_context_size, n_hidden)
    stack_layer = Lambda(K.stack, arguments={'axis': 1})
    expand_dims_layer = Lambda(K.expand_dims, arguments={'axis': 1})

    current = sub_model(inputs[pre_context_size])
    current_pooled = Sum()(current)
    
    pre = expand_dims_layer(AttentionWithTimeDecay(reverse_decay=True)(stack_layer([
        attention_layer([sub_model(inputs[i]), current_pooled])
        for i in range(pre_context_size)
    ])))
    padded = [pre, current]

    if post_context_size > 0:
        post = expand_dims_layer(AttentionWithTimeDecay(reverse_decay=False)(stack_layer([
            attention_layer([sub_model(inputs[i]), current_pooled])
            for i in range(pre_context_size+1, context_size)
        ])))
        padded.append(post)

    recurrent_layer = S2V.get_recurrent_layer(
        name=recurrent_name,
        n_hidden=n_hidden,
        return_sequences=True
    )
    pooling_layer = S2V.get_pooling_layer(name=pooling_name)
    f_f_layer = Dense(units=n_hidden, activation='tanh')

    outputs = f_f_layer(pooling_layer(recurrent_layer(
        ConcatenateContexts(axis=1)(padded)
    )))

    if with_extra_features:
        inputs = utlis.flatten(inputs)

    model = Model(inputs, outputs, name='base_network')

    plot_model(model, show_shapes=True, to_file=path_to_results + 'base_network.png')
    model.summary()

    return model


class Sum(Layer):
    def __init__(self, **kwargs):
        super(Sum, self).__init__(**kwargs)
        self.supports_masking = True

    def call(self, inputs, mask=None):
        return K.sum(
            x=inputs * K.expand_dims(K.cast(mask, K.floatx()), axis=-1),
            axis=1,
            keepdims=False
        )


class ConcatenateFeatures(Concatenate):
    def call(self, inputs, mask=None):
        return super(ConcatenateFeatures, self).call(inputs) * K.expand_dims(K.cast(mask[0], K.floatx()), axis=-1)


class ConcatenateContexts(Concatenate):
    def compute_mask(self, inputs, mask=None):
        current = mask[1]
        pre = K.ones_like(current)[:, 0:inputs[0].shape[1]]
        padded = [pre, current]

        if len(inputs) == 3:
            post = K.ones_like(current)[:, 0:inputs[2].shape[1]]
            padded.append(post)

        output_mask = K.concatenate(padded)
        # import tensorflow as tf
        # output_mask = tf.Print(output_mask, [output_mask], summarize=200)
        return output_mask
