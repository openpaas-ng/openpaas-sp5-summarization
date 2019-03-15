import os
import json
import dill
import utlis
import numpy as np
import main_network
import matplotlib.pyplot as plt
from datetime import datetime
from omega_index import Omega
from metrics import p_r_f1_at_k
from metrics import GlobalMetrics
from data.data_generation import *
from collections import defaultdict
from sklearn.preprocessing import normalize
from data.data_generator import DataGenerator
from skcmeans.algorithms import Probabilistic
from sklearn.model_selection import train_test_split
from keras.preprocessing.sequence import pad_sequences
from sklearn.metrics.pairwise import euclidean_distances, manhattan_distances
class ProbabilisticManhattan(Probabilistic):
    metric = 'cityblock'

####################
path_to_utterance = 'data/ami-corpus-annotations/output/dialogueActs/'
path_to_summlink = 'data/ami-corpus-annotations/output/summlink/'
path_to_word2vec = 'resource/GoogleNews-vectors-negative300.bin.gz'
path_to_results = 'results/' + str(datetime.now()).replace(' ', '_').split('.')[0] + '/'
os.mkdir(path_to_results)

corpus = 'AMI'  # AMI, ICSI
main_network_name = 'triplet'  # siamese, triplet

word_vectors_name = 'News'  # News, BERT
fine_tune_word_vectors = False  # True, False
with_extra_features = True  # role, index, dialogue act

# (Bi-)LSTM, (Bi-)GRU, LD, HAN, TIXIER
base_network_name = 'TIXIER'
training_batch_size = 16
validation_test_batch_size = 2048
epochs = 30

if main_network_name == 'triplet':
    loss = 'softmax_triplet_loss'
    distance = 'euclidean_distance'
if main_network_name == 'siamese':
    loss = 'mean_squared_error_loss'
    distance = 'exp_neg_manhattan_distance'

l2_normalization = False  # normalize embeddings before distance calculation

if base_network_name in ['LD', 'HAN', 'TIXIER']:
    pre_context_size = 3  # number of previous utterances
    post_context_size = 0  # number of following utterances
else:
    pre_context_size = 0
    post_context_size = 0
####################
l1 = set([file_name.split('.')[0] for file_name in os.listdir(path_to_utterance)])
l2 = set([file_name.split('.')[0] for file_name in os.listdir(path_to_summlink)])
meeting_list = list(l1.intersection(l2))  # {'IB4003', 'TS3012c'}

# 60%, 20%, 20% -> 81, 28, 28 meetings
# meeting_list_train, meeting_list_test = train_test_split(meeting_list, test_size=0.2)
# meeting_list_train, meeting_list_validation = train_test_split(meeting_list_train, test_size=0.25)
meeting_list_train = ['ES2002', 'ES2005', 'ES2006', 'ES2007', 'ES2008', 'ES2009', 'ES2010', 'ES2012', 'ES2013', 'ES2015', 'ES2016', 'IS1000', 'IS1001', 'IS1002', 'IS1003', 'IS1004', 'IS1005', 'IS1006', 'IS1007', 'TS3005', 'TS3008', 'TS3009', 'TS3010', 'TS3011', 'TS3012']
meeting_list_train = utlis.flatten([[mid+c for c in 'abcd'] for mid in meeting_list_train])
meeting_list_train.remove('IS1002a')
meeting_list_train.remove('IS1005d')
meeting_list_train.remove('TS3012c')

meeting_list_validation = ['ES2003', 'ES2011', 'IS1008', 'TS3004', 'TS3006']
meeting_list_validation = utlis.flatten([[mid+c for c in 'abcd'] for mid in meeting_list_validation])

meeting_list_test = ['ES2004', 'ES2014', 'IS1009', 'TS3003', 'TS3007']
meeting_list_test = utlis.flatten([[mid+c for c in 'abcd'] for mid in meeting_list_test])


dill.dump_session(path_to_results + 'variables.pkl')
#dill.load_session(path_to_results + 'variables.pkl')

if word_vectors_name == 'News':
    utterances, max_sequence_length, word_frequency = load_utterances(path_to_utterance, meeting_list)
    print('load ' + path_to_word2vec)
    word_vectors = load_word_vectors(path_to_word2vec, word_frequency)
elif word_vectors_name == 'BERT':
    utterances, max_sequence_length = load_utterances_BERT(path_to_utterance, meeting_list)
    word_vectors = None
else:
    raise ValueError()
utterances = encode_extra_features(corpus, utterances)

