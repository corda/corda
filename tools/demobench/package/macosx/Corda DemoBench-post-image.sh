if [ -z "$JAVA_HOME" ]; then
    echo "**** Please set JAVA_HOME correctly."
    exit 1
fi

function signApplication() {
    APPDIR="$1"
    IDENTITY="$2"

    # Re-sign the embedded JRE because we have included "bin/java"
    # after javapackager had already signed the JRE installation.
    if ! (codesign --force --sign "$IDENTITY" --preserve-metadata=identifier,entitlements,requirements --verbose "$APPDIR/Contents/PlugIns/Java.runtime"); then
        echo "**** Failed to re-sign the embedded JVM"
        return 1
    fi
}

# Switch to folder containing application.
cd ../images/image-*/Corda\ DemoBench.app

INSTALL_HOME=Contents/PlugIns/Java.runtime/Contents/Home/jre/bin
if (mkdir -p $INSTALL_HOME); then
    cp $JAVA_HOME/bin/java $INSTALL_HOME
fi

# Switch to image directory in order to sign it.
cd ..

# Sign the application using a 'Developer ID Application' key on our keychain.
if ! (signApplication "Corda DemoBench.app" "Developer ID Application: @signingKeyUserName@"); then
    echo "**** Failed to sign the application - ABORT SIGNING"
fi

