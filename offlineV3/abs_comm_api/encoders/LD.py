"""
Lee, J. Y., & Dernoncourt, F. (2016). Sequential Short-Text Classification with Recurrent and Convolutional Neural Networks. In Proceedings of NAACL-HLT (pp. 515-520).
http://www.aclweb.org/anthology/N16-1062
"""
import utlis
import itertools
import keras.backend as K
from keras.models import Model
from keras.utils import plot_model
from keras.legacy import interfaces
from keras.engine.topology import Layer
from encoders.S2V import S2V
from keras.engine.base_layer import InputSpec
from keras import initializers, regularizers, constraints
from keras.layers import Input, concatenate, add, Dense, Activation


def LD(context_size, history_sizes, input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features):
    """
    This implementation is the same with the paper's description

    Single *shared* S2V model will be created for three inputs (X_i-2, X_i-1, X_i).

    e.g. history_sizes = (1,1)
    Outputs from previous layer (s_i-2, s_i-1, s_i)

    p2 = dot(s_i-2, W_-2), p1 = dot(s_i-1, W_-1), p0=dot(s_i, W_0)
    # realised with Dense(activation=None, use_bias=False)

    y_i-1 = tanh(add(p2+p1)+bias_of_layer_1)
    y_i = tanh(add(p1+p0)+bias_of_layer_1)
    # *shared* bias_of_layer_1 is realised with custom Bias layer

    :param context_size:
    :param history_sizes: refers to the caption of Figue.2 in the paper

    :param word_vectors:
    :param input_length:
    :param layer_type:
    :param pooling_type:
    :param n_hidden:
    :param dropout_rate:
    :return:
    """
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

    s2v = S2V(input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features, with_last_f_f_layer=False)

    outpus_queue = [[
        s2v(inputs[i])
        for i in range(context_size)
    ]]

    # loop on network level
    for history_size in history_sizes:
        activations = [
            Dense(n_hidden, activation=None, use_bias=False)(outpus_queue[-1][i])
            for i in range(len(outpus_queue[-1]))
        ]  # outputs_queue[-1] refers to the outputs of last layer

        # [w1s1,w2s2,w3s3] -> [[w1s1], [w1s1, w3s2], [w1s1, w2s2, w3s3], [w2s2], [w2s2, w3s3], [w3s3]]
        tmp = [activations[i:j] for i, j in itertools.combinations(range(len(activations) + 1), 2)]

        if history_size > 0:
            # history_size=1 -> [[w1s1, w2s2], [w2s2, w3s3]] -> [w1s1+w2s2, w2s2+w3s3]
            adds = [add(ss) for ss in tmp if len(ss)==history_size+1]
        else:
            # history_size=0 -> [[w1s1], [w2s2], [w3s3]] -> [w1s1, w2s2, w3s3]
            adds = [ss[0] for ss in tmp if len(ss)==history_size+1]

        # Define FF
        a = Activation('tanh')
        b = Bias()

        # add new outputs to outputs_queue
        outpus_queue.append([
            a(b(adds[i]))
            for i in range(len(adds))
        ])

    # The network should have single output, if history_sizes is well-defined
    assert 1 == len(outpus_queue[-1])

    if with_extra_features:
        inputs = utlis.flatten(inputs)

    model = Model(inputs, outpus_queue[-1], name='base_network')

    plot_model(model, show_shapes=True, to_file=path_to_results+'base_network.png')
    model.summary()

    return model


