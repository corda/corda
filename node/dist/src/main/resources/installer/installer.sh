#!/usr/bin/env bash

# Script Varibles
ALREADY_ACCEPTED=$(case "( "$@" )" in  *"accept-license"*) echo "YES" ;; esac)
HELP_REQUESTED=$(case "( "$@" )" in  *"help"*) echo "YES" ;; esac)

if [ "${HELP_REQUESTED}" == "YES" ]
then
    echo "usage: ./installer.sh <--help> <--accept-license>"
    echo "--help: display this help printout"
    echo "--accept-license: confirm possession of a valid license without further user input"
    exit 0
fi


if [ "${ALREADY_ACCEPTED}" == "YES" ]
then
    echo "skipping license as already accepted"
else
    echo "Preparing License"
    more << EOF
    QQ_LICENSE_FILE_QQ
EOF
    read -p "Do you have a valid license and agree with the license terms [yes/No]? " answer

    while true
    do
      case ${answer} in
       [yY] | [yY][Ee][Ss]) echo "Accepting"
               break;;

       * )  echo "You answered: $answer - quitting" ; exit;;
      esac
    done
fi

# Extract
echo "Extracting install ... "
SKIP_TO_TAR=$(awk '/^__TARFILE_FOLLOWS__/ { print NR + 1; exit 0; }' $0)
tail -n +${SKIP_TO_TAR} $0 | tar xjv

exit 0
__TARFILE_FOLLOWS__
