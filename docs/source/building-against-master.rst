Building CorDapps against Master
================================

It is advisable to develop CorDapps against the most recent Corda stable release. However, you may need to build a CorDapp 
against the unstable Master branch if your CorDapp uses a very recent feature, or you are using the CorDapp to test a PR 
on the main codebase.

To work against the Master branch, proceed as follows:

1. Clone the `Corda repository <https://github.com/corda/corda>`_

2. Open a terminal window in the folder where you cloned the Corda repository

3. Use the following command to check out the latest master branch:

    ``git checkout master; git pull``

4. Publish Corda to your local Maven repository using the following commands:

  * Unix/Mac OSX: ``./gradlew install``
  * Windows: ``gradlew.bat install``

  .. warning:: If you do modify your local Corda repository after having published it to Maven local, then you must
     re-publish it to Maven local for the local installation to reflect the changes you have made.

  .. warning:: As the Corda repository evolves on a daily basis, two clones of the Master branch at different points in
     time may differ. If you are using a Master release and need help debugging an error, then please let us know the
     **commit** you are working from. This will help us ascertain the issue.

5. Update the ``ext.corda_release_version`` property in your CorDapp's root ``build.gradle`` file to match the version
   here: https://github.com/corda/corda/blob/master/build.gradle#L7
