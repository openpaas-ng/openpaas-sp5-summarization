import takahe
import utils
import copy



def compression(communities, stopwords, wv, lm, config, language):
    param = config['MSC']

    pos_separator = '/'
    punct_tag = 'PUNCT'

    # #############################
    # ### IDFS (meeting level)  ###
    # #############################
    # consider community as DOCUMENT, meeting as CORPUS
    # idf is based on lower_case form
    tokenized_document_list = []
    for tagged_community in communities:
        tagged_document = ' '.join(tagged_community)
        cleaned_tagged_document = utils.clean_tagged_text(
            tagged_document, stopwords,
            remove_stopwords=param.getboolean('remove_stopwords'), pos_filtering=param.getboolean('pos_filtering'),
            stemming=param.getboolean('stemming'), lower_case=True,
            pos_separator=pos_separator, punct_tag=punct_tag
        )
        cleaned_document = utils.remove_tags_from_text(cleaned_tagged_document)
        tokenized_document_list.append(cleaned_document.split(' '))
    meeting_idf_dict = utils.inverse_document_frequencies(tokenized_document_list)

    # #############################
    # ### LOOP OVER COMMUNITIES ###
    # #############################
    compressions = []
    graphs = []

    for tagged_community in communities:
        # print "\t\t\ttagged_community_id:", tagged_corpus[meeting_id].index(tagged_community)

        compresser = takahe.word_graph(
            system_name=param.get('system_name'),
            tagged_community=copy.copy(tagged_community),
            language=language,
            punct_tag=punct_tag,
            pos_separator=pos_separator,

            lm=lm,
            wv=wv,
            stopwords=stopwords,
            meeting_idf_dict=meeting_idf_dict,

            remove_stopwords=param.getboolean('remove_stopwords'),
            pos_filtering=param.getboolean('pos_filtering'),
            stemming=param.getboolean('stemming'),
            cr_w=param.getint('w'),
            cr_weighted=param.getboolean('weighted'),
            cr_overspanning=param.getboolean('overspanning'),
            nb_words=param.getint('nb_words'),
            diversity_n_clusters=param.getint('diversity_n_clusters'),

            keyphrase_reranker_window_size=0,
            common_hyp_threshold_verb=0.9,
            common_hyp_threshold_nonverb=0.3
        )

        # Write the word graph in the dot format
        # compresser.write_dot('new.dot')
        loose_verb_constraint = False
        while True:
            # Get the 200 best paths
            candidates = compresser.get_compression(nb_candidates=200, loose_verb_constraint=loose_verb_constraint)
            if len(candidates) > 0:
                final_paths = compresser.final_score(candidates, 1)  # n_results
                compressions.append(final_paths[0][1])
                graphs.append({
                    'nodes': compresser.graph.nodes(),
                    'edges': compresser.graph.edges()
                })
                break
            # Then reason of no candidate:
            # 1. minimum number of words allowed in the compression larger than
            # the maximum path length in graph, then decrease nb_words and diversity_n_clusters
            else:
                compresser.nb_words -= 1
                if compresser.nb_words == 0:
                    # 2. path should contain at least one verb, but no verb presented in the community
                    # in this case, then loose the verb constraint
                    loose_verb_constraint = True
                    # raise RuntimeError("MSC failed")

    return compressions, graphs

