import re
import json
import utlis
import string
import codecs
import itertools
import numpy as np
from sklearn.utils import shuffle
from gensim.models import Word2Vec
from collections import OrderedDict
from tokenization_BERT import FullTokenizer
from sklearn.preprocessing import LabelBinarizer
from keras.preprocessing.sequence import pad_sequences


def preprocess_text(text):
    # original_text = str(text)
    text = str(text)

    # printable
    text = ''.join([ch for ch in text if ch in string.printable])

    # clean ASR tags <vocalsound>, <disfmarker>, etc.
    text = re.sub(r"<[a-z]+>", "", text)

    # l_c_d_ -> lcd
    text = re.sub(r"\_", "", text)

    # initial ellipsis
    text = re.sub(r"'Kay", 'Okay', text)
    text = re.sub(r"'kay", 'okay', text)
    text = re.sub(r"'scuse", 'excuse', text)
    text = re.sub(r"'til", 'until', text)
    text = re.sub(r"'Cause", 'Because', text)
    text = re.sub(r"'cause", 'because', text)
    text = re.sub(r"'em", 'them', text)

    # print(text)
    # print(original_text)

    return text


def load_utterances(path_to_utterance, meeting_list, truncate_length=None):
    utterances_of_meetings = OrderedDict()
    max_sequence_length = 0

    vocabulary = {'<unk>': 0}
    inverse_vocabulary = ['<unk>']
    word_frequency = OrderedDict({'<unk>': 0})

    for meeting_id in meeting_list:
        utterances = OrderedDict()
        for utterance in json.load(open(path_to_utterance + meeting_id + '.json')):
            # text to number representation
            t2n = []
            for word in preprocess_text(utterance['text']).split():
                if word not in vocabulary.keys():
                    new_word_index = len(inverse_vocabulary)
                    t2n.append(new_word_index)

                    vocabulary[word] = new_word_index
                    inverse_vocabulary.append(word)
                    word_frequency[word] = 1
                else:
                    t2n.append(vocabulary[word])
                    word_frequency[word] += 1

            if len(t2n) == 0:
                continue

            if len(t2n) > max_sequence_length:
                max_sequence_length = len(t2n)

            if truncate_length is not None:
                t2n = t2n[:truncate_length]

            utterance['text_tokenized'] = t2n
            utterance['text_cleaned'] = ' '.join([inverse_vocabulary[n] for n in t2n])
            utterances[utterance['id']] = utterance

        utterances_of_meetings[meeting_id] = utterances

    if truncate_length is not None:
        max_sequence_length = truncate_length
    return utterances_of_meetings, max_sequence_length, word_frequency


def load_utterances_BERT(path_to_utterance, meeting_list, truncate_length=None):
    utterances_of_meetings = OrderedDict()
    max_sequence_length = 0

    vocabulary = {}
    inverse_vocabulary = []

    vocab_file = 'resource/cased_L-12_H-768_A-12/vocab.txt'
    with codecs.open(vocab_file, 'r', 'utf8') as reader:
        for line in reader:
            token = line.strip()
            vocabulary[token] = len(vocabulary)
            inverse_vocabulary.append(token)
    tokenizer = FullTokenizer(vocab_file, do_lower_case=False)

    for meeting_id in meeting_list:
        utterances = OrderedDict()
        for utterance in json.load(open(path_to_utterance + meeting_id + '.json')):
            # text to number representation
            t2n = []
            for word in tokenizer.tokenize(preprocess_text(utterance['text'])):
                if word not in vocabulary.keys():
                    t2n.append(vocabulary['[UNK]'])
                    print(word)
                else:
                    t2n.append(vocabulary[word])

            if len(t2n) == 0:
                continue

            if len(t2n) > max_sequence_length:
                max_sequence_length = len(t2n)

            if truncate_length is not None:
                t2n = t2n[:truncate_length]

            utterance['text_tokenized'] = [vocabulary['[CLS]']]+t2n+[vocabulary['[SEP]']]
            utterance['text_cleaned'] = ' '.join([inverse_vocabulary[n] for n in utterance['text_tokenized']])

            utterances[utterance['id']] = utterance

        utterances_of_meetings[meeting_id] = utterances

    if truncate_length is not None:
        max_sequence_length = truncate_length
    return utterances_of_meetings, max_sequence_length


