#!/usr/bin/env bash
#
# Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
#   * Redistributions of source code must retain the above copyright
#     notice, this list of conditions and the following disclaimer.
#   * Redistributions in binary form must reproduce the above copyright
#     notice, this list of conditions and the following disclaimer in
#     the documentation and/or other materials provided with the
#     distribution.
#   * Neither the name of Intel Corporation nor the names of its
#     contributors may be used to endorse or promote products derived
#     from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
#


set -x

srcdir=`dirname $0`
[ -z "$srcdir" ] && srcdir=.

ORIGDIR=`pwd`
cd $srcdir

#autoreconf -v --install || exit $?
#cd $ORIGDIR             || exit $?

if [ "$1" = "DEBUG" ] 
then
    COMMON_FLAGS="-ggdb -Og"
else
    COMMON_FLAGS="-g -O2 -D_FORTIFY_SOURCE=2"
fi

COMMON_FLAGS="$COMMON_FLAGS -DNO_HEAP_CHECK -DTCMALLOC_SGX -DTCMALLOC_NO_ALIASES $2"

ENCLAVE_CFLAGS="$COMMON_FLAGS -ffreestanding -nostdinc -fvisibility=hidden -fPIC"
ENCLAVE_CXXFLAGS="$ENCLAVE_CFLAGS -nostdinc++ -std=c++11"
CFLAGS="$CFLAGS $ENCLAVE_CFLAGS"
CXXFLAGS="$CXXFLAGS $ENCLAVE_CXXFLAGS"
CPPFLAGS="-I../../../common/inc -I../../../common/inc/tlibc -I../../../common/inc/internal/ -I../../../sdk/tlibcxx/include -I../../../sdk/trts/"

if echo $CFLAGS | grep -q -- '-m32'; then
   HOST_OPT='--host=i386-linux-gnu'
fi

export CFLAGS
export CXXFLAGS
export CPPFLAGS
$srcdir/configure $HOST_OPT --enable-shared=no \
   --disable-cpu-profiler \
   --disable-heap-profiler       \
   --disable-heap-checker \
   --disable-debugalloc \
   --enable-minimal

#must remove this attribute define in generated config.h, or can't debug tcmalloc with sgx-gdb
if [ "$1" = "DEBUG" ]
then
    sed -i 's/#define HAVE___ATTRIBUTE__ 1/\/\/#define HAVE___ATTRIBUTE__ 1/g' src/config.h
fi
