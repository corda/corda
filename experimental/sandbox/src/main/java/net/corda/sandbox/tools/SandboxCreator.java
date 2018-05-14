/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.tools;

import net.corda.sandbox.WhitelistClassLoader;
import net.corda.sandbox.visitors.SandboxPathVisitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * This class takes in an exploded set of JRE classes, and a whitelist, and rewrites all
 * classes (note: not methods) that have at least one whitelisted method to create a
 * sandboxed version of the class.
 */
// java8.scan.java.lang_and_util java8.interfaces_for_compat java8 sandbox
public final class SandboxCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxCreator.class);
    private static final String USAGE_STRING = "Usage: SandboxCreator <classes root dir> <output sandbox jar>";

    private final String basePathName;
    private final String outputJarName;
    private final WhitelistClassLoader wlcl;
    private final boolean hasInputJar;

    private static final OptionParser parser = new OptionParser();

    private static void usage() {
        System.err.println(USAGE_STRING);
    }

    private SandboxCreator(final OptionSet options) throws URISyntaxException {
        basePathName = (String) (options.valueOf("dir"));
        outputJarName = (String) (options.valueOf("out"));
        wlcl = WhitelistClassLoader.of(basePathName, true);
        hasInputJar = false;
    }

    private SandboxCreator(final String tmpDirName, final OptionSet options) throws URISyntaxException {
        basePathName = tmpDirName;
        outputJarName = (String) (options.valueOf("out"));
        wlcl = WhitelistClassLoader.of(basePathName, true);
        hasInputJar = true;
    }

    static String unpackJar(final String zipFilePath) throws IOException {
        final Path tmpDir = Files.createTempDirectory(Paths.get("/tmp"), "wlcl-extract");

        try (final ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();

            while (entry != null) {
                final Path newFile = tmpDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.copy(zipIn, newFile);
                } else {
                    Files.createDirectory(newFile);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }

        return tmpDir.toString();
    }

    void cleanup() {
        if (hasInputJar) {

        }
    }

    public static SandboxCreator of(final OptionSet options) throws URISyntaxException, IOException {
        final String inputJarName = (String) (options.valueOf("jar"));
        if (inputJarName != null) {
            final String tmpDirName = unpackJar(inputJarName);
            return new SandboxCreator(tmpDirName, options);
        }
        return new SandboxCreator(options);
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        parser.accepts("help", "Displays this help screen").forHelp();
        parser.accepts("dir", "The directory where classes to be sandboxed can be found").withRequiredArg().ofType(String.class);
        parser.accepts("jar", "The jar file where classes to be sandboxed can be found").withRequiredArg().ofType(String.class);
        parser.accepts("out", "The output jar file where rewritten classes will be found").withRequiredArg().ofType(String.class);

        final OptionSet options = parser.parse(args);

        if (options.has("help")) {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        final SandboxCreator sandboxer = SandboxCreator.of(options);
        sandboxer.walk();
        sandboxer.writeJar();
        sandboxer.cleanup();
    }

    /**
     * @param basePath
     * @param packageName
     * @throws IOException
     */
    void walk() throws IOException {
        final Path scanDir = Paths.get(basePathName);
        final SandboxPathVisitor visitor = new SandboxPathVisitor(wlcl, scanDir);
        Files.walkFileTree(scanDir, visitor);
    }

    private void writeJar() throws IOException, URISyntaxException {
        // When this method is called, wlcl should have loaded absolutely everything...
        Path outJar = Paths.get(outputJarName);
        wlcl.setOutpurJarPath(outJar);
        wlcl.createJar();
    }

}
