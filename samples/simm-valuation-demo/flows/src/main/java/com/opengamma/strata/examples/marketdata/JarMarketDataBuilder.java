package com.opengamma.strata.examples.marketdata;

import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.io.ResourceLocator;

import java.io.File;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Loads market data from the standard directory structure embedded within a JAR file.
 */
public class JarMarketDataBuilder extends ExampleMarketDataBuilder {

    /**
     * The JAR file containing the expected structure of resources.
     */
    private final File jarFile;
    /**
     * The root path to the resources within the JAR file.
     */
    private final String rootPath;
    /**
     * A cache of JAR entries under the root path.
     */
    private final ImmutableSet<String> entries;

    /**
     * Constructs an instance.
     *
     * @param jarFile  the JAR file containing the expected structure of resources
     * @param rootPath  the root path to the resources within the JAR file
     */
    public JarMarketDataBuilder(File jarFile, String rootPath) {
        // classpath resources are forward-slash separated
        String jarRoot = rootPath.startsWith("/") ? rootPath.substring(1) : rootPath;
        if (!jarRoot.endsWith("/")) {
            jarRoot += "/";
        }
        this.jarFile = jarFile;
        this.rootPath = jarRoot;
        this.entries = getEntries(jarFile, rootPath);
    }

    //-------------------------------------------------------------------------
    @Override
    protected Collection<ResourceLocator> getAllResources(String subdirectoryName) {
        String resolvedSubdirectory = subdirectoryName + "/";
        return entries.stream()
                .filter(e -> e.startsWith(resolvedSubdirectory) && !e.equals(resolvedSubdirectory))
                .map(e -> getEntryLocator(rootPath + e))
                .collect(Collectors.toSet());
    }

    @Override
    protected ResourceLocator getResource(String subdirectoryName, String resourceName) {
        String fullLocation = String.format(Locale.ENGLISH, "%s%s/%s", rootPath, subdirectoryName, resourceName);
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(fullLocation);
            if (entry == null) {
                return null;
            }
            return getEntryLocator(entry.getName());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    Messages.format("Error loading resource from JAR file: {}", jarFile), e);
        }
    }

    @Override
    protected boolean subdirectoryExists(String subdirectoryName) {
        // classpath resources are forward-slash separated
        String resolvedName = subdirectoryName.startsWith("/") ? subdirectoryName.substring(1) : subdirectoryName;
        if (!resolvedName.endsWith("/")) {
            resolvedName += "/";
        }
        return entries.contains(resolvedName);
    }

    //-------------------------------------------------------------------------
    // Gets the resource locator corresponding to a given entry
    private ResourceLocator getEntryLocator(String entryName) {
        return ResourceLocator.of(ResourceLocator.CLASSPATH_URL_PREFIX + entryName);
    }

    private static ImmutableSet<String> getEntries(File jarFile, String rootPath) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> jarEntries = jar.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(rootPath) && !entryName.equals(rootPath)) {
                    String relativeEntryPath = entryName.substring(rootPath.length() + 1);
                    if (!relativeEntryPath.trim().isEmpty()) {
                        builder.add(relativeEntryPath);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    Messages.format("Error scanning entries in JAR file: {}", jarFile), e);
        }
        return builder.build();
    }

}
