FROM ubuntu:16.04

ENV BASE /root

RUN apt update && apt install -y r-base r-cran-slam git maven openjdk-8-jdk
RUN echo "r <- getOption('repos'); r['CRAN'] <- 'https://cloud.r-project.org'; options(repos = r);" > ~/.Rprofile &&\
		R -e 'install.packages(c("stringr", "textcat", "SnowballC", "igraph", "hash"))'

WORKDIR $BASE
COPY . .

RUN mvn package && cp target/openpaas-summary-service-0.1.0.jar .

EXPOSE 8080

CMD java -jar ./openpaas-summary-service-*.jar
