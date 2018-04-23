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

autoreconf -v --install || exit $?
cd $ORIGDIR             || exit $?

CFLAGS="$CFLAGS -std=c99 -fno-builtin -DHAVE_SGX=1 -fPIC -DUNW_LOCAL_ONLY"

# Remove duplicated compiler options and filter out `-nostdinc'
CFLAGS=`echo $CFLAGS | tr ' ' '\n' | sort | uniq | grep -v nostdinc | tr '\n' ' '`

HOST_OPT=
if echo $CFLAGS | grep -q -- '-m32'; then
   HOST_OPT='--host=i386-linux-gnu'
fi

# Workaround for icc
#
# If we are running icc on Debian-like system, add
#   /usr/include/$(dpkg-architecture -qDEB_BUILD_MULTIARCH)
# to the header search path.
if [ -f /usr/bin/dpkg ]; then
    if [ x$CC != x"" ]; then
        if $CC -E -dM -xc /dev/null  | grep -q __INTEL_COMPILER; then
            INCVAR=$(dpkg-architecture -qDEB_BUILD_MULTIARCH)
            CFLAGS="$CFLAGS -I/usr/include/$INCVAR"
        fi
    fi
fi

export CFLAGS
$srcdir/configure $HOST_OPT --enable-shared=no \
   --disable-block-signals \
   --enable-debug=no       \
   --enable-debug-frame=no \
   --enable-cxx-exceptions \
   "$@"
