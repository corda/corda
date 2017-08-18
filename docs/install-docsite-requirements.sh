#!/usr/bin/env bash
# The purpose of this file is to install the requirements for the docsite
# You can call it manually if running make manually, otherwise gradle will run it for you

set -xeo pipefail

# Install the virtualenv
if [ ! -d "virtualenv" ]
then
    # If the canonical working directory contains whitespace, virtualenv installs broken scripts.
    # But if we pass in an absolute path that uses symlinks to avoid whitespace, that fixes the problem.
    # If you run this script manually (not via gradle) from such a path alias, it's available in PWD:
    absolutevirtualenv="$PWD/virtualenv"
    # Check if python2.7 is installed explicitly otherwise fall back to the default python
    if type "python2.7" > /dev/null; then
        virtualenv -p python2.7 "$absolutevirtualenv"
    else
        virtualenv "$absolutevirtualenv"
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
