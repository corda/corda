package net.corda.cordform;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface NetworkParametersGenerator {
    void run(Map<String, Boolean> notaryMap, List<Path> nodesDirs);
}