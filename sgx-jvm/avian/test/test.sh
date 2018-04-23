#!/bin/sh

vg="nice valgrind --leak-check=full --num-callers=32 \
--freelist-vol=100000000 --error-exitcode=1"

ld_path=${1}; shift
unit_tester=${1}; shift
vm=${1}; shift
mode=${1}; shift
flags=${1}; shift
tests=${@}

log=log.txt

if [ -n "${ld_path}" ]; then
  export ${ld_path}
fi

echo -n "" >${log}

printf "%20s------- Unit tests -------\n" ""
${unit_tester} 2>>${log}
if [ "${?}" != "0" ]; then
  trouble=1
  echo "unit tests failed!"
fi

echo

printf "%20s------- Java tests -------\n" ""
for test in ${tests}; do
  printf "%32s: " "${test}"

  case ${mode} in
    debug|debug-fast|fast|small )
      ${vm} ${flags} ${test} >>${log} 2>&1;;

    stress* )
      ${vg} ${vm} ${flags} ${test} \
        >>${log} 2>&1;;

    * )
      echo "unknown mode: ${mode}" >&2
      exit 1;;
  esac

  if [ "${?}" = "0" ]; then
    echo "success"
  else
    echo "fail"
    trouble=1
  fi
done

echo

if [ -n "${trouble}" ]; then
  printf "see ${log} for output\n"
  exit -1
fi
