Getting set up
==============

Software requirements
---------------------
Corda uses industry-standard tools:

* **Oracle JDK 8 JVM** - minimum supported version **8u131**
* **IntelliJ IDEA** - supported versions **2017.x** and **2018.x**
* **Git**

We also use Gradle and Kotlin, but you do not need to install them. A standalone Gradle wrapper is provided, and it 
will download the correct version of Kotlin.

Please note:

* Corda runs in a JVM. JVM implementations other than Oracle JDK 8 are not actively supported. However, if you do
  choose to use OpenJDK, you will also need to install OpenJFX

* Applications on Corda (CorDapps) can be written in any language targeting the JVM. However, Corda itself and most of
  the samples are written in Kotlin. Kotlin is an
  `official Android language <https://developer.android.com/kotlin/index.html>`_, and you can read more about why
  Kotlin is a strong successor to Java
  `here <https://medium.com/@octskyward/why-kotlin-is-my-next-programming-language-c25c001e26e3>`_. If you're
  unfamiliar with Kotlin, there is an official
  `getting started guide <https://kotlinlang.org/docs/tutorials/>`_, and a series of
  `Kotlin Koans <https://kotlinlang.org/docs/tutorials/koans.html>`_.

* IntelliJ IDEA is recommended due to the strength of its Kotlin integration.

Following these software recommendations will minimize the number of errors you encounter, and make it easier for
others to provide support. However, if you do use other tools, we'd be interested to hear about any issues that arise.

Set-up instructions
-------------------
The instructions below will allow you to set up a Corda development environment and run a basic CorDapp. If you have
any issues, please consult the :doc:`troubleshooting` page, or reach out on `Slack <http://slack.corda.net/>`_,
`Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or the `forums <https://discourse.corda.net/>`_.

The set-up instructions are available for the following platforms:

* :ref:`windows-label` (or `in video form <https://vimeo.com/217462250>`__)

* :ref:`mac-label` (or `in video form <https://vimeo.com/217462230>`__)

.. _windows-label:

Windows
-------

.. warning:: If you are using a Mac machine, please follow the :ref:`mac-label` instructions instead.

Java
^^^^
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Scroll down to "Java SE Development Kit 8uXXX" (where "XXX" is the latest minor version number)
3. Toggle "Accept License Agreement"
4. Click the download link for jdk-8uXXX-windows-x64.exe (where "XXX" is the latest minor version number)
5. Download and run the executable to install Java (use the default settings)
6. Add Java to the PATH environment variable by following the instructions at https://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html#path
7. Open a new command prompt and run ``java -version`` to test that Java is installed correctly

Git
^^^
1. Visit https://git-scm.com/download/win
2. Click the "64-bit Git for Windows Setup" download link.
3. Download and run the executable to install Git (use the default settings)
4. Open a new command prompt and type ``git --version`` to test that git is installed correctly

IntelliJ
^^^^^^^^
1. Visit https://www.jetbrains.com/idea/download/download-thanks.html?code=IIC
2. Download and run the executable to install IntelliJ Community Edition (use the default settings)

Download a sample project
^^^^^^^^^^^^^^^^^^^^^^^^^
1. Open a command prompt
2. Clone the CorDapp example repo by running ``git clone https://github.com/corda/cordapp-example``
3. Move into the ``cordapp-example`` folder by running ``cd cordapp-example``

Run from the command prompt
^^^^^^^^^^^^^^^^^^^^^^^^^^^
1. From the cordapp-example folder, deploy the nodes by running ``gradlew deployNodes``
2. Start the nodes by running ``call kotlin-source/build/nodes/runnodes.bat``
3. Wait until all the terminal windows display either "Webserver started up in XX.X sec" or "Node for "NodeC" started up and registered in XX.XX sec"
4. Test the CorDapp is running correctly by visiting the front end at http://localhost:10009/web/example/

Run from IntelliJ
^^^^^^^^^^^^^^^^^
1. Open IntelliJ Community Edition
2. On the splash screen, click ``Open`` (do **not** click ``Import Project``) and select the ``cordapp-example`` folder

.. warning:: If you click ``Import Project`` instead of ``Open``, the project's run configurations will be erased!

3. Once the project is open, click ``File``, then ``Project Structure``. Under ``Project SDK:``, set the project SDK by
   clicking ``New...``, clicking ``JDK``, and navigating to ``C:\\Program Files\\Java\\jdk1.8.0_XXX`` (where ``XXX`` is
   the latest minor version number). Click "OK"
4. Again under ``File`` then ``Project Structure``, select ``Modules``. Click ``+``, then ``Import Module``, then select
   the ``cordapp-example`` folder and click ``Open``. Choose to ``Import module from external model``, select
   ``Gradle``, click ``Next`` then ``Finish`` (leaving the defaults) and ``OK``
