FROM azul/zulu-openjdk:8u192

RUN apt-get update && apt-get -y install bash curl unzip

# Create dirs
RUN mkdir -p /opt/corda/mount-point/cordapps
RUN mkdir -p /opt/corda/mount-point/certificates
RUN mkdir -p /opt/corda/mount-point/drivers
RUN mkdir -p /etc/corda
RUN mkdir -p /opt/corda/logs



# Create corda user
RUN addgroup corda && \
    useradd corda -g corda -m -d /opt/corda

# Copy corda jar


WORKDIR /opt/corda

ENV CORDAPPS_FOLDER="/opt/corda/mount-point/cordapps"
ENV CERTIFICATES_FOLDER="/opt/corda/mount-point/certificates"
ENV DRIVERS_FOLDER="/opt/corda/mount-point/drivers"
ENV CONFIG_FOLDER="/etc/corda"

VOLUME ["/opt/corda/mount-point/cordapps"]
VOLUME ["/opt/corda/mount-point/certificates"]
VOLUME ["/opt/corda/mount-point/drivers"]
VOLUME ["/opt/corda/logs"]

ENTRYPOINT ["/start.sh"]
CMD []

ADD --chown=corda:corda corda.jar /opt/corda/corda.jar
ADD --chown=corda:corda config-exporter.jar /opt/corda/config-exporter.jar
ADD --chown=corda:corda start.sh /start.sh
ADD --chown=corda:corda starting-node.conf /opt/corda/starting-node.conf
RUN chmod +x /start.sh
