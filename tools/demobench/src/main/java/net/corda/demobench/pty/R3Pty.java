package net.corda.demobench.pty;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.pty4j.PtyProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

public class R3Pty implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(R3Pty.class);

    private final JediTermWidget terminal;
    private final String name;

    public R3Pty(String name, SettingsProvider settings, Dimension dimension) {
        terminal = new JediTermWidget(dimension, settings);
        this.name = name;
    }

    @Override
    public void close() {
        LOG.info("Closing terminal '{}'", name);
        terminal.close();
    }

    public String getName() {
        return name;
    }

    public JediTermWidget getTerminal() {
        return terminal;
    }

    private TtyConnector createTtyConnector(String[] command, Map<String, String> environment, String workingDir) {
        try {
            PtyProcess process = PtyProcess.exec(command, environment, workingDir);

            try {
                return new PtyProcessTtyConnector(name, process, UTF_8);
            } catch (Exception e) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void run(String[] args, Map<String, String> envs, String workingDir) {
        if (terminal.isSessionRunning()) {
            throw new IllegalStateException(terminal.getSessionName() + " is already running");
        }

        Map<String, String> environment = new HashMap<>(envs);
        if (!UIUtil.isWindows) {
            environment.put("TERM", "xterm");
        }

        TerminalSession session = terminal.createTerminalSession(createTtyConnector(args, environment, workingDir));
        session.start();
    }

    public void run(String[] args, Map<String, String> envs) {
        run(args, envs, null);
    }

    public void run(String... args) {
        run(args, System.getenv());
    }

}
