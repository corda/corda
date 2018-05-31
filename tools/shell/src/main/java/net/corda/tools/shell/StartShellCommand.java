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

// A simple forwarder to the "flow start" command, for easier typing.

import net.corda.tools.shell.utlities.ANSIProgressRenderer;
import net.corda.tools.shell.utlities.CRaSHANSIProgressRenderer;
import org.crsh.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.stream.Collectors.joining;

public class StartShellCommand extends InteractiveShellCommand {

    private static Logger logger = LoggerFactory.getLogger(StartShellCommand.class);

    @Command
    @Man("An alias for 'flow start'. Example: \"start Yo target: Some other company\"")
    public void main(@Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
                     @Usage("The data to pass as input") @Argument(unquote = false) List<String> input) {

        logger.info("Executing command \"start {} {}\",", name, (input != null) ? input.stream().collect(joining(" ")) : "<no arguments>");
        ANSIProgressRenderer ansiProgressRenderer = ansiProgressRenderer();
        FlowShellCommand.startFlow(name, input, out, ops(), ansiProgressRenderer != null ? ansiProgressRenderer : new CRaSHANSIProgressRenderer(out), objectMapper());
    }
}