package net.corda.tools.shell;

import org.crsh.command.InvocationContext;
import org.crsh.command.ScriptException;
import org.crsh.text.RenderPrintWriter;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OutputFormatCommandTest {

    private InvocationContext mockInvocationContext;
    private RenderPrintWriter printWriter;

    private OutputFormatCommand outputFormatCommand;

    private static final String JSON_FORMAT_STRING = "json";
    private static final String YAML_FORMAT_STRING = "yaml";

    @Before
    public void setup() {
        mockInvocationContext = mock(InvocationContext.class);
        printWriter = mock(RenderPrintWriter.class);

        outputFormatCommand = new OutputFormatCommand(printWriter);
    }

    @Test
    public void testValidUpdateToJson() {
        outputFormatCommand.set(mockInvocationContext, JSON_FORMAT_STRING);
        outputFormatCommand.get(mockInvocationContext);

        verify(printWriter).println(JSON_FORMAT_STRING);
    }

    @Test
    public void testValidUpdateToYaml() {
        outputFormatCommand.set(mockInvocationContext, YAML_FORMAT_STRING);
        outputFormatCommand.get(mockInvocationContext);

        verify(printWriter).println(YAML_FORMAT_STRING);
    }

    @Test
    public void testInvalidUpdate() {
        assertThatExceptionOfType(ScriptException.class).isThrownBy(() -> outputFormatCommand.set(mockInvocationContext, "some-invalid-format"))
                .withMessage("The provided format is not supported: some-invalid-format");
    }
}
