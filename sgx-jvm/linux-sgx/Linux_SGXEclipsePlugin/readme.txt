The eclipse plugin build from command line requires the following variables to be set.

$ECLIPSE_HOME - Path to eclipse home. In order to build the plugin eclipse needs to be installed.
$RELEASE_ID - The release id for the plugin. The release id should be changed for each source codes updates.
$DELETE_CURRENT_ECLIPSE - Delete current eclipse or not. Generally this variable is set to false.

The following plugins are pre-requisites to be installed in Eclipse before trying to build the plugin.

1. Eclipse IDE for C/C++ Developers  4.5.1.20150917-1200 (tested_version)
2. Eclipse PDE Plug-in Developer Resources   3.11.1.v20150904-0345 (tested_version)

run ./build.sh from command line under current directory.
Once the build script is run, the folder build_directory/updatesite/sgx-eclipse-plugin contains the update site. This is the path that needs to be provided to the eclipse while doing installation.

If the Intel(R) Software Guard Extensions (Intel(R) SGX) eclipse plugin is already installed to eclipse and to build and install a newer version, uninstall the old version and start eclipse with the -clean option.
Then try to build the new version of the plugin and install it in eclipse.

http://wiki.eclipse.org/FAQ_How_do_I_remove_a_plug-in%3F

The plugin has been tested with the following Eclipse ADT version

Build: v22.3.0-887826
