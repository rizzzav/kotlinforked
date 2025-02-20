/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.internal.execWithProgress
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.PreparedKotlinCompilationNpmResolution
import org.jetbrains.kotlin.gradle.utils.getFile
import java.io.File

class Npm internal constructor(
    private val execOps: ExecOperations,
    private val progressLoggerFactory: ProgressLoggerFactory,
) : NpmApiExecution<NpmEnvironment> {

    override fun preparedFiles(nodeJs: NodeJsEnvironment): Collection<File> {
        return listOf(
            nodeJs
                .rootPackageDir
                .getFile()
                .resolve(NpmProject.PACKAGE_JSON)
        )
    }

    override fun prepareRootProject(
        nodeJs: NodeJsEnvironment,
        packageManagerEnvironment: NpmEnvironment,
        rootProjectName: String,
        rootProjectVersion: String,
        subProjects: Collection<PreparedKotlinCompilationNpmResolution>,
    ) {
        return prepareRootPackageJson(
            nodeJs,
            rootProjectName,
            rootProjectVersion,
            subProjects,
            packageManagerEnvironment.overrides
                .associate { it.path to it.toVersionString() },
        )
    }

    private fun prepareRootPackageJson(
        nodeJs: NodeJsEnvironment,
        rootProjectName: String,
        rootProjectVersion: String,
        npmProjects: Collection<PreparedKotlinCompilationNpmResolution>,
        overrides: Map<String, String>,
    ) {
        val rootPackageJsonFile = preparedFiles(nodeJs).single()

        saveRootProjectWorkspacesPackageJson(
            rootProjectName,
            rootProjectVersion,
            npmProjects,
            overrides,
            rootPackageJsonFile
        )
    }

    override fun resolveRootProject(
        services: ServiceRegistry,
        logger: Logger,
        nodeJs: NodeJsEnvironment,
        packageManagerEnvironment: NpmEnvironment,
        cliArgs: List<String>,
    ) {
        npmExec(
            logger = logger,
            nodeJs = nodeJs,
            environment = packageManagerEnvironment,
            dir = nodeJs.rootPackageDir.map { it.asFile },
            description = NpmApiExecution.resolveOperationDescription("npm"),
            args = cliArgs,
        )
    }

    @Deprecated(
        "Updated to remove ServiceRegistry. Scheduled for removal in Kotlin 2.4.",
        ReplaceWith("npmExec(logger, nodeJs, environment, dir, description, args)"),
    )
    @Suppress("unused")
    fun yarnExec(
        @Suppress("UNUSED_PARAMETER")
        services: ServiceRegistry,
        logger: Logger,
        nodeJs: NodeJsEnvironment,
        environment: NpmEnvironment,
        dir: Provider<File>,
        description: String,
        args: List<String>,
    ) {
        npmExec(
            logger = logger,
            nodeJs = nodeJs,
            environment = environment,
            dir = dir,
            description = description,
            args = args,
        )
    }

    fun npmExec(
        logger: Logger,
        nodeJs: NodeJsEnvironment,
        environment: NpmEnvironment,
        dir: Provider<File>,
        description: String,
        args: List<String>,
    ) {
        val progressLogger = progressLoggerFactory.newOperation(ProgressStartEvent.BUILD_OP_CATEGORY)
        execWithProgress(progressLogger, description, execOps = execOps) { execSpec ->
            val arguments: List<String> = mutableListOf<String>().apply {
                add("install")
                addAll(args)
                if (logger.isDebugEnabled) add("--verbose")
                if (environment.ignoreScripts) add("--ignore-scripts")
            }.filter { it.isNotEmpty() }

            if (!environment.standalone) {
                val nodeExecutable = nodeJs.nodeExecutable
                val nodePath = File(nodeExecutable).parent
                execSpec.environment["PATH"] = "$nodePath${File.pathSeparator}${System.getenv("PATH")}"
            }

            val command = environment.executable

            execSpec.executable = command
            execSpec.workingDir = dir.get()

            execSpec.setArgs(arguments)
        }
    }

    private fun saveRootProjectWorkspacesPackageJson(
        rootProjectName: String,
        rootProjectVersion: String,
        npmProjects: Collection<PreparedKotlinCompilationNpmResolution>,
        overrides: Map<String, String>,
        rootPackageJsonFile: File,
    ) {
        val nodeJsWorldDir = rootPackageJsonFile.parentFile
        val rootPackageJson = PackageJson(rootProjectName, rootProjectVersion)
        rootPackageJson.private = true

        val npmProjectWorkspaces = npmProjects.map { it.npmProjectDir.getFile().relativeTo(nodeJsWorldDir).invariantSeparatorsPath }
        val importedProjectWorkspaces =
            NpmImportedPackagesVersionResolver(npmProjects, nodeJsWorldDir).resolveAndUpdatePackages()

        rootPackageJson.workspaces = npmProjectWorkspaces + importedProjectWorkspaces
        rootPackageJson.overrides = overrides
        rootPackageJson.saveTo(
            rootPackageJsonFile
        )
    }
}
