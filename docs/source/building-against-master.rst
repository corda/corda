Building against Master
=======================

When developing a CorDapp, it is advisable to use the most recent Corda Milestone release, which has been extensively
tested. However, if you need to use a very recent feature of the codebase, you may need to work against the unstable
Master branch.

To work against the Master branch, proceed as follows:

* Open a terminal window in the folder where you cloned the Corda repository
  (available `here <https://github.com/corda/corda>`_)

* Use the following command to check out the latest master branch:

    ``git fetch; git checkout master``

* Publish Corda to your local Maven repository using the following commands:

  * Unix/Mac OSX: ``./gradlew install``
  * Windows: ``gradlew.bat install``

  By default, the Maven local repository is found at:

  * ``~/.m2/repository`` on Unix/Mac OS X
  * ``%HOMEPATH%\.m2`` on Windows

  This step is not necessary when using a Milestone releases, as the Milestone releases are published online

.. warning:: If you do modify your local Corda repository after having published it to Maven local, then you must
   re-publish it to Maven local for the local installation to reflect the changes you have made.

.. warning:: As the Corda repository evolves on a daily basis, two clones of the Master branch at different points in
   time may differ. If you are using a Master release and need help debugging an error, then please let us know the
   **commit** you are working from. This will help us ascertain the issue.