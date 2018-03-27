FROM openjdk:8u151-jre-alpine

RUN mkdir /app
COPY corda.jar /app
COPY node.conf /app
COPY init.sh /app
COPY entrypoint.sh /app

WORKDIR /app

CMD ["/bin/sh", "entrypoint.sh"]
