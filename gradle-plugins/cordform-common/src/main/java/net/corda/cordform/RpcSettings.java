package net.corda.cordform;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public final class RpcSettings {

    private Config config = ConfigFactory.empty();

    private int port = 10003;
    private int adminPort = 10005;

    public int getPort() {
        return port;
    }

    public int getAdminPort() {
        return adminPort;
    }

    /**
     * RPC address for the node.
     */
    public final void address(final String value) {
        setValue("address", value);
    }

    /**
     * RPC Port for the node
     */
    public final void port(final int value) {
        this.port = value;
        setValue("address", "localhost:"+port);
    }

    /**
     * RPC admin address for the node (necessary if [useSsl] is false or unset).
     */
    public final void adminAddress(final String value) {
        setValue("adminAddress", value);
    }

    public final void adminPort(final int value) {
        this.adminPort = value;
        setValue("adminAddress", "localhost:"+adminPort);
    }

    /**
     * Specifies whether the node RPC layer will require SSL from clients.
     */
    public final void useSsl(final Boolean value) {
        setValue("useSsl", value);
    }

    /**
     * Specifies whether the RPC broker is separate from the node.
     */
    public final void standAloneBroker(final Boolean value) {
        setValue("standAloneBroker", value);
    }

    /**
     * Specifies SSL certificates options for the RPC layer.
     */
    public final void ssl(final SslOptions options) {
        config = options.addTo("ssl", config);
    }

    public final Config addTo(final String key, final Config config) {
        if (this.config.isEmpty()) {
            return config;
        }
        return config.withValue(key, this.config.root());
    }

    private void setValue(String path, Object value) {
        config = config.withValue(path, ConfigValueFactory.fromAnyRef(value));
    }
}
