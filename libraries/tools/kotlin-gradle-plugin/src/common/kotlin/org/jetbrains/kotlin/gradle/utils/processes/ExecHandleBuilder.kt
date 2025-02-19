/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils.processes

//import org.gradle.api.logging.Logger
//import org.gradle.api.logging.Logging
//import org.gradle.api.model.ObjectFactory
//import org.gradle.process.ExecOperations
//import org.gradle.process.ExecSpec
//import org.jetbrains.kotlin.gradle.utils.newInstance
//import org.jetbrains.kotlin.gradle.utils.processes.ProcessLaunchOptions.Companion.processLaunchOptions
//import java.io.InputStream
//import java.io.OutputStream
//import java.util.concurrent.Executor
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import javax.inject.Inject
//
///**
// * Internal builder for a [ExecHandle].
// *
// * Can be configured to control how a process is managed during its execution
// * (I/O, daemon, exit values).
// * (These options are configured separately to [ProcessLaunchOptions] because typically
// * these options are critical to launching and managing a process, and so should not
// * be modified by users.)
// */
//internal abstract class ExecHandleBuilder @Inject constructor(
//    val launchOpts: ProcessLaunchOptions,
//    private val execOps: ExecOperations,
//) {
//    /** Optional display name, used in logging output. */
//    var displayName: String? = null
//
//    /** Sets whether a non-zero exit value is ignored, else throw an exception. */
//    var ignoreExitValue: Boolean = false
//
////    /** Merge the [errorOutput] into [standardOutput]. */
////    var redirectErrorStream: Boolean = false
//
//    /**
//     * Sets the standard input stream for the process executing the command.
//     * The stream is closed after the process completes.
//     */
//    var standardInput: InputStream? = null
//
//    /**
//     * Sets the output stream to consume standard output from the process executing the command.
//     * The stream is closed after the process completes.
//     */
//    var standardOutput: OutputStream = System.out
//
//    /**
//     * Sets the output stream to consume standard error from the process executing the command.
//     * The stream is closed after the process completes.
//     */
//    var errorOutput: OutputStream = System.err
//
//    /**
//     * Java [Executor] used to launch the process and handle IO streams.
//     *
//     * The executor must be multithreaded, otherwise simultaneously handling IO streams and the process
//     * will cause a deadlock.
//     */
//    var executor: ExecutorService = defaultExecHandleExecutor
//
//    /** Arguments for the command to be executed. */
//    val arguments: MutableList<String> = mutableListOf()
//
//    /**
//     * Set [arguments], replacing any that were previously added.
//     */
//    fun setArguments(args: Iterable<String>) {
//        arguments.clear()
//        arguments.addAll(args)
//    }
//
//    /** Configure [launchOpts]. */
//    fun launchOpts(block: ProcessLaunchOptions.() -> Unit) {
//        launchOpts.apply(block)
//    }
//
//    fun build(): ExecHandle {
//        return ExecHandle(
//            displayName = displayName ?: "command ${launchOpts.executable.orNull}",
////            executor = executor,
//        ) {
//            execOps.exec { exec ->
//                copyTo(exec)
//            }
//        }
//
////        val r = executor.submit(Callable<ExecResult> {
////            thread(
////                start = false,
////                name = displayName,
////                isDaemon = true,
////            ) {
////                execOps.exec { exec ->
////                    copyTo(exec)
////                }
////            }
////        })
////
////        r.thenCompose()
////
////        r.cancel(true)
////        return thread(
////            start = false,
////            name = displayName,
////            isDaemon = true,
////        ) {
////            execOps.exec { exec ->
////                copyTo(exec)
////            }
////        }
//    }
//
//    private fun copyTo(exec: ExecSpec) {
//        exec.executable = launchOpts.executable.orNull ?: error("executable must not be empty")
//        exec.workingDir = launchOpts.workingDir.orNull?.asFile ?: error("workingDir is required")
//
//        exec.environment(launchOpts.environment.orNull.orEmpty())
//
//        exec.args(arguments)
//
//        exec.isIgnoreExitValue = ignoreExitValue
//
//        exec.standardOutput = standardOutput
//        exec.errorOutput = errorOutput
//        standardInput?.let(exec::setStandardInput)
//
//
//        logger.info("[ExecHandleBuilder ${displayName ?: exec.executable}] Configured ExecSpec: ${exec.commandLine}")
//    }
//
////    fun build(): ExecHandle {
////        val executable = launchOpts.executable.orNull
////        check(!executable.isNullOrBlank()) { "executable must not be empty" }
////
////        val streamsHandler = StreamsHandler.OutputStreamsForwarder(
////            standardOutput = standardOutput,
////            errorOutput = errorOutput,
////            readErrorStream = !redirectErrorStream
////        )
////
////        val inputHandler = standardInput?.let { StreamsHandler.ForwardStdin(it) } ?: StreamsHandler.EmptyStdIn()
////
////        val workingDir = launchOpts.workingDir.orNull?.asFile ?: error("workingDir is required")
////        val environment = launchOpts.environment.orNull.orEmpty()
////
////        return ExecHandle(
////            displayName = displayName ?: "command '$executable'",
////            directory = workingDir,
////            command = executable,
////            arguments = arguments,
////            environment = environment,
////            outputHandler = streamsHandler,
////            inputHandler = inputHandler,
////            redirectErrorStream = redirectErrorStream,
////            executor = executor,
////            ignoreExitValue = ignoreExitValue
////        )
////    }
//
//    companion object {
//
//        private val logger: Logger = Logging.getLogger(ExecHandleBuilder::class.java)
//
//        /**
//         * A default shared thread pool for all exec handles.
//         *
//         * Note: We re-use a pool to try to minimise resource use.
//         * Trying to manually shut down an executor is technically challenging
//         * (the pool is used for IO and the launched process, so there's no clear moment when everything stops.)
//         * The docs say "a pool that remains idle for long enough will not consume any resources", so a shared pool
//         * should have minimal impact.
//         */
//        private val defaultExecHandleExecutor: ExecutorService by lazy { Executors.newCachedThreadPool() }
//
//        /**
//         * Create a new instance of [ExecHandleBuilder].
//         */
//        internal fun ObjectFactory.execHandleBuilder(
//            block: ExecHandleBuilder.() -> Unit = {},
//        ): ExecHandleBuilder {
//            val launchOpts = processLaunchOptions()
//            return newInstance<ExecHandleBuilder>(launchOpts)
//                .apply(block)
//        }
//    }
//}
