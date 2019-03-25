Shell extensions for CLI Applications
=====================================

.. _installing-shell-extensions:

Installing shell extensions
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Users of ``bash`` or ``zsh`` can install an alias and auto-completion for Corda applications that contain a command line interface. Run:

.. code-block:: shell

   java -jar <name-of-JAR>.jar install-shell-extensions

Then, either restart your shell, or for ``bash`` users run:

.. code-block:: shell

   . ~/.bashrc

Or, for ``zsh`` run:

.. code-block:: shell

   . ~/.zshrc

You will now be able to run the command line application from anywhere by running the following:

.. code-block:: shell

   <alias> --<option>

For example, for the Corda node, install the shell extensions using

.. code-block:: shell

   java -jar corda-|corda_version|.jar install-shell-extensions

And then run the node by running:

.. code-block:: shell

   corda --<option>

Upgrading shell extensions
~~~~~~~~~~~~~~~~~~~~~~~~~~

Once the shell extensions have been installed, you can upgrade them in one of two ways.

1) Overwrite the existing JAR with the newer version. The next time you run the application, it will automatically update
   the completion file. Either restart the shell or see :ref:`above<installing-shell-extensions>` for instructions
   on making the changes take effect immediately.
2) If you wish to use a new JAR from a different directory, navigate to that directory and run:

   .. code-block:: shell

      java -jar <name-of-JAR>

   Which will update the alias to point to the new location, and update command line completion functionality. Either
   restart the shell or see :ref:`above<installing-shell-extensions>` for instructions on making the changes take effect immediately.

List of existing CLI applications
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

=========================================================   ==============================   ==========================================================
Description                                                 Alias                            JAR Name
=========================================================   ==============================   ==========================================================
:ref:`Corda node<starting-an-individual-corda-node>`        ``corda --<option>``             ``corda-|corda_version|.jar``
:doc:`Network bootstrapper<network-bootstrapper>`           ``bootstrapper --<option>``      ``corda-tools-network-bootstrapper-|corda_version|.jar``
:ref:`Standalone shell<standalone-shell>`                   ``corda-shell --<option>``       ``corda-tools-shell-cli-|corda_version|.jar``
:doc:`Blob inspector<blob-inspector>`                       ``blob-inspector --<option>``    ``corda-tools-blob-inspector-|corda_version|.jar``
=========================================================   ==============================   ==========================================================