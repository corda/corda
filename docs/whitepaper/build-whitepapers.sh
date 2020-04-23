#!/bin/sh

# Utility to render the white papers. LaTeX has the UX from hell and requires us to run the same commands over and over to make the paper
# render correctly, with a ToC and citations.

cmd="pdflatex -shell-escape"

for wp in introductory technical; do
  $cmd corda-$wp-whitepaper.tex
  bibtex corda-$wp-whitepaper.aux
  $cmd corda-$wp-whitepaper.tex
  $cmd corda-$wp-whitepaper.tex
  rm corda-$wp-whitepaper.toc corda-$wp-whitepaper.out corda-$wp-whitepaper.log corda-$wp-whitepaper.blg corda-$wp-whitepaper.bbl corda-$wp-whitepaper.aux
done


