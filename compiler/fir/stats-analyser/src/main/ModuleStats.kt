/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package main

/*
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.util.GarbageCollectionStats
import org.jetbrains.kotlin.util.PlatformType
import org.jetbrains.kotlin.util.SideStats
import org.jetbrains.kotlin.util.Time
import org.jetbrains.kotlin.util.UnitStats
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

@Serializable
class ModuleStats(
    */
/** Typically it should be a name of a module, but might be different
    if multiple `PerformanceManager` are used within the single module for handling multithreaded pipelines.
    In the last case, it should have a suffix like "(Child)".
     *//*

    val name: String?,
    val platform: PlatformType = PlatformType.JVM,
    val isK2: Boolean = true,
    val hasErrors: Boolean = false,
    val filesCount: Int,
    val linesCount: Int,

    // The following properties can be null in case of errors on previous stages.
    // For instance, if there is a syntax error in analysis, other stats info is not initialized.
    // In future `Time` can be replaced with more extended info if needed.
    val initStats: Time?,
    val analysisStats: Time?,
    val irGenerationStats: Time?,
    val irLoweringStats: Time?,
    val backendStats: Time?,

    // Null in case of java or binary files not used
    val findJavaClassStats: SideStats? = null,
    val findKotlinClassStats: SideStats? = null,

    // Null/empty if extended measurements are not enabled
    val gcStats: List<GarbageCollectionStats> = listOf(),
    val jitTimeMillis: Long? = null,

    // Deprecated stats (from performance counter)
    val extendedStats: List<String> = emptyList(),
) {
    fun toUnitStats(): UnitStats {
        val declaredProperties = ModuleStats::class.declaredMemberProperties.associateBy { it.name }
        val constructorParameters = ModuleStats::class.primaryConstructor!!.parameters

        return UnitStats(
            declaredProperties.getValue(constructorParameters[0].name).get(this)
        )
    }
}*/
