#!/bin/bash
while [ 1 -lt 2 ]; do
    NODE_INFO=$(ls | grep nodeInfo)
    if [ ${#NODE_INFO} -ge 5 ]; then
        echo "found nodeInfo copying to additional node node info folder"
        cp ${NODE_INFO} additional-node-infos/
        exit 0
    else
        echo "no node info found waiting"
        fi
    sleep 5
done