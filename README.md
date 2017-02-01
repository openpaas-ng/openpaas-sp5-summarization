# README #


### What is this repository for? ###

This repo contains the real-time and offline summarization modules of the openpaas project.

The REST interfaces and web-Socket connections are implemented in the service package


Test IP:195.251.235.92


Test port:8080


test username: user32


test password: test#@!


Dependencies:
--

R, Rscript must be installed and on the systems path.

Additionally the following R libraries must be installed:

stringr
textcat
SnowballC
igraph
hash
tools


Usage:
--
Go to the project folder and run

java -jar openpaas-summary-service-0.1.0.jar


Offline summarization
--

- Retrieving a summary for a meeting: 
    SERVER_IP:PORT/summary?id=meeting_id


- Pushing text-to-speech to generate a summary for a meeting:
    Http POST:
         headers:id,callbackurl
         body:transcript
    SERVER_IP:PORT/summary?id=meeting_id


Real-time Keyword extraction
--

Steps


- Register a current meeting
    SERVER_IP:PORT/stream?id=meeting_id&action=START


- Push text-to-speech snippets in real time using the websocket at /app/chat.
    Message format: "from": meeting_id, "text": speech_snippet
    e.g using stomp.js


- Retrieve related resources:
    SERVER_IP:PORT/resources?id=meeting_id&resources=email;so;wiki

- Un-register when the meeting ends:
    SERVER_IP:PORT/stream?id=meeting_id&action=STOP