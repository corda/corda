.. raw:: html

    <style> .red {color:red} </style>

.. role:: red

Deterministic Corda Modules
===========================

A Corda contract's verify function should always produce the same results for the same input data. To that end,
Corda provides the following modules:
 
 #. ``core-deterministic``
 #. ``serialization-deterministic``
 #. ``jdk8u-deterministic``

These are reduced version of Corda's ``core`` and ``serialization`` modules and the OpenJDK 8 ``rt.jar``, where the
non-deterministic functionality has been removed. The intention here is that all CorDapp classes required for
contract verification should be compiled against these modules to prevent them containing non-deterministic behaviour.

.. note:: These modules are only a development aid. They cannot guarantee determinism without also including
          deterministic versions of all their dependent libraries, e.g. ``kotlin-stdlib``.

Generating the Deterministic Modules
------------------------------------

JDK 8
  ``jdk8u-deterministic`` is a "pseudo JDK" image that we can point the Java and Kotlin compilers to. It downloads the
  ``rt.jar`` containing a deterministic subset of the Java 8 APIs from the Artifactory.

  To build a new version of this JAR and upload it to the Artifactory, see the ``create-jdk8u`` module. This is a
  standalone Gradle project within the Corda repository that will clone the ``deterministic-jvm8`` branch of Corda's
  `OpenJDK repository <https://github.com/corda/openjdk>`_ and then build it. (This currently requires a C++ compiler,
  GNU Make and a UNIX-like development environment.)

Corda Modules
  ``core-deterministic`` and ``serialization-deterministic`` are generated from Corda's ``core`` and ``serialization``
  modules respectively using both `ProGuard <https://www.guardsquare.com/en/proguard>`_ and Corda's ``JarFilter`` Gradle
  plugin. Corda developers configure these tools by applying Corda's ``@KeepForDJVM`` and ``@DeleteForDJVM``
  annotations to elements of ``core`` and ``serialization`` as described :ref:`here <deterministic_annotations>`.

The build generates each of Corda's deterministic JARs in six steps:

 #. Some *very few* classes in the original JAR must be replaced completely. This is typically because the original
    class uses something like ``ThreadLocal``, which is not available in the deterministic Java APIs, and yet the
    class is still required by the deterministic JAR. We must keep such classes to a minimum!
 #. The patched JAR is analysed by ProGuard for the first time using the following rule:

    .. sourcecode:: groovy

        keep '@interface net.corda.core.KeepForDJVM { *; }'

    ..

    ProGuard works by calculating how much code is reachable from given "entry points", and in our case these entry
    points are the ``@KeepForDJVM`` classes. The unreachable classes are then discarded by ProGuard's ``shrink``
    option.
 #. The remaining classes may still contain non-deterministic code. However, there is no way of writing a ProGuard rule
    explicitly to discard anything. Consider the following class:

    .. sourcecode:: kotlin

        @CordaSerializable
        @KeepForDJVM
        data class UniqueIdentifier @JvmOverloads @DeleteForDJVM constructor(
            val externalId: String? = null,
            val id: UUID = UUID.randomUUID()
        ) : Comparable<UniqueIdentifier> {
            ...
        }

    ..

    While CorDapps will definitely need to handle ``UniqueIdentifier`` objects, all of the secondary constructors
    generate a new random ``UUID`` and so are non-deterministic. Hence the next "determinising" step is to pass the
    classes to the ``JarFilter`` tool, which strips out all of the elements which have been annotated as
    ``@DeleteForDJVM`` and stubs out any functions annotated with ``@StubOutForDJVM``. (Stub functions that
    return a value will throw ``UnsupportedOperationException``, whereas ``void`` or ``Unit`` stubs will do nothing.)
 #. After the ``@DeleteForDJVM`` elements have been filtered out, the classes are rescanned using ProGuard to remove
    any more code that has now become unreachable.
 #. The remaining classes define our deterministic subset. However, the ``@kotlin.Metadata`` annotations on the compiled
    Kotlin classes still contain references to all of the functions and properties that ProGuard has deleted. Therefore
    we now use the ``JarFilter`` to delete these references, as otherwise the Kotlin compiler will pretend that the
    deleted functions and properties are still present.
 #. Finally, we use ProGuard again to validate our JAR against the deterministic ``rt.jar``:

    .. literalinclude:: ../../core-deterministic/build.gradle
       :language: groovy
       :start-after: DOCSTART 01
       :end-before: DOCEND 01
    ..

    This step will fail if ProGuard spots any Java API references that still cannot be satisfied by the deterministic
    ``rt.jar``, and hence it will break the build.

