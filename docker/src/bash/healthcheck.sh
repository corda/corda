#!/usr/bin/env bash
# Container health check for the Corda Enterprise image (used by the Dockerfile HEALTHCHECK).
#
# Healthy once a TCP socket is LISTENING on the node RPC port, which the node opens only after
# it has fully started. The check reads /proc/net/tcp{,6} and opens no connection, so it does not
# create noise in the node or Artemis logs and needs no extra tools (curl, nc, ...).
#
# Override the port with CORDA_HEALTHCHECK_PORT, for example when rpcSettings.address in node.conf
# does not use ${MY_RPC_PORT}. Orchestrators that ignore HEALTHCHECK (such as Kubernetes) can call
# this script from an exec probe, or use a tcpSocket probe on the same port.
set -u

port="${CORDA_HEALTHCHECK_PORT:-${MY_RPC_PORT:-10201}}"
if ! [[ "${port}" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "healthcheck: invalid port '${port}'" >&2
  exit 1
fi
port_hex=$(printf '%04X' "${port}")

# /proc/net/tcp columns: sl local_address rem_address st ... ; st 0A = TCP_LISTEN
for table in /proc/net/tcp /proc/net/tcp6; do
  [[ -r "${table}" ]] || continue
  while read -r _ local_address _ state _; do
    if [[ "${state}" == "0A" && "${local_address##*:}" == "${port_hex}" ]]; then
      exit 0
    fi
  done < "${table}"
done

echo "healthcheck: nothing listening on port ${port}" >&2
exit 1
