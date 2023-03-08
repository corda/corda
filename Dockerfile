FROM azul/zulu-openjdk:17.0.7
RUN apt-get update && apt-get install -y curl apt-transport-https \
                                              ca-certificates \
                                              curl \
                                              gnupg2 \
                                              software-properties-common \
                                              wget
ARG USER="corda"
RUN useradd -m ${USER}
