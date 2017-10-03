#!/bin/bash

echo "Starting API Diff"

apiCurrent=./api-current.txt
if [ ! -f $apiCurrent ]; then
   echo "Missing $apiCurrent file - cannot check API diff. Please rebase or add it to this release or ensure working dir is .ci/"
   exit -1
fi

diffContents=`diff -u $apiCurrent ../build/api/api-corda-*.txt`
echo "Diff contents: " 
echo "$diffContents"
removals=`echo "$diffContents" | grep "^-\s" | wc -l`
echo "Number of API removals/changes: "$removals
echo "Exiting with exit code" $removals
exit $removals
