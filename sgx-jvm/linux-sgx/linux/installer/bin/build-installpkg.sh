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


set -e

[[ $# -eq 1 ]] || {
    echo "Usage : ./build-installpkg.sh sdk | psw "
    exit 1
}

[[ "$1" == "sdk" ]] || [[ "$1" == "psw" ]] ||  {
    echo "Usage : ./build-installpkg.sh sdk | psw "
    exit 1
}

INSTALLER_TYPE="$1"

SCRIPT_DIR=$(dirname "$0")
ROOT_DIR="${SCRIPT_DIR}/../../.."
LINUX_INSTALLER_COMMON_DIR="${ROOT_DIR}/linux/installer/common"
LINUX_INSTALLER_COMMON_SDK_DIR="${LINUX_INSTALLER_COMMON_DIR}/sdk"
LINUX_INSTALLER_COMMON_PSW_DIR="${LINUX_INSTALLER_COMMON_DIR}/psw"

# The result dir of the build
BUILD_DIR=${ROOT_DIR}/build/linux

# Get the architecture of the build from generated binary
get_arch()
{
    local a=$(readelf -h $BUILD_DIR/sgx_sign | sed -n '2p' | awk '/:/{print $6}')
    test $a = 02 && echo 'x86_64' || echo 'x86'
}
 

ARCH=$(get_arch)

case $ARCH in
    x86_64)PACKAGE_SUFFIX="x64"
    ;;
    x86)PACKAGE_SUFFIX="x86"
    ;;
esac

case "$INSTALLER_TYPE" in
    psw)
        source ${LINUX_INSTALLER_COMMON_PSW_DIR}/installConfig.${PACKAGE_SUFFIX}
        ${LINUX_INSTALLER_COMMON_PSW_DIR}/createTarball.sh
        cp  ${LINUX_INSTALLER_COMMON_PSW_DIR}/output/${TARBALL_NAME} ${SCRIPT_DIR}
    ;;
    sdk)
        source ${LINUX_INSTALLER_COMMON_SDK_DIR}/installConfig.${PACKAGE_SUFFIX}
        ${LINUX_INSTALLER_COMMON_SDK_DIR}/createTarball.sh
        cp  ${LINUX_INSTALLER_COMMON_SDK_DIR}/output/${TARBALL_NAME} ${SCRIPT_DIR}
    ;;
esac

trap "rm -f ${SCRIPT_DIR}/$TARBALL_NAME 2>/dev/null" 0

# Create the tarball and compute its MD5 check sum.
m=$(md5sum ${SCRIPT_DIR}/$TARBALL_NAME | awk '{print $1}')
v=$(awk '/STRFILEVER/ {print $3}' ${ROOT_DIR}/common/inc/internal/se_version.h|sed 's/^\"\(.*\)\"$/\1/')
TEMPLATE_FILE=$SCRIPT_DIR/install-sgx-"$INSTALLER_TYPE".bin.tmpl
INSTALLER_NAME=$SCRIPT_DIR/sgx_linux_"${PACKAGE_SUFFIX}"_"$INSTALLER_TYPE"_"$v".bin
l=$(wc -l $TEMPLATE_FILE | awk '{print $1}')
l=$(($l+1))

sed -e "s:@linenum@:$l:" \
    -e "s:@md5sum@:$m:"  \
    -e "s:@arch@:$ARCH:"  \
    $TEMPLATE_FILE > $INSTALLER_NAME

cat ${SCRIPT_DIR}/${TARBALL_NAME} >> $INSTALLER_NAME
chmod +x $INSTALLER_NAME
echo "Generated ${INSTALLER_TYPE} installer: $INSTALLER_NAME"
exit 0
