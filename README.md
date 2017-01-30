# README #


### What is this repository for? ###

This repo contains the real-time and offline summarization modules of the openpaas project.

The REST interfaces and web-Socket connections are implemented in the service package

Usage:
--

Offline summarization
--

- Retrieving a summary for a meeting: 
    SERVER_IP:PORT/summary?id=meeting_id


- Pushing text-to-speech to generate a summary for a meeting:
    SERVER_IP:PORT/summary?id=meeting_id&transcript=text_to_speech


Real-time Keyword extraction
--

Steps


- Register a current meeting
    SERVER_IP:PORT/stream?id=meeting_id&action=START


- Push text-to-speech snippets in real time using the websocket to /app/chat.
    Message format: "from": meeting_id, "text": speech_snippet
    e.g using stomp.js


- Retrieve related resources:
    SERVER_IP:PORT/resources?id=meeting_id&resources="email;so;wiki"

- Un-register the meeting
    SERVER_IP:PORT/stream?id=meeting_id&action=STOP