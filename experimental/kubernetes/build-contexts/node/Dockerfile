FROM openjdk:8u151-jre-alpine

COPY corda.jar /app/corda.jar

COPY entrypoint.sh /app/entrypoint.sh

WORKDIR /app

EXPOSE 10001 10002

ENTRYPOINT ["/bin/sh", "entrypoint.sh"]