Configuring IntelliJ with a Deterministic SDK
---------------------------------------------

We would like to configure IntelliJ so that it will highlight uses of non-deterministic Java APIs as :red:`not found`.
Or, more specifically, we would like IntelliJ to use the ``deterministic-rt.jar`` as a "Module SDK" for deterministic
modules rather than the ``rt.jar`` from the default project SDK, to make IntelliJ consistent with Gradle.

This is possible, but slightly tricky to configure because IntelliJ will not recognise an SDK containing only the
``deterministic-rt.jar`` as being valid. It also requires that IntelliJ delegate all build tasks to Gradle, and that
Gradle be configured to use the Project's SDK.

Creating the Deterministic SDK
    Gradle creates a suitable JDK image in the project's ``jdk8u-deterministic/jdk`` directory, and you can
    configure IntelliJ to use this location for this SDK. However, you should also be aware that IntelliJ SDKs
    are available for *all* projects to use.

    To create this JDK image, execute the following:

    .. code-block:: bash

        $ gradlew jdk8u-deterministic:copyJdk

    ..

    Now select ``File/Project Structure/Platform Settings/SDKs`` and add a new JDK SDK with the
    ``jdk8u-deterministic/jdk`` directory as its home. Rename this SDK to something like "1.8 (Deterministic)".

    This *should* be sufficient for IntelliJ. However, if IntelliJ realises that this SDK does not contain a
    full JDK then you will need to configure the new SDK by hand:

        #. Create a JDK Home directory with the following contents:

            ``jre/lib/rt.jar``

           where ``rt.jar`` here is this renamed artifact:

           .. code-block:: xml

               <dependency>
                   <groupId>net.corda</groupId>
                   <artifactId>deterministic-rt</artifactId>
                   <classifier>api</classifier>
               </dependency>

           ..

        #. While IntelliJ is *not* running, locate the ``config/options/jdk.table.xml`` file in IntelliJ's configuration
           directory. Add an empty ``<jdk>`` section to this file:

           .. code-block:: xml

               <jdk version="2">
                   <name value="1.8 (Deterministic)"/>
                   <type value="JavaSDK"/>
                   <version value="java version &quot;1.8.0&quot;"/>
                   <homePath value=".. path to the deterministic JDK directory .."/>
                   <roots>
                   </roots>
               </jdk>

           ..

        #. Open IntelliJ and select ``File/Project Structure/Platform Settings/SDKs``. The "1.8 (Deterministic)" SDK
           should now be present. Select it and then click on the ``Classpath`` tab. Press the "Add" / "Plus" button to
           add ``rt.jar`` to the SDK's classpath. Then select the ``Annotations`` tab and include the same JAR(s) as
           the other SDKs.

Configuring the Corda Project
    #. Open the root ``build.gradle`` file and define this property:

       .. code-block:: gradle

           buildscript {
               ext {
                   ...
                   deterministic_idea_sdk = '1.8 (Deterministic)'
                   ...
               }
           }

       ..

Configuring IntelliJ
    #. Go to ``File/Settings/Build, Execution, Deployment/Build Tools/Gradle``, and configure Gradle's JVM to be the
       project's JVM.

    #. Go to ``File/Settings/Build, Execution, Deployment/Build Tools/Gradle/Runner``, and select these options:

        - Delegate IDE build/run action to Gradle
        - Run tests using the Gradle Test Runner

    #. Delete all of the ``out`` directories that IntelliJ has previously generated for each module.

    #. Go to ``View/Tool Windows/Gradle`` and click the ``Refresh all Gradle projects`` button.

These steps will enable IntelliJ's presentation compiler to use the deterministic ``rt.jar`` with the following modules:

    - ``core-deterministic``
    - ``serialization-deterministic``
    - ``core-deterministic:testing:common``

but still build everything using Gradle with the full JDK.

Testing the Deterministic Modules
---------------------------------

The ``core-deterministic:testing`` module executes some basic JUnit tests for the ``core-deterministic`` and
``serialization-deterministic`` JARs. These tests are compiled against the deterministic ``rt.jar``, although
they are still executed using the full JDK.

The ``testing`` module also has two sub-modules:

``core-deterministic:testing:data``
    This module generates test data such as serialised transactions and elliptic curve key pairs using the full
    non-deterministic ``core`` library and JDK. This data is all written into a single JAR which the ``testing``
    module adds to its classpath.

