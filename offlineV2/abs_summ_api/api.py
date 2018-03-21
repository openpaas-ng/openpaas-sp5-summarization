# -*- coding: utf-8 -*-
import os
import time
import json
import codecs
import utils
import configparser
from language_model import LanguageModel
from gensim.models import KeyedVectors
from nltk.tag.stanford import StanfordPOSTagger
from utterance_community_detection import detection
from multi_sentence_compression import compression
from budgeted_submodular_maximization import selection

path = 'asr_info_french.json'
summary_size = 100
language = 'fr' # en, fr

path_to_root = os.path.dirname(os.path.abspath(__file__)) + '/'
path_to_resources = path_to_root + 'resources/'

config = configparser.ConfigParser(interpolation=configparser.ExtendedInterpolation())
config.read_file(codecs.open('config.ini', encoding='utf-8'))

# #########################
# ### RESOURCES LOADING ###
# #########################
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

# #############################
# ### CORPUS PRE-PROCESSING ###
# #############################
print "pos..."
start = time.time()
speakers = []
utterances = []
for item in json.load(codecs.open(path, encoding='utf-8'), encoding='utf-8'):
    speakers.append(item['role'])
    utterances.append(utils.clean_utterance(item['text'], filler_words))

utterances_tagged = [
    ' '.join(['/'.join(t) for t in sent])
    for sent in pos_tagger.tag_sents([u.split() for u in utterances])
]
print "time_cost = %.2fs" % (time.time() - start)

data = zip(range(len(utterances)), speakers, utterances_tagged)
print "UCD..."
start = time.time()
communities = detection(data, stopwords, config)
print "time_cost = %.2fs" % (time.time() - start)
for c in communities[0]:
    print utils.remove_tags_from_text(c)

print "MSC..."
start = time.time()
compressions, graphs = compression(communities, stopwords, word_vectors, language_model, config, language)
print "time_cost = %.2fs" % (time.time() - start)
print compressions[0]

print "BSM..."
start = time.time()
summary = selection(compressions, utterances, stopwords, word_vectors, config, summary_size)
print "time_cost = %.2fs" % (time.time() - start)

