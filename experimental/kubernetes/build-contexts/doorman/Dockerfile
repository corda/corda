FROM openjdk:8u151-jre-alpine

RUN mkdir /app
COPY doorman.jar /app
COPY init.sh /app
COPY entrypoint.sh /app

WORKDIR /app

ENTRYPOINT ["/bin/sh", "entrypoint.sh"]
