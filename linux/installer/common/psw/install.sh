#!/usr/bin/env bash
#
# Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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

SCRIPT_DIR=$(dirname "$0")
source ${SCRIPT_DIR}/installConfig

PSW_DST_PATH=${SGX_PACKAGES_PATH}/${PSW_PKG_NAME}
AESM_PATH=$PSW_DST_PATH/aesm

# Install the AESM service

cut -d: -f1 /etc/passwd | grep -q -w aesmd || \
    /usr/sbin/useradd -r -c "User for aesmd" \
    -d /var/opt/aesmd -s /sbin/nologin aesmd

mkdir -p /var/opt/aesmd
cp -rf $AESM_PATH/data /var/opt/aesmd/
rm -rf $AESM_PATH/data
cp -rf $AESM_PATH/conf/aesmd.conf /etc/aesmd.conf
rm -rf $AESM_PATH/conf
chmod  0644 /etc/aesmd.conf
chown -R aesmd /var/opt/aesmd
chmod 0750 /var/opt/aesmd

if [ -d /run/systemd/system ]; then
    AESMD_NAME=aesmd.service
    AESMD_TEMP=$AESM_PATH/$AESMD_NAME
    if [ -d /lib/systemd/system ]; then
        AESMD_DEST=/lib/systemd/system/$AESMD_NAME
    else
        AESMD_DEST=/usr/lib/systemd/system/$AESMD_NAME
    fi
    echo -n "Installing $AESMD_NAME service ..."
    sed -e "s:@aesm_folder@:$AESM_PATH:" \
        $AESMD_TEMP > $AESMD_DEST
    chmod 0644 $AESMD_DEST
    rm -f $AESMD_TEMP
    rm -f $AESM_PATH/aesmd.conf
    DISABLE_AESMD="systemctl disable aesmd"
    systemctl enable aesmd
    retval=$?
elif [ -d /etc/init/ ]; then
    AESMD_NAME=aesmd.conf
    AESMD_TEMP=$AESM_PATH/$AESMD_NAME
    AESMD_DEST=/etc/init/$AESMD_NAME
    echo -n "Installing $AESMD_NAME service ..."
    sed -e "s:@aesm_folder@:$AESM_PATH:" \
        $AESMD_TEMP > $AESMD_DEST
    chmod 0644 $AESMD_DEST
    rm -f $AESMD_TEMP
    rm -f $AESM_PATH/aesmd.service
    sudo /sbin/initctl reload-configuration
    retval=$?
else
    echo " failed."
    echo "Unsupported platform - neither systemctl nor initctl is found."
    exit 5
fi

if test $retval -ne 0; then
    echo "$rcmngr failed to install $AESMD_NAME."
    exit 6
fi

echo " done."


cat > $PSW_DST_PATH/uninstall.sh <<EOF
#!/usr/bin/env bash

if test \$(id -u) -ne 0; then
    echo "Root privilege is required."
    exit 1
fi

# Killing AESM service
sudo /usr/sbin/service aesmd stop
$DISABLE_AESMD
# Removing AESM configuration files
rm -f $AESMD_DEST
rm -f /etc/aesmd.conf

# Removing AESM internal folder
rm -fr /var/opt/aesmd

# Removing runtime libraries
rm -f /usr/lib/libsgx_uae_service.so
rm -f /usr/lib/libsgx_urts.so

# Removing AESM folder
rm -fr $PSW_DST_PATH

# Removing AESM user and group
/usr/sbin/userdel aesmd
EOF

chmod +x $PSW_DST_PATH/uninstall.sh

# Start the aesmd service
if [ -d /run/systemd/system ]; then
    systemctl start aesmd
elif [ -d /etc/init/ ]; then
    sudo /sbin/initctl start aesmd
fi

echo -e "\nuninstall.sh script generated in $PSW_DST_PATH\n"

echo -e "Installation is successful!"

rm -fr $PSW_DST_PATH/scripts

exit 0

