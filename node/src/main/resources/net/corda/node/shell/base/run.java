package net.corda.node;

import net.corda.core.messaging.*;
import net.corda.jackson.*;
import org.crsh.cli.*;
import org.crsh.command.*;
import org.crsh.text.*;

import java.util.*;
import java.util.concurrent.*;

import static net.corda.node.InteractiveShell.*;

// This file is actually compiled at runtime with a bundled Java compiler by CRaSH. That's pretty weak: being able
// to do this is a neat party trick and means people can write new commands in Java then just drop them into
// their node directory, but it makes the first usage of the command slower for no good reason. There is a PR
// in the upstream CRaSH project that adds an ExternalResolver which might be useful. Then we could convert this
// file to Kotlin too.

public class run extends InteractiveShellCommand {
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

        String cmd = String.join(" ", command).trim();
        if (cmd.toLowerCase().startsWith("startflow")) {
            // The flow command provides better support and startFlow requires special handling anyway due to
            // the generic startFlow RPC interface which offers no type information with which to parse the
            // string form of the command.
            out.println("Please use the 'flow' command to interact with flows rather than the 'run' command.", Color.yellow);
            return null;
        }

        Object result = null;
        try {
            StringToMethodCallParser.ParsedMethodCall call = parser.parse(ops(), cmd);
            result = call.call();
            result = processResult(result);
        } catch (StringToMethodCallParser.UnparseableCallException e) {
            out.println(e.getMessage(), Color.red);
            out.println("Please try 'man run' to learn what syntax is acceptable", Color.red);
        }
        return result;
    }

    private Object processResult(Object result) {
        if (result != null && !(result instanceof kotlin.Unit) && !(result instanceof Void)) {
            result = printAndFollowRPCResponse(result, out);
        }
        if (result instanceof Future) {
            Future future = (Future) result;
            if (!future.isDone()) {
                out.println("Waiting for completion or Ctrl-C ... ");
                out.flush();
            }
            try {
                result = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
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
