/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.report.metrics

import java.io.Serializable

data class BuildMetrics(
    val buildTimes: BuildTimes = BuildTimes(),
    val buildPerformanceMetrics: BuildPerformanceMetrics = BuildPerformanceMetrics(),
    val buildAttributes: BuildAttributes = BuildAttributes(),
    val buildInputs: BuildInputs = BuildInputs()
) : Serializable {
    fun addAll(other: BuildMetrics?) =
        other?.also {
            buildTimes.addAll(other.buildTimes)
            buildPerformanceMetrics.addAll(other.buildPerformanceMetrics)
            buildAttributes.addAll(other.buildAttributes)
            buildInputs.addAll(other.buildInputs)
        }

    companion object {
        const val serialVersionUID = 1L
    }
}