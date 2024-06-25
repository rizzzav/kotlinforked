/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.resolution

import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.validityAsserted
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundOperation
import org.jetbrains.kotlin.analysis.api.resolution.KaPartiallyAppliedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.psi.KtExpression

@KaImplementationDetail
class KaBaseCompoundArrayAccessCall(
    private val backingCompoundAccess: KaCompoundOperation,
    indexArguments: List<KtExpression>,
    getPartiallyAppliedSymbol: KaPartiallyAppliedFunctionSymbol<KaNamedFunctionSymbol>,
    setPartiallyAppliedSymbol: KaPartiallyAppliedFunctionSymbol<KaNamedFunctionSymbol>,
) : KaCompoundArrayAccessCall, KaCompoundAccessCall {
    override val token: KaLifetimeToken get() = backingCompoundAccess.token
    override val compoundOperation: KaCompoundOperation get() = withValidityAssertion { backingCompoundAccess }
    override val indexArguments: List<KtExpression> by validityAsserted(indexArguments)
    override val getPartiallyAppliedSymbol: KaPartiallyAppliedFunctionSymbol<KaNamedFunctionSymbol>
            by validityAsserted(getPartiallyAppliedSymbol)

    override val setPartiallyAppliedSymbol: KaPartiallyAppliedFunctionSymbol<KaNamedFunctionSymbol>
            by validityAsserted(setPartiallyAppliedSymbol)
}