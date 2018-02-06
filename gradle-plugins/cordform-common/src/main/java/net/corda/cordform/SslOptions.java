package net.corda.cordform;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public final class SslOptions {

    private Config config = ConfigFactory.empty();

    /**
     * Password for the keystore.
     */
    public final void keyStorePassword(final String value) {
        setValue("keyStorePassword", value);
    }

    /**
     * Password for the truststore.
     */
    public final void trustStorePassword(final String value) {
        setValue("trustStorePassword", value);
    }

    /**
     * Directory under which key stores are to be placed.
     */
    public final void certificatesDirectory(final String value) {
        setValue("certificatesDirectory", value);
    }

    /**
     * Absolute path to SSL keystore. Default: "[certificatesDirectory]/sslkeystore.jks"
     */
    public final void sslKeystore(final String value) {
        setValue("sslKeystore", value);
    }

    /**
     * Absolute path to SSL truststore. Default: "[certificatesDirectory]/truststore.jks"
     */
    public final void trustStoreFile(final String value) {
        setValue("trustStoreFile", value);
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
