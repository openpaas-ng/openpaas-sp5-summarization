import os
import json
import numpy as np
import keras_bert
import keras_transformer
import losses_distances
import tensorflow as tf
from keras.models import load_model
from encoders.LD import Bias
from encoders.attention_with_vec import AttentionWithVec
from encoders.attention_with_context import AttentionWithContext
from encoders.attention_with_time_decay import AttentionWithTimeDecay
from encoders.TIXIER import Sum
from encoders.TIXIER import ConcatenateFeatures
from encoders.TIXIER import ConcatenateContexts
from encoders.S2V import Max


import matplotlib
if os.environ.get('DISPLAY', '') == '':
    # https://matplotlib.org/faq/usage_faq.html#what-is-a-backend
    print('no display found. Using non-interactive Agg backend')
    matplotlib.use('Agg')
import matplotlib.pyplot as plt


def plot_and_save_history(history, path_to_results):
    with open(path_to_results+'training_history.json', 'w') as fp:
        fp.write(json.dumps(history))

    epochs = np.arange(1, len(history['loss'])+1)
    for key in history.keys():
        if key.startswith('val_'):
            k = key.replace('val_', '')

            plt.plot(epochs, history[key])
            plt.title(k)
            plt.ylabel(k)
            plt.xlabel('epoch')

            if k in history.keys():
                plt.plot(epochs, history[k])
                plt.legend(['validation', 'train'])
            else:
                plt.legend(['validation'])

            plt.savefig(path_to_results+k+'.png', dpi=300, bbox_inches='tight')
            plt.clf()


def load_keras_model(path):
    return load_model(
        path,
        custom_objects={
            'tf': tf,
            'Bias': Bias,
            'AttentionWithVec': AttentionWithVec,
            'AttentionWithContext': AttentionWithContext,
            'AttentionWithTimeDecay': AttentionWithTimeDecay,
            'mean_squared_error_loss': losses_distances.mean_squared_error_loss,
            'compute_contrastive_loss_neculoiu': losses_distances.contrastive_loss_neculoiu(),
            'contrastive_loss_hadsell': losses_distances.contrastive_loss_hadsell,
            'triplet_loss': losses_distances.triplet_loss,
            'softmax_triplet_loss': losses_distances.softmax_triplet_loss,
            'softmax_triplet_loss_with_cross_entropy': losses_distances.softmax_triplet_loss_with_cross_entropy,
            'quadruplet_loss': losses_distances.quadruplet_loss,
            'softmax_quadruplet_loss': losses_distances.softmax_quadruplet_loss,
            'manhattan_distance': losses_distances.manhattan_distance,
            'euclidean_distance': losses_distances.euclidean_distance,
            'squared_euclidean_distance': losses_distances.squared_euclidean_distance,
            'cosine_similarity': losses_distances.cosine_similarity,
            'cosine_distance': losses_distances.cosine_distance,
            'exp_neg_manhattan_distance': losses_distances.exp_neg_manhattan_distance,
            'TokenEmbedding': keras_bert.bert.TokenEmbedding,
            'PositionEmbedding': keras_bert.bert.PositionEmbedding,
            'LayerNormalization': keras_bert.bert.LayerNormalization,
            'gelu': keras_bert.bert.gelu,
            'MultiHeadAttention': keras_transformer.transformer.MultiHeadAttention,
            'FeedForward': keras_transformer.transformer.FeedForward,
            'Sum': Sum,
            'Max': Max,
            'ConcatenateFeatures': ConcatenateFeatures,
            'ConcatenateContexts': ConcatenateContexts
        }
    )


def n_tuple(main_network_name):
    return {
        'siamese': 2,
        'triplet': 3,
        'quadruplet': 4
    }[main_network_name]


def flatten(list_of_list):
    return [item for sublist in list_of_list for item in sublist]


def plot_with_embedding_projector(path_to_results, meeting_id, output_embeddings, utterances, utterance_ids, communities):
    path = path_to_results + 'embedding_projector/'
    if not os.path.exists(path):
        os.makedirs(path)

    np.savetxt(path+meeting_id+'_vectors.tsv', output_embeddings, delimiter='\t')

    out = open(path+meeting_id+'_metadata.tsv', 'w')
    out.write('community index(es)\tutterance role\tutterance text\tutterance dialogue act\tutterance id\tutterance index\n')

    labels = []
    for utterance_id in utterance_ids:
        indexes = []
        for index, community in enumerate(communities):
            if utterance_id in community:
                indexes.append(str(index))

        labels.append('\t'.join([
            '-'.join(indexes),
            utterances[meeting_id][utterance_id]['attributes']['role'],
            utterances[meeting_id][utterance_id]['text'],
            utterances[meeting_id][utterance_id]['aspect'],
            utterances[meeting_id][utterance_id]['id'],
            str(utterances[meeting_id][utterance_id]['index']),
        ]))

    out.write('\n'.join(labels))
    out.close()