####################
print('generate training generator and validation data...')
data_generator_train = DataGenerator(
    path_to_summlink,
    utterances,
    utlis.n_tuple(main_network_name),
    meeting_list_train,
    pre_context_size,
    post_context_size,
    max_sequence_length,
    with_extra_features,
    training_batch_size
)

tuples_validation = generate_tuples(
    path_to_summlink,
    utterances,
    utlis.n_tuple(main_network_name),
    meeting_list_validation,
    pre_context_size,
    post_context_size,
    training=False
)
X_validation, Y_validation = get_X_Y(utlis.n_tuple(main_network_name), tuples_validation, utterances, max_sequence_length, with_extra_features)

global_metric = GlobalMetrics(main_network_name, utterances, pre_context_size, post_context_size, meeting_list_validation, path_to_summlink, max_sequence_length, with_extra_features, validation_test_batch_size, l2_normalization, path_to_results)
####################

history = main_network.train(
    main_network_name, word_vectors_name, fine_tune_word_vectors, with_extra_features, base_network_name,
    epochs, loss, distance, l2_normalization,
    pre_context_size, post_context_size,
    data_generator_train, X_validation, Y_validation,
    max_sequence_length, word_vectors, path_to_results, global_metric
)

utlis.plot_and_save_history(history, path_to_results)

####################
print("load the latest best model based on val_loss...")
# Load *the latest best model* according to the quantity monitored (val_loss)
val_losses = np.array(json.load(open(path_to_results+'training_history.json'))['val_loss'])
best_epoch = np.where(val_losses == val_losses.min())[0][-1] + 1  # epochs count from 1
print(" - best epoch: " + str(best_epoch))
model = utlis.load_keras_model(path_to_results + 'model_on_epoch_end/' + str(best_epoch) + '.h5')

print("test...")
prf1_kv = []
prf1_k10 = []  # average number of utterances per community 9.27 -> 11.4384 by removing singleton communities
omega_scores_cv = []
omega_scores_c11 = []  # average number of communities per meeting 17.28 -> 11.16 by removing nested ones

