package org.h2.jdbcx;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.h2.jdbc.JdbcConnection;

/**
 * Android entry point for H2's ordinary JDBC connections. Hub plugins such as
 * Polywoof use this name directly. The SQL engine and on-disk format stay H2's;
 * desktop JNDI, connection pooling and XA transactions are not implemented here.
 */
public final class JdbcDataSource implements DataSource {
    private String url = "";
    private String user = "";
    private char[] password = new char[0];
    private String description;
    private PrintWriter logWriter;
    private int loginTimeout;

    public String getURL() { return url; }
    public void setURL(String value) { url = value; }
    public String getUrl() { return getURL(); }
    public void setUrl(String value) { setURL(value); }
    public String getUser() { return user; }
    public void setUser(String value) { user = value; }
    public String getPassword() { return password == null ? null : new String(password); }
    public void setPassword(String value) { password = value == null ? null : value.toCharArray(); }
    public void setPasswordChars(char[] value) { password = value == null ? null : value.clone(); }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }

    @Override public Connection getConnection() throws SQLException {
        return new JdbcConnection(url, null, user, password == null ? null : password.clone(), false);
    }
    @Override public Connection getConnection(String username, String pass) throws SQLException {
        return new JdbcConnection(url, null, username, pass, false);
    }
    @Override public PrintWriter getLogWriter() { return logWriter; }
    @Override public void setLogWriter(PrintWriter writer) { logWriter = writer; }
    @Override public int getLoginTimeout() { return loginTimeout; }
    @Override public void setLoginTimeout(int seconds) { loginTimeout = seconds; }
    @Override public Logger getParentLogger() { return Logger.getLogger("org.h2"); }
    @Override public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (isWrapperFor(type)) return type.cast(this);
        throw new SQLException("Not a wrapper for " + type);
    }
}
