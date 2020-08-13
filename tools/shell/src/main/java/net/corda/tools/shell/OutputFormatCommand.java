package net.corda.tools.shell;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import net.corda.tools.shell.InteractiveShell.OutputFormat;
import org.crsh.cli.Argument;
import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Named;
import org.crsh.cli.Usage;
import org.crsh.command.InvocationContext;
import org.crsh.command.ScriptException;
import org.crsh.text.RenderPrintWriter;

import java.util.Map;

@Man("Allows you to see and update the format that's currently used for the commands' output.")
@Usage("Allows you to see and update the format that's currently used for the commands' output.")
@Named("output-format")
public class OutputFormatCommand extends CordaRpcOpsShellCommand {

    public OutputFormatCommand() {}

    @VisibleForTesting
    OutputFormatCommand(final RenderPrintWriter printWriter) {
        this.out = printWriter;
    }

    private static final BiMap<String, OutputFormat> OUTPUT_FORMAT_MAPPING = ImmutableBiMap.of(
            "json", OutputFormat.JSON,
            "yaml", OutputFormat.YAML
    );

    @Command
    @Man("Sets the output format of the commands.")
    @Usage("sets the output format of the commands.")
    public void set(InvocationContext<Map> context,
                    @Usage("The format of the commands output. Supported values: json, yaml.") @Argument String format) {
        OutputFormat outputFormat = parseFormat(format);

        InteractiveShell.setOutputFormat(outputFormat);
    }

    @Command
    @Man("Shows the output format of the commands.")
    @Usage("shows the output format of the commands.")
    public void get(InvocationContext<Map> context) {
        OutputFormat outputFormat = InteractiveShell.getOutputFormat();
        final String format = OUTPUT_FORMAT_MAPPING.inverse().get(outputFormat);

        out.println(format);
    }

    private OutputFormat parseFormat(String format) {
        if (!OUTPUT_FORMAT_MAPPING.containsKey(format)) {
            throw new ScriptException("The provided format is not supported: " + format);
        }

        return OUTPUT_FORMAT_MAPPING.get(format);
    }
}
