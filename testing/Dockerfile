FROM ubuntu:18.04

RUN apt-get update && apt-get install -y wget git
RUN wget https://d3pxv6yz143wms.cloudfront.net/8.222.10.1/java-1.8.0-amazon-corretto-jdk_8.222.10-1_amd64.deb
RUN apt-get install -y java-common && dpkg -i java-1.8.0-amazon-corretto-jdk_8.222.10-1_amd64.deb

RUN mkdir -p /tmp/source
COPY . /tmp/source
RUN cd /tmp/source && ./gradlew clean testClasses integrationTestClasses printIt

