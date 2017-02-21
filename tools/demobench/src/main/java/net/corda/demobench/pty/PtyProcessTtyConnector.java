package net.corda.demobench.pty;

import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.nio.charset.Charset;

/**
 * Copied from JediTerm pty.
 * JediTerm is not available in any Maven repository.
 * @author traff
 */
public class PtyProcessTtyConnector extends ProcessTtyConnector {
    private final PtyProcess myProcess;
    private final String name;

    PtyProcessTtyConnector(String name, PtyProcess process, Charset charset) {
        super(process, charset);
        myProcess = process;
        this.name = name;
    }

    @Override
    protected void resizeImmediately() {
        if (getPendingTermSize() != null && getPendingPixelSize() != null) {
            myProcess.setWinSize(
                new WinSize(getPendingTermSize().width, getPendingTermSize().height, getPendingPixelSize().width, getPendingPixelSize().height));
        }
    }

    @Override
    public boolean isConnected() {
        return myProcess.isRunning();
    }

    @Override
    public String getName() {
        return name;
    }

}
