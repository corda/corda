/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.tools.shell;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.corda.client.jackson.StringToMethodCallParser;
import net.corda.core.messaging.CordaRPCOps;
import org.crsh.cli.Argument;
import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Usage;
import org.crsh.command.InvocationContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

// Note that this class cannot be converted to Kotlin because CRaSH does not understand InvocationContext<Map<?, ?>> which
// is the closest you can get in Kotlin to raw types.

public class RunShellCommand extends InteractiveShellCommand {

    private static Logger logger = LoggerFactory.getLogger(RunShellCommand.class);

    @Command
    @Man(
            "Runs a method from the CordaRPCOps interface, which is the same interface exposed to RPC clients.\n\n" +

                    "You can learn more about what commands are available by typing 'run' just by itself, or by\n" +
                    "consulting the developer guide at https://docs.corda.net/api/kotlin/corda/net.corda.core.messaging/-corda-r-p-c-ops/index.html"
    )
    @Usage("runs a method from the CordaRPCOps interface on the node.")
    public Object main(InvocationContext<Map> context, @Usage("The command to run") @Argument(unquote = false) List<String> command) {
        logger.info("Executing command \"run {}\",", command.stream().collect(joining(" ")));
        StringToMethodCallParser<CordaRPCOps> parser = new StringToMethodCallParser<>(CordaRPCOps.class, objectMapper());

        if (command == null) {
            emitHelp(context, parser);
            return null;
        }
        return InteractiveShell.runRPCFromString(command, out, context, ops(), objectMapper(), isSsh());
    }

    private void emitHelp(InvocationContext<Map> context, StringToMethodCallParser<CordaRPCOps> parser) {
        // Sends data down the pipeline about what commands are available. CRaSH will render it nicely.
        // Each element we emit is a map of column -> content.
        Set<Map.Entry<String, String>> entries = parser.getAvailableCommands().entrySet();
        List<Map.Entry<String, String>> entryList = new ArrayList<>(entries);
        entryList.sort(comparing(Map.Entry::getKey));
        for (Map.Entry<String, String> entry : entryList) {
            // Skip these entries as they aren't really interesting for the user.
            if (entry.getKey().equals("startFlowDynamic")) continue;
            if (entry.getKey().equals("getProtocolVersion")) continue;

            try {
                context.provide(commandAndDesc(entry.getKey(), entry.getValue()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Lists.newArrayList(
                commandAndDesc("shutdown", "Shuts node down (immediately)"),
                commandAndDesc("gracefulShutdown", "Shuts node down gracefully, waiting for all flows to complete first.")
        ).forEach(stringStringMap -> {
            try {
                context.provide(stringStringMap);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @NotNull
    private Map<String, String> commandAndDesc(String command, String description) {
        // Use a LinkedHashMap to ensure that the Command column comes first.
        Map<String, String> abruptShutdown = Maps.newLinkedHashMap();
        abruptShutdown.put("Command", command);
        abruptShutdown.put("Parameter types", description);
        return abruptShutdown;
    }
}
