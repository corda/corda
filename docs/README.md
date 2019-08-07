# Corda Documentation Build

This Readme describes how to build the Corda documentation for the current version. The output html files will be written to the `corda\docs\build\html` directory.

## Prerequisites / First time build

Before you begin, you need to: 
1. Install Docker. 
1. Ensure that Docker is running. 
1. Select **Expose daemon on tcp://localhost:2375 without TLS** in the Docker Settings (which you can open from the **System Tray** by right-clicking the **Docker symbol** and then selecting **Settings**)

## Build process
1. Open a cmd dialogue. 
1. Navigate to the root location (this is the `\corda` directory)
1. Run the documentation build (`gradlew makeDocs` or `./gradlew makeDocs`)

**Windows users:** *If this task fails because Docker can't find make-docsite.sh, go to Settings > Shared Drives in the Docker system tray
agent, make sure the relevant drive is shared, and click 'Reset credentials'.*

# RST style guide

The Corda documentation is described using the ReStructured Text (RST) markup language. For details of the syntax, see [this](http://www.sphinx-doc.org/en/master/usage/restructuredtext/basics.html).  

# Version placeholders

We currently support the following placeholders; they get substituted with the correct value at build time:

```groovy
    "|corda_version|" 
    "|java_version|" 
    "|kotlin_version|" 
    "|gradle_plugins_version|" 
    "|quasar_version|"
```

If you put one of these in an rst file anywhere (including in a code tag), it will be substituted with the value from `constants.properties` 
(which is in the root of the project) at build time.

The code for this can be found near the top of the conf.py file in the `docs/source` directory.
