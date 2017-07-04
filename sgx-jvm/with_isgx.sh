#!/bin/bash
set -euo pipefail

function finish {
     sudo modprobe -r isgx
}
trap finish EXIT
sudo modprobe isgx
$@
