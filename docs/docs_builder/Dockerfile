FROM python:2-stretch

RUN apt-get update \
    && apt-get --no-install-recommends install -y texlive preview-latex-style texlive-generic-extra texlive-latex-extra latexmk dos2unix \
    && apt-get -y clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

ENV PATH="/opt/docs_builder:${PATH}"
WORKDIR /opt/docs_builder
COPY requirements.txt requirements.txt
RUN pip install -r requirements.txt
