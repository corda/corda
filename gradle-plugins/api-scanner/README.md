# API Scanner

Generates a text summary of a Jar's public API that we can check for API-breaking changes.

## Usage
Include this line in your `build.gradle` file:

```gradle
apply plugin: 'net.corda.plugins.api-scanner'
```

This will create a Gradle task called `scanApi` which will analyse the module's Jar artifacts. More precisely,
it will analyse all of the Jar artifacts that have not been assigned a Maven classifier, on the basis
that these should be the module's main artifacts.

The `scanApi` task supports the following configuration options:
```gradle
scanApi {
    // Make the classpath-scanning phase more verbose.
    verbose = {true|false}

    // Enable / disable the task within this module.
    enabled = {true|false}
}
```

The plugin writes its output files to the `$buildDir/api` directory.

### Sample Output
```
public interface net.corda.core.contracts.Attachment extends net.corda.core.contracts.NamedByHash
  public abstract void extractFile(String, java.io.OutputStream)
  @org.jetbrains.annotations.NotNull public abstract List getSigners()
  @org.jetbrains.annotations.NotNull public abstract java.io.InputStream open()
  @org.jetbrains.annotations.NotNull public abstract jar.JarInputStream openAsJAR()
--
public static final class net.corda.core.contracts.Attachment$DefaultImpls extends java.lang.Object
  public static void extractFile(net.corda.core.contracts.Attachment, String, java.io.OutputStream)
  @org.jetbrains.annotations.NotNull public static jar.JarInputStream openAsJAR(net.corda.core.contracts.Attachment)
--

```

## Installation
See [here](../README.rst) for full installation instructions.
