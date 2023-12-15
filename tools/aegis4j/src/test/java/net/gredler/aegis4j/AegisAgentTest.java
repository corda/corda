/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NoInitialContextException;
import javax.naming.ldap.LdapName;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.StubNotFoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Collections;

import static net.gredler.aegis4j.AegisAgent.toBlockList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link AegisAgent}.
 */
public class AegisAgentTest {

    @BeforeAll
    public static void installAgent() throws Exception {
        TestUtils.installAgent(null);
    }

    @AfterAll
    public static void uninstallAgent() throws Exception {
        TestUtils.installAgent("unblock=serialization");
    }

    @Test
    public void testParseBlockList() throws IOException {
        assertEquals(TestUtils.setOf("jndi", "rmi", "process", "httpserver", "serialization", "scripting"), toBlockList("", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "process", "httpserver", "serialization", "scripting"), toBlockList("   ", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "process", "httpserver", "scripting"), toBlockList("unblock=serialization", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "httpserver", "scripting"), toBlockList("unblock=serialization,process", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "httpserver", "scripting"), toBlockList("UNbloCk=SERIALIZATION,Process", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "httpserver", "scripting"), toBlockList(" unblock\t=    serialization      , process\t", null));
        assertEquals(TestUtils.setOf(), toBlockList("unblock=jndi,rmi,process,httpserver,serialization,scripting", null));
        assertEquals(TestUtils.setOf("jndi"), toBlockList("block=jndi", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "process"), toBlockList("block=jndi,rmi,process", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "process"), toBlockList("block = jndi\t, rmi ,\nprocess", null));
        assertEquals(TestUtils.setOf("jndi", "rmi", "process"), toBlockList("BLOck = JNDI\t, rmi ,\nProcESs", null));

        assertThrowsIAE(() -> toBlockList("blahblah", null), "Aegis4j ERROR: Invalid agent configuration string");
        assertThrowsIAE(() -> toBlockList("foo=bar", null), "Aegis4j ERROR: Unrecognized parameter name (should be one of 'block' or 'unblock'): foo");
        assertThrowsIAE(() -> toBlockList("block=incorrect", null), "Aegis4j ERROR: Unrecognized feature name: incorrect");
        assertThrowsIAE(() -> toBlockList("unblock=incorrect", null), "Aegis4j ERROR: Unrecognized feature name: incorrect");
        assertThrowsIAE(() -> toBlockList("block=serialization,process,incorrect,jndi", null), "Aegis4j ERROR: Unrecognized feature name: incorrect");
        assertThrowsIAE(() -> toBlockList("unblock=serialization,process,incorrect,jndi", null), "Aegis4j ERROR: Unrecognized feature name: incorrect");
    }

    @Test
    public void testJndi() throws Exception {
        String string = "foo";
        Name name = new LdapName("cn=foo");
        Object object = new Object();
        InitialContext initialContext = new InitialContext();

        assertThrowsNICE(() -> initialContext.lookup(string));
        assertThrowsNICE(() -> initialContext.lookup(name));
        assertThrowsNICE(() -> initialContext.bind(string, object));
        assertThrowsNICE(() -> initialContext.bind(name, object));
        assertThrowsNICE(() -> initialContext.rebind(string, object));
        assertThrowsNICE(() -> initialContext.rebind(name, object));
        assertThrowsNICE(() -> initialContext.unbind(string));
        assertThrowsNICE(() -> initialContext.unbind(name));
        assertThrowsNICE(() -> initialContext.rename(string, string));
        assertThrowsNICE(() -> initialContext.rename(name, name));
        assertThrowsNICE(() -> initialContext.list(string));
        assertThrowsNICE(() -> initialContext.list(name));
        assertThrowsNICE(() -> initialContext.listBindings(string));
        assertThrowsNICE(() -> initialContext.listBindings(name));
        assertThrowsNICE(() -> initialContext.destroySubcontext(string));
        assertThrowsNICE(() -> initialContext.destroySubcontext(name));
        assertThrowsNICE(() -> initialContext.createSubcontext(string));
        assertThrowsNICE(() -> initialContext.createSubcontext(name));
        assertThrowsNICE(() -> initialContext.lookupLink(string));
        assertThrowsNICE(() -> initialContext.lookupLink(name));
        assertThrowsNICE(() -> initialContext.getNameParser(string));
        assertThrowsNICE(() -> initialContext.getNameParser(name));
        assertThrowsNICE(() -> initialContext.addToEnvironment(string, object));
        assertThrowsNICE(() -> initialContext.removeFromEnvironment(string));
        assertThrowsNICE(() -> initialContext.getEnvironment());
        assertThrowsNICE(() -> initialContext.getNameInNamespace());
    }

