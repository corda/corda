/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox;

import net.corda.sandbox.costing.*;
import org.junit.*;

import javax.xml.bind.*;
import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import static org.junit.Assert.*;

public class TestUtils {

    private static ArrayList<FileSystem> tmpFileSystems = new ArrayList<>();
    private static Path jarFSDir = null;
    private static Path tmpdir;

    public static void setPathToTmpJar(final String resourcePathToJar) throws IOException {
        // Copy resource jar to tmp dir
        tmpdir = Files.createTempDirectory("wlcl-tmp-test");
        Path copiedJar = tmpdir.resolve("tmp-resource.jar");
        try (final InputStream in = TestUtils.class.getResourceAsStream(resourcePathToJar)) {
            Files.copy(in, copiedJar, StandardCopyOption.REPLACE_EXISTING);
        }
        final FileSystem fs = FileSystems.newFileSystem(copiedJar, null);
        tmpFileSystems.add(fs);
        jarFSDir = fs.getRootDirectories().iterator().next();
    }

    public static Path copySandboxJarToTmpDir(final String resourcePathToJar) throws IOException {

        Path sandboxJar = tmpdir.resolve("tmp-sandbox.jar");
        try (final InputStream in = TestUtils.class.getResourceAsStream(resourcePathToJar)) {
            Files.copy(in, sandboxJar, StandardCopyOption.REPLACE_EXISTING);
        }
        final FileSystem sandboxFs = FileSystems.newFileSystem(sandboxJar, null);
        tmpFileSystems.add(sandboxFs);
        return sandboxFs.getRootDirectories().iterator().next();
    }

    public static Path getJarFSRoot() {
        return jarFSDir;
    }

    public static void cleanupTmpJar() throws IOException {
        for (FileSystem fs : tmpFileSystems) {
            fs.close();
        }
        tmpFileSystems.clear();
        jarFSDir = null;
        Files.walkFileTree(tmpdir, new Reaper());
        tmpdir = null;
    }

    public static void checkAllCosts(final int allocCost, final int jumpCost, final int invokeCost, final int throwCost) {
        Assert.assertEquals(allocCost, RuntimeCostAccounter.getAllocationCost());
        assertEquals(jumpCost, RuntimeCostAccounter.getJumpCost());
        assertEquals(invokeCost, RuntimeCostAccounter.getInvokeCost());
        assertEquals(throwCost, RuntimeCostAccounter.getThrowCost());
    }

    public static Class<?> transformClass(final String classFName, final int originalLength, final int newLength) throws Exception {
        byte[] basic = getBytes(classFName);
        assertEquals(originalLength, basic.length);
        final byte[] tfmd = instrumentWithCosts(basic, new HashSet<>());
        final Path testdir = Files.createTempDirectory("greymalkin-test-");
        final Path out = testdir.resolve(classFName);
        Files.createDirectories(out.getParent());
        Files.write(out, tfmd);
        if (newLength > 0) {
            assertEquals(newLength, tfmd.length);
        }
        final MyClassloader mycl = new MyClassloader();
        final Class<?> clz = mycl.byPath(out);

        Files.walkFileTree(testdir, new Reaper());

        return clz;
    }

    public static Class<?> transformClass(final String resourceMethodAccessIsRewrittenclass, int i) throws Exception {
        return transformClass(resourceMethodAccessIsRewrittenclass, i, -1);
    }

    public static byte[] getBytes(final String original) throws IOException {
        return Files.readAllBytes(jarFSDir.resolve(original));
    }

    // Helper for finding the correct offsets if they change
    public static void printBytes(byte[] data) {
        byte[] datum = new byte[1];
        for (int i = 0; i < data.length; i++) {
            datum[0] = data[i];
            System.out.println(i + " : " + DatatypeConverter.printHexBinary(datum));
        }
    }

    public static int findOffset(byte[] classBytes, byte[] originalSeq) {
        int offset = 0;
        for (int i = 415; i < classBytes.length; i++) {
            if (classBytes[i] != originalSeq[offset]) {
                offset = 0;
                continue;
            }
            if (offset == originalSeq.length - 1) {
                return i - offset;
            }
            offset++;
        }

        return -1;
    }

    public static byte[] instrumentWithCosts(byte[] basic, Set<String> hashSet) throws Exception {
        final WhitelistClassLoader wlcl = WhitelistClassLoader.of("/tmp");
        return wlcl.instrumentWithCosts(basic, hashSet);
    }


    public static final class MyClassloader extends ClassLoader {

        public Class<?> byPath(Path p) throws IOException {
            final byte[] buffy = Files.readAllBytes(p);
            return defineClass(null, buffy, 0, buffy.length);
        }
    }

    public static final class Reaper extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc == null) {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            } else {
                throw exc;
            }
        }
    }
}