5. Wait for the indexing to finish (a progress bar will display at the bottom-right of the IntelliJ window until indexing
   is complete)
6. At the top-right of the screen, to the left of the green ``play`` arrow, you should see a dropdown. In that
   dropdown, select ``Run Example Cordapp - Kotlin`` and click the green ``play`` arrow.
7. Wait until the run windows displays the message ``Webserver started up in XX.X sec``
8. Test the CorDapp is running correctly by visiting the front end at `http://localhost:10009/web/example/

.. _mac-label:

Mac
---

.. warning:: If you are using a Windows machine, please follow the :ref:`windows-label` instructions instead.

Java
^^^^
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Scroll down to "Java SE Development Kit 8uXXX" (where "XXX" is the latest minor version number)
3. Toggle "Accept License Agreement"
4. Click the download link for jdk-8uXXX-macosx-x64.dmg (where "XXX" is the latest minor version number)
5. Download and run the executable to install Java (use the default settings)
6. Open a new terminal window and run ``java -version`` to test that Java is installed correctly

IntelliJ
^^^^^^^^
1. Visit https://www.jetbrains.com/idea/download/download-thanks.html?platform=mac&code=IIC
2. Download and run the executable to install IntelliJ Community Edition (use the default settings)

Download a sample project
^^^^^^^^^^^^^^^^^^^^^^^^^
1. Open a terminal
2. Clone the CorDapp example repo by running ``git clone https://github.com/corda/cordapp-example``
3. Move into the ``cordapp-example`` folder by running ``cd cordapp-example``

Run from the terminal
^^^^^^^^^^^^^^^^^^^^^
1. From the cordapp-example folder, deploy the nodes by running ``./gradlew deployNodes``
2. Start the nodes by running ``kotlin-source/build/nodes/runnodes``. Do not click while 8 additional terminal windows start up.
3. Wait until all the terminal windows display either "Webserver started up in XX.X sec" or "Node for "NodeC" started up and registered in XX.XX sec"
4. Test the CorDapp is running correctly by visiting the front end at http://localhost:10009/web/example/

Run from IntelliJ
^^^^^^^^^^^^^^^^^
1. Open IntelliJ Community Edition
2. On the splash screen, click ``Open`` (do **not** click ``Import Project``) and select the ``cordapp-example`` folder

.. warning:: If you click ``Import Project`` instead of ``Open``, the project's run configurations will be erased!

3. Once the project is open, click ``File``, then ``Project Structure``. Under ``Project SDK:``, set the project SDK by
   clicking ``New...``, clicking ``JDK``, and navigating to ``C:\\Program Files\\Java\\jdk1.8.0_XXX`` (where ``XXX`` is
   the latest minor version number). Click "OK"
4. Again under ``File`` then ``Project Structure``, select ``Modules``. Click ``+``, then ``Import Module``, then select
   the ``cordapp-example`` folder and click ``Open``. Choose to ``Import module from external model``, select
   ``Gradle``, click ``Next`` then ``Finish`` (leaving the defaults) and ``OK``
5. Wait for the indexing to finish (a progress bar will display at the bottom-right of the IntelliJ window until indexing
   is complete)
6. At the top-right of the screen, to the left of the green ``play`` arrow, you should see a dropdown. In that
   dropdown, select ``Run Example Cordapp - Kotlin`` and click the green ``play`` arrow.
7. Wait until the run windows displays the message ``Webserver started up in XX.X sec``
8. Test the CorDapp is running correctly by visiting the front end at http://localhost:10009/web/example/

Corda source code
-----------------

The Corda platform source code is available here:

    https://github.com/corda/corda.git

A CorDapp template that you can use as the basis for your own CorDapps is available in both Java and Kotlin versions:

    https://github.com/corda/cordapp-template-java.git

    https://github.com/corda/cordapp-template-kotlin.git

And a list of simple sample CorDapps for you to explore basic concepts is available here:

	https://www.corda.net/samples/

You can clone these repos to your local machine by running the command ``git clone [repo URL]``.

Next steps
----------
The best way to check that everything is working fine is by taking a deeper look at the
:doc:`example CorDapp <tutorial-cordapp>`.

Next, you should read through :doc:`Corda Key Concepts <key-concepts>` to understand how Corda works.

By then, you'll be ready to start writing your own CorDapps. Learn how to do this in the
:doc:`Hello, World tutorial <hello-world-introduction>`. You may want to refer to the API documentation, the
:doc:`flow cookbook <flow-cookbook>` and the `samples <https://www.corda.net/samples/>`_ along the way.

If you encounter any issues, please see the :doc:`troubleshooting` page, or ask on
`Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or via `our Slack channels <http://slack.corda.net/>`_.
