#!/bin/sh
pdflatex corda-platform-whitepaper.tex ; bibtex corda-platform-whitepaper.aux ; pdflatex corda-platform-whitepaper.tex; pdflatex corda-platform-whitepaper.tex
open corda-platform-whitepaper.pdf &
rm corda-platform-whitepaper.toc corda-platform-whitepaper.out corda-platform-whitepaper.log corda-platform-whitepaper.blg corda-platform-whitepaper.bbl corda-platform-whitepaper.aux

