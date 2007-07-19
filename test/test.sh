#!/bin/bash

log=build/log.txt
vg="nice valgrind --leak-check=full --num-callers=32 \
--freelist-vol=100000000 --error-exitcode=1"

vm=${1}; shift
mode=${1}; shift
flags=${1}; shift
tests=${@}

echo -n "" >${log}

for test in ${tests}; do
  printf "${test}: "

  case ${mode} in
    debug )
      ${vm} ${flags} ${test} >>${log} 2>&1;;

    stress* )
      ${vg} ${vm} ${flags} ${test} >>${log} 2>&1;;

    * )
      echo "unknown mode: ${mode}" >&2
      exit 1;;
  esac

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
