Building the documentation
==========================

The documentation is under the ``docs`` folder, and is written in reStructuredText format. Documentation in HTML format
is pre-generated, as well as code documentation, and this can be done automatically via a provided script.

Requirements
------------

To build the documentation, you will need:

* GNU Make
* Python and pip (tested with Python 2.7.10)
* Sphinx: http://www.sphinx-doc.org/
* sphinx_rtd_theme: https://github.com/snide/sphinx_rtd_theme

Note that to install under OS X El Capitan, you will need to tell pip to install under ``/usr/local``, which can be
done by specifying the installation target on the command line:

.. sourcecode:: shell

    sudo -H pip install --install-option '--install-data=/usr/local' Sphinx
    sudo -H pip install --install-option '--install-data=/usr/local' sphinx_rtd_theme

Build
-----

Once the requirements are installed, you can automatically build the HTML format user documentation and the API
documentation by running the following script:

.. sourcecode:: shell

    ./gradlew buildDocs

Alternatively you can build non-HTML formats from the ``docs`` folder. Change directory to the folder and then run the
following to see a list of all available formats:

.. sourcecode:: shell

    make

For example to produce the documentation in HTML format:

.. sourcecode:: shell

    make html
