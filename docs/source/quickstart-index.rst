Getting started developing CorDapps
===================================

.. toctree::
  :hidden:
  :titlesonly:
  :maxdepth: 0

  quickstart-deploy
  quickstart-build

Getting started with Corda will walk you through the process of setting up a development environment, deploying an example CorDapp, and building your own CorDapp based on the example.

1. `Setting up a development environment`_
2. `Deploying an example CorDapp <./quickstart-deploy.html>`_
3. `Building your own CorDapp <./quickstart-build.html>`_

Setting up a development environment
------------------------------------

Prerequisites
~~~~~~~~~~~~~

* **Java 8 JVK** - We require at least version |java_version|, but do not currently support Java 9 or higher.
* **IntelliJ IDEA** - IntelliJ is an IDE that offers strong support for Kotlin and Java development. We support versions **2017.x**, **2018.x** and **2019.x** (with Kotlin plugin version |kotlin_version|).
* **Git** - We use Git to host our sample CorDapp and provide version control.

Step One: Downloading a sample project
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Open a command prompt or terminal.
2. Clone the CorDapp example repo by running: ``git clone https://github.com/corda/samples``
3. Move into the ``cordapp-example`` folder by running: ``cd samples/cordapp-example``
4. Checkout the corresponding branch by running: ``git checkout release-V4`` in the current directory.


Step Two: Creating an IntelliJ project
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Open IntelliJ. From the splash screen, click **Open**, navigate to and select the ``cordapp-example`` folder, and click **Ok**. This creates an IntelliJ project to work from.

2. Click **File** >  **Project Structure**. To set the project SDK click **New...** > **JDK**, and navigating to the installation directory of your JDK. Click **Apply**.

3. Select **Modules** > **+** > **Import Module**. Select the ``cordapp-example`` folder and click **Open**. Select **Import module from external model** > **Gradle** > **Next** > tick the **Use auto-import** checkbox > **Finish** > **Ok**. Gradle will now download all the project dependencies and perform some indexing.

Your CorDapp development environment is now complete.

Next steps
----------

Now that you've successfully set up your CorDapp development environment, we'll cover deploying an example CorDapp locally, before writing a CorDapp from scratch.
