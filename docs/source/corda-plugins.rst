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

    c. The ``requiredFlows`` property is used to declare new protocols in 
    the plugin jar. Specifically the property must return a map with a key 
    naming each exposed top level flow class and a value which is a set 
    naming every parameter class that will be passed to the flow's 
    constructor. Standard ``java.lang.*`` and ``kotlin.*`` types do not need 
    to be included, but all other parameter types, or concrete interface 
    implementations need declaring. Declaring a specific flow in this map 
    white lists it for activation by the ``FlowLogicRefFactory``. White 
    listing is not strictly required for ``subFlows`` used internally, but 
    is required for any top level flow, or a flow which is invoked through 
    the scheduler. 

    d. The ``servicePlugins`` property returns a list of classes which will 
    be instantiated once during the ``AbstractNode.start`` call. These 
    classes must provide a single argument constructor which will receive a 
    ``PluginServiceHub`` reference. They must also extend the abstract class
    ``SingletonSerializeAsToken`` which ensures that if any reference to your
    service is captured in a flow checkpoint (i.e. serialized by Kryo as
    part of Quasar checkpoints, either on the stack or by reference within
    your flows) it is stored as a simple token representing your service.
    When checkpoints are restored, after a node restart for example,
    the latest instance of the service will be substituted back in place of
    the token stored in the checkpoint.

        i. Firstly, they can call ``PluginServiceHub.registerFlowInitiator`` and 
        register flows that will be initiated locally in response to remote flow 
        requests. 

        ii. Second, the service can hold a long lived reference to the 
        PluginServiceHub and to other private data, so the service can be used 
        to provide Oracle functionality. This Oracle functionality would 
        typically be exposed to other nodes by flows which are given a reference 
        to the service plugin when initiated (as defined by the 
        ``registerFlowInitiator`` call). The flow can then call into functions 
        on the plugin service singleton. Note, care should be taken to not allow 
        flows to hold references to fields which are not
        also ``SingletonSerializeAsToken``, otherwise Quasar suspension in the 
        ``StateMachineManager`` will fail with exceptions. An example oracle can 
        be seen in ``NodeInterestRates.kt`` in the irs-demo sample. 

        iii. The final 
        use case for service plugins is that they can spawn threads, or register 
        to monitor vault updates. This allows them to provide long lived active 
        functions inside the node, for instance to initiate workflows when 
        certain conditions are met. 

    e. The ``customizeSerialization`` function allows classes to be whitelisted
    for object serialisation, over and above those tagged with the ``@CordaSerializable``
    annotation. In general the annotation should be preferred.  For
    instance new state types will need to be explicitly registered. This will be called at
    various points on various threads and needs to be stable and thread safe. See
    :doc:`serialization`.

