/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.optimizations

import org.jetbrains.kotlin.backend.common.lower.optimizations.LivenessAnalysis
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.backend.konan.DECLARATION_ORIGIN_INLINE_CLASS_SPECIAL_FUNCTION
import org.jetbrains.kotlin.backend.konan.NativeGenerationState
import org.jetbrains.kotlin.backend.konan.ir.isArray
import org.jetbrains.kotlin.backend.konan.ir.isVirtualCall
import org.jetbrains.kotlin.backend.konan.ir.konanLibrary
import org.jetbrains.kotlin.backend.konan.lower.bridgeTarget
import org.jetbrains.kotlin.backend.konan.lower.originalConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.isSingleFieldValueClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrSuspensionPoint
import org.jetbrains.kotlin.ir.inline.FunctionInlining
import org.jetbrains.kotlin.ir.inline.InlineFunctionResolver
import org.jetbrains.kotlin.ir.inline.InlineMode
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.library.metadata.isCInteropLibrary
import kotlin.collections.*

internal class PreCodegenInliner(
        val generationState: NativeGenerationState,
        val moduleDFG: ModuleDFG,
        val callGraph: CallGraph,
) {
    private val context = generationState.context
    private val symbols = context.ir.symbols
    private val noInline = symbols.noInline
    private val string = symbols.string
    private val throwable = symbols.throwable
    private val kFunctionImpl = symbols.kFunctionImpl
    private val invokeSuspendFunction = symbols.invokeSuspendFunction

    private val rootSet = callGraph.rootSet

    fun run() {
        val computationStates = mutableMapOf<DataFlowIR.FunctionSymbol.Declared, ComputationState>()
        val stack = rootSet.toMutableList()
        for (root in stack)
            computationStates[root] = ComputationState.NEW

        while (stack.isNotEmpty()) {
            val functionSymbol = stack.peek()!!
            val function = moduleDFG.functions[functionSymbol]!!
            val state = computationStates[functionSymbol]!!
            val callSites = callGraph.directEdges[functionSymbol]!!.callSites.filter {
                !it.isVirtual && callGraph.directEdges.containsKey(it.actualCallee)
            }
            when (state) {
                ComputationState.NEW -> {
                    computationStates[functionSymbol] = ComputationState.PENDING
                    for (callSite in callSites) {
                        val calleeSymbol = callSite.actualCallee as DataFlowIR.FunctionSymbol.Declared
                        if (computationStates[calleeSymbol] == null || computationStates[calleeSymbol] == ComputationState.NEW) {
                            computationStates[calleeSymbol] = ComputationState.NEW
                            stack.push(calleeSymbol)
                        }
                    }
                }

                ComputationState.PENDING -> {
                    stack.pop()
                    computationStates[functionSymbol] = ComputationState.DONE

                    val irFunction = functionSymbol.irFunction ?: continue
                    val irBody = irFunction.body ?: continue
                    if (irFunction.bridgeTarget != null
                            || irFunction.konanLibrary?.isCInteropLibrary() == true
                            || irFunction.originalConstructor?.let { constructor ->
                                // To support IR pattern recognition in IrToBitcode.kt on IrConstantObject generation.
                                constructor.valueParameters.isEmpty()
                                        && constructor.constructedClass.superTypes.any { it.classOrNull == kFunctionImpl }
                            } == true
                    ) {
                        continue
                    }

                    val functionsToInline = mutableSetOf<IrFunction>()
                    val devirtualizedCallSitesFromFunctionsToInline = mutableMapOf<IrCall, DevirtualizationAnalysis.DevirtualizedCallSite>()
                    for (callSite in callSites) {
                        val calleeSymbol = callSite.actualCallee as DataFlowIR.FunctionSymbol.Declared
                        if (computationStates[calleeSymbol] != ComputationState.DONE) continue
                        val calleeIrFunction = calleeSymbol.irFunction ?: continue
                        val callee = moduleDFG.functions[calleeSymbol]!!

                        var isALoop = false
                        callee.body.forEachNonScopeNode { node ->
                            if (node is DataFlowIR.Node.Call && node.callee == calleeSymbol)
                                isALoop = true
                        }

                        val calleeSize = callee.body.allScopes.sumOf { it.nodes.size }
                        val threshold = 40
                        val shouldInline = !isALoop && calleeSize <= threshold
                                && (calleeIrFunction.origin != DECLARATION_ORIGIN_INLINE_CLASS_SPECIAL_FUNCTION)
                                && calleeIrFunction.konanLibrary?.isCInteropLibrary() != true
                                && !calleeIrFunction.hasAnnotation(noInline)
                                && calleeIrFunction.correspondingPropertySymbol?.owner?.hasAnnotation(noInline) != true
                                && !calleeIrFunction.overrides(invokeSuspendFunction.owner)
                                && calleeIrFunction.correspondingPropertySymbol?.owner?.let {
                            it.parentClassOrNull?.isSingleFieldValueClass == true && it.backingField != null
                        } != true
                                && calleeIrFunction.originalConstructor?.constructedClass?.let { it.isArray || it.symbol == string } != true
                                && calleeIrFunction.originalConstructor?.constructedClass?.getAllSuperclasses()?.contains(throwable.owner) != true
                        if (shouldInline)
                            functionsToInline.add(calleeIrFunction)
                    }

                    if (functionsToInline.isNotEmpty()) {
                        val inliner = FunctionInlining(
                                context,
                                inlineFunctionResolver = object : InlineFunctionResolver(inlineMode = InlineMode.ALL_FUNCTIONS) {
                                    override fun shouldExcludeFunctionFromInlining(symbol: IrFunctionSymbol) =
                                            symbol.owner !in functionsToInline

                                    override fun needsInlining(expression: IrFunctionAccessExpression): Boolean {
                                        return (expression as? IrCall)?.isVirtualCall != true
                                    }
                                },
                        )
                        inliner.lower(irBody, irFunction)

                        LivenessAnalysis.run(irBody) { it is IrSuspensionPoint }
                                .forEach { (irElement, liveVariables) ->
                                    generationState.liveVariablesAtSuspensionPoints[irElement as IrSuspensionPoint] = liveVariables
                                }

                        val rebuiltFunction = FunctionDFGBuilder(generationState, moduleDFG.symbolTable).build(irFunction)
                        moduleDFG.functions[functionSymbol] = rebuiltFunction
                    }
                }

                ComputationState.DONE -> {
                    stack.pop()
                }
            }
        }
    }

    private enum class ComputationState {
        NEW,
        PENDING,
        DONE
    }
}