    @Test
    public void testRmi() {
        int integer = 9090;
        String string = "foo";
        RMIClientSocketFactory clientSocketFactory = null;
        RMIServerSocketFactory serverSocketFactory = null;

        assertThrowsSNFE(() -> LocateRegistry.getRegistry(integer));
        assertThrowsSNFE(() -> LocateRegistry.getRegistry(string));
        assertThrowsSNFE(() -> LocateRegistry.getRegistry(string, integer));
        assertThrowsSNFE(() -> LocateRegistry.getRegistry(string, integer, clientSocketFactory));
        assertThrowsSNFE(() -> LocateRegistry.createRegistry(integer));
        assertThrowsSNFE(() -> LocateRegistry.createRegistry(integer, clientSocketFactory, serverSocketFactory));
    }

    @Test
    public void testProcess() {
        Runtime runtime = Runtime.getRuntime();
        String string = "foo";
        String[] array = new String[] { "foo" };
        File file = new File(".");

        assertThrowsIOE(() -> runtime.exec(string));
        assertThrowsIOE(() -> runtime.exec(array));
        assertThrowsIOE(() -> runtime.exec(string, array));
        assertThrowsIOE(() -> runtime.exec(array, array));
        assertThrowsIOE(() -> runtime.exec(string, array, file));
        assertThrowsIOE(() -> runtime.exec(array, array, file));

        assertThrowsIOE(() -> new ProcessBuilder(string).start());
        assertThrowsIOE(() -> new ProcessBuilder(array).start());
        assertThrowsIOE(() -> new ProcessBuilder(Collections.emptyList()).start());
    }

    @Test
    public void testHttpServer() {
        assertThrowsRE(() -> HttpServer.create(), "HTTP server provider lookup blocked by aegis4j");
        assertThrowsRE(() -> HttpServer.create(null, 0), "HTTP server provider lookup blocked by aegis4j");
        assertThrowsRE(() -> HttpServerProvider.provider(), "HTTP server provider lookup blocked by aegis4j");
    }

    @Test
    public void testScripting() {
        assertThrowsRE(() -> new ScriptEngineManager(), "Scripting blocked by aegis4j");
        assertThrowsRE(() -> new ScriptEngineManager(getClass().getClassLoader()), "Scripting blocked by aegis4j");
        assertThrowsRE(() -> new SimpleScriptContext(), "Scripting blocked by aegis4j");
        assertThrowsRE(() -> new CompiledScript() {
            @Override public Object eval(ScriptContext context) { return null; }
            @Override public ScriptEngine getEngine() { return null; }
        }, "Scripting blocked by aegis4j");
    }

    @Test
    public void testSerialization() {

        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        assertThrowsRE(() -> new ObjectInputStream(bais), "Java deserialization blocked by aegis4j");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThrowsRE(() -> new ObjectOutputStream(baos), "Java serialization blocked by aegis4j");
    }

    private static void assertThrowsNICE(Executable task) {
        assertThrows(task, NoInitialContextException.class, "JNDI context creation blocked by aegis4j");
    }

    private static void assertThrowsSNFE(Executable task) {
        assertThrows(task, StubNotFoundException.class, "RMI registry creation blocked by aegis4j");
    }

    private static void assertThrowsIOE(Executable task) {
        assertThrows(task, IOException.class, "Process execution blocked by aegis4j");
    }

    private static void assertThrowsIAE(Executable task, String msg) {
        assertThrows(task, IllegalArgumentException.class, msg);
    }

    private static void assertThrowsRE(Executable task, String msg) {
        assertThrows(task, RuntimeException.class, msg);
    }

    private static void assertThrows(Executable task, Class< ? extends Throwable > type, String msg) {
        Throwable root;
        try {
            task.execute();
            root = null;
        } catch (Throwable t) {
            root = getRootCause(t);
        }
        assertNotNull(root, "No exception thrown");
        assertInstanceOf(type, root, "Exception is wrong type");
        assertEquals(msg, root.getMessage(), "Exception has wrong message");
    }

    private static Throwable getRootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
