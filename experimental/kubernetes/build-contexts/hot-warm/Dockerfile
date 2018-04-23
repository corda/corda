FROM openjdk:8u151-jre-alpine

COPY corda.jar /app/corda.jar
COPY plugins /app/plugins/
COPY entrypoint.sh /app/entrypoint.sh
COPY node.conf /app/node.conf

WORKDIR /app

EXPOSE 10001 10002

CMD ["/bin/sh", "entrypoint.sh"]
