"""
https://stackoverflow.com/questions/47485216/how-does-mask-zero-in-keras-embedding-layer-work
https://stackoverflow.com/questions/49670832/keras-lstm-with-masking-layer-for-variable-length-inputs
https://stackoverflow.com/questions/51720236/multi-feature-sequence-padding-and-masking-in-rnn-using-keras
https://stackoverflow.com/questions/47057361/keras-using-tensorflow-backend-masking-on-loss-function
"""
import tensorflow as tf
import keras.backend as K
from keras.models import Model
from keras.utils import plot_model
from keras.initializers import Constant
from encoders.BERT import load_trained_bert_from_checkpoint
from encoders.attention_with_context import AttentionWithContext
from keras.layers import Layer, Input, Embedding, LSTM, GRU, Lambda, Dropout, Bidirectional, Dense, Concatenate, RepeatVector


def get_embdedding_layer(name, embedding_matrix, max_sequence_length, trainable):
    if name == 'News':
        layer = Embedding(
            embedding_matrix.shape[0],
            embedding_matrix.shape[1],
            embeddings_initializer=Constant(embedding_matrix),
            input_length=max_sequence_length,
            trainable=trainable,
            mask_zero=True
        )
    elif name == 'BERT':
        layer = load_trained_bert_from_checkpoint(
            config_file='resource/cased_L-12_H-768_A-12/bert_config.json',
            checkpoint_file='resource/cased_L-12_H-768_A-12/bert_model.ckpt',
            seq_len=max_sequence_length,
            trainable=trainable,
            num_hidden_layers=None
        )
        # This BERT implementation supports zero-masking, and outputs masks as normal keras Embedding layer
        # TODO: drop the vectors for [CLS] and [SEP] for BERT
    else:
        raise NotImplementedError()

    return layer


def get_pooling_layer(name):
    if name == 'first':
        layer = Lambda(lambda tensor: tensor[:, 0, :])
    elif name == 'last':
        layer = Lambda(lambda tensor: tensor[:, -1, :])
    elif name == 'attention':
        layer = AttentionWithContext()
    elif name == 'max':
        layer = Max()
    else:
        raise NotImplementedError()

    return layer


def get_recurrent_layer(name, n_hidden, return_sequences):
    if name.endswith('LSTM'):
        layer = LSTM(
            units=n_hidden,
            activation='tanh',
            return_sequences=return_sequences
        )
    elif name.endswith('GRU'):
        layer = GRU(
            units=n_hidden,
            activation='tanh',
            return_sequences=return_sequences
        )
    else:
        raise NotImplementedError()

    if name.startswith('Bi'):
        # mode is one of {'sum', 'mul', 'concat', 'ave', None}
        layer = Bidirectional(layer, merge_mode='concat', weights=None)

    return layer


class ConcatenateFeatures(Concatenate):
    def call(self, inputs, mask=None):
        return super(ConcatenateFeatures, self).call(inputs) * K.expand_dims(K.cast(mask[0], K.floatx()), axis=-1)


class Max(Layer):
    def __init__(self, **kwargs):
        super(Max, self).__init__(**kwargs)
        self.supports_masking = True

    def compute_output_shape(self, input_shape):
        return input_shape[0], input_shape[-1]

    def compute_mask(self, inputs, mask=None):
        return None

    def call(self, inputs, mask=None):
        mask_inv = tf.logical_not(mask)
        negative_mask = K.cast(mask_inv, K.floatx()) * -2
        output = K.max(inputs + K.expand_dims(negative_mask, axis=-1), axis=1)

        #output = tf.Print(output, [mask, inputs, output], summarize=200)
        return output


def S2V(input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features, with_last_f_f_layer=True):
    embdedding_layer = None
    if with_embdedding_layer:
        embdedding_layer = get_embdedding_layer(
            name=word_vectors_name,
            embedding_matrix=word_vectors,
            max_sequence_length=input_shape[0],
            trainable=fine_tune_word_vectors
        )

    dropout_layer = Dropout(rate=dropout_rate)

    recurrent_layer = get_recurrent_layer(
        name=recurrent_name,
        n_hidden=n_hidden,
        return_sequences=True
    )

    pooling_layer = get_pooling_layer(name=pooling_name)

    if with_embdedding_layer:
        if with_extra_features:
            inputs = [
                Input(shape=input_shape, dtype='int32'),
                Input(shape=(21,), dtype='float32')
            ]

            f_f_layer_1 = Dense(units=n_hidden, activation='tanh')

            outputs = pooling_layer(recurrent_layer(
                f_f_layer_1(
                    ConcatenateFeatures(axis=-1)([
                        dropout_layer(embdedding_layer(inputs[0])),
                        RepeatVector(input_shape[0])(inputs[1])
                    ])
                )
            ))
            if with_last_f_f_layer:
                f_f_layer_2 = Dense(units=n_hidden, activation='tanh')
                outputs = f_f_layer_2(outputs)

        else:
            inputs = Input(shape=input_shape, dtype='int32')
            outputs = pooling_layer(recurrent_layer(dropout_layer(embdedding_layer(inputs))))
    else:
        inputs = Input(shape=input_shape, dtype='float32')
        outputs = pooling_layer(recurrent_layer(inputs))

    if is_base_network:
        model = Model(inputs, outputs, name='base_network')
        plot_model(model, show_shapes=True, to_file=path_to_results + 'base_network.png')
    else:
        model = Model(inputs, outputs)
        plot_model(model, show_shapes=True, to_file=path_to_results + 'S2V.png')

    model.summary()
    return model