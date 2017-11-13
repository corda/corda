The Doorman source code is located under `network-management/src`

To build a fat jar containing all the doorman code you can simply invoke
.. sourcecode:: bash
    ./gradlew network-management:buildDoormanJAR

The built file will appear in
``network-management/build/libs/doorman-<VERSION>-capsule.jar``