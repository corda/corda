#!/bin/bash
export PATH=/home/jenkins/jdk/bin/:$PATH


# Get the Eclipse launcher and build script to use
set -x
set -e

TRUNK_HOME=$(cd $(pwd)/../../ ; pwd)
#gives you the posibility to overwrite eclipse, if you do not use URL
[ -n "${ECLIPSE_HOME}" ] || { echo "using default ECLIPSE_HOME=${TRUNK_HOME}/eclipse"; ECLIPSE_HOME=${TRUNK_HOME}/eclipse; }


BUILD_RELEASE_ID_PREFIX=Linux_SGX_1.5

if [ "$RELEASE_ID" != "${RELEASE_ID%$BUILD_RELEASE_ID_PREFIX*}" ]; then
    echo "$BUILD_RELEASE_ID_PREFIX IS in $RELEASE_ID, so it is an triggered build. Change the RELEASE_ID to an accepted form."
    temp=${RELEASE_ID#$BUILD_RELEASE_ID_PREFIX}
    RELEASE_ID=v`echo ${temp} |  tr -d _ | tr -d -`
else
    echo "$BUILD_RELEASE_ID_PREFIX is NOT in $RELEASE_ID. Keeping the user specified RELEASE_ID."
fi

function main() {
  validate-jenkins-parameters
  cleanupPreBuild
  checkEnvironment
  buildPlugin
  archivePlugin
}

function validate-jenkins-parameters {
  validate-parameter "DELETE_CURRENT_ECLIPSE" "$DELETE_CURRENT_ECLIPSE"
  [[ "ECLIPSE_DOWNLOAD_URL" != "" ]] &&
      echo "[WARNING] ECLIPSE_DOWNLOAD_URL is not set; assume eclipse archive is already downloaded"
}

function validate-parameter {
  local NAME="$1"
  local VALUE="$2"
  [[ ! -z "$VALUE" ]] || {
    echo "Mandatory Jenkins parameter '\$$NAME' not set !"
    exit 1
  }
}

function cleanupPreBuild() {
  ./clean.sh

  [[ "false" == "${DELETE_CURRENT_ECLIPSE}" ]] || {
    forceRemoveEclipse
  }
}

function forceRemoveEclipse() {
  pushd ${TRUNK_HOME}
  rm -fr eclipse
  popd
}

function checkEnvironment() {
  if [ ! -d "${ECLIPSE_HOME}" ]; then
    echo "Eclipse does not exist"
    echo "Downloading eclipse"

    getEclipse
  fi

  if [ -z "$RELEASE_ID" ]; then
    echo "Mandatory variable RELEASE_ID not defined; exiting"
    exit
  fi
}

function getEclipse() {
  local eclipseArchiveURL="${ECLIPSE_DOWNLOAD_URL}"

  pushd $TRUNK_HOME
  cleanupEclipseArchive
  downloadEclipse "${eclipseArchiveURL}"
  unzipEclipse
 installPDE
  cleanupEclipseArchive
  popd
}

function cleanupEclipseArchive() {
  find . -maxdepth 1 -mindepth 1 -name "*eclipse*.zip*" | xargs rm -f
}

function downloadEclipse() {
    local URL="$1"
    if [[ "$1" != "" ]] ; then
	echo " wget --no-proxy "$1""
	wget --no-proxy "$1"
    else
	echo "skip downloaded empty url"
    fi

}

function unzipEclipse() {
    pwd
    rm -fr eclipse
  local eclipseArchiveName="$(find . -maxdepth 1 -mindepth 1 -name "*eclipse*.zip*")"
  unzip "${eclipseArchiveName}"

  [[ -d eclipse ]] || {
    echo "Eclipse directory does not exist!"
    exit
  }

#  local eclipseFolderName=${eclipseArchiveName%.zip}
#  local eclipseArchiveName="eclipse"
#  mv "${eclipseFolderName}" eclipse
}

function installPDE() {
echo "~~~~>"
pwd
${ECLIPSE_HOME}/eclipse -nosplash \
  -application org.eclipse.equinox.p2.director \
  -repository http://download.eclipse.org/eclipse/updates/4.4 \
  -destination ${ECLIPSE_HOME} \
  -installIU org.eclipse.pde.source.feature.group \
  -installIU org.eclipse.pde.feature.group
}

function preBuild() {
  local BUILDDIR="$1"
  local BUILDDIRWORK="$2"
  
  local SITEFILE="$BUILDDIRWORK/sites/site.xml"
  local FEATUREDIR="$BUILDDIRWORK/features"
  local FEATUREFILE="feature.xml"
  local PLUGINDIR="$BUILDDIRWORK/plugins"
  local PLUGINFILE="META-INF/MANIFEST.MF"  

  local ROOTDIR=$(dirname "$0")"/.."
  local VERSION=$(awk '/STRFILEVER/ {print $3}' ${ROOTDIR}/common/inc/internal/se_version.h|sed 's/^\"\(.*\)\"$/\1/')
  VERSION=$(echo "$VERSION" | awk -F'.' '{for(i=1; i<=NF&&i<=3; i++) if(i==1){version=$i} else{version=version"."$i}}; END{print version}')

  if [[ "$VERSION" =~ ^[0-9]{1,}(.[0-9]{1,}){2}$ ]]; then
    rm -fr "$BUILDDIRWORK"
    cp -fr "$BUILDDIR" "$BUILDDIRWORK"

    #site.xml
    sed -i "s#[0-9]\{1,\}\(\.[0-9]\{1,\}\)\{0,2\}\.qualifier#$VERSION\.qualifier#g" "$SITEFILE"

    #feature
    for DIR in $(ls "$FEATUREDIR"); do
      sed -i "s#[0-9]\{1,\}\(\.[0-9]\{1,\}\)\{0,2\}\.qualifier#$VERSION\.qualifier#g" "$FEATUREDIR/$DIR/$FEATUREFILE"
    done

    #plugin
    for DIR in $(ls "$PLUGINDIR"); do
      sed -i "s#[0-9]\{1,\}\(\.[0-9]\{1,\}\)\{0,2\}\.qualifier#$VERSION\.qualifier#g" "$PLUGINDIR/$DIR/$PLUGINFILE"
    done
  fi
}

function postBuild() {
  local BUILDDIR="$1"
  local BUILDDIRWORK="$2"
  local UPDATESITEDIR="updatesite"
  
  if [[ -d "$BUILDDIRWORK" ]] && [[ -d "$BUILDDIRWORK/$UPDATESITEDIR" ]]; then
    rm -fr "$BUILDDIR/$UPDATESITEDIR"
    cp -fr "$BUILDDIRWORK/$UPDATESITEDIR" "$BUILDDIR/$UPDATESITEDIR"
    rm -fr "$BUILDDIRWORK"
  fi
}

function buildPlugin() {
    pwd

  echo "PWD=`pwd`"
  echo "ECLIPSE_HOME=$ECLIPSE_HOME"

  #BASELOCATION="$PWD/target_platform"
  BASELOCATION="$ECLIPSE_HOME"
  BUILDVERSION="$RELEASE_ID"
  BUILDDIR="$PWD/build_directory"
  BUILDDIRWORK="$PWD/.build_directory"
  BUILDCONFIG="$PWD/build_config"
  LAUNCHER=`findFirst "$ECLIPSE_HOME"/plugins/org.eclipse.equinox.launcher_*.jar`
  BUILDFILE=`findFirst "$ECLIPSE_HOME"/plugins/org.eclipse.pde.build_*/scripts/build.xml`

  # make sure we found valid files
  if [ ! -f "$LAUNCHER" ]; then
    echo "Installation Error: Eclipse plugin org.eclipse.equinox.launcher...jar not detected. " \
         "Found '$LAUNCHER'. Aborting."
    exit 1
  fi
  if [ ! -f "$BUILDFILE" ]; then
    echo "Installation Error: Eclipse build file org.eclipse.pde.build_.../scripts/build.xml " \
         "not detected. Found '$BUILDFILE'. Aborting."
    exit 1
  fi

  preBuild "$BUILDDIR" "$BUILDDIRWORK"

  #
  # -- Print configuration used and actually execute the build --  
  #
  echo "Eclipse configuration found:"
  echo "  Eclipse Home: $ECLIPSE_HOME"
  echo "  Launcher:     $LAUNCHER"
  echo "  Build File:   $BUILDFILE"
  echo "  Build Config: $BUILDCONFIG"
  echo "  Base Location: $BASELOCATION"
  echo "  Build Directory: $BUILDDIRWORK"
  echo "  Build Version: $BUILDVERSION"
  echo "  Java:         " $(which java)
  java -version

#    CURRENT_DIR=$(pwd)
#    ${ECLIPSE_HOME}/eclipse -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher -metadataRepository file:/${CURRENT_DIR}/build_directory/updatesite/sgx-eclipse-plugin -artifactRepository file:/${CURRENT_DIR}/build_directory/updatesite/featuresAndBundles  -source ${CURRENT_DIR}/build_directory/  -config gtk.linux.x86 -compress -publishArtifacts
#   cp ./build_directory/updatesite/featuresAndBundles/artifacts.jar ./build_directory/updatesite/sgx-eclipse-plugin/
   
  java \
    -jar $LAUNCHER \
    -application org.eclipse.ant.core.antRunner \
    -buildfile $BUILDFILE \
    -DbuildDirectory=$BUILDDIRWORK \
    -DbaseLocation=$BASELOCATION \
    -Dbuilder=$BUILDCONFIG \
    -DforceContextQualifier=$BUILDVERSION \
      -v -v -v -v 

  postBuild "$BUILDDIR" "$BUILDDIRWORK"
}

function findFirst() {
    echo "enter Find First, $@" 1>&2
  for i in "$@"; do
    if [ -f "$i" ]; then
	echo "found $i" 1>&2
      echo "$i"
      return
    fi
  done
}

function archivePlugin() {
  pushd build_directory/updatesite/sgx-eclipse-plugin
  zip -r Intel-sgx-eclipse-plugin.zip *
  popd
}

main 
