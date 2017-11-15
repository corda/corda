package net.corda.cordform;

import java.nio.file.Path;
import java.util.Map;

public interface NetworkParametersGenerator {
    void run(Path baseDirectory, Map<String, Boolean> notaryMap);
}