def LD_alt(context_size, history_sizes, input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features):
    """
    This implementation is similar to the paper's structure, but not exactly the same.

    Three *non-shared* S2V models will be created for three inputs (X_i-2, X_i-1, X_i).

    e.g. history_sizes = (1,1)
    Outputs from previous layer (s_i-2, s_i-1, s_i)
    will be *concatenated* to s_i-2s_i-1, s_i-1s_i,
    then feed to two *non-shared* ordinary Dense layers

    :param context_size:
    :param history_sizes: refers to the caption of Figue.2 in the paper

    :param word_vectors:
    :param input_length:
    :param layer_type:
    :param pooling_type:
    :param n_hidden:
    :param dropout_rate:
    :return:
    """
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

    S2Vs = [
        S2V(input_shape, recurrent_name, pooling_name, n_hidden, dropout_rate, path_to_results, is_base_network, with_embdedding_layer, word_vectors_name, fine_tune_word_vectors, word_vectors, with_extra_features, with_last_f_f_layer=False)
        for _ in range(context_size)
    ]

    outputs_queue = [[
        S2Vs[i](inputs[i])
        for i in range(context_size)
    ]]

    # loop on network level
    for history_size in history_sizes:
        # [s1,s2,s3] -> [[s1], [s1, s2], [s1, s2, s3], [s2], [s2, s3], [s3]]
        tmp = [outputs_queue[-1][i:j] for i, j in itertools.combinations(range(len(outputs_queue[-1])+1), 2)]  # outputs_queue[-1] refers to the outputs of last layer

        if history_size > 0:
            # e.g. history_size=1 -> [[s1, s2], [s2, s3]] -> [s1s2, s2s3]
            concatenateds = [concatenate(ss) for ss in tmp if len(ss)==history_size+1]
        else:
            # history_size=0 -> [[s1], [s2], [s3]] -> [s1, s2, s3]
            concatenateds = [ss[0] for ss in tmp if len(ss)==history_size+1]

        # Define FF
        FFs = [
            Dense(units=n_hidden, activation='tanh')
            for _ in range(len(concatenateds))
        ]

        # add new outputs to outputs_queue
        outputs_queue.append([
            FFs[i](concatenateds[i])
            for i in range(len(concatenateds))
        ])

    # The network should have single output, if history_sizes is well-defined
    assert 1 == len(outputs_queue[-1])

    if with_extra_features:
        inputs = utlis.flatten(inputs)

    model = Model(inputs, outputs_queue[-1], name='base_network')

    plot_model(model, show_shapes=True, to_file=path_to_results+'base_network.png')
    model.summary()

    return model


class Bias(Layer):
    """layer that adds bias to input

    `Bias` implements the operation:
    `output = input + bias`
    `bias` is a bias vector created by the layer

    Note: if the input to the layer has a rank greater than 2, then
    it is flattened prior to the initial dot product with `kernel`.

    # Example

    ```python
        # as first layer in a sequential model:
        model = Sequential()
        model.add(Bias(input_shape=(16,)))
        # now the model will take as input arrays of shape (*, 16)
        # and output arrays of shape (*, 16)

        # after the first layer, you don't need to specify
        # the size of the input anymore:
        model.add(Bias())
    ```

    # Arguments
        bias_initializer: Initializer for the bias vector
        bias_regularizer: Regularizer function applied to the bias vector
        bias_constraint: Constraint function applied to the bias vector

    # Input shape
        nD tensor with shape: `(batch_size, ..., input_dim)`.
        The most common situation would be
        a 2D input with shape `(batch_size, input_dim)`.

    # Output shape
        nD tensor with shape: `(batch_size, ..., input_dim)`.
        For instance, for a 2D input with shape `(batch_size, input_dim)`,
        the output would have shape `(batch_size, input_dim)`.
    """

    @interfaces.legacy_dense_support
    def __init__(self, bias_initializer='zeros',
                 bias_regularizer=None,
                 bias_constraint=None,
                 **kwargs):
        if 'input_shape' not in kwargs and 'input_dim' in kwargs:
            kwargs['input_shape'] = (kwargs.pop('input_dim'),)
        super(Bias, self).__init__(**kwargs)
        self.bias_initializer = initializers.get(bias_initializer)
        self.bias_regularizer = regularizers.get(bias_regularizer)
        self.bias_constraint = constraints.get(bias_constraint)
        self.input_spec = InputSpec(min_ndim=2)
        self.supports_masking = True

    def build(self, input_shape):
        assert len(input_shape) >= 2
        input_dim = input_shape[-1]

        self.bias = self.add_weight(shape=(input_dim,),
                                    initializer=self.bias_initializer,
                                    name='bias',
                                    regularizer=self.bias_regularizer,
                                    constraint=self.bias_constraint)

        self.input_spec = InputSpec(min_ndim=2, axes={-1: input_dim})
        self.built = True

    def call(self, inputs, **kwargs):
        output = K.bias_add(inputs, self.bias)

        return output

    def compute_output_shape(self, input_shape):
        assert input_shape and len(input_shape) >= 2
        assert input_shape[-1]
        output_shape = list(input_shape)
        return tuple(output_shape)

    def get_config(self):
        config = {
            'bias_initializer': initializers.serialize(self.bias_initializer),
            'bias_regularizer': regularizers.serialize(self.bias_regularizer),
            'bias_constraint': constraints.serialize(self.bias_constraint)
        }
        base_config = super(Bias, self).get_config()
        return dict(list(base_config.items()) + list(config.items()))

LD1 = LD
LD2 = LD_alt