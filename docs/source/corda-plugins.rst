The Corda plugin framework
==========================

The intention is that Corda is a common platform, which will be extended 
by numerous application extensions (CorDapps). These extensions will 
package together all of the Corda contract code, state structures, 
protocols/flows to create and modify state as well as RPC extensions for 
node clients. Details of writing these CorDapps is given elsewhere 
:doc:`creating-a-cordapp`.

To enable these plugins to register dynamically with the Corda framework 
the node uses the Java ``ServiceLoader`` to locate and load the plugin 
components during the ``AbstractNode.start`` call. Therefore, 
to be recognised as a plugin the component must: 

1. Include a default constructable class extending from 
``net.corda.core.node.CordaPluginRegistry`` which overrides the relevant 
registration methods. 

2. Include a resource file named 
``net.corda.core.node.CordaPluginRegistry`` in the ``META-INF.services`` 
path. This must include a line containing the fully qualified name of 
the ``CordaPluginRegistry`` implementation class. Multiple plugin 
registries are allowed in this file if desired. 

3. The plugin component must be on the classpath. In the normal use this 
means that it should be present within the plugins subfolder of the 
node's workspace. 

4. As a plugin the registered components are then allowed access to some 
of the node internal subsystems.

5. The overridden properties on the registry class information about the different 
extensions to be created, or registered at startup. In particular: 

    a. The ``webApis`` property is a list of JAX-RS annotated REST access 
    classes. These classes will be constructed by the bundled web server
    and must have a single argument constructor taking a ``CordaRPCOps``
    reference. This will allow it to communicate with the node process
    via the RPC interface. These web APIs will not be available if the
    bundled web server is not started.

    b. The ``staticServeDirs`` property maps static web content to virtual 
    paths and allows simple web demos to be distributed within the CorDapp 
    jars. These static serving directories will not be available if the
    bundled web server is not started.

    c. The ``customizeSerialization`` function allows classes to be whitelisted
    for object serialisation, over and above those tagged with the ``@CordaSerializable``
    annotation. In general the annotation should be preferred.  For
    instance new state types will need to be explicitly registered. This will be called at
    various points on various threads and needs to be stable and thread safe. See
    :doc:`serialization`.

