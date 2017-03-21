DemoBench
=========

DemoBench is a standalone sales/educational tool that makes it easy to configure and launch local Corda nodes on your desktop.

Building the Installer
----------------------

There are three scripts in the ``tools/demobench`` directory:

 #. ``package-demobench-exe.bat`` (Windows)
 #. ``package-demobench-dmg.sh`` (MacOS)
 #. ``package-demobench-rpm.sh`` (Fedora/Linux)

Each script can only be run on its target platform, and each expects the platform's installation tools already to be available.

 #. Windows: `Inno Setup 5+ <http://www.jrsoftware.org/isinfo.php>`_
 #. MacOS: The packaging tools should be available automatically. The DMG contents will also be signed if the packager finds a valid ``Developer ID Application`` certificate on the keyring. You can create such a certificate by generating a Certificate Signing Request and then asking your local "Apple team agent" to upload it to the Apple Developer portal. (See `here <https://developer.apple.com/library/content/documentation/IDEs/Conceptual/AppDistributionGuide/MaintainingCertificates/MaintainingCertificates.html>`_.)
 #. Fedora/Linux: ``rpm-build`` packages.

You will also need to define the environment variable ``JAVA_HOME`` to point to the same JDK that you use to run Gradle. The installer will be written to the ``tools/demobench/build/javapackage/bundles`` directory, and can be installed like any other application for your platform.

Running DemoBench
-----------------

Configuring a Node
  Each node must have a unique name to identify it to the network map service. DemoBench will also suggest local port numbers to use.

  The first node will host the network map service, and therefore *must* be a notary. Hence only notary services will be available to be selected in the ``Services`` list. For subsequent nodes you may also select any of Corda's other built-in services.

.. note:: Press ``Ctrl``/``Cmd`` and then click with the mouse to select multiple services, and also to deselect a service again.

..

  Press the ``Create Node`` button to launch the Corda node with your configuration.

Running Nodes
  DemoBench launches each new node in a terminal emulator attached to the pty, and then connects to it over RPC. The ``View Database``, ``Launch Explorer`` and ``Launch WebServer`` buttons will all be disabled until this RPC connection succeeds. Once connected, DemoBench will display simple statistics about the node such as its cash balance.

.. warning:: After switching tabs, it may currently be necessary to click on the new tab panel before its contents update correctly. This is a BUG and we're working on it.

..

Profiles
  You can save all of DemoBench's currently running nodes into a profile, which is a ``ZIP`` file with the following layout, e.g.:

.. parsed-literal::

    notary/
        node.conf
        plugins/
    banka/
        node.conf
        plugins/
    bankb/
        node.conf
        plugins/
            example-cordapp.jar
    ...

..

  When DemoBench reloads this profile it will close any nodes that it is currently running and then launch these new nodes instead.

