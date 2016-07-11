#!/bin/bash

set -xeo pipefail

if [ ! -d "virtualenv" ]
then
    virtualenv -p python2.7 virtualenv
fi

(
    . virtualenv/bin/activate
    if [ ! -d "virtualenv/lib/python2.7/site-packages/sphinx" ]
    then
        pip install -r requirements.txt
    fi
    make html
)
