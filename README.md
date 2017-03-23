# README #


### What is this repository for? ###

This repository contains the real-time and offline summarization modules of the openpaas sp5 project.

The REST interfaces and web-Socket connections are implemented in the service package


Dependencies:
--
Java 1.8+

Maven is used for packaging.

R, Rscript must be installed and placed on the systems path.

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



- Pushing text-to-speech to generate a summary for a meeting:
    Http POST:
         parameters:id,callbackurl
         body:transcript
    SERVER_IP:PORT/summary?id=meeting_id&callbackurl=localhost


Real-time Keyword extraction
--

Steps


- Register a current meeting
    SERVER_IP:PORT/stream?id=meeting_id&action=START

- Push text-to-speech snippets in real time using the websocket at /app/chat.
    Message format: "from": meeting_id, "text": speech_snippet
    

- Retrieve related resources:
    SERVER_IP:PORT/resources?id=meeting_id&resources=email;so;wiki

- De-register when the meeting ends in order to stop tracking the meeting:
    SERVER_IP:PORT/stream?id=meeting_id&action=STOP