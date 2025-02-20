/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.diagnostics

import org.gradle.api.Project
import org.gradle.api.problems.*
import org.gradle.api.problems.internal.DefaultProblemGroup
import org.jetbrains.kotlin.gradle.plugin.VariantImplementationFactories
import org.jetbrains.kotlin.gradle.plugin.variantImplementationFactory
import org.jetbrains.kotlin.gradle.utils.newInstance
import javax.inject.Inject

internal interface ProblemsReporter {
    fun reportProblemDiagnostic(diagnostic: ToolingDiagnostic)

    interface Factory : VariantImplementationFactories.VariantImplementationFactory {
        fun getInstance(project: Project): ProblemsReporter
    }
}

internal fun ProblemReporter.report(diagnostic: ToolingDiagnostic, fillSpec: (ProblemSpec) -> ProblemSpec) {
    if (diagnostic.throwable != null) {
        throwing { fillSpec(it) }
    } else {
        reporting(diagnostic::configureProblemSpec)
    }
}

internal class DefaultProblemsReporter @Inject constructor(
    private val problems: Problems,
) : ProblemsReporter {
    override fun reportProblemDiagnostic(diagnostic: ToolingDiagnostic) {
        problems.reporter.report(diagnostic) { fillSpec(it, diagnostic) }
    }

    private fun fillSpec(spec: ProblemSpec, diagnostic: ToolingDiagnostic): ProblemSpec {
        return spec
            .id(diagnostic.id, diagnostic.identifier.displayName, diagnostic.problemGroup())
            .details(diagnostic.message)
            .severity(diagnostic.severity.problemSeverity)
            .apply { diagnostic.configureProblemSpec(this) }
    }

    class Factory : ProblemsReporter.Factory {
        override fun getInstance(project: Project) = project.objects.newInstance<DefaultProblemsReporter>()
    }
}

internal val Project.problemsReporter
    get() = variantImplementationFactory<ProblemsReporter.Factory>()
        .getInstance(this)

internal fun ToolingDiagnostic.problemGroup(): ProblemGroup {
    fun createGroup(name: String, displayName: String, parent: DiagnosticGroup?): ProblemGroup {
        return DefaultProblemGroup(name, displayName, parent?.let { createGroup(it.groupId, it.displayName, it.parent) })
    }

    return identifier.group.let { createGroup(it.groupId, it.displayName, it.parent) }
}

internal fun ToolingDiagnostic.configureProblemSpec(spec: ProblemSpec): ProblemSpec {
    var mSpec = spec
    solutions.forEach {
        mSpec = mSpec.solution(it)
    }

    documentation?.let {
        mSpec = mSpec.documentedAt(it.url)
    }

    throwable?.let {
        mSpec = mSpec.withException(RuntimeException(it))
    }

    return mSpec
}

internal val ToolingDiagnostic.Severity.problemSeverity: Severity
    get() = when (this) {
        ToolingDiagnostic.Severity.WARNING -> Severity.WARNING
        else -> Severity.ERROR
    }
