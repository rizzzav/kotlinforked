/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils.processes

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.gradle.utils.processes.ExecAsyncHandle.Companion.execAsync
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


/**
 * A handle to the process launched by [ExecOperations.execAsync].
 */
internal sealed interface ExecAsyncHandle {
    val displayName: String

    /**
     * Start, without blocking.
     *
     * Use [waitForResult] to get the result.
     */
    fun start(): ExecAsyncHandle

    /**
     * Block until the process has finished.
     *
     * @returns the result, or `null` if the process failed.
     */
    // Note: we use ExecOperations.exec {} to launch the process, which does not directly support cancellation.
    // We have to kill the thread, and then Gradle cannot return the process result, so we must return 'null'.
    fun waitForResult(): ExecResult?

    /**
     * Block until the process has finished.
     *
     * @returns the failure, or `null` if the process did not throw an exception.
     */
    fun waitForFailure(): Throwable?

    /**
     * Cancel the running process.
     *
     * This will trigger an [InterruptedException].
     */
    fun abort()

    fun isAlive(): Boolean

    companion object {
        /**
         * Launch a process asynchronously.
         *
         * @see ExecAsyncHandle
         */
        internal fun ExecOperations.execAsync(
            displayName: String,
            abortTimeout: Duration = Duration.ofSeconds(30),
            configure: (execSpec: ExecSpec) -> Unit,
        ): ExecAsyncHandle {
            return ExecAsyncHandleImpl(
                displayName = displayName,
                abortTimeout = abortTimeout,
            ) {
                exec { exec ->
                    exec.isIgnoreExitValue = true
                    configure(exec)
                }
            }
        }
    }
}


private class ExecAsyncHandleImpl(
    override val displayName: String,
    private val abortTimeout: Duration,
    private val run: () -> ExecResult,
) : ExecAsyncHandle {
    private val result: AtomicReference<ExecResult?> = AtomicReference(null)
    private val failure: AtomicReference<Exception?> = AtomicReference(null)

    private val thread: Thread = thread(
        start = false,
        name = displayName,
        isDaemon = true,
        block = {
            try {
                result.set(run())
            } catch (e: Exception) {
                failure.set(e)
            }
        },
    )

    override fun start(): ExecAsyncHandle {
        logger.info("[ExecAsyncHandle $displayName] starting... (${thread.state})")
        thread.start()
        return this
    }

    override fun waitForResult(): ExecResult? {
        waitForCompletion()

        if (failure.get() != null) {
            return null
        }

        return requireNotNull(result.get()) {
            "[ExecAsyncHandle $displayName] result is null (${thread.state})"
        }
    }

    override fun waitForFailure(): Exception {
        waitForCompletion()

        return requireNotNull(failure.get()) {
            "[ExecAsyncHandle $displayName] failure is null (${thread.state})"
        }
    }

    private fun waitForCompletion() {
        try {
            thread.join()
        } catch (ex: InterruptedException) {
            logger.info("[ExecAsyncHandle $displayName] interrupted $ex (${thread.state})")
            thread.interrupt()
            thread.join(abortTimeout.toMillis())
        }
    }

    override fun abort() {
        logger.info("[ExecAsyncHandle $displayName] aborting... (${thread.state})")
        thread.interrupt()
        logger.info("[ExecAsyncHandle $displayName] finished aborting (${thread.state})")
    }

    override fun isAlive(): Boolean = !thread.isAlive

    companion object {
        private val logger: Logger = Logging.getLogger(ExecAsyncHandle::class.java)
    }
}
