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

The getting started experience is designed to be lightweight and get to code as quickly as possible, for more detail, see the following documentation:

* CorDapp design best practice
* Testing CorDapps

For a more operations-focused experience, see the following operations documentation:

* Node structure and configuration
* Deploying a node
* Notary docs
* HSM configuration

Setting up a development environment
------------------------------------

**write some text here**

Prerequisites
~~~~~~~~~~~~~

* **Java 8 JVK** - We require at least version |java_version|, but do not currently support Java 9 or higher.
* **IntelliJ IDEA** - IntelliJ is an IDE that offers strong support for Kotlin and Java development. We support versions **2017.x**, **2018.x** and **2019.x** (with Kotlin plugin version |kotlin_version|)
* **Gradle** - Gradle is a build automation tool that we use for dependency management. We use version 4.10 and the ``gradlew`` script in the project/samples directories will download it for you.
* **Git** - We use Git to host our sample CorDapp and provide version control.


Step One: Downloading a sample project
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Open a command prompt or terminal.
2. Clone the CorDapp example repo by running: ``git clone https://github.com/corda/cordapp-example``
3. Move into the ``cordapp-example`` folder by running: ``cd cordapp-example``
4. Checkout the corresponding branch by running: ``git checkout release-V4`` in the current directory.


Step Two: Creating an IntelliJ project
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Open IntelliJ. From the splash screen, click **Open**, navigate to and select the ``cordapp-example`` folder, and click **Ok**. This creates an IntelliJ project to work from.

2. Once the project is open, click **File**, then **Project Structure**. Under **Project SDK:**, set the project SDK by clicking **New...**, clicking **JDK**, and navigating to ``C:\Program Files\Java\jdk1.8.0_XXX`` on Windows or ``Library/Java/JavaVirtualMachines/jdk1.8.XXX`` on MacOSX, where ``XXX`` is the latest minor version number. Click **Apply** followed by **Ok**. This instructs IntelliJ to use the version of the Java JDK downloaded in the prerequisites.

3. Click **File** then **Project Structure**, select **Modules**. Click **+**, then **Import Module**, then select the ``cordapp-example`` folder and click **Open**. Select **Import module from external model**, select **Gradle**, click **Next** then **Finish** and **Ok**. Gradle will now download all the project dependencies and perform some indexing.

Your CorDapp development environment is now complete.

Next steps
----------

Now that you've successfully set up your CorDapp development environment, we'll cover deploying the example CorDapp locally.
