set -eux

export COMPATIBILITY_ZONE_URL=$(eval "echo http://${DOORMAN_PORT_1300_TCP_ADDR}:1300")
export P2P_ADDRESS=$(eval "echo ${HOSTNAME}:10001")
export RPC_ADDRESS=$(eval "echo ${HOSTNAME}:10002")


exec java -Xmx1g -jar /app/corda.jar --log-to-console \
  --no-local-shell \
  --base-directory=/data \
  --network-root-truststore=/truststore/network-root-truststore.jks
