#!/bin/bash

echo "Starting API Diff"

APIHOME=$(dirname $0)

apiCurrent=$APIHOME/api-current.txt
if [ ! -f $apiCurrent ]; then
   echo "Missing $apiCurrent file - cannot check API diff. Please rebase or add it to this release"
   exit -1
fi

diffContents=`diff -u $apiCurrent $APIHOME/../build/api/api-corda-*.txt`
echo "Diff contents: " 
echo "$diffContents"
removals=`echo "$diffContents" | grep "^-\s" | wc -l`
echo "Number of API removals/changes: "$removals
echo "Exiting with exit code" $removals
exit $removals
