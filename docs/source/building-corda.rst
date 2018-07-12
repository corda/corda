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

Git, bash and make
~~~~~~~~~~~~~~~~~~

In order to build Corda you need a ``bash`` emulator with ``make`` installed and accessible from the command prompt. Git for
Windows ships with a version of MinGW that contains a ``bash`` emulator, to which you can download and add a Windows port of
make, instructions for which are provided below. Alternatively you can install a full version of MinGW from `here <http://www.mingw.org/>`_.

1. Visit https://git-scm.com/download/win
2. Click the "64-bit Git for Windows Setup" download link.
3. Download and run the executable to install Git (use the default installation values) and make a note of the installation directory.
4. Open a new command prompt and type ``git --version`` to test that git is installed correctly
5. Go to `ezwinports <https://sourceforge.net/projects/ezwinports/files/>`_ and click the download for ``make-4.2.1-without-guile-w32-bin.zip``
6. Navigate to the git installation directory (by default ``C:\Program Files\Git``), open ``mingw64``
7. Unzip the downloaded file into this directory, but do NOT overwrite/replace any existing files
8. Add the git ``bin`` directory to your system PATH environment variable (by default ``C:\Program Files\Git\bin``)
9. Open a new command prompt and run ``bash`` to test that you can access the git bash emulator
10. Type ``make`` to make sure it has been installed successfully (you should get an error
    like ``make: *** No targets specified and no makefile found.  Stop.``)

Python, pip and virtualenv
~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Visit https://www.python.org/downloads
2. Scroll down to the most recent v2 release (tested with v.2.7.15) and click the download link
3. Download the "Windows x86-64 MSI installer"
4. Run the installation, making a note of the python installation directory (defaults to ``c:\Python27``)
5. Add the python installation directory (e.g. ``c:\Python27``) to your system PATH environment variable
6. Add the python scripts sub-directory (e.g. ``c:\Python27\scripts``) to your System PATH environment variable
7. Open a new command prompt and check you can run python by running ``python --version``
8. Check you can run pip by running ``pip --version``
9. Install ``virtualenv`` by running ``pip install virtualenv`` from the commandline
10. Check you can run ``virualenv`` by running ``virtualenv --version`` from the commandline.

LaTeX
~~~~~

Corda requires LaTeX to be available for building the documentation. The instructions below are for installing TeX Live
but other distributions are available.

1. Visit https://tug.org/texlive/
2. Click download
3. Download and run ``install-tl-windows.exe``
4. Keep the default options (simple installation is fine)
5. Open a new command prompt and check you can run ``pdflatex`` by running ``pdflatex --version``

Buillding Corda
~~~~~~~~~~~~~~~

1. Open a command prompt
2. Run ``git clone https://github.com/corda/coda``
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

Install Pip/VirtualEnv
~~~~~~~~~~~~~~~~~~~~~~
1. Run ``sudo apt-get install python-pip``
2. Run ``pip install virtualenv``
3. Run ``pip --version`` to verify that pip is installed correctly
4. Run ``virtualenv --version`` to verify that virtualenv is installed correctly

Install LaTeX
~~~~~~~~~~~~~

Corda requires LaTeX to be available for building the documentation. The instructions below are for installing TeX Live
but other distributions are available.

1. Run ``sudo apt-get install texlive-full``

Building Corda
~~~~~~~~~~~~~~

1. Open the terminal
2. Run ``git clone https://github.com/corda/coda``
3. Run ``./gradlew build``


