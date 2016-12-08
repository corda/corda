The Quasar.jar in this directory is for runtime instrumentation of classes by Quasar.

When running corda outside of the given gradle building you must add the following flag with the
correct path to your call to Java:

   java -javaagent:path-to-quasar-jar.jar ...

See the Quasar docs for more information: http://docs.paralleluniverse.co/quasar/