/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.visitors;

import net.corda.sandbox.Utils;
import net.corda.sandbox.WhitelistClassLoader;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This helper class visits each file (represented as a Path) in some directory
 * tree containing classes to be sandboxed.
 *
 * @author ben
 */
public final class SandboxPathVisitor extends SimpleFileVisitor<Path> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxPathVisitor.class);

    private final WhitelistClassLoader loader;

    private final Path startFrom;

    public SandboxPathVisitor(final WhitelistClassLoader wlcl, final Path baseDir) {
        startFrom = baseDir;
        loader = wlcl;
    }

    @Override
    public FileVisitResult visitFile(final Path path, final BasicFileAttributes attr) {
        // Check that this is a class file
        if (!path.toString().matches(Utils.CLASSFILE_NAME_SUFFIX)) {
            System.out.println("Skipping: " + path);
            return FileVisitResult.CONTINUE;
        }

        // Check to see if this path corresponds to an allowedClass
        final String classFileName = startFrom.relativize(path).toString().replace(".class", "");

        if (!Utils.classShouldBeSandboxedInternal(classFileName)) {
            return FileVisitResult.CONTINUE;
        }

        final String nameToLoad = Utils.convertInternalFormToQualifiedClassName(classFileName);

        try {
            loader.findClass(nameToLoad);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        return FileVisitResult.CONTINUE;
    }

}