def encode_extra_features(corpus, utterances_of_meeting):
    if corpus == 'AMI':
        roles = ['PM', 'ID', 'UI', 'ME']
        aspects = [
            'be.pos', 'el.und', 'el.inf',
            'bck', 'inf', 'off', 'und',
            'be.neg', 'el.ass', 'fra',
            'el.sug', 'stl', 'None',
            'ass', 'oth', 'sug'
        ]
    else:
        raise NotImplementedError()

    role_lb = dict(zip(roles, LabelBinarizer().fit_transform(roles)))
    aspect_lb = dict(zip(aspects, LabelBinarizer().fit_transform(aspects)))

    for meeting_id, utterances in utterances_of_meeting.items():
        n_utterances = float(len(utterances.keys()))

        for index, item in enumerate(utterances.items()):
            utterance_id, utterance = item

            utterance['attributes']['role_vectorized'] = role_lb[utterance['attributes']['role']]
            utterance['aspect_vectorized'] = aspect_lb[utterance['aspect']]

            utterance['index'] = index
            utterance['index_scaled'] = [index / n_utterances]

    return utterances_of_meeting


def load_word_vectors(path_to_word2vec, word_frequency):
    word_vector_dim = 300
    vocabulary = list(word_frequency.keys())

    word_vectors = Word2Vec(size=word_vector_dim, min_count=1)
    word_vectors.build_vocab_from_freq(word_frequency)

    word_vectors.intersect_word2vec_format(path_to_word2vec, binary=True)

    # Words without an entry in the binary file are silently initialized to random values.
    # We can detect those vectors via their norms which approach zero.
    words_zero_norm = [word for word in word_vectors.wv.vocab if np.linalg.norm(word_vectors[word]) < 0.05]
    print(' - words not in GoogleNews: %d / %d' % (len(words_zero_norm), len(vocabulary) - 1), words_zero_norm[:50])

    embeddings = np.zeros((len(vocabulary), word_vector_dim))
    # the 0-th row, used for <unk>, stays at zero
    for index, word in enumerate(vocabulary[1:], 1):
        embeddings[index] = word_vectors[word]

    from sklearn.decomposition import PCA
    embeddings = PCA(n_components=21).fit_transform(embeddings)
    embeddings[0] = 0

    return embeddings


def get_contextualized_utterance_ids(utterance_ids, pre_context_size, post_context_size):
    n_utterances = len(utterance_ids)

    # A dictionary {utterance_id: context} that map and extend utterance_id by including its left and right context
    contextualized_utterance_ids = OrderedDict()

    for index, utterance_id in enumerate(utterance_ids):
        context = []
        for i in range(index - pre_context_size, index + post_context_size + 1):
            # ! boundary expanding
            context.append(utterance_ids[i if i < n_utterances else i - n_utterances])
        contextualized_utterance_ids[utterance_id] = context

    return contextualized_utterance_ids


def load_communities(path_to_summlink, meeting_id, utterance_ids):
    # load Abstractive-Extractive linking (abstractive communities)
    communities = []
    for summlink in json.load(open(path_to_summlink + meeting_id + '.json')):
        community = set()
        for extractive in summlink['extractive']:
            # remove unwanted utterance_ids
            if extractive['id'] in utterance_ids:
                community.add(extractive['id'])
                # print(extractive['text'])
            # else:
            #     print(extractive)
        if len(community) == 0:
            continue

        if community not in communities:
            communities.append(community)

    communities = [list(community) for community in communities]

    return communities


def remove_nested_communities(communities):
    new_communities = []
    for i in range(len(communities)):
        nested = False
        for j in range(len(communities)):
            if i == j:
                continue
            if set(communities[i]).issubset(set(communities[j])):
                nested = True
                break

        if nested is False:
            new_communities.append(communities[i])
    return new_communities


