# clean input directories to make sure there's nothing left from previous run

rm -rfv ./build_directory/features/com.intel.sgx.build.driver
rm -fv build_directory/*.properties
rm -fv build_directory/*.xml
rm -fv build_directory/plugins/com.intel.sgx/build.xml
rm -fv build_directory/plugins/com.intel.sgx.userguide/build.xml
rm -rfv build_directory/plugins/com.intel.sgx/bin
rm -rfv build_directory/plugins/com.intel.sgx.userguide/bin
rm -rfv build_directory/plugins/com.intel.sgx.source_1.0.0.*
rm -rfv build_directory/features/com.intel.sgx.source
rm -fv build_directory/features/com.intel.sgx.feature/build.xml
rm -fv build_directory/features/com.intel.sgx.feature/*.zip
rm -rfv build_directory/nestedJars
rm -rfv build_directory/updatesite/sgx-eclipse-plugin
find . -name "*.zip" ! -name  "eclipse_mars.v4.5.1_x64.zip" | xargs rm -rfv
find . -name "javaCompiler*" | xargs rm -rfv
find . -name "@*" | xargs rm -rfv
find build_directory -maxdepth 1 -mindepth 1 | grep -v "features" | grep -v "plugins" | grep -v "sites" | grep -v "updatesite" | xargs rm -frv

