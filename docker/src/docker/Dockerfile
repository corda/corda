## Base image pinned by digest. To update, change the tag and replace the digest with the one
## printed by: docker buildx imagetools inspect azul/zulu-openjdk:<tag>
FROM azul/zulu-openjdk:17.0.21.1@sha256:4a90876af5459eba762bf028926a84733ded1db6f57cf74e59ef3588c149e17b AS runtime-base

## Remove Azul Zulu repo, as it is gone by now
RUN rm -rf /etc/apt/sources.list.d/zulu.list

## Upgrade OS packages (JDK held at the pinned version), install the tools the scripts need, clean the apt cache,
## and remove setuid/setgid bits (su, passwd, mount, ...) so a non-root user cannot use them to become root
RUN apt-get update && \
    apt-mark hold zulu17-jdk && \
    DEBIAN_FRONTEND=noninteractive apt-get -y upgrade && \
    DEBIAN_FRONTEND=noninteractive apt-get -y install bash curl unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    find / -xdev -type f \( -perm -4000 -o -perm -2000 \) -exec chmod a-s {} +

## Create corda user, with a fixed UID/GID of 1000 so it can be referenced by number (e.g. Kubernetes runAsUser: 1000).
## The build fails if the base image already uses 1000.
RUN addgroup --gid 1000 corda && \
    useradd -u 1000 -g corda -m -d /opt/corda corda

##ASSEMBLE STAGE: gives the Corda files their final permissions before they enter the image, so no later
##step has to change them (a chmod in a later layer would store a second copy of every jar).
##Nothing from this stage reaches the image except the files copied from /dist below.
FROM runtime-base AS assemble
COPY corda.jar config-exporter.jar starting-node.conf run-corda.sh generate-config.sh healthcheck.sh /staging/
##Jars and config 0444, scripts 0555
RUN mkdir -p /dist/bin /dist/base && \
    cp /staging/corda.jar /dist/bin/ && \
    cp /staging/run-corda.sh /dist/bin/run-corda && \
    cp /staging/generate-config.sh /dist/bin/config-generator && \
    cp /staging/healthcheck.sh /dist/bin/healthcheck && \
    cp /staging/config-exporter.jar /staging/starting-node.conf /dist/base/ && \
    chmod 0444 /dist/bin/corda.jar \
               /dist/base/config-exporter.jar \
               /dist/base/starting-node.conf && \
    chmod 0555 /dist/bin/run-corda \
               /dist/bin/config-generator \
               /dist/bin/healthcheck

##LAUNCHER CHECK: start corda.jar once on this image's own JDK and fail the build if it cannot start.
##corda.jar is a Capsule, and Capsule cannot parse Java versions with more than four numbers: on a JDK such as
##17.0.21.0.3 every start fails with "CAPSULE EXCEPTION: Could not parse version". Re-pinning a base image to such a
##JDK would otherwise produce an image that builds fine but cannot start a node. This runs in the assemble stage,
##so the files Capsule unpacks are discarded and nothing is added to the image. It adds a few seconds to the build.
RUN java -XshowSettings:properties -version 2>&1 | grep "java.version =" && \
    java -jar /dist/bin/corda.jar --version

##RUNTIME STAGE: the published image
FROM runtime-base

## Create dirs and change ownership. Node data, message journal and keys are readable by the corda user and group
## only, and the folder holding the Corda binaries is owned by root, so the corda user cannot change its contents
RUN mkdir -p /opt/corda/cordapps && \
    mkdir -p /opt/corda/persistence && \
    mkdir -p /opt/corda/artemis && \
    mkdir -p /opt/corda/certificates && \
    mkdir -p /opt/corda/drivers && \
    mkdir -p /opt/corda/logs && \
    mkdir -p /opt/corda/bin && \
    mkdir -p /opt/corda/additional-node-infos && \
    mkdir -p /etc/corda && \
    chown -R corda:corda /opt/corda && \
    chown -R corda:corda /etc/corda && \
    chmod 750 /opt/corda/persistence /opt/corda/artemis /opt/corda/certificates && \
    chown root:root /opt/corda/bin && \
    chmod 755 /opt/corda/bin

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

##CORDA DISTRIBUTION: owned by root (COPY default), permissions set in the assemble stage
COPY --from=assemble /dist/bin/ /opt/corda/bin/
COPY --from=assemble /dist/base/config-exporter.jar /dist/base/starting-node.conf /opt/corda/

##Numeric UID/GID of the corda user, so Kubernetes runAsNonRoot can verify the image runs as non-root
USER 1000:1000
EXPOSE ${MY_P2P_PORT} ${MY_RPC_PORT} ${MY_RPC_ADMIN_PORT}
WORKDIR /opt/corda

##HEALTHY ONCE THE NODE LISTENS ON ITS RPC PORT (override with CORDA_HEALTHCHECK_PORT)
HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=3 \
    CMD ["/opt/corda/bin/healthcheck"]

CMD ["run-corda"]
