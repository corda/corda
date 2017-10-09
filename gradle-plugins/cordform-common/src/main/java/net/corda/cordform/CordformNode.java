package net.corda.cordform;

import static java.util.Collections.emptyList;
import com.typesafe.config.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CordformNode implements NodeDefinition {
    /**
     * Path relative to the running node where the serialized NodeInfos are stored.
     */
    public static final String NODE_INFO_DIRECTORY = "additional-node-infos";

    protected static final String DEFAULT_HOST = "localhost";

    /**
     * Name of the node.
     */
    private String name;

    public String getName() {
        return name;
    }

    /**
     * A list of advertised services ID strings.
     */
    public List<String> advertisedServices = emptyList();

    /**
     * Set the RPC users for this node. This configuration block allows arbitrary configuration.
     * The recommended current structure is:
     * [[['username': "username_here", 'password': "password_here", 'permissions': ["permissions_here"]]]
     * The above is a list to a map of keys to values using Groovy map and list shorthands.
     *
     * Incorrect configurations will not cause a DSL error.
     */
    public List<Map<String, Object>> rpcUsers = emptyList();

    /**
     * Apply the notary configuration if this node is a notary. The map is the config structure of
     * net.corda.node.services.config.NotaryConfig
     */
    public Map<String, Object> notary = null;

    protected Config config = ConfigFactory.empty();

    public Config getConfig() {
        return config;
    }

    /**
     * Set the name of the node.
     *
     * @param name The node name.
     */
    public void name(String name) {
        this.name = name;
        config = config.withValue("myLegalName", ConfigValueFactory.fromAnyRef(name));
    }

    /**
     * Set the Artemis P2P port for this node.
     *
     * @param p2pPort The Artemis messaging queue port.
     */
    public void p2pPort(Integer p2pPort) {
        config = config.withValue("p2pAddress", ConfigValueFactory.fromAnyRef(DEFAULT_HOST + ':' + p2pPort));
    }

    /**
     * Set the Artemis RPC port for this node.
     *
     * @param rpcPort The Artemis RPC queue port.
     */
    public void rpcPort(Integer rpcPort) {
        config = config.withValue("rpcAddress", ConfigValueFactory.fromAnyRef(DEFAULT_HOST + ':' + rpcPort));
    }
}
