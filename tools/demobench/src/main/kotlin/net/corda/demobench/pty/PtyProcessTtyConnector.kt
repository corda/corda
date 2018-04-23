/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.pty

import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.Charset

/**
 * Copied from JediTerm pty.
 * JediTerm is not available in any Maven repository.
 * @author traff
 */
class PtyProcessTtyConnector(
        private val name: String,
        private val process: PtyProcess,
        charset: Charset
) : ProcessTtyConnector(process.zeroFiltered(), charset) {

    override fun getName() = name

    override fun isConnected() = process.isRunning

    override fun resizeImmediately() {
        if (pendingTermSize != null && pendingPixelSize != null) {
            process.winSize = WinSize(
                    pendingTermSize.width,
                    pendingTermSize.height,
                    pendingPixelSize.width,
                    pendingPixelSize.height
            )
        }
    }

}
