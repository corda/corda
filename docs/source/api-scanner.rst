Api Stability Check
===================

We have committed not to alter Corda's API so that developers will not have to keep rewriting their CorDapps with each
new Corda release. The stable Corda modules are listed :doc:`here </corda-api>`. Our CI process runs an "API Stability"
check for each GitHub pull request in order to check that we don't accidentally introduce an API-breaking change.

Build Process
-------------

As part of the build process our the following commands are run for each PR:

.. code-block:: shell

   $ gradlew generateApi
   $ .ci/check-api-changes.sh

The script's return value is the number of API-breaking changes that it has detected, and this should be zero for the
check to pass. There are three kinds of breaking changes.

* Removal or modification of existing API, i.e. an existing class, method or field has been either deleted or renamed, or
  its signature somehow altered.
* Addition of a new method to an interface or abstract class. Types that have been annotated as ``@DoNotImplement`` are
  excluded from this check. (This annotation is also inherited across subclasses and subinterfaces.)
* Exposure of an internal type via a public API. Internal types are considered to be anything in a ``*.internal.`` package
  or anything in a module that isn't in the stable modules list :doc:`here </corda-api>`.

Developers can execute these commands themselves before submitting their PR, to ensure that they haven't inadvertently
broken Corda's API. (The shell script works on MacOSX and distributions of Linux.)


How it works
------------

The ``generateApi`` Gradle task writes a summary of Corda's public API into the file ``build/api/api-corda-<version>.txt``.
The ``.ci/check-api-changes.sh`` script then compares this file with the contents of ``.ci/api-current.txt``, which is a
managed file within the Corda repository.

The Gradle task itself is implemented by the API Scanner plugin. More information on the API Scanner plugin is available `here <https://github.com/corda/corda-gradle-plugins/tree/master/api-scanner>`_.


Updating the API
----------------

As a rule, ``api-current.txt`` should only be updated by the release manager for each Corda release.

We do not expect modifications to ``api-current.txt`` as part of normal development. However, we may sometimes need to adjust
the public API in ways that would not break developers' CorDapps but which would be blocked by the API Stabilty check.
For example, migrating a method from an interface into a superinterface. Any changes to the API summary file should be
included in the PR, which would then need explicit approval from either Mike Hearn, Rick Parker or Matthew Nesbit.

.. note:: If you need to modify ``api-current.txt``, do not re-generate the file on the master branch. This will include new api that
   hasn't been released or committed to, and may be subject to change. Manually change the specific line or lines of the
   existing committed api that has changed.