def generate_tuples(path_to_summlink, utterances, n_tuple, meeting_list, pre_context_size, post_context_size, training):
    # convert singleton_community (community contains single utterance) to a tuple
    singleton_community_to_tuple = True

    tuples_of_meetings = {}
    for meeting_id in meeting_list:
        utterance_ids = list(utterances[meeting_id].keys())
        contextualized_utterance_ids = get_contextualized_utterance_ids(utterance_ids, pre_context_size, post_context_size)
        communities = load_communities(path_to_summlink, meeting_id, utterance_ids)

        # generate pairs
        if n_tuple == 2:
            genuine_label = 1
            impostor_label = 0

            genuine_pairs = []
            for community in communities:
                if len(community) == 1:
                    if singleton_community_to_tuple is True:
                        genuine_pairs += [tuple(community*2)]
                    else:
                        pass
                else:
                    genuine_pairs += list(itertools.combinations(community, 2))

            all_pairs = list(itertools.combinations(list(set(utlis.flatten(communities))), 2))

            impostor_pairs = list(set(all_pairs) - set(genuine_pairs) - set([(t[1], t[0]) for t in genuine_pairs]))

            volume = len(list(itertools.combinations(communities, 2)))
            if training is False:
                volume *= 1

            replacement = False
            if volume > len(genuine_pairs):
                replacement=True

            genuine_pairs = list(np.array(genuine_pairs)[np.random.choice(len(genuine_pairs), size=volume, replace=replacement)])
            impostor_pairs = list(np.array(impostor_pairs)[np.random.choice(len(impostor_pairs), size=volume, replace=False)])

            tuples_of_meetings[meeting_id] = \
                [(contextualized_utterance_ids[pair[0]], contextualized_utterance_ids[pair[1]], genuine_label) for pair in genuine_pairs] +\
                [(contextualized_utterance_ids[pair[0]], contextualized_utterance_ids[pair[1]], impostor_label) for pair in impostor_pairs]

        # generate triplets
        elif n_tuple == 3:
            all_tuples = []

            for community_i, community_j in itertools.combinations(communities, 2):
                set_i = set(community_i)
                set_j = set(community_j)
                tuples = []

                if len(set_i & set_j) == 0:
                    if len(community_i) == 1:
                        if singleton_community_to_tuple is True:
                            genuine_pairs = [tuple(community_i * 2)]
                            tuples += list(itertools.product(genuine_pairs, community_j))
                        else:
                            pass
                    else:
                        genuine_pairs = list(itertools.permutations(community_i, 2))
                        tuples += list(itertools.product(genuine_pairs, community_j))

                    if len(community_j) == 1:
                        if singleton_community_to_tuple is True:
                            genuine_pairs = [tuple(community_j * 2)]
                            tuples += list(itertools.product(genuine_pairs, community_i))
                        else:
                            pass
                    else:
                        genuine_pairs = list(itertools.permutations(community_j, 2))
                        tuples += list(itertools.product(genuine_pairs, community_i))

                else:
                    if set_i.issubset(set_j):
                        A = list(set_j - set_i)
                        B = list(set_i)
                        D = []
                        for community in communities:
                            if len(set(community).intersection(set_j)) == 0:
                                D += community

                        if len(A) == 1:
                            genuine_pairs = [tuple(A * 2)]
                            tuples += list(itertools.product(genuine_pairs, B))
                        else:
                            genuine_pairs = list(itertools.permutations(A, 2))
                            tuples += list(itertools.product(genuine_pairs, B))

                        if len(B) == 1:
                            genuine_pairs = [tuple(B * 2)]
                            tuples += list(itertools.product(genuine_pairs, A))
                        else:
                            genuine_pairs = list(itertools.permutations(B, 2))
                            tuples += list(itertools.product(genuine_pairs, A))

                        genuine_pairs = list(itertools.product(A, B)) + list(itertools.product(B, A))
                        tuples += list(itertools.product(genuine_pairs, D))
                    elif set_j.issubset(set_i):
                        A = list(set_i - set_j)
                        B = list(set_j)
                        D = []
                        for community in communities:
                            if len(set(community).intersection(set_i)) == 0:
                                D += community

                        if len(A) == 1:
                            genuine_pairs = [tuple(A * 2)]
                            tuples += list(itertools.product(genuine_pairs, B))
                        else:
                            genuine_pairs = list(itertools.permutations(A, 2))
                            tuples += list(itertools.product(genuine_pairs, B))

                        if len(B) == 1:
                            genuine_pairs = [tuple(B * 2)]
                            tuples += list(itertools.product(genuine_pairs, A))
                        else:
                            genuine_pairs = list(itertools.permutations(B, 2))
                            tuples += list(itertools.product(genuine_pairs, A))

                        genuine_pairs = list(itertools.product(A, B)) + list(itertools.product(B, A))
                        tuples += list(itertools.product(genuine_pairs, D))
                    else:
                        A = list(set_i - set_j)
                        B = list(set_i & set_j)
                        C = list(set_j - set_i)
                        D = []
                        for community in communities:
                            if len(set(community).intersection(set_i|set_j)) == 0:
                                D += community

                        if len(A) == 1:
                            genuine_pairs = [tuple(A * 2)]
                            tuples += list(itertools.product(genuine_pairs, B))
                        else:
                            genuine_pairs = list(itertools.permutations(A, 2))
                            tuples += list(itertools.product(genuine_pairs, B))

                        if len(B) == 1:
                            genuine_pairs = [tuple(B * 2)]
                            tuples += list(itertools.product(genuine_pairs, A))
                        else:
                            genuine_pairs = list(itertools.permutations(B, 2))
                            tuples += list(itertools.product(genuine_pairs, A))

                        genuine_pairs = list(itertools.product(A, B)) + list(itertools.product(B, A))
                        tuples += list(itertools.product(genuine_pairs, D))

                        if len(C) == 1:
                            genuine_pairs = [tuple(C * 2)]
                            tuples += list(itertools.product(genuine_pairs, B))
                        else:
                            genuine_pairs = list(itertools.permutations(C, 2))
                            tuples += list(itertools.product(genuine_pairs, B))

                        if len(B) == 1:
                            genuine_pairs = [tuple(B * 2)]
                            tuples += list(itertools.product(genuine_pairs, C))
                        else:
                            genuine_pairs = list(itertools.permutations(B, 2))
                            tuples += list(itertools.product(genuine_pairs, C))

                        genuine_pairs = list(itertools.product(C, B)) + list(itertools.product(B, C))
                        tuples += list(itertools.product(genuine_pairs, D))

                        if len(A) == 1:
                            genuine_pairs = [tuple(A * 2)]
                            tuples += list(itertools.product(genuine_pairs, C))
                        else:
                            genuine_pairs = list(itertools.permutations(A, 2))
                            tuples += list(itertools.product(genuine_pairs, C))

                        if len(C) == 1:
                            genuine_pairs = [tuple(C * 2)]
                            tuples += list(itertools.product(genuine_pairs, A))
                        else:
                            genuine_pairs = list(itertools.permutations(C, 2))
                            tuples += list(itertools.product(genuine_pairs, A))

                        genuine_pairs = list(itertools.product(B, A))
                        tuples += list(itertools.product(genuine_pairs, C))

                        genuine_pairs = list(itertools.product(B, C))
                        tuples += list(itertools.product(genuine_pairs, A))
                if training:
                    all_tuples += list(np.array(tuples)[np.random.choice(len(tuples), size=1, replace=False)])
                else:
                    all_tuples += list(np.array(tuples)[np.random.choice(len(tuples), size=1, replace=True)])

            tuples_of_meetings[meeting_id] = [
                (contextualized_utterance_ids[triplet[0][0]], contextualized_utterance_ids[triplet[0][1]], contextualized_utterance_ids[triplet[1]])
                for triplet in all_tuples
            ]

        # generate quadruplets
        elif n_tuple == 4:
            pass
        else:
            raise NotImplementedError()

    return tuples_of_meetings


