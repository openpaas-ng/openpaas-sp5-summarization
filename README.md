# README #


### What is this repository for? ###

This repo contains the real-time and offline summarization modules of the openpaas project.

The REST interfaces and web-Socket connections are implemented in the service package

Usage:
--

Offline summarization
--

Retrieving a summary for a meeting: 
    SERVER_IP:PORT/summary?id=meeting_id
Pushing text-to-speech to generate a summary for a meeting:
    SERVER_IP:PORT/summary?id=meeting_id&transcript=text_to_speech

Real-time Keyword extraction
--