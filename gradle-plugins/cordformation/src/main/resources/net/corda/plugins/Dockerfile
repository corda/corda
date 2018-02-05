# Base image from (http://phusion.github.io/baseimage-docker)
FROM openjdk:8u151-jre-alpine

ENV CORDA_VERSION=${BUILDTIME_CORDA_VERSION}
ENV JAVA_OPTIONS=${BUILDTIME_JAVA_OPTIONS}

# Set image labels
LABEL net.corda.version = ${CORDA_VERSION} \
      maintainer = "<devops@r3.com>" \
      vendor = "R3"

RUN apk upgrade --update && \
    apk add --update --no-cache bash iputils && \
    rm -rf /var/cache/apk/* && \
    # Add user to run the app && \
    addgroup corda && \
    adduser -G corda -D -s /bin/bash corda && \
    # Create /opt/corda directory && \
    mkdir -p /opt/corda/plugins && \
    mkdir -p /opt/corda/logs

# Copy corda files
ADD --chown=corda:corda corda.jar               /opt/corda/corda.jar
ADD --chown=corda:corda node.conf               /opt/corda/node.conf
ADD --chown=corda:corda network-parameters      /opt/corda/
ADD --chown=corda:corda cordapps/               /opt/corda/cordapps
ADD --chown=corda:corda additional-node-infos/  /opt/corda/additional-node-infos
ADD --chown=corda:corda certificates/           /opt/corda/certificates
ADD --chown=corda:corda drivers/                /opt/corda/drivers
ADD --chown=corda:corda persistence*            /opt/corda/

COPY run-corda.sh /run-corda.sh

RUN chmod +x /run-corda.sh && \
    sync && \
    chown -R corda:corda /opt/corda

# Working directory for Corda
WORKDIR /opt/corda
ENV HOME=/opt/corda
USER corda

# Start it
CMD ["/run-corda.sh"]