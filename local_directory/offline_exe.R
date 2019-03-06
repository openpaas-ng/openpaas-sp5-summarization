#!/usr/bin/Rscript

# clear up workspace
rm(list=ls())

########## packages and functions loading ##########

tt0 = proc.time()['elapsed']
library(stringr)
library(textcat)
library(SnowballC)
library(igraph)
library(hash)
library(tools)
cat('packages loaded in',proc.time()['elapsed'] - tt0, 'second(s)')

tt = proc.time()['elapsed']
hide = lapply(list("from_keyword_to_summary_submodularity.R","concept_submodularity_objective.R","from_terms_to_keywords.R","assign_attributes_to_graph_nocomm.R","sentence_extraction_submodularity.R","cores_dec.R","assign_attributes_to_graph_initial.R","clean_utterances.R","cleaning_meeting_text.R","cleaning_transcript.R","from_cleaned_transcript_to_terms_list.R","from_terms_to_graph.R","keyword_extraction.R","keyword_extraction_inner.R","sentence_selection_greedy.R","string_join.R","utterance_collapse.R","get_elbow_point.R"), function(x) {source(paste0('~/local_directory/',x))})
cat('\n functions loaded in',proc.time()['elapsed'] - tt, 'second(s)')

########## variables passed from command line ##########

# first is name of input file
# second is summary size in words

args = commandArgs(trailingOnly=TRUE)

# example command prompt
# Rscript --vanilla offline_exe.R asr_info_english.txt 350
# note that 'offline_exe.R' needs to be present in the terminal working directory, outside of the local directory with all the R files

########## internal variables ##########

input_file_name = as.character(args[1])
user_summary_size = as.integer(args[2])

r_directory='local_directory'

operating_system = .Platform$OS.type
# tuning parameters       
method = "CRP"
scaling_factor = 0.3
lambda = 5

cat('\n internal variables defined')

########## text loading ##########

# the file passed should be utf-8 encoded, with 4 tab-separated columns devoid of any header
asr_info = read.delim(paste0('~/local_directory/input/',input_file_name), stringsAsFactors=FALSE, header=FALSE, fileEncoding = 'utf-8', col.names = c('start', 'end', 'role', 'text'))
cat('\n input file read')

detected_language = textcat(paste(asr_info[,'text'],collapse=' '))
cat('\n language detected:', detected_language)

if (detected_language=='french') {

	if (operating_system == 'unix'){
		custom_stopwords = readLines(paste0('~/local_directory/resources/custom_stopwords_full_french.txt'), encoding='utf-8')
		filler_words = readLines(paste0('~/local_directory/resources/filler_words_french.txt'), encoding='utf-8')
	} else if (operating_system == 'windows'){
		custom_stopwords = read.csv(paste0('~/local_directory/resources/custom_stopwords_full_french.csv'),header=FALSE,stringsAsFactors=FALSE)[,1]
		filler_words = read.csv(paste0('~/local_directory/resources/filler_words_french.csv'),header=FALSE,stringsAsFactors=FALSE)[,1]
	}
	
	cat('\n French stopwords and filler words loaded')

} else if (detected_language=='english') {

	custom_stopwords = read.csv(paste0('~/local_directory/resources/custom_stopwords_full.csv'),header=FALSE,stringsAsFactors = FALSE)[,1]
	filler_words = read.csv(paste0('~/local_directory/resources/filler_words.csv'),header=FALSE,stringsAsFactors = FALSE)[,1]
	cat('\n English stopwords and filler words loaded')

}

########## text cleaning ##########

if (detected_language %in% c('french','english')){

tt = proc.time()['elapsed']
cleaned_transcript = cleaning_transcript(my_transcript_df = asr_info, time_prune = 0.85, custom = custom_stopwords, pos=FALSE, to_stem=TRUE, overlap_threshold = 1.5, detected_language=detected_language)	
cleaned_transcript_df = cleaned_transcript$collapse_output
cleaned_transcript_df_processed = cleaned_transcript_df$reduced_my_df_proc
cleaned_transcript_df_unprocessed = cleaned_transcript_df$reduced_my_df_unproc
terms_list = list(processed = from_cleaned_transcript_to_terms_list(cleaned_transcript_df_processed[,4])$terms_list_partial, unprocessed = from_cleaned_transcript_to_terms_list(cleaned_transcript_df_unprocessed[,4])$terms_list_partial)
cat('\n transcript cleaned in',proc.time()['elapsed'] - tt, 'second(s)')

tt = proc.time()['elapsed']
utterances = as.character(cleaned_transcript_df_unprocessed[,4])
start_time = cleaned_transcript_df_unprocessed[,1]        
utterances_lengthes = unlist(lapply(utterances, function(x){length(setdiff(unlist(strsplit(tolower(x),split=" ")), custom_stopwords))}))
index_remove = which(utterances_lengthes<=3)
if (length(index_remove)>0){          
	utterances = utterances[-index_remove]
	start_time = start_time[-index_remove]         
}   
utterances = clean_utterances(utterances, my_stopwords=custom_stopwords, filler_words=filler_words)$utterances
cat('\n utterances cleaned in',proc.time()['elapsed'] - tt, 'second(s)')

########## keyword extraction ##########

tt = proc.time()['elapsed']
keywords_scores = from_terms_to_keywords(terms_list=terms_list, window_size=12, to_overspan=T, to_build_on_processed=T, community_algo="none", weighted_comm=NA, directed_comm=NA, rw_length=NULL, size_threshold=NULL, degeneracy="weighted_k_core", directed_mode="all", method=method, use_elbow=FALSE, use_percentage=NA, percentage=0.15, number_to_retain=NA, which_nodes="all", overall_wd=r_directory, edgelist_file_name = unlist(strsplit(input_file_name, split='\\.'))[1])$output
cat('\n queries extracted in',proc.time()['elapsed'] - tt, 'second(s)')

df_wc = data.frame(words = keywords_scores$extracted_keywords, freq = round(as.numeric(keywords_scores$scores),4))

write.table(df_wc, paste0('~/local_directory/output/keywords_',input_file_name), col.names = FALSE, row.names = FALSE, quote=FALSE)

cat('\n queries written to disk')

########## summary generation ##########

tt = proc.time()['elapsed']
my_summary = from_keyword_to_summary_submodularity(graph_keywords_scores_temp = keywords_scores, utterances = utterances, start_time = start_time, to_stem=T, max_summary_length=user_summary_size, scaling_factor=scaling_factor, weighted_sum_concepts=T, negative_terms=FALSE, lambda=lambda)$my_summary
cat('\n summary of',user_summary_size,'words generated in',proc.time()['elapsed'] - tt, 'second(s)')

writeLines(my_summary, paste0('~/local_directory/output/',input_file_name))
cat('\n summary written to disk')
cat('\n')

cat(paste(my_summary, collapse=' '))

cat('\n')
cat('\n total processing time:',proc.time()['elapsed'] - tt0, 'second(s)')


} else {

cat('\n Sorry, only English and French are currently supported')

}
      