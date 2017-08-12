#! /bin/bash
# Should run within git-hub sgx driver directory:
# Usage:
#	kernel_2_extern <in-kernel-root-path> <patch-file-name>
pa=`pwd`

file="$1/arch/x86/include/asm/sgx.h"
if [ ! -f $file ]; then
	echo "Missing file $file"
	exit
fi
cp $file sgx_arch.h

file="$1/arch/x86/include/uapi/asm/sgx.h"
if [ ! -f $file ]; then
	echo "Missing file $file"
	exit
fi
cp $file sgx_user.h

cd $1
git apply $pa/$2

cd $pa

cp $1/drivers/platform/x86/intel_sgx/sgx* .

