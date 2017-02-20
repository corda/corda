if [ -z "$JAVA_HOME" ]; then
    echo "Please set JAVA_HOME correctly."
else
    INSTALL_HOME=../images/DemoBench.app/Contents/PlugIns/Java.runtime/Contents/Home/jre/bin
    mkdir -p $INSTALL_HOME
    cp $JAVA_HOME/jre/bin/java $INSTALL_HOME
fi
