#! /bin/bash
# Should be run from git-hub sgx driver root directory.
# Assumes in kernel sgx master branch code repo has been cloned
#
# Usage:
#	kernel_2_extern <in-kernel-root-path>
pa=`pwd`

patchfile="$pa/inker2ext/internal-to-external-tree-changes.patch"

if [ ! -f $file ]; then 
 	echo "Missing patch file: $file" 
 	echo "You should run the script from the out of tree driver repository root directory" 
 	exit 
fi 

cd $1
git apply $patchfile

cp *.c $pa
cp *.h $pa
cp Makefile $pa

cd $pa


