
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class CordaCapletBaseDirectoryParsingTest {
    @Parameterized.Parameters
    public static Collection<Object[]> CombinationsToTest() {
        return Arrays.asList(
                new Object[][]{
                        {new String[]{"--base-directory", "blah"}},
                        {new String[]{"--base-directory=blah"}},
                        {new String[]{"-b", "blah"}},
                        {new String[]{"-b=blah"}}
                });
    }

    private String[] cmdLineArguments;

    public CordaCapletBaseDirectoryParsingTest(String[] arr) {
        this.cmdLineArguments = arr;
    }

    @Test
    public void testThatBaseDirectoryParameterIsRecognised() {
        final CordaCaplet caplet = CordaCapletTestUtils.getCaplet();
        final String returnPath = caplet.getBaseDirectory(Arrays.asList(cmdLineArguments));
        final String expected = Paths.get(".").resolve("blah").toAbsolutePath().normalize().toString();
        assertEquals(expected, returnPath);
    }
}

