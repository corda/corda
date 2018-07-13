Building the documentation
==========================

The documentation is under the ``docs`` folder, and is written in reStructuredText format. Documentation in HTML format
is pre-generated, as well as code documentation, and this can be done automatically via a provided script.

Requirements
------------

In order to build the documentation you will need a development environment set up as described under :doc:`building-corda`.

You will also need additional dependencies based on your O/S which are detailed below.

Windows
-------

Git, bash and make
~~~~~~~~~~~~~~~~~~

In order to build the documentation for Corda you need a ``bash`` emulator with ``make`` installed and accessible from the command prompt. Git for
Windows ships with a version of MinGW that contains a ``bash`` emulator, to which you can download and add a Windows port of
make, instructions for which are provided below. Alternatively you can install a full version of MinGW from `here <http://www.mingw.org/>`_.

1. Go to `ezwinports <https://sourceforge.net/projects/ezwinports/files/>`_ and click the download for ``make-4.2.1-without-guile-w32-bin.zip``
2. Navigate to the git installation directory (by default ``C:\Program Files\Git``), open ``mingw64``
3. Unzip the downloaded file into this directory, but do NOT overwrite/replace any existing files
4. Add the git ``bin`` directory to your system PATH environment variable (by default ``C:\Program Files\Git\bin``)
5. Open a new command prompt and run ``bash`` to test that you can access the git bash emulator
6. Type ``make`` to make sure it has been installed successfully (you should get an error
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


Debian/Ubuntu Linux
-------------------

These instructions were tested on Ubuntu Server 18.04 LTS. This distribution includes ``git`` and ``python`` so only the following steps are required:

Pip/VirtualEnv
~~~~~~~~~~~~~~

1. Run ``sudo apt-get install python-pip``
2. Run ``pip install virtualenv``
3. Run ``pip --version`` to verify that pip is installed correctly
4. Run ``virtualenv --version`` to verify that virtualenv is installed correctly

LaTeX
~~~~~

Corda requires LaTeX to be available for building the documentation. The instructions below are for installing TeX Live
but other distributions are available.

1. Run ``sudo apt-get install texlive-full``


Build
-----

Once the requirements are installed, you can automatically build the HTML format user documentation, PDF, and
the API documentation by running the following script:

.. sourcecode:: shell

    // On Windows
    gradlew buildDocs

    // On Mac
    ./gradlew buildDocs

Alternatively you can build non-HTML formats from the ``docs`` folder.

However, running ``make`` from the command line requires further dependencies to be installed. When building in gradle they
are installed in a `python virtualenv <https://virtualenv.pypa.io/en/stable/>`_, so they will need explicitly installing
by running:

.. sourcecode:: shell

    pip install -r requirements.txt

Change directory to the ``docs`` folder and then run the following to see a list of all available formats:

.. sourcecode:: shell

    make

For example to produce the documentation in HTML format run:

.. sourcecode:: shell

    make html