``core-deterministic:testing:common``
    This module provides the test classes which the ``testing`` and ``data`` modules need to share. It is therefore
    compiled against the deterministic API subset.


.. _deterministic_annotations:

Applying @KeepForDJVM and @DeleteForDJVM annotations
----------------------------------------------------

Corda developers need to understand how to annotate classes in the ``core`` and ``serialization`` modules correctly
in order to maintain the deterministic JARs.

.. note:: Every Kotlin class still has its own ``.class`` file, even when all of those classes share the same
          source file. Also, annotating the file:

          .. sourcecode:: kotlin

              @file:KeepForDJVM
              package net.corda.core.internal

          ..

          *does not* automatically annotate any class declared *within* this file. It merely annotates any
          accompanying Kotlin ``xxxKt`` class.

For more information about how ``JarFilter`` is processing the byte-code inside ``core`` and ``serialization``,
use Gradle's ``--info`` or ``--debug`` command-line options.

Deterministic Classes
    Classes that *must* be included in the deterministic JAR should be annotated as ``@KeepForDJVM``.

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/KeepForDJVM.kt
       :language: kotlin
       :start-after: DOCSTART 01
       :end-before: DOCEND 01
    ..

    To preserve any Kotlin functions, properties or type aliases that have been declared outside of a ``class``,
    you should annotate the source file's ``package`` declaration instead:

    .. sourcecode:: kotlin

        @file:JvmName("InternalUtils")
        @file:KeepForDJVM
        package net.corda.core.internal

        infix fun Temporal.until(endExclusive: Temporal): Duration = Duration.between(this, endExclusive)

    ..

Non-Deterministic Elements
    Elements that *must* be deleted from classes in the deterministic JAR should be annotated as ``@DeleteForDJVM``.

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/DeleteForDJVM.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01
    ..

    You must also ensure that a deterministic class's primary constructor does not reference any classes that are
    not available in the deterministic ``rt.jar``. The biggest risk here would be that ``JarFilter`` would delete the
    primary constructor and that the class could no longer be instantiated, although ``JarFilter`` will print a warning
    in this case. However, it is also likely that the "determinised" class would have a different serialisation
    signature than its non-deterministic version and so become unserialisable on the deterministic JVM.

    Primary constructors that have non-deterministic default parameter values must still be annotated as
    ``@DeleteForDJVM`` because they cannot be refactored without breaking Corda's binary interface. The Kotlin compiler
    will automatically apply this ``@DeleteForDJVM`` annotation - along with any others - to all of the class's
    secondary constructors too. The ``JarFilter`` plugin can then remove the ``@DeleteForDJVM`` annotation from the
    primary constructor so that it can subsequently delete only the secondary constructors.

    The annotations that ``JarFilter`` will "sanitise" from primary constructors in this way are listed in the plugin's
    configuration block, e.g.

    .. sourcecode:: groovy

        task jarFilter(type: JarFilterTask) {
            ...
            annotations {
                ...

                forSanitise = [
                    "net.corda.core.DeleteForDJVM"
                ]
            }
        }

    ..

    Be aware that package-scoped Kotlin properties are all initialised within a common ``<clinit>`` block inside
    their host ``.class`` file. This means that when ``JarFilter`` deletes these properties, it cannot also remove
    their initialisation code. For example:

    .. sourcecode:: kotlin

        package net.corda.core

        @DeleteForDJVM
        val map: MutableMap<String, String> = ConcurrentHashMap()

    ..

    In this case, ``JarFilter`` would delete the ``map`` property but the ``<clinit>`` block would still create
    an instance of ``ConcurrentHashMap``. The solution here is to refactor the property into its own file and then
    annotate the file itself as ``@DeleteForDJVM`` instead.

Non-Deterministic Function Stubs
    Sometimes it is impossible to delete a function entirely. Or a function may have some non-deterministic code
    embedded inside it that cannot be removed. For these rare cases, there is the ``@StubOutForDJVM``
    annotation:

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/StubOutForDJVM.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01
    ..

    This annotation instructs ``JarFilter`` to replace the function's body with either an empty body (for functions
    that return ``void`` or ``Unit``) or one that throws ``UnsupportedOperationException``. For example:

    .. sourcecode:: kotlin

        fun necessaryCode() {
            nonDeterministicOperations()
            otherOperations()
        }

        @StubOutForDJVM
        private fun nonDeterministicOperations() {
            // etc
        }

    ..
