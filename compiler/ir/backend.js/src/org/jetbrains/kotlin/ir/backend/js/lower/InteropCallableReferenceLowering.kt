/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.compilationException

import org.jetbrains.kotlin.backend.common.lower.LoweredStatementOrigins
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsStatementOrigins
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.utils.Namer
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.memoryOptimizedMap
import org.jetbrains.kotlin.utils.memoryOptimizedMapIndexed
import org.jetbrains.kotlin.utils.memoryOptimizedPlus

class InteropCallableReferenceLowering(val context: JsIrBackendContext) : BodyLoweringPass {

    val generateInlineAnonymousFunctions: Boolean
        get() = context.configuration.getBoolean(JSConfigurationKeys.GENERATE_INLINE_ANONYMOUS_FUNCTIONS)

    /**
     * A factory that creates [IrFunctionExpression] for lambdas being constructed.
     *
     * Basically, replaces a constructor call of a lambda class with a JS function expression,
     * remapping the captured values.
     */
    private inner class FunctionExpressionFactory(
        private val lambdaDeclaration: IrSimpleFunction,
        private val constructor: IrConstructor,
        private val lambdaInfo: LambdaInfo,
    ) {
        fun createFunctionExpression(ctorCall: IrConstructorCall): IrExpression {
            val superClass = lambdaInfo.superInvokeFun.parentAsClass
            val lambdaType = lambdaInfo.lambdaClass.superTypes.single { it.classifierOrNull === superClass.symbol }

            val lambdaContextMapping = remapCapturedFields(constructor) { ctorParameter ->
                when (val valueArgument = ctorCall.getValueArgument(ctorParameter.owner.index)!!) {
                    is IrGetValue -> valueArgument.symbol
                    // IrTypeOperatorCall can be passed to a lambda constructor if it was generated by the inlineLambdaBody method.
                    is IrTypeOperatorCall -> valueArgument.argument.safeAs<IrGetValue>()?.symbol
                    else -> null
                }
            }

            val outerReceiverMapping = remapCapturedFields(constructor) { ctorParameter ->
                val valueArgument = ctorCall.getValueArgument(ctorParameter.owner.index)!!
                valueArgument.safeAs<IrGetField>()
            }

            assert(lambdaContextMapping.size + outerReceiverMapping.size == ctorCall.valueArgumentsCount)

            fun Iterable<IrValueSymbol>.findDispatchReceiver() = find { it.owner.isDispatchReceiver }

            val capturedDispatchReceiver = lambdaContextMapping.values.findDispatchReceiver()
                ?: outerReceiverMapping.values.mapNotNull { it.receiver?.safeAs<IrGetValue>()?.symbol }.findDispatchReceiver()

            lambdaDeclaration.body = inlineLambdaBody(
                lambdaDeclaration,
                lambdaInfo.invokeFun,
                lambdaInfo.createOldToNewInvokeParametersMapping(lambdaDeclaration),
                lambdaContextMapping,
                outerReceiverMapping,
            )
            val functionExpression = IrFunctionExpressionImpl(
                ctorCall.startOffset,
                ctorCall.endOffset,
                lambdaType,
                lambdaDeclaration,
                JsStatementOrigins.CALLABLE_REFERENCE_CREATE
            )

            // TODO: If we generate arrow functions instead of anonymous functions, there's no need for jsBind
            // TODO: Do we need to set proper offsets?
            if (capturedDispatchReceiver != null)
                return IrCallImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    context.irBuiltIns.anyType,
                    context.intrinsics.jsBind,
                    valueArgumentsCount = 2,
                    typeArgumentsCount = 0,
                    origin = JsStatementOrigins.BIND_CALL,
                ).apply {
                    putValueArgument(0, IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, capturedDispatchReceiver))
                    putValueArgument(1, functionExpression)
                }

