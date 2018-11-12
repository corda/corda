FROM azul/zulu-openjdk:8u192

RUN apt-get update && apt-get install bash

# Create dirs
RUN mkdir -p /opt/corda/cordapps
RUN mkdir -p /opt/corda/certificates
RUN mkdir -p /opt/corda/drivers
RUN mkdir -p /opt/corda/logs

# Create corda user
RUN addgroup corda && \
    useradd corda -g corda -m -d /opt/corda

# Copy corda jar

ADD --chown=corda:corda corda.jar /opt/corda/corda.jar
ADD --chown=corda:corda start.sh /start.sh
RUN chmod +x /start.sh

WORKDIR /opt/corda
CMD ["start.sh"]