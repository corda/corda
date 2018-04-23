#!/bin/bash

# Args:
#   1) path to avian
#   2) path to the app jar (corda-enclave.jar)
#   3) path to the output dir
#   4) path to proguard jar
#   5) path to proguard conf
#   6) path to openjdk image

if [ ! -d "$1" ]; then echo "$1 is not a directory"; exit 1; fi
if [ ! -e "$2" ]; then echo "$2 does not exist"; exit 1; fi
if [ ! -e "$4" ]; then echo "$4 is not a ProGuard jar"; exit 1; fi

set -ex

avianpath=$( readlink -f $1 )
bootjar="$avianpath/classpath.jar"
if [ ! -e "$bootjar" ]; then echo "$avianpath does not appear to be an Avian build directory"; exit 1; fi
appjar=$( readlink -f $2 )
outputjardir=$( readlink -f $3 )
proguard_jar=$( readlink -f $4 )
proguard_conf=$( readlink -f $5 )
openjdk_libs=$6/lib

mkdir -p $outputjardir
if [ ! -e $openjdk_libs/jsse.jar ]; then
	echo "$6 does not appear to be an OpenJDK directory."
    exit 1
fi

cmd="java -jar $proguard_jar  @$avianpath/../../vm.pro @$avianpath/../../openjdk.pro @$avianpath/../../corda.pro @$proguard_conf -injars $bootjar -injars $appjar -injars $openjdk_libs/jsse.jar -injars $openjdk_libs/jce.jar -outjars $outputjardir"
# echo $cmd
$cmd

mkdir -p $outputjardir/temp
cd $outputjardir/temp
jar xf ../jce.jar
jar xf ../jsse.jar
jar xf $openjdk_libs/charsets.jar
jar xf $2
rm -f META-INF/*.DSA
rm -f META-INF/*.RSA
rm -f META-INF/*.SF
rm -f META-INF/*.MF
jar cf ../app.jar *
cd ..
rm -r temp
