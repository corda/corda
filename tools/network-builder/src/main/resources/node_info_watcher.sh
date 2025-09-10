#!/bin/bash
while [ 1 -lt 2 ]; do
    NODE_INFO=$(ls | grep nodeInfo)
    if [ ${#NODE_INFO} -ge 5 ]; then
        echo "Found nodeInfo file, copying to additional-node-infos folder."
        cp ${NODE_INFO} additional-node-infos/
        exit 0
    else
        echo "No nodeInfo file found, waiting..."
        fi
    sleep 5
done