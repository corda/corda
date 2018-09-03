package com.opengamma.strata.examples.marketdata;

import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.io.ResourceLocator;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Loads market data from the standard directory structure on disk.
 */
public class DirectoryMarketDataBuilder extends ExampleMarketDataBuilder {

    /**
     * The path to the root of the directory structure.
     */
    private final Path rootPath;

    /**
     * Constructs an instance.
     *
     * @param rootPath  the path to the root of the directory structure
     */
    public DirectoryMarketDataBuilder(Path rootPath) {
        this.rootPath = rootPath;
    }

    //-------------------------------------------------------------------------
    @Override
    protected Collection<ResourceLocator> getAllResources(String subdirectoryName) {
        File dir = rootPath.resolve(subdirectoryName).toFile();
        if (!dir.exists()) {
            throw new IllegalArgumentException(Messages.format("Directory does not exist: {}", dir));
        }
        return Arrays.stream(dir.listFiles())
                .filter(f -> !f.isHidden())
                .map(ResourceLocator::ofFile)
                .collect(Collectors.toList());
    }

    @Override
    protected ResourceLocator getResource(String subdirectoryName, String resourceName) {
        File file = rootPath.resolve(subdirectoryName).resolve(resourceName).toFile();
        if (!file.exists()) {
            return null;
        }
        return ResourceLocator.ofFile(file);
    }

    @Override
    protected boolean subdirectoryExists(String subdirectoryName) {
        File file = rootPath.resolve(subdirectoryName).toFile();
        return file.exists();
    }

}