for mid in meeting_list_test:
    utterance_ids = list(utterances[mid].keys())
    contextualized_utterance_ids = get_contextualized_utterance_ids(utterance_ids, pre_context_size, post_context_size)
    communities = load_communities(path_to_summlink, mid, utterance_ids)

    utterance_ids = list(set(utlis.flatten(communities)))

    records = []
    for utterance_id in utterance_ids:
        uids = contextualized_utterance_ids[utterance_id]
        records.append((
            mid, uids,
            [utterances[mid][uid]['text_tokenized'] for uid in uids],
            [utterances[mid][uid]['attributes']['role_vectorized'] for uid in uids],
            [utterances[mid][uid]['index_scaled'] for uid in uids],
            [utterances[mid][uid]['aspect_vectorized'] for uid in uids]
        ))

    records_dict = dict(zip(
        ['mid', 'uids', 'tokens', 'roles', 'indexes', 'aspects'],
        list(map(list, zip(*records)))
    ))

    X = []

    for col in range(len(records_dict['uids'][0])):
        X.append(pad_sequences(
            [items[col] for items in records_dict['tokens']],
            maxlen=max_sequence_length,
            padding='post'
        ))

        if with_extra_features:
            X.append(
                np.concatenate((
                    [items[col] for items in records_dict['roles']],
                    [items[col] for items in records_dict['indexes']],
                    [items[col] for items in records_dict['aspects']]
                ), axis=-1)
            )

    output_embeddings = model.get_layer('base_network').predict(X, batch_size=validation_test_batch_size)

    if l2_normalization:
        output_embeddings = normalize(output_embeddings, norm='l2', axis=1)

    utlis.plot_with_embedding_projector(path_to_results, mid, output_embeddings, utterances, utterance_ids, communities)
    ####################
    scores_kv = []
    scores_k10 = []
    for community in communities:
        # except singleton community
        if len(community) == 1:
            continue

        sub_scores_kv = []
        sub_scores_k10 = []
        for utterance_id in community:
            if main_network_name == 'triplet':
                distances = utlis.flatten(
                    euclidean_distances(output_embeddings, [output_embeddings[utterance_ids.index(utterance_id)]])
                )
            if main_network_name == 'siamese':
                distances = utlis.flatten(
                    manhattan_distances(output_embeddings, [output_embeddings[utterance_ids.index(utterance_id)]])
                )

            indexes = sorted(range(len(distances)), key=lambda i: distances[i])

            community_predicted = list(np.array(utterance_ids)[indexes])
            community_predicted.remove(utterance_id)

            community_true = list(community)
            community_true.remove(utterance_id)

            sub_scores_kv.append(
                p_r_f1_at_k(community_true, community_predicted, len(community)-1)
            )
            sub_scores_k10.append(
                p_r_f1_at_k(community_true, community_predicted, 10)
            )
        scores_kv.append(
            [np.mean(np.array(sub_scores_kv)[:, i]) for i in range(3)]
        )
        scores_k10.append(
            [np.mean(np.array(sub_scores_k10)[:, i]) for i in range(3)]
        )
    prf1_kv.append(
        [np.mean(np.array(scores_kv)[:, i]) for i in range(3)]
    )
    prf1_k10.append(
        [np.mean(np.array(scores_k10)[:, i]) for i in range(3)]
    )
    print(mid)
    print('prf1_kv', prf1_kv[-1])
    print('prf1_k10', prf1_k10[-1])
    ####################
    communities = remove_nested_communities(communities)
    # from sklearn.decomposition import PCA
    # output_embeddings = PCA(n_components=2).fit_transform(output_embeddings)
    # plt.figure(figsize=(5, 5)).add_subplot(aspect='equal')
    if main_network_name == 'triplet':
        clusterer = Probabilistic(n_clusters=len(communities), n_init=20)
    if main_network_name == 'siamese':
        clusterer = ProbabilisticManhattan(n_clusters=len(communities), n_init=20)
    clusterer.fit(output_embeddings)

    communities_predict = defaultdict(list)
    for j in range(clusterer.n_clusters):
        for i, utterance_id in enumerate(utterance_ids):
            if clusterer.memberships_[i][j] > 0.2:
                communities_predict[j].append(utterance_id)

    tmp = set(utlis.flatten(communities_predict.values()))
    if len(tmp) < len(utterance_ids):
        communities_predict[clusterer.n_clusters] = list(set(utterance_ids) - set(tmp))

    omega_score = Omega(dict(enumerate(communities)), communities_predict).omega_score
    omega_scores_cv.append(omega_score)
    print('omega_scores_cv', omega_scores_cv[-1])
    ####################
    if main_network_name == 'triplet':
        clusterer = Probabilistic(n_clusters=11, n_init=20)
    if main_network_name == 'siamese':
        clusterer = ProbabilisticManhattan(n_clusters=11, n_init=20)
    clusterer.fit(output_embeddings)

    communities_predict = defaultdict(list)
    for j in range(clusterer.n_clusters):
        for i, utterance_id in enumerate(utterance_ids):
            if clusterer.memberships_[i][j] > 0.2:
                communities_predict[j].append(utterance_id)

    tmp = set(utlis.flatten(communities_predict.values()))
    if len(tmp) < len(utterance_ids):
        communities_predict[clusterer.n_clusters] = list(set(utterance_ids) - set(tmp))

    omega_score = Omega(dict(enumerate(communities)), communities_predict).omega_score
    omega_scores_c11.append(omega_score)
    print('omega_scores_c11', omega_scores_c11[-1])

    # xx, yy = np.array(np.meshgrid(np.linspace(-10, 10, 1000), np.linspace(-10, 10, 1000)))
    # z = np.rollaxis(clusterer.calculate_memberships(np.c_[xx.ravel(), yy.ravel()]).reshape(*xx.shape, -1), 2, 0)
    # colors = 'rgbycmrrrr'
    #
    # for membership, color in zip(z, colors):
    #     cp = plt.contour(xx, yy, membership, colors=color, alpha=0.5, levels=[0.3, 0.9])
    #     plt.clabel(cp, inline=False, fontsize=10)
    # plt.scatter(output_embeddings[:, 0], output_embeddings[:, 1], c='k')
    # plt.show()

final_prf1_kv = [np.mean(np.array(prf1_kv)[:, i]) for i in range(3)]
final_prf1_k10 = [np.mean(np.array(prf1_k10)[:, i]) for i in range(3)]
final_omega_scores_cv = np.mean(omega_scores_cv)
final_omega_scores_c11 = np.mean(omega_scores_c11)

print('final_prf1_kv', final_prf1_kv)
print('final_prf1_k10', final_prf1_k10)
print('final_omega_scores_cv', final_omega_scores_cv)
print('final_omega_scores_c11', final_omega_scores_c11)

