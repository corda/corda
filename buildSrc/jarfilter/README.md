# JarFilter

Deletes annotated elements at the byte-code level from a JAR of Java/Kotlin code. In the case of Kotlin
code, it also modifies the `@kotlin.Metadata` annotations not to contain any functions, properties or
type aliases that have been deleted. This prevents the Kotlin compiler from successfully compiling against
any elements which no longer exist.

## Usage
This plugin is automatically available on Gradle's classpath since it lives in Corda's `buildSrc` directory.
You need only `import` the plugin's task classes in the `build.gradle` file and then use them to declare
tasks.

You can enable the tasks' logging output using Gradle's `--info` or `--debug` command-line options.

### The `JarFilter` task
The `JarFilter` task removes unwanted elements from `class` files, namely:
- Deleting both Java methods/fields and Kotlin functions/properties/type aliases.
- Stubbing out methods by replacing the byte-code of their implementations.
- Removing annotations from classes/methods/fields.

It supports the following configuration options:
```gradle
import net.corda.gradle.jarfilter.JarFilterTask
task jarFilter(type: JarFilterTask) {
    // Task(s) whose JAR outputs should be filtered.
    jars jar

    // The annotations assigned to each filtering role. For example:
    annotations {
        forDelete = [
            "org.testing.DeleteMe"
        ]
        forStub = [
            "org.testing.StubMeOut"
        ]
        forRemove = [
            "org.testing.RemoveMe"
        ]
    }

    // Location for filtered JARs. Defaults to "$buildDir/filtered-libs".
    outputDir file(...)

    // Whether the timestamps on the JARs' entries should be preserved "as is"
    // or set to a platform-independent constant value (1st February 1980).
    preserveTimestamps = {true|false}

    // The maximum number of times (>= 1) to pass the JAR through the filter.
    maxPasses = 5

    // Writes more information about each pass of the filter.
    verbose = {true|false}
}
```

You can specify as many annotations for each role as you like. The only constraint is that a given
annotation cannot be assigned to more than one role.

### The `MetaFixer` task
The `MetaFixer` task updates the `@kotlin.Metadata` annotations by removing references to any functions,
constructors, properties or nested classes that no longer exist in the byte-code. This is primarily to
"repair" Kotlin library code that has been processed by ProGuard.

Kotlin type aliases exist only inside `@Metadata` and so are unaffected by this task. Similarly, the
constructors for Kotlin's annotation classes don't exist in the byte-code either because Java annotations
are interfaces really. The `MetaFixer` task will therefore ignore annotations' constructors too.

It supports these configuration options:
```gradle
import net.corda.gradle.jarfilter.MetaFixerTask
task metafix(type: MetaFixerTask) {
    // Task(s) whose JAR outputs should be fixed.
    jars jar

    // Location for fixed JARs. Defaults to "$buildDir/metafixed-libs"
    outputDir file(...)

    // Tag to be appended to the JAR name. Defaults to "-metafixed".
    suffix = "..."

    // Whether the timestamps on the JARs' entries should be preserved "as is"
    // or set to a platform-independent constant value (1st February 1980).
    preserveTimestamps = {true|false}
}
```

## Implementation Details

### Code Coverage
You can generate a JaCoCo code coverage report for the unit tests using:
```bash
$ cd buildSrc
$ ../gradlew jarfilter:jacocoTestReport
```

### Kotlin Metadata
The Kotlin compiler encodes information about each class inside its `@kotlin.Metadata` annotation.

```kotlin
import kotlin.annotation.AnnotationRetention.*

@Retention(RUNTIME)
annotation class Metadata {
    val k: Int = 1
    val d1: Array<String> = []
    val d2: Array<String> = []
    // ...
}
```

This is an internal feature of Kotlin which is read by Kotlin Reflection. There is no public API
for writing this information, and the content format of arrays `d1` and `d2` depends upon the
"class kind" `k`. For the kinds that we are interested in, `d1` contains a buffer of ProtoBuf
data and `d2` contains an array of `String` identifiers which the ProtoBuf data refers to by index.

Although ProtoBuf generates functions for both reading and writing the data buffer, the
Kotlin Reflection artifact only contains the functions for reading. This is almost certainly
because the writing functionality has been removed from the `kotlin-reflect` JAR using
ProGuard. However, the complete set of generated ProtoBuf classes is still available in the
`kotlin-compiler-embeddable` JAR. The `jarfilter:kotlin-metadata` module uses ProGuard to
extracts these classes into a new `kotlin-metdata` JAR, discarding any classes that the
ProtoBuf ones do not need and obfuscating any other ones that they do.

The custom `kotlin-metadata` object was originally created as a workaround for
[KT-18621](https://youtrack.jetbrains.com/issue/KT-18621). However, reducing the number of unwanted
classes on the classpath anyway can only be a Good Thing<sup>(TM)</sup>.

At runtime, `JarFilter` decompiles the ProtoBuf buffer into POJOs, deletes the elements that
no longer exist in the byte-code and then recompiles the POJOs into a new ProtoBuf buffer. The
`@Metadata` annotation is then rewritten using this new buffer for `d1` and the _original_ `String`
identifiers for `d2`. While some of these identifiers are very likely no longer used after this,
removing them would also require re-indexing the ProtoBuf data. It is therefore simpler just to
leave them as harmless cruft in the byte-code's constant pool.

The majority of `JarFilter`'s unit tests use Kotlin and Java reflection and so should not be
brittle as Kotlin evolves because `kotlin-reflect` is public API. Also, Kotlin's requirement that
it remain backwards-compatible with itself should imply that the ProtoBuf logic shouldn't change
(much). However, the ProtoBuf classes are still internal to Kotlin and so it _is_ possible that they
will occasionally move between packages. This has already happened for Kotlin 1.2.3x -> 1.2.4x, but
I am hoping this means that they will not move again for a while.

### JARs vs ZIPs
The `JarFilter` and `MetaFixer` tasks _deliberately_ use `ZipFile` and `ZipOutputStream` rather
than `JarInputStream` and `JarOutputStream` when reading and writing their JAR files. This is to
ensure that the original `META-INF/MANIFEST.MF` files are passed through unaltered. Note also that
there is no `ZipInputStream.getComment()` method, and so we need to use `ZipFile` in order to
preserve any JAR comments.

Neither `JarFilter` nor `MetaFixer` should change the order of the entries inside the JAR files.
