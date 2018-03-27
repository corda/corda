FROM openjdk:8u151-jre-alpine

COPY app.jar /app/app.jar

COPY entrypoint.sh /app/entrypoint.sh

WORKDIR /app

CMD ["/bin/sh", "entrypoint.sh"]
