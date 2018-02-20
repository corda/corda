# API Scanner

Generates a text summary of Corda's public API that we can check for API-breaking changes.

```bash
$ gradlew generateApi
```

See [here](../../docs/source/corda-api.rst) for Corda's public API strategy. We will need to
apply this plugin to other modules in future Corda releases as those modules' APIs stabilise.

Basically, this plugin will document a module's `public` and `protected` classes/methods/fields,
excluding those from our `*.internal.*` packages, any synthetic methods, bridge methods, or methods
identified as having Kotlin's  `internal` scope. (Kotlin doesn't seem to have implemented `internal`
scope for classes or fields yet as these are currently `public` inside the `.class` file.)

## Usage
Include this line in the `build.gradle` file of every Corda module that exports public API:

```gradle
apply plugin: 'net.corda.plugins.api-scanner'
```

This will create a Gradle task called `scanApi` which will analyse that module's Jar artifacts. More precisely,
it will analyse all of the Jar artifacts that have not been assigned a Maven classifier, on the basis
that these should be the module's main artifacts.

The `scanApi` task supports the following configuration options:
```gradle
scanApi {
    // Make the classpath-scanning phase more verbose.
    verbose = {true|false}

    // Enable / disable the task within this module.
    enabled = {true|false}

    // Names of classes that should be excluded from the output.
    excludeClasses = [
        ...
    ]
}
```

All of the `ScanApi` tasks write their output files to their own `$buildDir/api` directory, where they
are collated into a single output file by the `GenerateApi` task. The `GenerateApi` task is declared
in the root project's `build.gradle` file:

```gradle
task generateApi(type: net.corda.plugins.GenerateApi){
    baseName = "api-corda"
}
```

The final API file is written to `$buildDir/api/$baseName-$project.version.txt`

### Sample Output
```
public interface net.corda.core.contracts.Attachment extends net.corda.core.contracts.NamedByHash
  public abstract void extractFile(String, java.io.OutputStream)
  @org.jetbrains.annotations.NotNull public abstract List getSigners()
  @org.jetbrains.annotations.NotNull public abstract java.io.InputStream open()
  @org.jetbrains.annotations.NotNull public abstract jar.JarInputStream openAsJAR()
##
public interface net.corda.core.contracts.AttachmentConstraint
  public abstract boolean isSatisfiedBy(net.corda.core.contracts.Attachment)
##
public final class net.corda.core.contracts.AttachmentResolutionException extends net.corda.core.flows.FlowException
  public <init>(net.corda.core.crypto.SecureHash)
  @org.jetbrains.annotations.NotNull public final net.corda.core.crypto.SecureHash getHash()
##
```

#### Notes
The `GenerateApi` task will collate the output of every `ScanApi` task found either in the same project,
or in any of that project's subprojects. So it is _theoretically_ possible also to collate the API output
from subtrees of modules simply by defining a new `GenerateApi` task at the root of that subtree.

## Plugin Installation
See [here](../README.rst) for full installation instructions.
