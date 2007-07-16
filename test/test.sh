#!/bin/bash

log=build/log.txt

vm=${1}; shift
flags=${1}; shift
tests=${@}

echo -n "" >${log}

for test in ${tests}; do
  printf "${test}: "

  ${vm} ${flags} ${test} >>${log} 2>&1

  if (( ${?} == 0 )); then
    echo "success"
  else
    echo "fail"
    trouble=1
  fi
done

if [ -n "${trouble}" ]; then
  printf "\nsee ${log} for output\n"
fi
