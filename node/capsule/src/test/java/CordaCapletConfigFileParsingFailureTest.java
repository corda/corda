import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class CordaCapletConfigFileParsingFailureTest {
    @Parameterized.Parameters
    public static Collection<Object[]> CombinationsToTest() {
        return Arrays.asList(
                new Object[][]{
                        {new String[]{"--config-file", "--another-option"}},
                        {new String[]{"--config-file=", "-a"}},
                        {new String[]{"-f", "--another-option"}},
                        {new String[]{"-f=", "-a"}}
                }
        );
    }

    private String[] cmdLineArguments;

    public CordaCapletConfigFileParsingFailureTest(String[] baseOption) {
        this.cmdLineArguments = baseOption;
    }

    @Test
    public void testThatBaseDirectoryFallsBackToDefaultWhenConfigFileIsNotSupplied() {
        final CordaCaplet caplet = CordaCapletTestUtils.getCaplet();
        final File returnPath = caplet.getConfigFile(Arrays.asList(cmdLineArguments), CordaCapletTestUtils.getBaseDir());
        final File expected = Paths.get(".").resolve("node.conf").toAbsolutePath().normalize().toFile();
        Assert.assertEquals(expected, returnPath);
    }
}

