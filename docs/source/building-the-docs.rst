Building the documentation
==========================

The documentation is under the ``docs`` folder, and is written in reStructuredText format. Documentation in HTML format
is pre-generated, as well as code documentation, and this can be done automatically via a provided script.

Requirements
------------

In order to build the documentation you will need a development environment set up as described under :doc:`building-corda`.

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
