#!/bin/bash
# This script was originally written by maxiaohao in the aws-mock GitHub project.
# https://github.com/treelogic-swe/aws-mock/

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then

  echo "Start to publish lastest Javadoc to gh-pages..."

  cd build/

  if test -d gh-pages
  then
    cd gh-pages
    git pull
  else
    git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/ReadyTalk/avian gh-pages > /dev/null
    cd gh-pages
    git config user.email "travis@travis-ci.org"
    git config user.name "travis-ci"
  fi

  git rm -rf ./javadoc
  cp -Rf ../javadoc ./javadoc
  git add -f .
  git commit -m "Lastest javadoc on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to gh-pages"
  git push -fq origin gh-pages > /dev/null

  echo "Done magic with auto publishment to gh-pages."
fi
