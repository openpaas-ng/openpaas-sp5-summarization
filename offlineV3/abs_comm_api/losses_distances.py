import keras
import numpy as np
import keras.backend as K
import tensorflow as tf
from scipy.optimize import fsolve

#############################
# Losses
#############################


def get_loss_function(name):
    return globals()[name]


def mean_squared_error_loss(y_true, y_pred):
    return keras.losses.mean_squared_error(y_true, y_pred)


def contrastive_loss_margin_neculoiu(class_weights):
    """
    (Neculoiu etal. 2016) http://www.aclweb.org/anthology/W16-1617
    """
    def contrastive_loss_equations_neculoiu(p, ratio):
        x, y = p
        return y - x ** 2, y - ratio * ((1 - x) ** 2)

    ratio = class_weights[0] / class_weights[1]
    # intersection of loss functions of two cases
    intersection_point = fsolve(lambda p: contrastive_loss_equations_neculoiu(p, ratio), np.array([0, 0]))
    if intersection_point[0] < 0:
        raise ValueError()
    return intersection_point[0]


def contrastive_loss_neculoiu():
    """
    (Neculoiu etal. 2016) http://www.aclweb.org/anthology/W16-1617
    only compatible with cosine_similarity

    https://github.com/keras-team/keras/issues/2121#issuecomment-214551349
    https://github.com/keras-team/keras/issues/9307
    """
    class_weights = {0: 1, 1: 1}
    ratio = class_weights[0] / class_weights[1]
    margin = contrastive_loss_margin_neculoiu(class_weights)

    # print(" - Neculoiu contrastive loss, ratio: " + str(ratio))
    # print(" - Neculoiu contrastive loss, margin: " + str(margin))

    def compute_contrastive_loss_neculoiu(y_true, y_pred):
        positive_case = ratio * K.square(1 - y_pred)
        negative_case = K.square(
            tf.cast(y_pred >= margin, y_pred.dtype) * y_pred  # set y_pred[y_pred<margin]=0
        )

        return K.mean(y_true * positive_case + (1 - y_true) * negative_case, axis=-1)
    return compute_contrastive_loss_neculoiu


def contrastive_loss_hadsell(y_true, y_pred):
    """
    (Hadsell et al.2006) http://yann.lecun.com/exdb/publis/pdf/hadsell-chopra-lecun-06.pdf
    https://www.quora.com/When-training-siamese-networks-how-does-one-determine-the-margin-for-contrastive-loss-How-do-you-convert-this-loss-to-accuracy
    """
    margin = 0.2
    positive_case = 0.5 * K.square(y_pred)
    negative_case = 0.5 * K.square(K.maximum(0, margin - y_pred))
    return K.mean(y_true * positive_case + (1 - y_true) * negative_case, axis=-1)


def triplet_loss(y_true, y_pred):
    """
    (Schroff et al. 2015) https://arxiv.org/pdf/1503.03832.pdf
    https://stats.stackexchange.com/questions/248511/purpose-of-l2-normalization-for-triplet-network
    """
    margin = 0.2
    return K.mean(tf.maximum(0., y_pred[:, 0:1] - y_pred[:, 1:2] + margin), axis=-1)


def softmax_triplet_loss(y_true, y_pred):
    """
    (Hoffer and Ailon. 2015) https://arxiv.org/pdf/1412.6622.pdf
    """
    y_true = K.concatenate([K.zeros_like(y_true), K.ones_like(y_true)], axis=-1)
    y_pred = K.softmax(y_pred, axis=-1)

    # y_true = K.print_tensor(y_true, message='y_true: ')
    # y_pred = K.print_tensor(y_pred, message='y_pred: ')

    return mean_squared_error_loss(y_true, y_pred)


def softmax_triplet_loss_with_cross_entropy(y_true, y_pred):
    """
    Variation of softmax_triplet_loss
    """
    y_true = K.flatten(K.ones_like(y_true, dtype='int32'))

    # y_true = K.print_tensor(y_true)
    return tf.nn.sparse_softmax_cross_entropy_with_logits(logits=y_pred, labels=y_true)


def quadruplet_loss(y_true, y_pred):
    """
    (Chen et al. 2017) https://arxiv.org/pdf/1704.01719.pdf
    """
    margin_1 = 0.2
    margin_2 = 0.2
    return K.mean(
        tf.maximum(0., y_pred[:, 0:1] - y_pred[:, 1:2] + margin_1) + tf.maximum(0., y_pred[:, 0:1] - y_pred[:, 2:3] + margin_2),
        axis=-1
    )


def softmax_quadruplet_loss(y_true, y_pred):
    y_true = K.concatenate([K.zeros_like(y_true), K.ones_like(y_true)], axis=-1)

    y_pred_1 = K.softmax(y_pred[:, 0:2], axis=-1)
    y_pred_2 = K.softmax(K.concatenate([y_pred[:, 0:1], y_pred[:, 2:3]]), axis=-1)

    return mean_squared_error_loss(y_true, y_pred_1) + mean_squared_error_loss(y_true, y_pred_2)


#############################
# Distances
#############################


def get_distance_function(name):
    return globals()[name]


def manhattan_distance(vectors):
    return K.sum(K.abs(vectors[0] - vectors[1]), axis=-1, keepdims=True)


def euclidean_distance(vectors):
    return K.sqrt(squared_euclidean_distance(vectors))


def squared_euclidean_distance(vectors):
    return K.sum(K.square(vectors[0] - vectors[1]), axis=-1, keepdims=True)


def cosine_similarity(vectors):
    """
    (Neculoiu et al. 2016) http://www.aclweb.org/anthology/W16-1617
    only compatible with contrastive_loss_neculoiu
    """
    return K.sum(K.l2_normalize(vectors[0], axis=-1) * K.l2_normalize(vectors[1], axis=-1), axis=-1, keepdims=True)


def cosine_distance(vectors):
    return 1 - cosine_similarity(vectors)


def exp_neg_manhattan_distance(vectors):
    """
    (Mueller and Thyagarajan. 2016) http://www.mit.edu/~jonasm/info/MuellerThyagarajan_AAAI16.pdf
    only compatible with mean_squared_error_loss for siamese
    """
    return K.exp(-manhattan_distance(vectors))
