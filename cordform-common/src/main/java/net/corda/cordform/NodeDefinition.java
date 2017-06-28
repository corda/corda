package net.corda.cordform;

import com.typesafe.config.Config;

public interface NodeDefinition {
    String getName();

    Config getConfig();
}
