/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import liquibase.changelog.ChangeLogParameters;
import liquibase.exception.ChangeLogParseException;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests Liquibase patching.
 */
public class CVE_2022_0839 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void test() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/liquibase-mods.properties");
        try {
                /*
                String xmlpoc = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"http://127.0.0.1/\">]><foo>&xxe;</foo>";

                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                saxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                SAXParser saxParser = saxParserFactory.newSAXParser();
                saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "http,https");
                saxParser.parse(new ByteArrayInputStream(xmlpoc.getBytes()), new HandlerBase());
                */

            String changeLog = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE changeSet [<!ENTITY xxe SYSTEM \"http://127.0.0.1/\">]>\n" +
                    "<databaseChangeLog xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n" +
                    "                   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "                   xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.5.xsd\">\n" +
                    "\n" +
                    "    <changeSet id=\"non-clustered_pk-1\" author=\"R3.Corda\" onValidationFail=\"MARK_RAN\">\n" +
                    "        &xxe;\n" +
                    "    </changeSet>\n" +
                    "</databaseChangeLog>";


            XMLChangeLogSAXParser parser = new XMLChangeLogSAXParser();
            parser.parse("", new ChangeLogParameters(), new StringResourceAccessor("", changeLog));

        } catch (ChangeLogParseException e) {
            if (e.getCause() instanceof ConnectException) {
                fail("Exception not expected", e);
            }
        }
    }

    static class StringResourceAccessor implements ResourceAccessor {
        private String resource;
        private String resourceContent;

        public StringResourceAccessor(String resource, String resourceContent) {
            this.resource = resource;
            this.resourceContent = resourceContent;
        }

        @Override
        public Set<InputStream> getResourcesAsStream(String path) throws IOException {
            if (resource.equals(path)) {
                return Collections.singleton(new ByteArrayInputStream(resourceContent.getBytes(StandardCharsets.UTF_8)));
            } else {
                return Collections.singleton(this.getClass().getResource(path).openStream());
            }
        }

        @Override
        public Set<String> list(String relativeTo, String path, boolean includeFiles, boolean includeDirectories, boolean recursive) throws IOException {
            return null;
        }

        @Override
        public ClassLoader toClassLoader() {
            return null;
        }
    }
}
