#!/usr/bin/env bash

NUM_CPU=$(nproc)

if ((NUM_CPU <= 8)); then
  export CORE_TESTING_FORKS=1
  export NODE_INT_TESTING_FORKS=1
  export NODE_TESTING_FORKS=1
  export INT_TESTING_FORKS=1
  export TESTING_FORKS=1

elif ((NUM_CPU > 8 && NUM_CPU <= 16)); then
  export CORE_TESTING_FORKS=2
  export NODE_INT_TESTING_FORKS=2
  export NODE_TESTING_FORKS=2
  export INT_TESTING_FORKS=2
  export TESTING_FORKS=2

elif ((NUM_CPU > 16 && NUM_CPU <= 32)); then
  export CORE_TESTING_FORKS=4
  export NODE_INT_TESTING_FORKS=4
  export NODE_TESTING_FORKS=4
  export INT_TESTING_FORKS=2
  export TESTING_FORKS=2

elif ((NUM_CPU > 32 && NUM_CPU <= 64)); then
  export CORE_TESTING_FORKS=8
  export NODE_INT_TESTING_FORKS=8
  export NODE_TESTING_FORKS=8
  export INT_TESTING_FORKS=4
  export TESTING_FORKS=4
fi