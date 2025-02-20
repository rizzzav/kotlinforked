/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.yarn

import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.internal.execWithProgress
import org.jetbrains.kotlin.gradle.internal.newBuildOpLogger
import org.jetbrains.kotlin.gradle.targets.js.npm.NodeJsEnvironment
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmApiExecution
import java.io.File
import javax.inject.Inject

abstract class YarnBasics internal constructor(
    private val execOps: ExecOperations,
    private val objects: ObjectFactory,
) : NpmApiExecution<YarnEnvironment> {

    @Deprecated(
        "Updated to remove ServiceRegistry.",
        ReplaceWith("yarnExec(logger, nodeJs, yarn, dir, description, args)")
    )
    @Suppress("unused")
    fun yarnExec(
        @Suppress("UNUSED_PARAMETER")
        services: ServiceRegistry,
        logger: Logger,
        nodeJs: NodeJsEnvironment,
        yarn: YarnEnvironment,
        dir: File,
        description: String,
        args: List<String>,
    ) {
        yarnExec(
            logger = logger,
            nodeJs = nodeJs,
            yarn = yarn,
            dir = dir,
            description = description,
            args = args,
        )
    }

    fun yarnExec(
        logger: Logger,
        nodeJs: NodeJsEnvironment,
        yarn: YarnEnvironment,
        dir: File,
        description: String,
        args: List<String>,
    ) {
        val services = objects.newInstance(Services::class.java)
        val progLog = services.progressLoggerFactory.newBuildOpLogger()
        execWithProgress(progLog, description, execOps) { exec ->
            val arguments = mutableListOf<String>().apply {
                addAll(args)
                if (logger.isDebugEnabled) add("--verbose")
                if (yarn.ignoreScripts) add("--ignore-scripts")
            }.filter { it.isNotEmpty() }

            val nodeExecutable = nodeJs.nodeExecutable
            if (!yarn.ignoreScripts) {
                val nodePath = File(nodeExecutable).parent
                exec.environment("PATH", "$nodePath${File.pathSeparator}${System.getenv("PATH")}")
            }

            val command = yarn.executable
            if (yarn.standalone) {
                exec.executable = command
                exec.setArgs(arguments)
            } else {
                exec.executable = nodeExecutable
                exec.setArgs(listOf(command) + arguments)
            }

            exec.workingDir = dir
        }
    }

    internal interface Services {
        @get:Inject
        val progressLoggerFactory: ProgressLoggerFactory
    }
}
