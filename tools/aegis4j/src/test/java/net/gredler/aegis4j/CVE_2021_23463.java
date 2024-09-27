/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import org.h2.command.CommandInterface;
import org.h2.engine.SessionInterface;
import org.h2.jdbc.JdbcConnection;
import org.h2.jdbc.JdbcSQLXML;
import org.h2.message.Trace;
import org.h2.store.DataHandler;
import org.h2.value.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import javax.xml.transform.dom.DOMSource;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests H2 SQL XML blocking.
 */
public class CVE_2021_23463 {
    @AfterAll
    public static void uninstallAgent() throws Exception {
        System.clearProperty("aegis4j.additional.args");
    }

    @Test
    public void test() throws Exception {
        TestUtils.installAgent("path=" + System.getProperty("aegis4j.projectDir") + "/src/test/resources/h2-mods.properties");
        try {
            SessionInterface session = new SessionInterface() {
                @Override
                public ArrayList<String> getClusterServers() {
                    return null;
                }

                @Override
                public CommandInterface prepareCommand(String sql, int fetchSize) {
                    return null;
                }

                @Override
                public void close() {

                }

                @Override
                public Trace getTrace() {
                    return null;
                }

                @Override
                public boolean isClosed() {
                    return false;
                }

                @Override
                public int getPowerOffCount() {
                    return 0;
                }

                @Override
                public void setPowerOffCount(int i) {

                }

                @Override
                public DataHandler getDataHandler() {
                    return null;
                }

                @Override
                public boolean hasPendingTransaction() {
                    return false;
                }

                @Override
                public void cancel() {

                }

                @Override
                public boolean isReconnectNeeded(boolean write) {
                    return false;
                }

                @Override
                public SessionInterface reconnect(boolean write) {
                    return null;
                }

                @Override
                public void afterWriting() {

                }

                @Override
                public boolean getAutoCommit() {
                    return false;
                }

                @Override
                public void setAutoCommit(boolean autoCommit) {

                }

                @Override
                public void addTemporaryLob(Value v) {

                }

                @Override
                public boolean isRemote() {
                    return false;
                }

                @Override
                public void setCurrentSchemaName(String schema) {

                }

                @Override
                public String getCurrentSchemaName() {
                    return null;
                }

                @Override
                public boolean isSupportsGeneratedKeys() {
                    return false;
                }
            };
            JdbcConnection connection = new JdbcConnection(session, "user", "url");
            new JdbcSQLXML(connection, null, null, 0).getSource(DOMSource.class);
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertEquals("H2 SQL XML blocked by aegis4j", e.getMessage());
        }
    }
}
