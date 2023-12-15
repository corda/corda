/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static net.gredler.aegis4j.TestUtils.OWNED;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Sets a system property when {@link #getConnection()} is called, as proof of vulnerability.
 */
public class SerializableDataSource implements DataSource, Serializable {

    private static final long serialVersionUID = 1663970917224862202L;

    @Override
    public Connection getConnection() throws SQLException {
        System.setProperty(OWNED, Boolean.TRUE.toString());
        return null;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        System.setProperty(OWNED, Boolean.TRUE.toString());
        return null;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    @Override
    public < T > T unwrap(Class< T > iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class< ? > iface) throws SQLException {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        // empty
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        // empty
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }
}
