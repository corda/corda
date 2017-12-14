#!/bin/bash
set +o posix

echo "Starting API Diff"

APIHOME=$(dirname $0)

apiCurrent=$APIHOME/api-current.txt
if [ ! -f $apiCurrent ]; then
   echo "Missing $apiCurrent file - cannot check API diff. Please rebase or add it to this release"
   exit -1
fi

# Remove the two header lines from the diff output.
diffContents=`diff -u $apiCurrent $APIHOME/../build/api/api-corda-*.txt | tail -n +3`
echo "Diff contents:"
echo "$diffContents"
echo

# A removed line means that an API was either deleted or modified.
removals=$(echo "$diffContents" | grep "^-")
removalCount=`grep -v "^$" <<EOF | wc -l
$removals
EOF
`

echo "Number of API removals/changes: "$removalCount
if [ $removalCount -gt 0 ]; then
    echo "$removals"
    echo
fi

# Adding new abstract methods could also break the API.
# However, first exclude anything with the @DoNotImplement annotation.

function forUserImpl() {
    awk '/DoNotImplement/,/^##/{ next }{ print }' $1
}

userDiffContents=`diff -u <(forUserImpl $apiCurrent) <(forUserImpl $APIHOME/../build/api/api-corda-*.txt) | tail -n +3`

newAbstracts=$(echo "$userDiffContents" | grep "^+" | grep "\(public\|protected\) abstract")
abstractCount=`grep -v "^$" <<EOF | wc -l
$newAbstracts
EOF
`

echo "Number of new abstract APIs: "$abstractCount
if [ $abstractCount -gt 0 ]; then
    echo "$newAbstracts"
    echo
fi

badChanges=$(($removalCount + $abstractCount))
if [ $badChanges -gt 255 ]; then
    echo "OVERFLOW! Number of bad API changes: $badChanges"
    badChanges=255
fi

echo "Exiting with exit code" $badChanges
exit $badChanges