f = open(path_to_results+'results.txt', 'w')
print('best_epoch', best_epoch, file=f)
print('val_losses_min', val_losses.min(), file=f)
print('final_prf1_kv', final_prf1_kv, file=f)
print('final_prf1_k10', final_prf1_k10, file=f)
print('final_omega_scores_cv', final_omega_scores_cv, file=f)
print('final_omega_scores_c11', final_omega_scores_c11, file=f)
print('prf1_kv', prf1_kv, file=f)
print('prf1_k10', prf1_k10, file=f)
print('omega_scores_cv', omega_scores_cv, file=f)
print('omega_scores_c11', omega_scores_c11, file=f)
f.close()

####################

print("load the latest best model based on val_final_prf1_kv...")
# Load *the latest best model* according to the quantity monitored (val_final_prf1_kv)
val_final_prf1_kvs = np.array(json.load(open(path_to_results+'training_history.json'))['val_final_prf1_kv'])
best_epoch = np.where(val_final_prf1_kvs == val_final_prf1_kvs.max())[0][-1] + 1  # epochs count from 1
print(" - best epoch: " + str(best_epoch))
model = utlis.load_keras_model(path_to_results + 'model_on_epoch_end/' + str(best_epoch) + '.h5')

print("test...")
prf1_kv = []
prf1_k10 = []  # average number of utterances per community 9.27 -> 11.4384 by removing singleton communities
omega_scores_cv = []
omega_scores_c11 = []  # average number of communities per meeting 17.28 -> 11.16 by removing nested ones

