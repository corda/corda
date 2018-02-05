package net.corda.cordform;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public class CordformNode implements NodeDefinition {
    /**
     * Path relative to the running node where the serialized NodeInfos are stored.
     */
    public static final String NODE_INFO_DIRECTORY = "additional-node-infos";

    protected static final String DEFAULT_HOST = "localhost";

    /**
     * Name of the node. Node will be placed in directory based on this name - all lowercase with whitespaces removed.
     * Actual node name inside node.conf will be as set here.
     */
    private String name;

    public String getName() {
        return name;
    }

    /**
     * p2p Port.
     */
    private int p2pPort = 10002;

    public int getP2pPort() { return p2pPort; }

    /**
     * RPC Port.
     */
    private int rpcPort = 10003;

    public int getRpcPort() { return rpcPort; }

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

    public Map<String, Object> extraConfig = null;

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
        setValue("myLegalName", name);
    }

    /**
     * Get the artemis address for this node.
     *
     * @return This node's P2P address.
     */
    @Nonnull
    public String getP2pAddress() {
        return config.getString("p2pAddress");
    }

    /**
     * Set the Artemis P2P port for this node on localhost.
     *
     * @param p2pPort The Artemis messaging queue port.
     */
    public void p2pPort(int p2pPort) {
        p2pAddress(DEFAULT_HOST + ':' + p2pPort);
        this.p2pPort = p2pPort;
    }

    /**
     * Set the Artemis P2P address for this node.
     *
     * @param p2pAddress The Artemis messaging queue host and port.
     */
    public void p2pAddress(String p2pAddress) {
        setValue("p2pAddress", p2pAddress);
    }

    /**
     * Returns the RPC address for this node, or null if one hasn't been specified.
     */
    @Nullable
    public String getRpcAddress() {
        if (config.hasPath("rpcSettings.address")) {
            return config.getConfig("rpcSettings").getString("address");
        }
        return getOptionalString("rpcAddress");
    }

    /**
     * Set the Artemis RPC port for this node on localhost.
     *
     * @param rpcPort The Artemis RPC queue port.
     * @deprecated Use {@link CordformNode#rpcSettings(RpcSettings)} instead.
     */
    @Deprecated
    public void rpcPort(int rpcPort) {
        rpcAddress(DEFAULT_HOST + ':' + rpcPort);
        this.rpcPort = rpcPort;
    }

    /**
     * Set the Artemis RPC address for this node.
     *
     * @param rpcAddress The Artemis RPC queue host and port.
     * @deprecated Use {@link CordformNode#rpcSettings(RpcSettings)} instead.
     */
    @Deprecated
    public void rpcAddress(String rpcAddress) {
        setValue("rpcAddress", rpcAddress);
    }

    /**
     * Returns the address of the web server that will connect to the node, or null if one hasn't been specified.
     */
    @Nullable
    public String getWebAddress() {
        return getOptionalString("webAddress");
    }

    /**
     * Configure a webserver to connect to the node via RPC. This port will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    public void webPort(int webPort) {
        webAddress(DEFAULT_HOST + ':' + webPort);
    }

    /**
     * Configure a webserver to connect to the node via RPC. This address will specify the port it will listen on. The node
     * must have an RPC address configured.
     */
    public void webAddress(String webAddress) {
        setValue("webAddress", webAddress);
    }

    /**
     * Specifies RPC settings for the node.
     */
    public void rpcSettings(RpcSettings settings) {
        config = settings.addTo("rpcSettings", config);
    }

    /**
     * Set the path to a file with optional properties, which are appended to the generated node.conf file.
     *
     * @param configFile The file path.
     */
    public void configFile(String configFile) {
        setValue("configFile", configFile);
    }

    private String getOptionalString(String path) {
        return config.hasPath(path) ? config.getString(path) : null;
    }

    private void setValue(String path, Object value) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value));
    }
}
