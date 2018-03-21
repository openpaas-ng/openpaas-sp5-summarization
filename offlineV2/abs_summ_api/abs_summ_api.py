# -*- coding: utf-8 -*-
import os
import time
import codecs
import utils
import configparser
import nltk
import copy
from language_model import LanguageModel
from gensim.models import KeyedVectors
from nltk.tag.stanford import StanfordPOSTagger
from utterance_community_detection import detection
from multi_sentence_compression import compression
from budgeted_submodular_maximization import selection
from flask import Flask, jsonify, request, abort

path_to_root = os.path.dirname(os.path.abspath(__file__)) + '/'
path_to_resources = path_to_root + 'resources/'

config = configparser.ConfigParser(interpolation=configparser.ExtendedInterpolation())
config.read_file(codecs.open(path_to_root + 'config.ini', encoding='utf-8'))

nltk.data.path.append(path_to_resources + 'nltk_data/')

# #########################
# ### RESOURCES LOADING ###
# #########################
resources = {}
for language in ['fr', 'en']:

    print "loading resources..."
    start = time.time()

    URIs = config['URI_' + language]
    stopwords = utils.load_stopwords(
        path_to_resources + URIs['stopwords']
    )

    filler_words = utils.load_filler_words(
        path_to_resources + URIs['filler_words']
    )

    word_vectors = KeyedVectors.load_word2vec_format(
        path_to_resources + URIs['word_vectors'],
        binary=True
    )

    language_model = LanguageModel(
        path_to_resources + URIs['language_model']
    )

    pos_tagger = StanfordPOSTagger(
        model_filename=path_to_resources + URIs['pos_tagger_model'],
        path_to_jar=path_to_resources + URIs['pos_tagger_jar']
    )

    print "time_cost = %.2fs" % (time.time() - start)

    resources[language] = {
        'stopwords': stopwords,
        'filler_words': filler_words,
        'pos_tagger': pos_tagger,
        'word_vectors': word_vectors,
        'language_model': language_model
    }

# ========================================================

app = Flask(__name__) # create a Flask app


@app.route("/")
def index():
    return 'abs_summ'


@app.route('/api', methods=['POST'])
def abs_summ_api():
    if not request.json:
        abort(400)

    post = request.get_json()

    transcript = post['transcription']
    size = int(post['summary_size'])
    lang = post['language']

    speakers = []
    utterances = []
    for item in transcript:
        speakers.append(item['role'])
        utterances.append(utils.clean_utterance(item['text'], resources[lang]['filler_words']))

    utterances_tagged = [
        ' '.join(['/'.join(t) for t in sent])
        for sent in resources[lang]['pos_tagger'].tag_sents([u.split() for u in utterances])
    ]

    data = zip(range(len(utterances)), speakers, utterances_tagged)
    communities = detection(data, resources[lang]['stopwords'], config)
    compressions, graphs = compression(communities, resources[lang]['stopwords'], resources[lang]['word_vectors'], resources[lang]['language_model'], config, lang)
    summary = selection(compressions, utterances, resources[lang]['stopwords'], resources[lang]['word_vectors'], config, size)

    return jsonify({'summary': summary})


@app.route('/api_app', methods=['POST'])
def abs_summ_api_app():
    if not request.json:
        abort(400)

    post = request.get_json()

    transcript = post['transcription']
    size = int(post['summary_size'])
    lang = post['language']

    config_copy = configparser.ConfigParser(interpolation=configparser.ExtendedInterpolation())
    config_copy.read_file(codecs.open(path_to_root + 'config.ini', encoding='utf-8'))

    config_copy['UCD']['n_comms'] = unicode(post['n_comms'])
    config_copy['MSC']['system_name'] = unicode(post['system_name'])
    config_copy['MSC']['nb_words'] = unicode(post['minimum_path_length'])
    config_copy['BSM']['scaling_factor'] = unicode(post['scaling_factor'])
    config_copy['BSM']['lambda'] = unicode(post['lambda'])

    speakers = []
    utterances = []
    for item in transcript:
        speakers.append(item['role'])
        utterances.append(utils.clean_utterance(item['text'], resources[lang]['filler_words']))

    utterances_tagged = [
        ' '.join(['/'.join(t) for t in sent])
        for sent in resources[lang]['pos_tagger'].tag_sents([u.split() for u in utterances])
    ]

    data = zip(range(len(utterances)), speakers, utterances_tagged)
    communities = detection(data, resources[lang]['stopwords'], config_copy)
    compressions, graphs = compression(communities, resources[lang]['stopwords'], resources[lang]['word_vectors'], resources[lang]['language_model'], config_copy, lang)
    summary = selection(compressions, utterances, resources[lang]['stopwords'], resources[lang]['word_vectors'], config_copy, size)

    return jsonify({'summary': summary, 'communities': communities, 'graphs': graphs, 'compressions': compressions})

# =============================================================================

def main():
    app.run() # this will start a local server

if __name__ == '__main__':
    main()