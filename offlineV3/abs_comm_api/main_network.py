"""
Siamese, Triplet, Quadruplet Networks
"""
import os
import utlis
import datetime
import losses_distances
import keras.backend as K
from time import time
from keras.models import Model
from keras.optimizers import Adam
from keras.layers import Input, Lambda, concatenate
from keras.callbacks import ModelCheckpoint, EarlyStopping

from encoders.S2V import S2V
from encoders.HAN import HAN
from encoders.LD import LD
from encoders.TIXIER import TIXIER

def train(main_network_name, word_vectors_name, fine_tune_word_vectors, with_extra_features, base_network_name, epochs, loss, distance, l2_normalization, pre_context_size, post_context_size, data_generator_train, X_validation, Y_validation, max_sequence_length, word_vectors, path_to_results, global_metrics):
    context_size = pre_context_size + 1 + post_context_size

    n_hidden = 32
    dropout_rate = 0.5

    loss_function = losses_distances.get_loss_function(name=loss)
    distance_function = losses_distances.get_distance_function(name=distance)
    optimizer = Adam()
    metrics = None  # batch-wise metrics, ['accuracy']

    os.mkdir(path_to_results+'model_on_epoch_end')
    model_checkpoint = ModelCheckpoint(filepath=path_to_results+'model_on_epoch_end/'+'{epoch}.h5', monitor="val_loss", verbose=1, save_best_only=False, save_weights_only=False, mode='min', period=1)
    callbacks = [global_metrics, model_checkpoint]

    # define inputs
    inputs = []

    if with_extra_features:
        for _ in range(utlis.n_tuple(main_network_name)):
            inputs.append(utlis.flatten([
                [Input(shape=(max_sequence_length,), dtype='int32'),
                 Input(shape=(21,), dtype='float32')]
                for _ in range(context_size)
            ]))
    else:
        for _ in range(utlis.n_tuple(main_network_name)):
            inputs.append([
                Input(shape=(max_sequence_length,), dtype='int32')
                for _ in range(context_size)
            ])

    # define sentence_encoder
    if base_network_name == 'LD':
        base_network = LD(
            context_size=context_size,
            history_sizes=(context_size-1, 0),

            input_shape=(max_sequence_length,),
            recurrent_name='LSTM',
            pooling_name='max',
            n_hidden=n_hidden,
            dropout_rate=dropout_rate,
            path_to_results=path_to_results,
            is_base_network=False,

            with_embdedding_layer=True,
            word_vectors_name=word_vectors_name,
            fine_tune_word_vectors=fine_tune_word_vectors,
            word_vectors=word_vectors,

            with_extra_features=with_extra_features
        )
    elif base_network_name == 'HAN':
        base_network = HAN(
            context_size=context_size,

            input_shape=(max_sequence_length,),
            recurrent_name='Bi-GRU',
            pooling_name='attention',
            n_hidden=n_hidden,
            dropout_rate=dropout_rate,
            path_to_results=path_to_results,
            is_base_network=False,

            with_embdedding_layer=True,
            word_vectors_name=word_vectors_name,
            fine_tune_word_vectors=fine_tune_word_vectors,
            word_vectors=word_vectors,

            with_extra_features=with_extra_features
        )
    elif base_network_name == 'TIXIER':
        base_network = TIXIER(
            pre_context_size=pre_context_size,
            post_context_size=post_context_size,

            input_shape=(max_sequence_length,),
            recurrent_name='Bi-GRU',
            pooling_name='attention',
            n_hidden=n_hidden,
            dropout_rate=dropout_rate,
            path_to_results=path_to_results,
            is_base_network=False,

            with_embdedding_layer=True,
            word_vectors_name=word_vectors_name,
            fine_tune_word_vectors=fine_tune_word_vectors,
            word_vectors=word_vectors,

            with_extra_features=with_extra_features
        )
    else:
        base_network = S2V(
            input_shape=(max_sequence_length,),
            recurrent_name=base_network_name,
            pooling_name='attention',
            n_hidden=n_hidden,
            dropout_rate=dropout_rate,
            path_to_results=path_to_results,
            is_base_network=True,

            with_embdedding_layer=True,
            word_vectors_name=word_vectors_name,
            fine_tune_word_vectors=fine_tune_word_vectors,
            word_vectors=word_vectors,

            with_extra_features=with_extra_features
        )

    outputs = [base_network(input) for input in inputs]

    outputs[1] = Lambda(lambda x: x + K.epsilon())(outputs[1])

    if l2_normalization:
        outputs = [Lambda(lambda x: K.l2_normalize(x, axis=-1))(output) for output in outputs]

    if main_network_name == 'siamese':
        d = Lambda(distance_function)([outputs[0], outputs[1]])

        model = Model(utlis.flatten(inputs), d)
    elif main_network_name == 'triplet':
        d_pos = Lambda(distance_function)([outputs[1], outputs[0]])
        d_neg = Lambda(distance_function)([outputs[1], outputs[2]])

        model = Model(utlis.flatten(inputs), concatenate([d_pos, d_neg]))
    elif main_network_name == 'quadruplet':
        d_pos = Lambda(distance_function)([outputs[1], outputs[0]])
        d_neg = Lambda(distance_function)([outputs[1], outputs[2]])
        d_neg_extra = Lambda(distance_function)([outputs[2], outputs[3]])

        model = Model(utlis.flatten(inputs), concatenate([d_pos, d_neg, d_neg_extra]))
    else:
        raise NotImplementedError()

    from keras.utils import plot_model
    plot_model(model, show_shapes=True, to_file=path_to_results+'main_network.png')
    print(model.summary())

    # https://datascience.stackexchange.com/questions/23895/multi-gpu-in-keras
    # https://keras.io/getting-started/faq/#how-can-i-run-a-keras-model-on-multiple-gpus
    # automatically detect and use *Data parallelism* gpus model
    # try:
    #     model = multi_gpu_model(model, gpus=None)
    # except:
    #     pass

    model.compile(loss=loss_function, optimizer=optimizer, metrics=metrics)

    training_start_time = time()

    model_trained = model.fit_generator(
        data_generator_train,
        epochs=epochs,
        validation_data=(X_validation, Y_validation),
        callbacks=callbacks
    )

    print("Training time finished.\n{} epochs in {}".format(epochs, datetime.timedelta(seconds=time() - training_start_time)))

    return {**model_trained.history, **global_metrics.val_scores}
