import dill
from omega_index import Omega
from metrics import p_r_f1_at_k
from data.data_generation import *
from collections import defaultdict
from skcmeans.algorithms import Probabilistic
from flask import Flask, jsonify, request
from keras.preprocessing.sequence import pad_sequences
from sklearn.metrics.pairwise import euclidean_distances, manhattan_distances
class ProbabilisticManhattan(Probabilistic):
    metric = 'cityblock'

# ========================================================

path_to_models = 'models/'

current_system = 'siamese'
current_language = 'en'

dill.load_session(path_to_models+current_system+'-'+current_language+'.pkl')
utterances, max_sequence_length, _ = load_utterances(path_to_utterance, meeting_list)
utterances = encode_extra_features(corpus, utterances)
model = utlis.load_keras_model(path_to_models+current_system+'-'+current_language+'.h5')

# ========================================================

app = Flask(__name__)
app.config['JSON_AS_ASCII'] = False


@app.route("/abstractive_summary/<mid>")
def abstractive_summary(mid):
    path_to_abstractive = 'data/ami-corpus-annotations/output/abstractive/'
    return jsonify(json.load(open(path_to_abstractive + mid + '.json')))


@app.route("/transcription/<mid>")
def transcription(mid):
    path_to_utterance = 'data/ami-corpus-annotations/output/dialogueActs/'
    return jsonify(json.load(open(path_to_utterance + mid + '.json')))


@app.route("/abstractive_communities/<mid>")
def abstractive_communities(mid):
    path_to_summlink = 'data/ami-corpus-annotations/output/summlink/'
    utterance_ids = list(utterances[mid].keys())
    communities = load_communities(path_to_summlink, mid, utterance_ids)
    return jsonify(dict(enumerate(communities)))


@app.route("/automatic_abstractive_communities/<mid>")
def automatic_abstractive_communities(mid):
    global current_language
    global current_system
    global utterances
    global max_sequence_length
    global model

    global main_network_name
    global corpus
    global path_to_utterance
    global path_to_summlink
    global meeting_list
    global pre_context_size
    global post_context_size


    lang = request.args.get('lang', default=current_language)
    sys = request.args.get('sys', default=current_system)

    if current_language != lang or current_system != sys:
        current_language = lang
        current_system = sys
        dill.load_session(path_to_models + current_system + '-' + current_language + '.pkl')
        utterances, max_sequence_length, _ = load_utterances(path_to_utterance, meeting_list)
        utterances = encode_extra_features(corpus, utterances)
        model = utlis.load_keras_model(path_to_models + current_system + '-' + current_language + '.h5')
        print('switch to', current_system, current_language)

    meeting_list_test = [mid]

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

            X.append(
                np.concatenate((
                    [items[col] for items in records_dict['roles']],
                    [items[col] for items in records_dict['indexes']],
                    [items[col] for items in records_dict['aspects']]
                ), axis=-1)
            )

        output_embeddings = model.get_layer('base_network').predict(X, batch_size=512)

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
        ####################
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

    final_prf1_kv = [np.mean(np.array(prf1_kv)[:, i]) for i in range(3)]
    final_prf1_k10 = [np.mean(np.array(prf1_k10)[:, i]) for i in range(3)]
    final_omega_scores_cv = np.mean(omega_scores_cv)
    final_omega_scores_c11 = np.mean(omega_scores_c11)

    print('final_prf1_kv', final_prf1_kv)
    print('final_prf1_k10', final_prf1_k10)
    print('final_omega_scores_cv', final_omega_scores_cv)
    print('final_omega_scores_c11', final_omega_scores_c11)

    return jsonify(communities_predict)

# =============================================================================


def main():
    # https://stackoverflow.com/questions/51127344/tensor-is-not-an-element-of-this-graph-deploying-keras-model
    app.run(threaded=False)


if __name__ == '__main__':
    main()
