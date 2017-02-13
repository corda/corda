if [ -z "$JAVA_HOME" ]; then
    echo "Please set JAVA_HOME correctly."
else
    cp $JAVA_HOME/jre/bin/java ./DemoBench/runtime/bin
fi
