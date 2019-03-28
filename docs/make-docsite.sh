#!/usr/bin/env bash
echo "Generating PDF document ..."
make latexpdf LATEXMKOPTS="-quiet"

echo "Generating HTML pages ..."
make html

echo "Moving PDF file from $(eval echo $PWD/build/latex/corda-developer-site.pdf) to $(eval echo $PWD/build/html/_static/corda-developer-site.pdf)"
mv $PWD/build/latex/corda-developer-site.pdf $PWD/build/html/_static/corda-developer-site.pdf