def get_X_Y(n_tuple, tuples_of_meetings, utterances, max_sequence_length, with_extra_features):
    records_dict = {}
    if n_tuple == 2:
        records = []
        for mid in tuples_of_meetings.keys():
            for pair in tuples_of_meetings[mid]:
                uids = pair[0] + pair[1]
                records.append((
                    mid, uids,
                    [utterances[mid][uid]['text_tokenized'] for uid in uids],
                    [utterances[mid][uid]['attributes']['role_vectorized'] for uid in uids],
                    [utterances[mid][uid]['index_scaled'] for uid in uids],
                    [utterances[mid][uid]['aspect_vectorized'] for uid in uids],
                    pair[2]
                ))

        records_dict = dict(zip(
            ['mid', 'uids', 'tokens', 'roles', 'indexes', 'aspects', 'genuine'],
            list(map(list, zip(*shuffle(records))))
        ))

    if n_tuple == 3:
        records = []
        for mid in tuples_of_meetings.keys():
            for triplet in tuples_of_meetings[mid]:
                uids = triplet[0] + triplet[1] + triplet[2]
                records.append((
                    mid, uids,
                    [utterances[mid][uid]['text_tokenized'] for uid in uids],
                    [utterances[mid][uid]['attributes']['role_vectorized'] for uid in uids],
                    [utterances[mid][uid]['index_scaled'] for uid in uids],
                    [utterances[mid][uid]['aspect_vectorized'] for uid in uids]
                ))

        records_dict = dict(zip(
            ['mid', 'uids', 'tokens', 'roles', 'indexes', 'aspects'],
            list(map(list, zip(*shuffle(records))))
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

    Y = np.array(records_dict['genuine']) if n_tuple == 2 else -np.ones(len(records_dict['mid']), dtype=int)

    return X, Y
