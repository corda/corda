Building Corda
==============

These instructions are for downloading and building the Corda code locally. If you only wish to develop CorDapps for
use on Corda, you don't need to do this, follow the instructions at :doc:`getting-set-up` and use the precompiled binaries.

Windows
-------

Java
~~~~
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Scroll down to "Java SE Development Kit 8uXXX" (where "XXX" is the latest minor version number)
3. Toggle "Accept License Agreement"
4. Click the download link for jdk-8uXXX-windows-x64.exe (where "XXX" is the latest minor version number)
5. Download and run the executable to install Java (use the default settings)
6. Add Java to the PATH environment variable by following the instructions at https://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html#path
7. Open a new command prompt and run ``java -version`` to test that Java is installed correctly

Git
~~~

1. Visit https://git-scm.com/download/win
2. Click the "64-bit Git for Windows Setup" download link.
3. Download and run the executable to install Git (use the default installation values) and make a note of the installation directory.
4. Open a new command prompt and type ``git --version`` to test that Git is installed correctly

Buillding Corda
~~~~~~~~~~~~~~~

1. Open a command prompt
2. Run ``git clone https://github.com/corda/corda.git``
3. Run ``gradlew build``


Debian/Ubuntu Linux
-------------------

These instructions were tested on Ubuntu Server 18.04 LTS. This distribution includes ``git`` and ``python`` so only the following steps are required:

Java
~~~~
1. Run ``sudo add-apt-repository ppa:webupd8team/java`` from the terminal. Press ENTER when prompted.
2. Run ``sudo apt-get update``
3. Then run ``sudo apt-get install oracle-java8-installer``. Press Y when prompted and agree to the licence terms.
4. Run ``java --version`` to verify that java is installed correctly


Building Corda
~~~~~~~~~~~~~~

1. Open the terminal
2. Run ``git clone https://github.com/corda/corda.git``
3. Run ``./gradlew build``


