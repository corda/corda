#!/usr/bin/env bash
# The purpose of this file is to install the requirements for the docsite
# You can call it manually if running make manually, otherwise gradle will run it for you

set -xeo pipefail

# Install the virtualenv
if [ ! -d "virtualenv" ]
then
    # Check if python2.7 is installed explicitly otherwise fall back to the default python
    if type "python2.7" > /dev/null; then
        virtualenv -p python2.7 virtualenv
    else
        virtualenv virtualenv
    fi
fi

# Activate the virtualenv
if [ -d "virtualenv/bin" ]
then
    # it's a Unix system
    source virtualenv/bin/activate
else
    source virtualenv/Scripts/activate
fi

# Install PIP requirements
if [ ! -d "virtualenv/lib/python2.7/site-packages/sphinx" ]
then
    echo "Installing pip dependencies ... "
    pip install -r requirements.txt
fi