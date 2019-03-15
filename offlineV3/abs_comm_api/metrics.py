from collections import defaultdict
from keras.callbacks import Callback
from data.data_generation import *
from omega_index import Omega
from sklearn.metrics.pairwise import euclidean_distances, manhattan_distances
from keras.preprocessing.sequence import pad_sequences
from sklearn.preprocessing import normalize
from skcmeans.algorithms import Probabilistic
class ProbabilisticManhattan(Probabilistic):
    metric = 'cityblock'


def p_r_f1_at_k(actual, predicted, k):
    act_set = set(actual)
    pred_set = set(predicted[:k])

    precision = len(act_set & pred_set) / float(k)
    recall = len(act_set & pred_set) / float(len(act_set))
    try:
        f1_score = 2 * (precision * recall) / (precision + recall)
    except ZeroDivisionError:
        f1_score = 0.

    return precision, recall, f1_score


class GlobalMetrics(Callback):
    def __init__(self, main_network_name, utterances, pre_context_size, post_context_size, meeting_list_validation, path_to_summlink, max_sequence_length, with_extra_features, validation_test_batch_size, l2_normalization, path_to_results):
        self.main_network_name = main_network_name
        self.utterances = utterances
        self.pre_context_size = pre_context_size
        self.post_context_size = post_context_size
        self.meeting_list_validation = meeting_list_validation
        self.path_to_summlink = path_to_summlink
        self.max_sequence_length = max_sequence_length
        self.with_extra_features = with_extra_features
        self.validation_test_batch_size = validation_test_batch_size
        self.l2_normalization = l2_normalization
        self.path_to_results = path_to_results

        super(GlobalMetrics, self).__init__()

    def on_train_begin(self, logs={}):
        self.val_scores = defaultdict(list)

    def on_epoch_end(self, epoch, logs={}):
        prf1_kv = []
        prf1_k10 = []  # average number of utterances per community 9.27 -> 11.4384 by removing singleton communities
        omega_scores_cv = []
        omega_scores_c11 = []  # average number of communities per meeting 17.28 -> 11.16 by removing nested ones

        for mid in self.meeting_list_validation:
            utterance_ids = list(self.utterances[mid].keys())
            contextualized_utterance_ids = get_contextualized_utterance_ids(utterance_ids, self.pre_context_size,
                                                                            self.post_context_size)
            communities = load_communities(self.path_to_summlink, mid, utterance_ids)

            utterance_ids = list(set(utlis.flatten(communities)))

            records = []
            for utterance_id in utterance_ids:
                uids = contextualized_utterance_ids[utterance_id]
                records.append((
                    mid, uids,
                    [self.utterances[mid][uid]['text_tokenized'] for uid in uids],
                    [self.utterances[mid][uid]['attributes']['role_vectorized'] for uid in uids],
                    [self.utterances[mid][uid]['index_scaled'] for uid in uids],
                    [self.utterances[mid][uid]['aspect_vectorized'] for uid in uids]
                ))

            records_dict = dict(zip(
                ['mid', 'uids', 'tokens', 'roles', 'indexes', 'aspects'],
                list(map(list, zip(*records)))
            ))

            X = []

            for col in range(len(records_dict['uids'][0])):
                X.append(pad_sequences(
                    [items[col] for items in records_dict['tokens']],
                    maxlen=self.max_sequence_length,
                    padding='post'
                ))

                if self.with_extra_features:
                    X.append(
                        np.concatenate((
                            [items[col] for items in records_dict['roles']],
                            [items[col] for items in records_dict['indexes']],
                            [items[col] for items in records_dict['aspects']]
                        ), axis=-1)
                    )

            output_embeddings = self.model.get_layer('base_network').predict(X, batch_size=self.validation_test_batch_size)

            if self.l2_normalization:
                output_embeddings = normalize(output_embeddings, norm='l2', axis=1)

            #utlis.plot_with_embedding_projector(path_to_results, mid, output_embeddings, utterances, utterance_ids, communities)
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
                    if self.main_network_name == 'triplet':
                        distances = utlis.flatten(
                            euclidean_distances(output_embeddings, [output_embeddings[utterance_ids.index(utterance_id)]])
                        )
                    if self.main_network_name == 'siamese':
                        distances = utlis.flatten(
                            manhattan_distances(output_embeddings, [output_embeddings[utterance_ids.index(utterance_id)]])
                        )

                    indexes = sorted(range(len(distances)), key=lambda i: distances[i])

                    community_predicted = list(np.array(utterance_ids)[indexes])
                    community_predicted.remove(utterance_id)

                    community_true = list(community)
                    community_true.remove(utterance_id)

                    sub_scores_kv.append(
                        p_r_f1_at_k(community_true, community_predicted, len(community) - 1)
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
            # print(mid)
            # print('prf1_kv', prf1_kv[-1])
            # print('prf1_k10', prf1_k10[-1])
            ####################
            communities = remove_nested_communities(communities)
            # from sklearn.decomposition import PCA
            # output_embeddings = PCA(n_components=2).fit_transform(output_embeddings)
            # plt.figure(figsize=(5, 5)).add_subplot(aspect='equal')
            if self.main_network_name == 'triplet':
                clusterer = Probabilistic(n_clusters=len(communities), n_init=20)
            if self.main_network_name == 'siamese':
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
            # print('omega_scores_cv', omega_scores_cv[-1])
            ####################
            if self.main_network_name == 'triplet':
                clusterer = Probabilistic(n_clusters=11, n_init=20)
            if self.main_network_name == 'siamese':
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
            # print('omega_scores_c11', omega_scores_c11[-1])

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

        self.val_scores['val_final_prf1_kv'].append(final_prf1_kv)
        self.val_scores['val_final_prf1_k10'].append(final_prf1_k10)
        self.val_scores['val_final_omega_scores_cv'].append(final_omega_scores_cv)
        self.val_scores['val_final_omega_scores_c11'].append(final_omega_scores_c11)

        print(' - final_prf1_kv', final_prf1_kv)
        print(' - final_prf1_k10', final_prf1_k10)
        print(' - final_omega_scores_cv', final_omega_scores_cv)
        print(' - final_omega_scores_c11', final_omega_scores_c11)

        f = open(self.path_to_results + 'model_on_epoch_end/' + str(epoch+1) + '_results.txt', 'w')
        print('current_epoch', epoch+1, file=f)
        print('final_prf1_kv', final_prf1_kv, file=f)
        print('final_prf1_k10', final_prf1_k10, file=f)
        print('final_omega_scores_cv', final_omega_scores_cv, file=f)
        print('final_omega_scores_c11', final_omega_scores_c11, file=f)
        print('prf1_kv', prf1_kv, file=f)
        print('prf1_k10', prf1_k10, file=f)
        print('omega_scores_cv', omega_scores_cv, file=f)
        print('omega_scores_c11', omega_scores_c11, file=f)
        f.close()
        return
