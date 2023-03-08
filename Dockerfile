FROM azul/zulu-openjdk:17.0.6
RUN apt-get update && apt-get install -y curl apt-transport-https \
                                              ca-certificates \
                                              curl \
                                              gnupg2 \
                                              software-properties-common \
                                              wget
ARG USER="stresstester"
RUN useradd -m ${USER}