for mid in meeting_list_test:
    utterance_ids = list(utterances[mid].keys())
    contextualized_utterance_ids = get_contextualized_utterance_ids(utterance_ids, pre_context_size, post_context_size)
    communities = load_communities(path_to_summlink, mid, utterance_ids)

    utterance_ids = list(set(utlis.flatten(communities)))

    records = []
    for utterance_id in utterance_ids:
        uids = contextualized_utterance_ids[utterance_id]
        records.append((
            mid, uids,
            [utterances[mid][uid]['text_tokenized'] for uid in uids],
            [utterances[mid][uid]['attributes']['role_vectorized'] for uid in uids],
            [utterances[mid][uid]['index_scaled'] for uid in uids],
            [utterances[mid][uid]['aspect_vectorized'] for uid in uids]
        ))

    records_dict = dict(zip(
        ['mid', 'uids', 'tokens', 'roles', 'indexes', 'aspects'],
        list(map(list, zip(*records)))
    ))

    X = []

    for col in range(len(records_dict['uids'][0])):
        X.append(pad_sequences(
            [items[col] for items in records_dict['tokens']],
            maxlen=max_sequence_length,
            padding='post'
        ))

        if with_extra_features:
            X.append(
                np.concatenate((
                    [items[col] for items in records_dict['roles']],
                    [items[col] for items in records_dict['indexes']],
                    [items[col] for items in records_dict['aspects']]
                ), axis=-1)
            )

    output_embeddings = model.get_layer('base_network').predict(X, batch_size=validation_test_batch_size)

    if l2_normalization:
        output_embeddings = normalize(output_embeddings, norm='l2', axis=1)

    utlis.plot_with_embedding_projector(path_to_results, mid, output_embeddings, utterances, utterance_ids, communities)
    ####################
    scores_kv = []
    scores_k10 = []
    for community in communities:
        # except singleton community
        if len(community) == 1:
            continue

        sub_scores_kv = []
        sub_scores_k10 = []
        for utterance_id in community:
            if main_network_name == 'triplet':
                distances = utlis.flatten(
                    euclidean_distances(output_embeddings, [output_embeddings[utterance_ids.index(utterance_id)]])
                )
            if main_network_name == 'siamese':
                distances = utlis.flatten(
                    manhattan_distances(output_embeddings, [output_embeddings[utterance_ids.index(utterance_id)]])
                )
            indexes = sorted(range(len(distances)), key=lambda i: distances[i])

            community_predicted = list(np.array(utterance_ids)[indexes])
            community_predicted.remove(utterance_id)

            community_true = list(community)
            community_true.remove(utterance_id)

            sub_scores_kv.append(
                p_r_f1_at_k(community_true, community_predicted, len(community)-1)
            )
            sub_scores_k10.append(
                p_r_f1_at_k(community_true, community_predicted, 10)
            )
        scores_kv.append(
            [np.mean(np.array(sub_scores_kv)[:, i]) for i in range(3)]
        )
        scores_k10.append(
            [np.mean(np.array(sub_scores_k10)[:, i]) for i in range(3)]
        )
    prf1_kv.append(
        [np.mean(np.array(scores_kv)[:, i]) for i in range(3)]
    )
    prf1_k10.append(
        [np.mean(np.array(scores_k10)[:, i]) for i in range(3)]
    )
    print(mid)
    print('prf1_kv', prf1_kv[-1])
    print('prf1_k10', prf1_k10[-1])
    ####################
    communities = remove_nested_communities(communities)
    # from sklearn.decomposition import PCA
    # output_embeddings = PCA(n_components=2).fit_transform(output_embeddings)
    # plt.figure(figsize=(5, 5)).add_subplot(aspect='equal')
    if main_network_name == 'triplet':
        clusterer = Probabilistic(n_clusters=len(communities), n_init=20)
    if main_network_name == 'siamese':
        clusterer = ProbabilisticManhattan(n_clusters=len(communities), n_init=20)
    clusterer.fit(output_embeddings)

    communities_predict = defaultdict(list)
    for j in range(clusterer.n_clusters):
        for i, utterance_id in enumerate(utterance_ids):
            if clusterer.memberships_[i][j] > 0.2:
                communities_predict[j].append(utterance_id)

    tmp = set(utlis.flatten(communities_predict.values()))
    if len(tmp) < len(utterance_ids):
        communities_predict[clusterer.n_clusters] = list(set(utterance_ids) - set(tmp))

    omega_score = Omega(dict(enumerate(communities)), communities_predict).omega_score
    omega_scores_cv.append(omega_score)
    print('omega_scores_cv', omega_scores_cv[-1])
    ####################
    if main_network_name == 'triplet':
        clusterer = Probabilistic(n_clusters=11, n_init=20)
    if main_network_name == 'siamese':
        clusterer = ProbabilisticManhattan(n_clusters=11, n_init=20)
    clusterer.fit(output_embeddings)

    communities_predict = defaultdict(list)
    for j in range(clusterer.n_clusters):
        for i, utterance_id in enumerate(utterance_ids):
            if clusterer.memberships_[i][j] > 0.2:
                communities_predict[j].append(utterance_id)

    tmp = set(utlis.flatten(communities_predict.values()))
    if len(tmp) < len(utterance_ids):
        communities_predict[clusterer.n_clusters] = list(set(utterance_ids) - set(tmp))

    omega_score = Omega(dict(enumerate(communities)), communities_predict).omega_score
    omega_scores_c11.append(omega_score)
    print('omega_scores_c11', omega_scores_c11[-1])

    # xx, yy = np.array(np.meshgrid(np.linspace(-10, 10, 1000), np.linspace(-10, 10, 1000)))
    # z = np.rollaxis(clusterer.calculate_memberships(np.c_[xx.ravel(), yy.ravel()]).reshape(*xx.shape, -1), 2, 0)
    # colors = 'rgbycmrrrr'
    #
    # for membership, color in zip(z, colors):
    #     cp = plt.contour(xx, yy, membership, colors=color, alpha=0.5, levels=[0.3, 0.9])
    #     plt.clabel(cp, inline=False, fontsize=10)
    # plt.scatter(output_embeddings[:, 0], output_embeddings[:, 1], c='k')
    # plt.show()

final_prf1_kv = [np.mean(np.array(prf1_kv)[:, i]) for i in range(3)]
final_prf1_k10 = [np.mean(np.array(prf1_k10)[:, i]) for i in range(3)]
final_omega_scores_cv = np.mean(omega_scores_cv)
final_omega_scores_c11 = np.mean(omega_scores_c11)

print('final_prf1_kv', final_prf1_kv)
print('final_prf1_k10', final_prf1_k10)
print('final_omega_scores_cv', final_omega_scores_cv)
print('final_omega_scores_c11', final_omega_scores_c11)

f = open(path_to_results+'results_alt.txt', 'w')
print('best_epoch', best_epoch, file=f)
print('val_final_prf1_kv', val_final_prf1_kvs.max(), file=f)
print('final_prf1_kv', final_prf1_kv, file=f)
print('final_prf1_k10', final_prf1_k10, file=f)
print('final_omega_scores_cv', final_omega_scores_cv, file=f)
print('final_omega_scores_c11', final_omega_scores_c11, file=f)
print('prf1_kv', prf1_kv, file=f)
print('prf1_k10', prf1_k10, file=f)
print('omega_scores_cv', omega_scores_cv, file=f)
print('omega_scores_c11', omega_scores_c11, file=f)
f.close()
