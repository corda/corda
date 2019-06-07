
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CordaCapletConfigFileParsingTest {
    @Parameterized.Parameters
    public static Collection<Object[]> CombinationsToTest() {
        return Arrays.asList(
                new Object[][]{
                        {new String[]{"--config-file", "blah.conf"}},
                        {new String[]{"--config-file=blah.conf"}},
                        {new String[]{"-f", "blah.conf"}},
                        {new String[]{"-f=blah.conf"}}
                });
    }

    private String[] cmdLineArguments;

    public CordaCapletConfigFileParsingTest(String[] arr) {
        this.cmdLineArguments = arr;
    }

    @Test
    public void testThatConfigFileParameterIsRecognised() {
        final CordaCaplet caplet = CordaCapletTestUtils.getCaplet();
        final File returnPath = caplet.getConfigFile(Arrays.asList(cmdLineArguments), CordaCapletTestUtils.getBaseDir());
        final File expected = Paths.get(".").resolve("blah.conf").toAbsolutePath().normalize().toFile();
        assertEquals(expected, returnPath.getAbsoluteFile());
    }
}

