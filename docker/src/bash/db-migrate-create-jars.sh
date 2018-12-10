#!/usr/bin/env bash

java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Djava.security.egd=file:/dev/./urandom -jar /opt/corda/bin/database-manager.jar create-migration-sql-for-cordapp \
                                                  -b=/opt/corda \
                                                  -f=/etc/corda/node.conf \
                                                  --log-to-console \
                                                  --jar

