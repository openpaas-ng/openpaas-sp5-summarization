"""
Yang, Z., Yang, D., Dyer, C., He, X., Smola, A., & Hovy, E. (2016). Hierarchical attention networks for document classification. In Proceedings of the 2016 Conference of the North American Chapter of the Association for Computational Linguistics: Human Language Technologies (pp. 1480-1489).
http://www.aclweb.org/anthology/N16-1174
"""
import utlis
import keras.backend as K
from keras.models import Model
from keras.utils import plot_model
from keras.layers import Input, Lambda, Dense
from encoders.S2V import S2V

def HAN(context_size, input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features):
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

    s2v_1 = S2V(input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features, with_last_f_f_layer=False)

    # convert list of 2D tensors to a 3D tensor (None, context_size, n_hidden)
    stack_layer = Lambda(K.stack, arguments={'axis': 1})

    # merge_mode='concat'
    input_shape = (context_size, n_hidden*2)

    s2v_2 = S2V(input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer=False, word_vectors_name=None, fine_tune_word_vectors=None, word_vectors=None, with_extra_features=False, with_last_f_f_layer=False)

    f_f_layer = Dense(units=n_hidden, activation='tanh')

    output = f_f_layer(s2v_2(
        stack_layer([
            s2v_1(inputs[i])
            for i in range(context_size)
        ])
    ))

    if with_extra_features:
        inputs = utlis.flatten(inputs)

    model = Model(inputs, output, name='base_network')

    plot_model(model, show_shapes=True, to_file=path_to_results+'base_network.png')
    model.summary()

    return model


