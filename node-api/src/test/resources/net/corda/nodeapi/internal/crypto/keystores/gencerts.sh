#!/bin/bash

# These jks files have been produced with KeyTool using commands from V3 Float/Bridge setup here:
# https://docs.corda.r3.com/bridge-configuration-file.html#complete-example

# More specifically the following script can be run on mac to generate the files needed. 
# Trust Root with EC algo
keytool -genkeypair -keyalg EC -keysize 256 -alias floatroot -validity 3650 -dname "CN=Float Root,O=Local Only,L=London,C=GB" -ext bc:ca:true,pathlen:1 -keystore floatca.jks -storepass capass -keypass cakeypass

# Bridge and Float with EC
keytool -genkeypair -keyalg EC -keysize 256 -alias bridgecert -validity 3650 -dname "CN=Bridge Local,O=Local Only,L=London,C=GB" -ext bc:ca:false -keystore bridge_ec.jks -storepass bridgepass -keypass bridgepass
keytool -genkeypair -keyalg EC -keysize 256 -alias floatcert -validity 3650 -dname "CN=Float Local,O=Local Only,L=London,C=GB" -ext bc:ca:false -keystore float_ec.jks -storepass floatpass -keypass floatpass

# Bridge and Float with RSA
keytool -genkeypair -keyalg RSA -keysize 1024 -alias bridgecert -validity 3650 -dname "CN=Bridge Local,O=Local Only,L=London,C=GB" -ext bc:ca:false -keystore bridge_rsa.jks -storepass bridgepass -keypass bridgepass
keytool -genkeypair -keyalg RSA -keysize 1024 -alias floatcert -validity 3650 -dname "CN=Float Local,O=Local Only,L=London,C=GB" -ext bc:ca:false -keystore float_rsa.jks -storepass floatpass -keypass floatpass

# Export Trust root for subsequent chaining
keytool -exportcert -rfc -alias floatroot -keystore floatca.jks -storepass capass -keypass cakeypass > root.pem
keytool -importcert -noprompt -file root.pem -alias root -keystore trust.jks -storepass trustpass

# Create a chain for EC Bridge
keytool -certreq -alias bridgecert -keystore bridge_ec.jks -storepass bridgepass -keypass bridgepass |keytool -gencert -validity 3650 -ext ku:c=dig,keyEncipherment -ext: eku:true=serverAuth,clientAuth -rfc -keystore floatca.jks -alias floatroot -storepass capass -keypass cakeypass > bridge_ec.pem
cat root.pem bridge_ec.pem >> bridgechain_ec.pem
keytool -importcert -noprompt -file bridgechain_ec.pem -alias bridgecert -keystore bridge_ec.jks -storepass bridgepass -keypass bridgepass

# Create a chain for RSA Bridge
keytool -certreq -alias bridgecert -keystore bridge_rsa.jks -storepass bridgepass -keypass bridgepass |keytool -gencert -validity 3650 -ext ku:c=dig,keyEncipherment -ext: eku:true=serverAuth,clientAuth -rfc -keystore floatca.jks -alias floatroot -storepass capass -keypass cakeypass > bridge_rsa.pem
cat root.pem bridge_rsa.pem >> bridgechain_rsa.pem
keytool -importcert -noprompt -file bridgechain_rsa.pem -alias bridgecert -keystore bridge_rsa.jks -storepass bridgepass -keypass bridgepass

# Create a chain for EC Float
keytool -certreq -alias floatcert -keystore float_ec.jks -storepass floatpass -keypass floatpass |keytool -gencert -validity 3650 -ext ku:c=dig,keyEncipherment -ext: eku::true=serverAuth,clientAuth -rfc -keystore floatca.jks -alias floatroot -storepass capass -keypass cakeypass > float_ec.pem
cat root.pem float_ec.pem >> floatchain_ec.pem
keytool -importcert -noprompt -file floatchain_ec.pem -alias floatcert -keystore float_ec.jks -storepass floatpass -keypass floatpass

# Create a chain for RSA Float
keytool -certreq -alias floatcert -keystore float_rsa.jks -storepass floatpass -keypass floatpass |keytool -gencert -validity 3650 -ext ku:c=dig,keyEncipherment -ext: eku::true=serverAuth,clientAuth -rfc -keystore floatca.jks -alias floatroot -storepass capass -keypass cakeypass > float_rsa.pem
cat root.pem float_rsa.pem >> floatchain_rsa.pem
keytool -importcert -noprompt -file floatchain_rsa.pem -alias floatcert -keystore float_rsa.jks -storepass floatpass -keypass floatpass
