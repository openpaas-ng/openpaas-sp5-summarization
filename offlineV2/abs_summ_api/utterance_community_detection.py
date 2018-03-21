# -*- coding: utf-8 -*-
import utils
import numpy as np
import random
import math
import core_rank
from collections import Counter
from scipy import sparse
from sklearn.preprocessing import OneHotEncoder
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import KMeans
from sklearn.cluster import AgglomerativeClustering
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.decomposition import TruncatedSVD
from sklearn.preprocessing import Normalizer
from sklearn.pipeline import make_pipeline


def detection(utterances_indexed_tagged, stopwords, config):
    param = config['UCD']

    # #####################################
    # ### Pre-processing for Clustering ###
    # #####################################
    utterances_processed = []
    for utterance_indexed_tagged in utterances_indexed_tagged:
        index, role, utt_tagged = utterance_indexed_tagged
        utt_cleaned = utils.clean_text(
            utils.remove_tags_from_text(utt_tagged),
            stopwords=stopwords,
            remove_stopwords=True,
            pos_filtering=False,
            stemming=True,
            # clustering based on lowercase form.
            lower_case=True
        )
        # remove utterances with less than min_words number of non-stopwords
        if len(utt_cleaned) >= param.getint('min_words'):
            utterances_processed.append((index, role, ' '.join(utt_cleaned)))

    # ############################
    # ### UTTERANCE CLUSTERING ###
    # ############################
    membership = cluster_utterances(
        utterances_processed,
        algorithm=param.get('algorithm'),
        aware=param.get('aware'),
        n_comms=param.getint('n_comms'),
        feature=param.get('feature'),
        ngram_range=tuple(map(int, param.get('ngram_range').split(','))),
        extra_features=param.get('extra_features').split(','),
        lsa=param.getboolean('lsa'),
        lsa_n_components=param.getint('lsa_n_components'),
        twidf_window_size=param.getint('w')
    )

    # remove communities with less than min_elt number of utterances
    comm_labels = [k for k, v in dict(Counter(membership)).iteritems() if v >= param.getint('min_elt')]

    # ##############
    # ### OUTPUT ###
    # ##############
    communities = []
    for label in comm_labels:
        community = []
        for my_label in [sent[0] for i, sent in enumerate(utterances_processed) if membership[i] == label]:
            to_write = [elt[2] for elt in utterances_indexed_tagged if elt[0] == my_label][0]
            # one utterance per line
            community.append(to_write)
        # separate communities by white line
        communities.append(community)

    return communities


