package net.corda.node.shell;

import net.corda.core.messaging.*;
import net.corda.client.jackson.*;
import org.crsh.cli.*;
import org.crsh.command.*;

import java.util.*;

// Note that this class cannot be converted to Kotlin because CRaSH does not understand InvocationContext<Map<?, ?>> which
// is the closest you can get in Kotlin to raw types.

public class RunShellCommand extends InteractiveShellCommand {
    @Command
    @Man(
            "Runs a method from the CordaRPCOps interface, which is the same interface exposed to RPC clients.\n\n" +

                    "You can learn more about what commands are available by typing 'run' just by itself, or by\n" +
                    "consulting the developer guide at https://docs.corda.net/api/kotlin/corda/net.corda.core.messaging/-corda-r-p-c-ops/index.html"
    )
    @Usage("runs a method from the CordaRPCOps interface on the node.")
    public Object main(
            InvocationContext<Map> context,
            @Usage("The command to run") @Argument(unquote = false) List<String> command
    ) {
        StringToMethodCallParser<CordaRPCOps> parser = new StringToMethodCallParser<>(CordaRPCOps.class, objectMapper());

        if (command == null) {
            emitHelp(context, parser);
            return null;
        }

        return InteractiveShell.runRPCFromString(command, out, context);
    }

    private void emitHelp(InvocationContext<Map> context, StringToMethodCallParser<CordaRPCOps> parser) {
        // Sends data down the pipeline about what commands are available. CRaSH will render it nicely.
        // Each element we emit is a map of column -> content.
        Map<String, String> cmdsAndArgs = parser.getAvailableCommands();
        for (Map.Entry<String, String> entry : cmdsAndArgs.entrySet()) {
            // Skip these entries as they aren't really interesting for the user.
            if (entry.getKey().equals("startFlowDynamic")) continue;
            if (entry.getKey().equals("getProtocolVersion")) continue;

            // Use a LinkedHashMap to ensure that the Command column comes first.
            Map<String, String> m = new LinkedHashMap<>();
            m.put("Command", entry.getKey());
            m.put("Parameter types", entry.getValue());
            try {
                context.provide(m);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
