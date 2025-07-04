package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.getLogger
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.callbackBased
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.ZMQ
import kotlin.concurrent.thread

class JupyterZmqClientSocketManager(
    private val loggerFactory: KernelLoggerFactory,
    side: JupyterSocketSide = JupyterSocketSide.CLIENT,
) : JupyterClientSocketManager {
    private val delegate = JupyterZmqClientReceiveSocketManager(loggerFactory, side)

    override fun open(config: KernelConfig): JupyterZmqClientSockets = JupyterZmqClientSockets(delegate.open(config), loggerFactory)
}

class JupyterZmqClientSockets internal constructor(
    private val delegate: JupyterZmqClientReceiveSockets,
    loggerFactory: KernelLoggerFactory,
) : JupyterClientSockets {
    private val logger = loggerFactory.getLogger(this::class)

    val context: ZMQ.Context get() = delegate.context

    override val shell = delegate.shell.callbackBased(logger)
    override val control = delegate.control.callbackBased(logger)
    override val stdin = delegate.stdin.callbackBased(logger)

    // sending to iopub does not make sense, but JupyterCallbackBasedSocket interface requires it
    override val ioPub = delegate.ioPub.noOpSend().callbackBased(logger)

    private val mainThread =
        thread {
            val socketThreads =
                listOf(shell, control, stdin, ioPub).map {
                    thread {
                        socketLoop(parentThread = Thread.currentThread()) { it.receiveMessageAndRunCallbacks() }
                    }
                }
            try {
                socketThreads.forEach { it.join() }
            } catch (_: InterruptedException) {
                socketThreads.forEach { it.interrupt() }
                socketThreads.forEach {
                    try {
                        it.join()
                    } catch (_: InterruptedException) {
                        // skip
                    }
                }
            }
        }

    override fun close() {
        delegate.close()
        mainThread.interrupt()
        try {
            mainThread.join()
        } catch (_: InterruptedException) {
            // skip
        }
    }

    /** Such socket ignores attempts to send messages */
    private fun JupyterReceiveSocket.noOpSend(): JupyterSendReceiveSocket =
        object : JupyterSendReceiveSocket, JupyterReceiveSocket by this {
            override fun sendRawMessage(msg: RawMessage) {}
        }
}

private inline fun socketLoop(
    parentThread: Thread,
    loopBody: () -> Unit,
) {
    while (true) {
        try {
            loopBody()
        } catch (_: InterruptedException) {
            parentThread.interrupt()
            break
        }
    }
}