def cluster_utterances(
        utterances_processed,
        algorithm,
        aware,
        n_comms,
        feature,
        ngram_range,
        extra_features,
        lsa,
        lsa_n_components,
        twidf_window_size
):
    n_comms = min(n_comms, len(utterances_processed))
    corpus = [elt[2] for elt in utterances_processed]

    # ############################
    # ### FEATURE CONSTRUCTION ###
    # ############################
    # Row textual feature vector is normalized by l2, which makes KMeans behave as
    # spherical k-means for better results.
    if feature == 'tfidf':
        vectorizer = TfidfVectorizer(stop_words=None, ngram_range=ngram_range, norm='l2')
        feature_matrix = vectorizer.fit_transform(corpus)
    elif feature == 'twidf':
        tokens = [i.split(' ') for i in corpus]
        idf = tf_idf.inverse_document_frequencies(tokens)
        vocab = list(set([item for sublist in tokens for item in sublist]))
        feature_matrix = []
        for token in tokens:
            row = [0] * len(vocab)
            # CoreRank dict for each utterance
            core_rank_scores = core_rank.get_core_rank_scores([token], window_size=twidf_window_size, overspanning=False, weighted=True)
            for w in token:
                row[vocab.index(w)] = core_rank_scores[w] * idf[w]
            feature_matrix.append(row)
        normalizer = Normalizer(norm='l2', copy=False)
        feature_matrix = sparse.csr_matrix(normalizer.fit_transform(feature_matrix))
    elif feature == 'binary':
        vectorizer = TfidfVectorizer(use_idf=False, binary=True, stop_words=None, ngram_range=ngram_range, norm='l2')
        feature_matrix = vectorizer.fit_transform(corpus)
    elif feature == 'tf':
        vectorizer = TfidfVectorizer(use_idf=False, binary=False, stop_words=None, ngram_range=ngram_range, norm='l2')
        feature_matrix = vectorizer.fit_transform(corpus)

    # ##########################
    # ### FEATURE REDUCTION ####
    # ##########################
    if lsa:
        # lsa_n_components = feature_matrix.shape[1]-1
        svd = TruncatedSVD(lsa_n_components)
        # LSA/SVD results are not normalized, we have to redo the normalization.
        normalizer = Normalizer(norm='l2', copy=False)
        lsa = make_pipeline(svd, normalizer)
        feature_matrix = lsa.fit_transform(feature_matrix)
        feature_matrix = sparse.csr_matrix(feature_matrix)

    # ######################
    # ### EXTRA FEATURES ###
    # ######################
    # expanding textual feature matrix by extra features
    if extra_features != ['None']:
        for feature in extra_features:
            if feature == 'length':
                length = np.array([len(i.split(' ')) for i in corpus])
                length_standardized = StandardScaler().fit_transform(length.reshape(-1, 1))
                feature_matrix = sparse.hstack((feature_matrix, length_standardized))
            if feature == 'speaker':
                speaker_list = [elt[1] for elt in utterances_processed]
                speaker_set = list(set(speaker_list))
                tmp = {speaker_set[i]: i for i in range(len(speaker_set))}
                # Encoding categorical feature with one-hot vector
                speaker_encoded = OneHotEncoder().fit_transform([[tmp[s]] for s in speaker_list])
                feature_matrix = sparse.hstack((feature_matrix, speaker_encoded.toarray()))
            if feature == 'position':
                position = np.array([elt[0] for elt in utterances_processed])
                position_standardized = StandardScaler().fit_transform(position.reshape(-1, 1))
                feature_matrix = sparse.hstack((feature_matrix, position_standardized))
        feature_matrix = sparse.csr_matrix(feature_matrix)

    # ##################
    # ### CLUSTERING ###
    # ##################
    if algorithm == 'kmeans':
        if aware == 'None':
            membership = KMeans(n_clusters=n_comms, init='k-means++', n_init=50, random_state=1)\
                .fit_predict(feature_matrix)
            # print membership
        elif aware == 'speaker':
            # n_comms allocation for each speaker based on times of speech
            speaker_list = [elt[1] for elt in utterances_processed]
            speaker_set = list(set(speaker_list))
            speaker_count = dict(Counter(speaker_list))
            n_comms_speaker = {s: math.ceil((float(speaker_count[s]) / len(speaker_list)) * n_comms) for s in speaker_set}

            while sum(n_comms_speaker.values()) > n_comms:
                speaker = random.choice(speaker_set)
                if n_comms_speaker[speaker] > 1:
                    n_comms_speaker[speaker] = n_comms_speaker[speaker] - 1
            print 'speaker awareness n_comms:', n_comms_speaker

            # get indexes of speech for each speaker
            idx = {speaker : [] for speaker in speaker_set}
            for speaker in speaker_set:
                for i in range(len(utterances_processed)):
                    if utterances_processed[i][1] == speaker:
                        idx[speaker].append(i)

            # clustering for each speaker
            memberships = {speaker: [] for speaker in speaker_set}
            offset = 0
            for speaker in speaker_set:
                memberships[speaker] = KMeans(n_clusters=int(n_comms_speaker[speaker]), init='k-means++', n_init=50, random_state=1)\
                    .fit_predict(feature_matrix[idx[speaker]]) + offset
                offset += n_comms_speaker[speaker]

            # merge memberships
            membership = np.zeros(len(utterances_processed))
            for speaker in speaker_set:
                for i in range(len(idx[speaker])):
                    membership[idx[speaker][i]] = memberships[speaker][i]

    if algorithm == 'agglomerative_clustering':
        feature_matrix = feature_matrix.toarray()
        if aware == 'None':
            membership = AgglomerativeClustering(n_clusters=n_comms, affinity='euclidean', linkage='ward')\
                .fit_predict(feature_matrix)
            # print membership
        elif aware == 'speaker':
            # n_comms allocation for each speaker based on times of speech
            speaker_list = [elt[1] for elt in utterances_processed]
            speaker_set = list(set(speaker_list))
            speaker_count = dict(Counter(speaker_list))
            n_comms_speaker = {s : math.ceil((float(speaker_count[s]) / len(speaker_list)) * n_comms) for s in speaker_set}

            while sum(n_comms_speaker.values()) > n_comms:
                speaker = random.choice(speaker_set)
                if n_comms_speaker[speaker] > 1:
                    n_comms_speaker[speaker] = n_comms_speaker[speaker] - 1
            print 'speaker awareness n_comms:', n_comms_speaker

            # get indexes of speech for each speaker
            idx = {speaker: [] for speaker in speaker_set}
            for speaker in speaker_set:
                for i in range(len(utterances_processed)):
                    if utterances_processed[i][1] == speaker:
                        idx[speaker].append(i)

            # clustering for each speaker
            memberships = {speaker: [] for speaker in speaker_set}
            offset = 0
            for speaker in speaker_set:
                if len(idx[speaker]) != 1:
                    memberships[speaker] = AgglomerativeClustering(n_clusters=int(n_comms_speaker[speaker]), affinity='euclidean', linkage='ward')\
                        .fit_predict(feature_matrix[idx[speaker]]) + offset
                else:
                    # ValueError: Found array with 1 sample(s) (shape=(1, 30)) while a minimum of 2 is required by AgglomerativeClustering
                    # In case of only one sample, then this sample belong to cluster id 0
                    memberships[speaker] = [0]
                offset += n_comms_speaker[speaker]

            # merge memberships
            membership = np.zeros(len(utterances_processed))
            for speaker in speaker_set:
                for i in range(len(idx[speaker])):
                    membership[idx[speaker][i]] = memberships[speaker][i]
    return membership
