Quasar Utils
============


Plugin Maven Name::

    quasar-utils

Quasar utilities adds several tasks and configuration that provide a default Quasar setup and removes some boilerplate.
One line must be added to your build.gradle once you apply this plugin:

.. code-block:: text

    quasarScan.dependsOn('classes')

If any sub-projects are added that this project depends on then add the gradle target for that project to the depends
on statement. eg:

.. code-block:: text

    quasarScan.dependsOn('classes', 'subproject:subsubproject', ...)
