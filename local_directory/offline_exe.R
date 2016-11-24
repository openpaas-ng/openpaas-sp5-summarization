#!/usr/bin/Rscript

# clear up workspace
rm(list=ls())

########## packages and functions loading ##########

tt = proc.time()['elapsed']
library(stringr)
library(textcat)
library(SnowballC)
library(igraph)
library(hash)
library(tools)
cat('packages loaded in',proc.time()['elapsed'] - tt, 'second(s)')

tt = proc.time()['elapsed']
source("from_keyword_to_summary_submodularity.R")
source("concept_submodularity_objective.R")
source("from_terms_to_keywords.R")
source("assign_attributes_to_graph_nocomm.R")
source("sentence_extraction_submodularity.R")
source("cores_dec.R")
source("assign_attributes_to_graph_initial.R")
source("clean_utterances.R")
source("cleaning_meeting_text.R")
source("cleaning_transcript.R")
source("from_cleaned_transcript_to_terms_list.R")
source("from_terms_to_graph.R")
source("keyword_extraction.R")
source("keyword_extraction_inner.R")
source("sentence_selection_greedy.R")
source("string_join.R")
source("utterance_collapse.R")
source("get_elbow_point.R")
cat('\n functions loaded in',proc.time()['elapsed'] - tt, 'second(s)')

########## variables passed from command line ##########

# first is name of input file
# second is summary size in words

args = commandArgs(trailingOnly=TRUE)

########## internal variables ##########

overall_wd = getwd()
operating_system = .Platform$OS.type
# tuning parameters       
method="CRP"
scaling_factor=0.3
lambda=5

########## text loading ##########

asr_info = read.delim(paste0(overall_wd,'/input/',as.character(args[1])), stringsAsFactors=FALSE, header=TRUE, fileEncoding = 'utf-8')
cat('\n input file read')

detected_language = textcat(paste(asr_info[,'text'],collapse=' '))
cat('\n language detected:', detected_language)

if (detected_language=='french') {

	if (operating_system == 'unix'){
		custom_stopwords = readLines(paste0(overall_wd,'/resources/custom_stopwords_full_french.txt'), encoding='utf-8')
		filler_words = readLines(paste0(overall_wd,'/resources/filler_words_french.txt'), encoding='utf-8')
	} else if (operating_system == 'windows'){
		custom_stopwords = read.csv(paste0(overall_wd,'/resources/custom_stopwords_full_french.csv'),header=FALSE,stringsAsFactors=FALSE)[,1]
		filler_words = read.csv(paste0(overall_wd,'/resources/filler_words_french.csv'),header=FALSE,stringsAsFactors=FALSE)[,1]
	}
	
	cat('\n French stopwords and filler words loaded')

} else if (detected_language=='english') {

	custom_stopwords = read.csv(paste0(overall_wd,'/resources/custom_stopwords_full.csv'),header=FALSE,stringsAsFactors = FALSE)[,1]
	filler_words = read.csv(paste0(overall_wd,'/resources/filler_words.csv'),header=FALSE,stringsAsFactors = FALSE)[,1]
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
keywords_scores = from_terms_to_keywords(terms_list=terms_list, window_size=12, to_overspan=T, to_build_on_processed=T, community_algo="none", weighted_comm=NA, directed_comm=NA, rw_length=NULL, size_threshold=NULL, degeneracy="weighted_k_core", directed_mode="all", method=method, use_elbow=FALSE, use_percentage=NA, percentage=0.15, number_to_retain=NA, which_nodes="all", overall_wd)$output
cat('\n keywords extracted in',proc.time()['elapsed'] - tt, 'second(s)')

df_wc = data.frame(words = keywords_scores$extracted_keywords, freq = round(as.numeric(keywords_scores$scores),4))

write.table(df_wc, paste0(overall_wd,'/output/keywords','_',format(Sys.Date(),'%Y_%m_%d'),'_',format(Sys.time(), '%H_%M'),'.txt'), col.names = FALSE, row.names = FALSE, quote=FALSE)
cat('\n keywords written to disk')

########## summary generation ##########

tt = proc.time()['elapsed']
my_summary = from_keyword_to_summary_submodularity(graph_keywords_scores_temp = keywords_scores, utterances = utterances, start_time = start_time, to_stem=T, max_summary_length=as.integer(args[2]), scaling_factor=scaling_factor, weighted_sum_concepts=T, negative_terms=FALSE, lambda=lambda)$my_summary
cat('\n summary of',args[2],'words generated in',proc.time()['elapsed'] - tt, 'second(s)')
	
writeLines(my_summary, paste0(overall_wd,'/output/summary','_',format(Sys.Date(),'%Y_%m_%d'),'_',format(Sys.time(), '%H_%M'),'.txt'))
cat('\n summary written to disk')

} else {

cat('\n Sorry, only English and French are currently supported')

}
      