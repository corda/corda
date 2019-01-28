FROM python:2-stretch

RUN apt-get update && apt-get install -y texlive preview-latex-style texlive-generic-extra texlive-latex-extra latexmk dos2unix
ENV PATH="/opt/docs_builder:${PATH}"
WORKDIR /opt/docs_builder
COPY requirements.txt requirements.txt
RUN pip install -r requirements.txt