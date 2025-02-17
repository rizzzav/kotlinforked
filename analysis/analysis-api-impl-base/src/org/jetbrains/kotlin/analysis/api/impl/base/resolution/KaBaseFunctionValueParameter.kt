/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.resolution

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.types.KaFunctionValueParameter
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.Name

@KaExperimentalApi
class KaBaseFunctionValueParameter(override val name: Name?, override val type: KaType) : KaFunctionValueParameter() {
    override fun toString(): String = "KaRegularFunctionValueParameter [name: $name, type: ${type.symbol?.classId}]"
}