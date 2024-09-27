/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static net.gredler.aegis4j.TestUtils.testLdap;

/**
 * Tests mitigation of CVE-2021-44228 (a.k.a. Log4Shell). During setup we simply configure log4j to
 * perform basic logging, and set up our LDAP server to serve serialized {@link SerializablePojo}
 * instances. The vulnerability is triggered when we log a message which contains a JNDI lookup.
 *
 * @see <a href="https://nvd.nist.gov/vuln/detail/CVE-2021-44228">CVE-2021-44228</a>
 * @see <a href="https://www.wired.com/story/log4j-log4shell/">Everybody freaking out</a>
 * @see <a href="https://research.nccgroup.com/2021/12/12/log4j-jndi-be-gone-a-simple-mitigation-for-cve-2021-44228/">log4j-jndi-be-gone</a>
 */
public class CVE_2021_44228 {
    @Test
    public void test() throws Throwable {

        Executable setup = () -> {
            configureLog4J2();
        };

        Executable trigger = () -> {
            Logger logger = LogManager.getLogger();
            logger.info("${jndi:ldap://localhost:8181/dc=foo}");
        };

        testLdap(setup, trigger, SerializablePojo.class, false);
    }

    // https://logging.apache.org/log4j/2.x/manual/customconfig.html
    // https://www.baeldung.com/log4j2-programmatic-config
    private static void configureLog4J2() {

        ConfigurationBuilder< BuiltConfiguration > builder = ConfigurationBuilderFactory.newConfigurationBuilder();

        LayoutComponentBuilder standard = builder.newLayout("PatternLayout");
        standard.addAttribute("pattern", "%d [%t] %-5level: %msg%n");

        AppenderComponentBuilder console = builder.newAppender("stdout", "console");
        console.add(standard);
        builder.add(console);

        RootLoggerComponentBuilder root = builder.newRootLogger(Level.INFO);
        root.add(builder.newAppenderRef("stdout"));
        builder.add(root);

        Configurator.initialize(builder.build());
    }
}
