What is this
------------

This is a javaagent that hooks into Kryo serializers to record a breakdown of how many bytes objects take in the output.

The dump is quite ugly now, but the in-memory representation is a simple tree so we could put some nice visualisation on
top if we want. 

How do I run it
---------------

Build the agent:
```
./gradlew experimental:kryo-hook:jar
```

Add this JVM flag to what you're running:

```
-javaagent:<PROJECT>/experimental/kryo-hook/build/libs/kryo-hook.jar
```

The agent will dump the output when the JVM shuts down.
