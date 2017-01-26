Publishing Corda
================

Before Publishing
-----------------

Before publishing you must make sure the version you plan to publish has a unique version number. Jcenter and Maven
Central will not allow overwriting old versions _unless_ the version is a snapshot.

This guide assumes you are trying to publish to net.corda.*. Any other Maven coordinates require approval from Jcenter
and Maven Central.

Publishing Locally
------------------

To publish the codebase locally to Maven Local you must run:

.. code-block:: text

     gradlew install

.. note:: This command is an alias for `publishToMavenLocal`.

Publishing to Jcenter
---------------------

.. note:: The module you wish to publish must be linked to jcenter in Bintray. Only the founding account can do this.

To publish to Jcenter you must first have the following;

1. An account on Bintray in the R3 organisation
2. Our GPG key's passphrase for signing the binaries to publish

Getting Setup
`````````````

You must now set the following environment variables:

* CORDA_BINTRAY_USER your Bintray username
* CORDA_BINTRAY_KEY to your Bintray API key (found at: https://bintray.com/profile/edit)
* CORDA_BINTRAY_GPG_PASSPHRASE to our GPG passphrase

Publishing
``````````

Once you are setup you can upload all modules in a project with

.. code-block:: text

    gradlew bintrayUpload

Now login to Bintray and navigate to the corda repository, you will see a box stating you have published N files
and asking if you wish to publish. You can now publish to Bintray and Jcenter by clicking this button.

.. warning:: Before publishing you should check that all of the files are uploaded and are signed.

Within a minute your new version will be available to download and use.

Publishing to Maven Central
---------------------------

To publish to Maven Central you need the following;

1. An admin account on our Bintray R3 organisation
2. A published version in Bintray
3. An account with our Sonatype organisation (Maven Central's host)

Publishing
``````````

1. Publish to Bintray
2. Navigate to the project you wish to publish
3. Click "Maven Central"
4. Enter your Sonatype credentials to publish a new version

.. note:: The project you publish must be already published to Bintray and the project must be linked to Jcenter
