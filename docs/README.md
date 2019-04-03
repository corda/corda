# Corda Documentation Build

To run the Corda Documentation build run ``./gradlew makeDocs``

Note: In order to run the documentation build you will need Docker installed.

Windows users: If this task fails because Docker can't find make-docsite.sh, go to Settings > Shared Drives in the Docker system tray
agent, make sure the relevant drive is shared, and click 'Reset credentials'.

## rst style guide

It's probably worth reading [this](http://www.sphinx-doc.org/en/master/usage/restructuredtext/basics.html) 
to get your head around the rst syntax we're using.  

## version placeholders

Currently we support five placeholders that get substituted at build time:

```groovy
    "|corda_version|" 
    "|java_version|" 
    "|kotlin_version|" 
    "|gradle_plugins_version|" 
    "|quasar_version|"
```

If you put one of these in an rst file anywhere (including in a code tag) then it will be substituted with the value in constants.properties 
(which is in the root of the project) at build time.

The code for this can be found near the top of the conf.py file in the `docs/source` directory.



