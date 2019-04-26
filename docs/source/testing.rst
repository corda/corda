Testing your changes
====================

Automated tests
---------------
Corda has a suite of tests that any contributing developers must maintain and extend when adding new code.

There are several test suites:

* **Unit tests**: These are traditional unit tests that should only test a single code unit, typically a method or class.
* **Integration tests**: These tests should test the integration of small numbers of units, preferably with mocked out services.
* **Smoke tests**: These are full end to end tests which start a full set of Corda nodes and verify broader behaviour.
* **Other**: These include tests such as performance tests, stress tests, etc, and may be in an external repo.

Running the automated tests
^^^^^^^^^^^^^^^^^^^^^^^^^^^
These tests are mostly written with JUnit and can be run via ``gradle``:

* **Windows**: Run ``gradlew test integrationTest smokeTest``
* **Unix/Mac OSX**: Run ``./gradlew test integrationTest smokeTest``

Before creating a pull request please make sure these pass.

Manual testing
--------------
You should manually test anything that would be impacted by your changes. The areas that usually need to be manually tested and when are
as follows:

* **Node startup** - changes in the ``node`` or ``node:capsule`` project in both the Kotlin or gradle or the ``cordformation`` gradle plugin.
* **Sample project** - changes in the ``samples`` project. eg; changing the IRS demo means you should manually test the IRS demo.
* **Explorer** - changes to the ``tools/explorer`` project.
* **Demobench** - changes to the ``tools/demobench`` project.

How to manually test each of these areas differs and is currently not fully specified. For now the best thing to do is to ensure the
program starts, that you can interact with it, and that no exceptions are generated in normal operation.

Running tests in IntelliJ
-------------------------

We recommend editing your IntelliJ preferences so that you use the Gradle runner - this means that the quasar utils
plugin will make sure that some flags (like ``-javaagent`` - see :ref:`below <tutorial_cordapp_alternative_test_runners>`) are
set for you.

To switch to using the Gradle runner:

* Navigate to ``Build, Execution, Deployment -> Build Tools -> Gradle -> Runner`` (or search for `runner`)

  * Windows: this is in "Settings"
  * MacOS: this is in "Preferences"

* Set "Delegate IDE build/run actions to gradle" to true
* Set "Run test using:" to "Gradle Test Runner"

.. _tutorial_cordapp_alternative_test_runners:

If you would prefer to use the built in IntelliJ JUnit test runner, you can add some code to your ``build.gradle`` file and
it will copy your quasar JAR file to the lib directory.

.. note:: Before creating the IntelliJ run configurations for these unit tests
    go to Run -> Edit Configurations -> Defaults -> JUnit, add
    ``-javaagent:lib/quasar.jar``
    to the VM options, and set Working directory to ``$PROJECT_DIR$``
    so that the ``Quasar`` instrumentation is correctly configured.

Add the following to your ``build.gradle`` file - ideally to a ``build.gradle`` that already contains the quasar-utils plugin line:

.. sourcecode:: groovy

    apply plugin: 'net.corda.plugins.quasar-utils'

    task installQuasar(type: Copy) {
        destinationDir rootProject.file("lib")
        from(configurations.quasar) {
            rename 'quasar-core(.*).jar', 'quasar.jar'
        }
    }


and then you can run ``gradlew installQuasar``.