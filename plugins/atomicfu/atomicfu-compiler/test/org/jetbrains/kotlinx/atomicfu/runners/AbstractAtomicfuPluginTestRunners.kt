/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.runners

import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.frontend.fir.FirFailingTestSuppressor
import org.jetbrains.kotlin.test.runners.AbstractFirPsiDiagnosticTest
import org.jetbrains.kotlin.test.runners.codegen.*
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import java.io.File
import org.jetbrains.kotlin.konan.test.irText.AbstractClassicNativeIrTextTest

open class AbstractAtomicfuJvmIrTest : AbstractIrBlackBoxCodegenTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configureForKotlinxAtomicfu()
    }
}

open class AbstractAtomicfuJvmFirLightTreeTest : AbstractFirLightTreeBlackBoxCodegenTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configureForKotlinxAtomicfu()
    }
}

abstract class AbstractAtomicfuFirCheckerTest : AbstractFirPsiDiagnosticTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            configureForKotlinxAtomicfu()
            useAfterAnalysisCheckers(::FirFailingTestSuppressor)
        }
    }
}

abstract class AbstractAtomicfuNativeIrTest : AbstractClassicNativeIrTextTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            useConfigurators(
                ::AtomicfuExtensionRegistrarConfigurator
            )
            useCustomRuntimeClasspathProviders(
                {
                    object : RuntimeClasspathProvider(it) {
                        override fun runtimeClassPaths(module: TestModule): List<File> {
                            val str = System.getProperty("atomicfuNative.classpath")?.split(File.pathSeparator) ?: emptyList()
                            val compilerPluginClasspath = System.getProperty("atomicfu.compiler.plugin")?.split(File.pathSeparator) ?: emptyList()
                            return (str + compilerPluginClasspath).map { File(it) }
                        }
                    }
                }
            )
        }
    }
}