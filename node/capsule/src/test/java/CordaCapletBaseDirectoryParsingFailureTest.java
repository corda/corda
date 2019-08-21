import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class CordaCapletBaseDirectoryParsingFailureTest {
    @Parameterized.Parameters
    public static Collection<Object[]> CombinationsToTest() {
        return Arrays.asList(
                new Object[][]{
                        {new String[]{"--base-directory", "--another-option"}},
                        {new String[]{"--base-directory=", "-a"}},
                        {new String[]{"-b", "--another-option"}},
                        {new String[]{"-b=", "-a"}}
                }
        );
    }

    private String[] cmdLineArguments;

    public CordaCapletBaseDirectoryParsingFailureTest(String[] baseOption) {
        this.cmdLineArguments = baseOption;
    }

    @Test
    public void testThatBaseDirectoryFallsBackToCurrentWhenBaseDirectoryIsNotSupplied() {
        final CordaCaplet caplet = CordaCapletTestUtils.getCaplet();
        final String returnPath = caplet.getBaseDirectory(Arrays.asList(cmdLineArguments));
        final String expected = Paths.get(".").toAbsolutePath().normalize().toString();
        Assert.assertEquals(expected, returnPath);
    }
}

