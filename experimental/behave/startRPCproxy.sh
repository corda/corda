#!/bin/sh

DISTRO_DIR=$1

if [ ! -d "$DISTRO_DIR" ]; then
    echo "Must specify location of Corda distribution (directory does not exist: $DISTRO_DIR)"
    exit 1
fi

if [ ! -f "$DISTRO_DIR/corda.jar" ]; then
    echo "Distribution corda.jar not found"
    exit 1
fi

if [ ! -f "$DISTRO_DIR/corda-rpcProxy.jar" ]; then
    echo "Distribution corda-rpcProxy.jar not found"
    exit 1
fi

# unzip corda jars into proxy directory (if not already there)
if [ ! -d "$DISTRO_DIR/proxy" ]; then
    mkdir -p $DISTRO_DIR/proxy
    /usr/bin/unzip $DISTRO_DIR/corda.jar -d $DISTRO_DIR/proxy
fi

# launch proxy
echo "Launching RPC proxy ..."
echo "/usr/bin/java -cp $DISTRO_DIR/corda-rpcProxy.jar:\
\n\t$(ls $DISTRO_DIR/proxy/*.jar | tr '\n' ':'):\
\n\t$(ls $DISTRO_DIR/apps/*.jar | tr '\n' ':') \
\n\tnet.corda.behave.service.proxy.RPCProxyServerKt
"

/usr/bin/java -cp $DISTRO_DIR/corda-rpcProxy.jar:\
$(ls $DISTRO_DIR/proxy/*.jar | tr '\n' ':'):\
$(ls $DISTRO_DIR/apps/*.jar | tr '\n' ':') \
net.corda.behave.service.proxy.RPCProxyServerKt & echo $! > /tmp/rpcProxy-pid &
echo "RPCProxyServer PID: $(cat /tmp/rpcProxy-pid)"
