## Azul Zulu 17.70+201 (JDK 17.0.21.1), which fixes CVE-2026-114938. Pinned by digest, so every build uses this image.
FROM azul/zulu-openjdk:17.0.21.1@sha256:4a90876af5459eba762bf028926a84733ded1db6f57cf74e59ef3588c149e17b

## Remove Azul Zulu repo, as it is gone by now
RUN rm -rf /etc/apt/sources.list.d/zulu.list

## Add packages, clean cache, create dirs, create corda user and change ownership
RUN apt-get update && \
    apt-mark hold zulu17-jdk && \
    apt-get -y upgrade && \
    apt-get -y install bash curl unzip && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /opt/corda/cordapps && \
    mkdir -p /opt/corda/persistence && \
    mkdir -p /opt/corda/artemis && \
    mkdir -p /opt/corda/certificates && \
    mkdir -p /opt/corda/drivers && \
    mkdir -p /opt/corda/logs && \
    mkdir -p /opt/corda/bin && \
    mkdir -p /opt/corda/additional-node-infos && \
    mkdir -p /etc/corda && \
    addgroup corda && \
    useradd corda -g corda -m -d /opt/corda && \
    chown -R corda:corda /opt/corda && \
    chown -R corda:corda /etc/corda

ENV CORDAPPS_FOLDER="/opt/corda/cordapps" \
    PERSISTENCE_FOLDER="/opt/corda/persistence" \
    ARTEMIS_FOLDER="/opt/corda/artemis" \
    CERTIFICATES_FOLDER="/opt/corda/certificates" \
    DRIVERS_FOLDER="/opt/corda/drivers" \
    CONFIG_FOLDER="/etc/corda" \
    MY_P2P_PORT=10200 \
    MY_RPC_PORT=10201 \
    MY_RPC_ADMIN_PORT=10202 \
    PATH=$PATH:/opt/corda/bin \
    JVM_ARGS="-XX:+UnlockExperimentalVMOptions " \
    CORDA_ARGS=""

##CORDAPPS FOLDER
VOLUME ["/opt/corda/cordapps"]
##PERSISTENCE FOLDER
VOLUME ["/opt/corda/persistence"]
##ARTEMIS FOLDER
VOLUME ["/opt/corda/artemis"]
##CERTS FOLDER
VOLUME ["/opt/corda/certificates"]
##OPTIONAL JDBC DRIVERS FOLDER
VOLUME ["/opt/corda/drivers"]
##LOG FOLDER
VOLUME ["/opt/corda/logs"]
##ADDITIONAL NODE INFOS FOLDER
VOLUME ["/opt/corda/additional-node-infos"]
##CONFIG LOCATION
VOLUME ["/etc/corda"]

##CORDA JAR
COPY --chown=corda:corda corda.jar /opt/corda/bin/corda.jar
##CONFIG MANIPULATOR JAR
COPY --chown=corda:corda config-exporter.jar /opt/corda/config-exporter.jar
##CONFIG GENERATOR SHELL SCRIPT
COPY --chown=corda:corda generate-config.sh /opt/corda/bin/config-generator
##CORDA RUN SCRIPT
COPY --chown=corda:corda run-corda.sh /opt/corda/bin/run-corda
##BASE CONFIG FOR GENERATOR
COPY --chown=corda:corda starting-node.conf /opt/corda/starting-node.conf

USER "corda"
EXPOSE ${MY_P2P_PORT} ${MY_RPC_PORT} ${MY_RPC_ADMIN_PORT}
WORKDIR /opt/corda

##LAUNCHER CHECK: start corda.jar once on this image's own JDK and fail the build if it cannot start.
##corda.jar is a Capsule, and Capsule cannot parse Java versions with more than four numbers: on a JDK such as
##17.0.21.0.3 every start fails with "CAPSULE EXCEPTION: Could not parse version". Re-pinning a base image to such a
##JDK would otherwise produce an image that builds fine but cannot start a node. The files the check writes (the
##unpacked Capsule, logs, JVM temporary files) are removed in the same step, so nothing is added to the image.
RUN java -XshowSettings:properties -version 2>&1 | grep "java.version =" && \
    java -jar /opt/corda/bin/corda.jar --version && \
    rm -rf /opt/corda/.capsule /opt/corda/.hawtjni /opt/corda/logs/* /tmp/hsperfdata_corda

CMD ["run-corda"]
