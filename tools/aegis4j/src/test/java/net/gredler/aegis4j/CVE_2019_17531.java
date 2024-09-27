/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.receivers.db.JNDIConnectionSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static net.gredler.aegis4j.TestUtils.testLdap;

/**
 * Tests mitigation of CVE-2019-17531. No setup is required besides starting the LDAP server that
 * serves serialized {@link SerializableDataSource} instances. The vulnerability is triggered when
 * we deserialize a JSON payload which contains a {@link JNDIConnectionSource} which references
 * our LDAP server, and re-serializing the deserialized {@link JNDIConnectionSource} triggers a
 * JNDI lookup.
 *
 * @see <a href="https://nvd.nist.gov/vuln/detail/CVE-2019-17531">CVE-2019-17531</a>
 * @see <a href="https://github.com/FasterXML/jackson-databind/issues/2498">Jackson issue #2498</a>
 * @see <a href="https://cowtowncoder.medium.com/on-jackson-cves-dont-panic-here-is-what-you-need-to-know-54cd0d6e8062">On Jackson CVEs</a>
 * @see <a href="https://swapneildash.medium.com/understanding-insecure-implementation-of-jackson-deserialization-7b3d409d2038">Understanding Jackson deserialization</a>
 */
public class CVE_2019_17531 {
    @Test
    public void test() throws Throwable {

        Executable setup = () -> {
            // only the LDAP server is needed
        };

        Executable trigger = () -> {
            ObjectMapper mapper = new ObjectMapper();
            String json = "{ \"property\": { \"@class\": \"org.apache.log4j.receivers.db.JNDIConnectionSource\", \"jndiLocation\": \"ldap://localhost:8181/dc=foo\" } }";
            JsonPayload payload = mapper.readValue(json, JsonPayload.class);
            mapper.writeValueAsString(payload); // triggers payload.property.getConnection()
        };

        testLdap(setup, trigger, SerializableDataSource.class, true);
    }
}