            return functionExpression
        }
    }

    override fun lower(irFile: IrFile) {

        // Regular contextless lambdas are always transformed to function references
        val ctorToFreeFunctionMap = hashMapOf<IrConstructorSymbol, IrSimpleFunctionSymbol>()

        // Regular lambdas with captured variables are transformed to function expressions whenever possible.
        // However, we don't do that if the lambda captures a variable declared in a loop, at least when variable
        // declarations are lowered into 'var' statements in JS. See the CapturedVariablesDeclaredInLoops class.
        // We also don't do that if there is more than one constructor call for a single lambda.
        val ctorToFunctionExpressionMap = hashMapOf<IrConstructorSymbol, FunctionExpressionFactory>()

        // Suspend lambdas are transformed to factory calls
        val ctorToFactoryMap = hashMapOf<IrConstructorSymbol, IrSimpleFunctionSymbol>()

        val closureUsageAnalyser = ClosureUsageAnalyser()

        if (generateInlineAnonymousFunctions)
            irFile.acceptChildrenVoid(closureUsageAnalyser)

        val callableReferenceClassTransformer = CallableReferenceClassTransformer(
            ctorToFactoryMap,
            ctorToFreeFunctionMap,
            ctorToFunctionExpressionMap,
            closureUsageAnalyser
        )

        irFile.transform(callableReferenceClassTransformer, null)

        irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                expression.transformChildrenVoid()
                if (expression.origin != JsStatementOrigins.CALLABLE_REFERENCE_CREATE) return expression

                ctorToFreeFunctionMap[expression.symbol]?.let { liftedLambda ->
                    return replaceLambdaConstructorCallWithReferenceToLiftedLambda(expression, liftedLambda)
                }

                ctorToFunctionExpressionMap[expression.symbol]?.let { functionExpressionFactory ->
                    return functionExpressionFactory.createFunctionExpression(expression).apply {
                        // Make sure to also apply this transformation to the inlined lambda body.
                        transformChildrenVoid()
                    }
                }

                ctorToFactoryMap[expression.symbol]?.let { factory ->
                    return replaceLambdaConstructorCallWithFactoryCall(expression, factory)
                }

                return expression
            }
        })
    }

    private fun replaceLambdaConstructorCallWithFactoryCall(
        expression: IrConstructorCall,
        factory: IrSimpleFunctionSymbol
    ): IrCall {
        val newCall = expression.run {
            IrCallImpl(startOffset, endOffset, type, factory, typeArgumentsCount, valueArgumentsCount, origin)
        }

        newCall.dispatchReceiver = expression.dispatchReceiver
        newCall.extensionReceiver = expression.extensionReceiver

        for (i in 0 until expression.typeArgumentsCount) {
            newCall.putTypeArgument(i, expression.getTypeArgument(i))
        }

        for (i in 0 until expression.valueArgumentsCount) {
            newCall.putValueArgument(i, expression.getValueArgument(i))
        }

        return newCall
    }

    private fun replaceLambdaConstructorCallWithReferenceToLiftedLambda(
        expression: IrConstructorCall,
        liftedLambda: IrSimpleFunctionSymbol
    ): IrRawFunctionReference = IrRawFunctionReferenceImpl(
        expression.startOffset,
        expression.endOffset,
        expression.type,
        liftedLambda,
    )

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        compilationException("Unreachable", irBody)
    }

    /**
     * This class helps to determine if a lambda captures a variable declared in a loop.
     * This is needed because variable declarations are lowered into JavaScript `var` statements, which
     * are function scoped. Because anonymous functions in JavaScript capture variables by reference, this
     * may lead to unintended effects like
     * [this](https://stackoverflow.com/questions/750486/javascript-closure-inside-loops-simple-practical-example).
     *
     * ES6 `let` statements don't have this problem.
     */
    private class ClosureUsageAnalyser : IrElementVisitorVoid {

        private val lambdaConstructorCalls: MutableMap<IrConstructorSymbol, MutableList<IrConstructorCall>> = hashMapOf()
        private val variablesDeclaredInLoops: MutableSet<IrValueDeclaration> = hashSetOf()
        private var loopNestedness = 0

        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitLoop(loop: IrLoop) {
            ++loopNestedness
            loop.acceptChildrenVoid(this)
            --loopNestedness
        }

        override fun visitVariable(declaration: IrVariable) {
            if (loopNestedness > 0)
                variablesDeclaredInLoops.add(declaration)
            declaration.acceptChildrenVoid(this)
        }

        override fun visitConstructorCall(expression: IrConstructorCall) {
            if (expression.origin == JsStatementOrigins.CALLABLE_REFERENCE_CREATE)
                lambdaConstructorCalls
                    .getOrPut(expression.symbol, ::mutableListOf)
                    .add(expression)
            expression.acceptChildrenVoid(this)
        }

        private fun IrElement.referencesVariablesDeclaredInLoops(): Boolean {
            var result = false
            acceptVoid(object : IrElementVisitorVoid {

                override fun visitElement(element: IrElement) {
                    if (!result)
                        element.acceptChildrenVoid(this)
                }

                override fun visitGetValue(expression: IrGetValue) {
                    if (expression.symbol.owner in variablesDeclaredInLoops)
                        result = true
                    else
                        expression.acceptChildrenVoid(this)
                }
            })
            return result
        }

        private fun IrConstructorCall.referencesVariablesDeclaredInLoops(): Boolean =
            (0 until valueArgumentsCount).any { i ->
                getValueArgument(i)!!.referencesVariablesDeclaredInLoops()
            }

        fun lambdaCapturesVariablesDeclaredInLoops(lambdaClass: IrClass): Boolean {
            val primaryConstructor = lambdaClass.primaryConstructor ?: return false
            val ctorCalls = lambdaConstructorCalls[primaryConstructor.symbol] ?: return false
            return ctorCalls.any { it.referencesVariablesDeclaredInLoops() }
        }

        fun getLambdaConstructorCalls(constructorSymbol: IrConstructorSymbol): List<IrConstructorCall> =
            lambdaConstructorCalls[constructorSymbol] ?: emptyList()
    }

    private inner class CallableReferenceClassTransformer(
        private val ctorToFactoryMap: MutableMap<IrConstructorSymbol, IrSimpleFunctionSymbol>,
        private val ctorToFreeFunctionMap: MutableMap<IrConstructorSymbol, IrSimpleFunctionSymbol>,
        private val ctorToFunctionExpressionMap: MutableMap<IrConstructorSymbol, FunctionExpressionFactory>,
        private val closureUsageAnalyser: ClosureUsageAnalyser
    ) : IrElementTransformerVoid() {

        override fun visitFile(declaration: IrFile): IrFile {
            declaration.transformChildrenVoid()
            declaration.transformDeclarationsFlat { it.transformCallableReference() }
            return declaration
        }

        override fun visitClass(declaration: IrClass): IrStatement {
            declaration.transformChildrenVoid()
            declaration.transformDeclarationsFlat { it.transformCallableReference() }
            return declaration
        }

        override fun visitScript(declaration: IrScript): IrStatement {
            declaration.transformChildrenVoid()
            declaration.statements.transformFlat { s ->
                if (s is IrDeclaration) s.transformCallableReference()
                else null
            }
            return declaration
        }

        private fun IrDeclaration.asCallableReference(): IrClass? {
            if (origin == CallableReferenceLowering.FUNCTION_REFERENCE_IMPL || origin == CallableReferenceLowering.LAMBDA_IMPL)
                return this as? IrClass
            return null
        }

        private fun IrDeclaration.transformCallableReference(): List<IrDeclaration>? {
            return asCallableReference()?.let {
                replaceWithFactory(it)
            }
        }

        private fun IrSimpleFunction.hasEs6UnsafeSuperCalls(): Boolean {
            if (!context.es6mode) return false
            var result = false
            body?.acceptChildrenVoid(
                object : IrElementVisitorVoid {
                    override fun visitElement(element: IrElement) {
                        element.acceptChildrenVoid(this)
                    }

                    override fun visitCall(expression: IrCall) {
                        if (expression.superQualifierSymbol != null) {
                            result = true
                        } else {
                            expression.acceptChildrenVoid(this)
                        }
                    }
                }
            )
            return result
        }

        private fun replaceWithFactory(lambdaClass: IrClass): List<IrDeclaration> {
            val lambdaInfo = LambdaInfo(lambdaClass)

            return if (lambdaClass.origin == CallableReferenceLowering.LAMBDA_IMPL && !lambdaInfo.isSuspendLambda) {
                if (lambdaClass.fields.none()) {
                    // Optimization:
                    // If the lambda has no context, we lift it, i.e. instead of generating an anonymous function,
                    // we generate a named free function. The usage of the lambda is then replaced with a reference to the free function.
                    // This allows us to avoid allocating a new object each time the lambda is created.
                    liftLambda(ctorToFreeFunctionMap, lambdaInfo)
                } else if (
                    generateInlineAnonymousFunctions
                    // If the lambda constructor is called from more than one place, don't inline.
                    && closureUsageAnalyser.getLambdaConstructorCalls(lambdaClass.primaryConstructor!!.symbol).size == 1
                    // In-line anonymous functions that capture variables declared in loops are dangerous.
                    // See https://stackoverflow.com/questions/750486/javascript-closure-inside-loops-simple-practical-example
                    && !closureUsageAnalyser.lambdaCapturesVariablesDeclaredInLoops(lambdaClass)
                    // If the lambda body contains `super`-qualified calls, don't inline, since anonymous functions cannot contain
                    // `super` calls in ES6; only direct class members.
                    && !lambdaInfo.invokeFun.hasEs6UnsafeSuperCalls()
                ) {
                    // If possible, generate anonymous functions in-line instead of factories of anonymous functions.
                    buildFunctionExpression(ctorToFunctionExpressionMap, lambdaInfo)
                } else {
                    buildFactoryFunction(ctorToFactoryMap, lambdaInfo)
                }
            } else {
                buildFactoryFunction(ctorToFactoryMap, lambdaInfo)
            }.onEach { it.parent = lambdaClass.parent }
        }
    }

    /**
     * [lambdaContextMapping] — a mapping from lambda class fields to the values outside the lambda that the lambda captures.
     *
     * [outerReceiverMapping] — a mapping from lambda class fields to outer class receivers, in case the lambda is inside an inner class,
     * and it captures the outer classes.
     */
    private fun inlineLambdaBody(
        lambdaDeclaration: IrSimpleFunction,
        invokeFun: IrSimpleFunction,
        invokeMapping: Map<IrValueParameterSymbol, IrValueParameterSymbol>,
        lambdaContextMapping: Map<IrFieldSymbol, IrValueSymbol>,
        outerReceiverMapping: Map<IrFieldSymbol, IrGetField> = emptyMap()
    ): IrBlockBody {
        val oldBody = invokeFun.body as? IrBlockBody
            ?: compilationException(
                "invoke() method has to have a body",
                invokeFun
            )
        // Don't use offsets from oldBody, use offsets from invokeFun instead. This is more precise.
        val body = context.irFactory.createBlockBody(invokeFun.startOffset, invokeFun.endOffset, oldBody.statements)

        fun IrExpression.getValue(d: IrValueSymbol): IrExpression = IrGetValueImpl(startOffset, endOffset, d)
        fun IrExpression.getCastedValue(d: IrValueSymbol, toType: IrType): IrExpression =
            IrTypeOperatorCallImpl(startOffset, endOffset, toType, IrTypeOperator.IMPLICIT_CAST, toType, getValue(d))

        // TODO: remap type parameters???
        body.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitGetField(expression: IrGetField): IrExpression {
                expression.transformChildrenVoid()

                lambdaContextMapping[expression.symbol]?.let {
                    return expression.getValue(it)
                }

                outerReceiverMapping[expression.symbol]?.let {
                    return IrGetFieldImpl(
                        expression.startOffset,
                        expression.endOffset,
                        it.symbol,
                        it.type,
                        it.receiver?.deepCopyWithSymbols()
                    )
                }

                return expression
            }

            override fun visitGetValue(expression: IrGetValue): IrExpression {
                expression.transformChildrenVoid()
                val parameter = invokeMapping[expression.symbol] ?: return expression
                val parameterType = invokeFun.valueParameters[parameter.owner.index].type
                return expression.getCastedValue(parameter, parameterType)
            }

            override fun visitReturn(expression: IrReturn): IrExpression {
                expression.transformChildrenVoid()
                if (expression.returnTargetSymbol != invokeFun.symbol) return expression
                return expression.run {
                    IrReturnImpl(startOffset, endOffset, type, lambdaDeclaration.symbol, value)
                }
            }
        })

        // Fix parents of declarations inside body
        body.patchDeclarationParents(lambdaDeclaration)

        if (invokeFun.returnType.isUnit()) {
            val unitValue = JsIrBuilder.buildGetObjectValue(context.irBuiltIns.unitType, context.irBuiltIns.unitClass)
            // Set both offsets of the IrReturn to body.previousOffset so that a breakpoint set at the closing brace of a lambda expression
            // could be hit.
            body.statements.add(
                IrReturnImpl(
                    body.endOffset.previousOffset,
                    body.endOffset.previousOffset,
                    context.irBuiltIns.nothingType,
                    lambdaDeclaration.symbol,
                    unitValue
                )
            )
        }

        return body
    }

    private fun buildLambdaBody(instance: IrVariable, lambdaDeclaration: IrSimpleFunction, invokeFun: IrSimpleFunction): IrBlockBody {
        val invokeExpression = IrCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            invokeFun.returnType,
            invokeFun.symbol,
            0,
            invokeFun.valueParameters.size,
            JsStatementOrigins.EXPLICIT_INVOKE,
            null
        )

        fun getValue(d: IrValueDeclaration): IrExpression = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, d.symbol)

        invokeExpression.dispatchReceiver = getValue(instance)
        for ((i, vp) in lambdaDeclaration.valueParameters.withIndex()) {
            invokeExpression.putValueArgument(i, getValue(vp))
        }

        return context.irFactory.createBlockBody(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            listOf(
                IrReturnImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    context.irBuiltIns.nothingType,
                    lambdaDeclaration.symbol,
                    invokeExpression
                )
            )
        )
    }

    /**
     * Returns a mapping from a lambda class field to the corresponding captured value.
     *
     * [remapVP] accepts a lambda constructor's value parameter symbol, for which it should return the corresponding captured value.
     */
    private fun <T> remapCapturedFields(
        lambdaConstructor: IrConstructor,
        remapVP: (IrValueParameterSymbol) -> T?
    ): Map<IrFieldSymbol, T> {
        val statements = lambdaConstructor.body?.statements
            ?: compilationException(
                "Expecting Body for function ref constructor",
                lambdaConstructor
            )
        return statements
            .asSequence()
            .filterIsInstance<IrSetField>()
            .filter { it.origin == LoweredStatementOrigins.STATEMENT_ORIGIN_INITIALIZER_OF_FIELD_FOR_CAPTURED_VALUE }
            .mapNotNull { irSetField ->
                remapVP(irSetField.value.cast<IrGetValue>().symbol.cast())?.let {
                    irSetField.symbol to it
                }
            }
            .toMap()
    }

    private fun extractReferenceReflectionName(getter: IrSimpleFunction): IrExpression {
        val body = getter.body?.cast<IrBlockBody>()
            ?: compilationException(
                "Expected body",
                getter
            )
        val statements = body.statements

        val returnStmt = statements[0] as IrReturn
        return returnStmt.value
    }

    private class LambdaInfo(val lambdaClass: IrClass) {
        val invokeFun = lambdaClass.invokeFun!!
        val superInvokeFun = invokeFun.overriddenSymbols.first { it.owner.isSuspend == invokeFun.isSuspend }.owner
        val isSuspendLambda = invokeFun.overriddenSymbols.any { it.owner.isSuspend }

        fun createOldToNewInvokeParametersMapping(lambdaDeclaration: IrSimpleFunction) =
            invokeFun.valueParameters.associateBy({ it.symbol }) { lambdaDeclaration.valueParameters[it.index].symbol }

        fun lambdaInnerClasses() =
            lambdaClass.declarations.filter { it is IrClass || (it is IrSimpleFunction && it.dispatchReceiverParameter == null) }
    }

    private fun buildFactoryBody(
        factoryFunction: IrSimpleFunction,
        newDeclarations: MutableList<IrDeclaration>,
        lambdaInfo: LambdaInfo
    ): IrBlockBody {
        val superClass = lambdaInfo.superInvokeFun.parentAsClass
        val lambdaName = Name.identifier("${lambdaInfo.lambdaClass.name.asString()}\$lambda")

        val lambdaDeclaration =
            createLambdaDeclaration(lambdaInfo.invokeFun, lambdaName, factoryFunction, lambdaInfo.superInvokeFun)

        val statements = ArrayList<IrStatement>(4)
        val constructor = lambdaInfo.lambdaClass.declarations.firstNotNullOf { it as? IrConstructor }

        if (lambdaInfo.isSuspendLambda) {
            // Due to suspend lambda is a class itself it's not easy to inline it correctly and moreover I see no reason to do so
            val lambdaType = lambdaInfo.lambdaClass.defaultType
            val instanceVal = JsIrBuilder.buildVar(lambdaType, factoryFunction, "i").apply {
                val newCtorCall = IrConstructorCallImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    lambdaType,
                    constructor.symbol,
                    lambdaInfo.lambdaClass.typeParameters.size,
                    constructor.typeParameters.size,
                    constructor.valueParameters.size
                )

                for ((i, vp) in factoryFunction.valueParameters.withIndex()) {
                    newCtorCall.putValueArgument(i, IrGetValueImpl(startOffset, endOffset, vp.type, vp.symbol))
                }

                initializer = newCtorCall
            }

            statements.add(instanceVal)

            lambdaDeclaration.body = buildLambdaBody(instanceVal, lambdaDeclaration, lambdaInfo.invokeFun)

            newDeclarations.add(lambdaInfo.lambdaClass)
        } else {
            val fieldToParameterMapping = remapCapturedFields(constructor) { factoryFunction.valueParameters[it.owner.index].symbol }
            val oldToNewInvokeParametersMapping = lambdaInfo.createOldToNewInvokeParametersMapping(lambdaDeclaration)
            lambdaDeclaration.body =
                inlineLambdaBody(lambdaDeclaration, lambdaInfo.invokeFun, oldToNewInvokeParametersMapping, fieldToParameterMapping)

            // lambdas can contain another lambdas and local classes in so let's not lose them
            newDeclarations.addAll(lambdaInfo.lambdaInnerClasses())
        }

        val lambdaType = lambdaInfo.lambdaClass.superTypes.single { it.classifierOrNull === superClass.symbol }
        val functionExpression = lambdaInfo.lambdaClass.run {
            IrFunctionExpressionImpl(startOffset, endOffset, lambdaType, lambdaDeclaration, JsStatementOrigins.CALLABLE_REFERENCE_CREATE)
        }

        val nameGetter = context.mapping.reflectedNameAccessor[lambdaInfo.lambdaClass]

        if (nameGetter != null || lambdaDeclaration.isSuspend) {
            val tmpVar = JsIrBuilder.buildVar(functionExpression.type, factoryFunction, "l", initializer = functionExpression)
            statements.add(tmpVar)

            if (nameGetter != null) {
                statements.add(setDynamicProperty(tmpVar.symbol, Namer.KCALLABLE_NAME, extractReferenceReflectionName(nameGetter)))
            }

            if (lambdaDeclaration.isSuspend) {
                statements.add(
                    setDynamicProperty(
                        tmpVar.symbol, Namer.KCALLABLE_ARITY,
                        IrConstImpl.int(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            context.irBuiltIns.intType,
                            lambdaDeclaration.valueParameters.size
                        )
                    )
                )
            }

            statements.add(
                JsIrBuilder.buildReturn(
                    factoryFunction.symbol,
                    JsIrBuilder.buildGetValue(tmpVar.symbol),
                    context.irBuiltIns.nothingType
                )
            )
        } else {
            statements.add(JsIrBuilder.buildReturn(factoryFunction.symbol, functionExpression, context.irBuiltIns.nothingType))
        }

        return context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, statements)
    }

    private fun createLambdaDeclaration(
        invokeFun: IrSimpleFunction,
        lambdaName: Name,
        parent: IrDeclarationParent,
        superInvokeFun: IrSimpleFunction
    ): IrSimpleFunction {
        val anyNType = context.irBuiltIns.anyNType
        val lambdaDeclaration = context.irFactory.buildFun {
            startOffset = invokeFun.startOffset
            endOffset = invokeFun.endOffset
            // Since box/unbox is done on declaration side in case of suspend function use the specified type
            returnType = if (invokeFun.isSuspend) invokeFun.returnType else anyNType
            visibility = DescriptorVisibilities.LOCAL
            name = lambdaName
            isSuspend = invokeFun.isSuspend
        }

        lambdaDeclaration.parent = parent

        lambdaDeclaration.valueParameters = superInvokeFun.valueParameters.memoryOptimizedMapIndexed { id, vp ->
            val originalValueParameter = invokeFun.valueParameters[id]
            vp.copyTo(
                irFunction = lambdaDeclaration,
                origin = originalValueParameter.origin,
                startOffset = originalValueParameter.startOffset,
                endOffset = originalValueParameter.endOffset,
                name = originalValueParameter.name,
                type = anyNType,
            )
        }
        return lambdaDeclaration
    }

    private fun buildFactoryFunction(
        ctorToFactoryMap: MutableMap<IrConstructorSymbol, IrSimpleFunctionSymbol>,
        lambdaInfo: LambdaInfo
    ): List<IrDeclaration> {
        val newDeclarations = mutableListOf<IrDeclaration>()
        val constructor = lambdaInfo.lambdaClass.constructors.single()

        val factoryDeclaration = context.irFactory.stageController.restrictTo(lambdaInfo.lambdaClass) {
            context.irFactory.buildFun {
                startOffset = lambdaInfo.lambdaClass.startOffset
                endOffset = lambdaInfo.lambdaClass.endOffset
                visibility = lambdaInfo.lambdaClass.visibility
                returnType = lambdaInfo.lambdaClass.defaultType
                name = lambdaInfo.lambdaClass.name
                origin = JsStatementOrigins.FACTORY_ORIGIN
            }
        }

        factoryDeclaration.valueParameters = constructor.valueParameters.memoryOptimizedMap { it.copyTo(factoryDeclaration) }
        factoryDeclaration.typeParameters = constructor.typeParameters.memoryOptimizedMap {
            it.copyToWithoutSuperTypes(factoryDeclaration).also { tp ->
                // TODO: make sure it is done well
                tp.superTypes = tp.superTypes memoryOptimizedPlus it.superTypes
            }
        }

        factoryDeclaration.body = buildFactoryBody(factoryDeclaration, newDeclarations, lambdaInfo)

        newDeclarations.add(factoryDeclaration)
        ctorToFactoryMap[constructor.symbol] = factoryDeclaration.symbol

        return newDeclarations
    }

    /**
     * Builds a declaration of the function expression that the lambda will be lowered to.
     * The declaration doesn't have a body — the body will be generated at a later stage, when we visit lambda constructor calls.
     */
    private fun buildFunctionExpression(
        ctorToFunctionExpressionMap: MutableMap<IrConstructorSymbol, FunctionExpressionFactory>,
        lambdaInfo: LambdaInfo
    ): List<IrDeclaration> {
        val lambdaDeclaration = createLambdaDeclaration(
            lambdaInfo.invokeFun,
            lambdaInfo.lambdaClass.name,
            lambdaInfo.lambdaClass.parent,
            lambdaInfo.superInvokeFun
        )

        val constructor = lambdaInfo.lambdaClass.constructors.single()

        ctorToFunctionExpressionMap[constructor.symbol] =
            FunctionExpressionFactory(lambdaDeclaration, constructor, lambdaInfo)

        // lambdas can contain another lambdas and local classes in so let's not lose them
        return lambdaInfo.lambdaInnerClasses()
    }

    /**
     * Replaces a contextless lambda class with a free function.
     */
    private fun liftLambda(
        ctorToFreeFunctionMap: MutableMap<IrConstructorSymbol, IrSimpleFunctionSymbol>,
        lambdaInfo: LambdaInfo
    ): List<IrDeclaration> {
        val constructor = lambdaInfo.lambdaClass.constructors.single()
        val newDeclarations = mutableListOf<IrDeclaration>()
        val freeFunctionDeclaration = createLambdaDeclaration(
            lambdaInfo.invokeFun,
            lambdaInfo.lambdaClass.name,
            lambdaInfo.lambdaClass.parent,
            lambdaInfo.superInvokeFun
        )

        freeFunctionDeclaration.body = inlineLambdaBody(
            freeFunctionDeclaration,
            lambdaInfo.invokeFun,
            lambdaInfo.createOldToNewInvokeParametersMapping(freeFunctionDeclaration),
            emptyMap()
        )

        newDeclarations.add(freeFunctionDeclaration)

        // lambdas can contain another lambdas and local classes in so let's not lose them
        newDeclarations.addAll(lambdaInfo.lambdaInnerClasses())

        ctorToFreeFunctionMap[constructor.symbol] = freeFunctionDeclaration.symbol

        return newDeclarations
    }

    private fun setDynamicProperty(r: IrValueSymbol, property: String, value: IrExpression): IrStatement {
        return IrDynamicOperatorExpressionImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.unitType, IrDynamicOperator.EQ).apply {
            receiver = IrDynamicMemberExpressionImpl(startOffset, endOffset, context.dynamicType, property, JsIrBuilder.buildGetValue(r))
            arguments += value
        }
    }
}
