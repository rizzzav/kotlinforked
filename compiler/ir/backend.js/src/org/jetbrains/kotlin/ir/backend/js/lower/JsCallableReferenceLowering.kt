/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.lower.AbstractFunctionReferenceLowering
import org.jetbrains.kotlin.backend.common.reflectedNameAccessor
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsCommonBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsStatementOrigins
import org.jetbrains.kotlin.ir.backend.js.utils.compileSuspendAsJsGenerator
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.memoryOptimizedPlus

class JsCallableReferenceLowering(context: JsCommonBackendContext) : AbstractFunctionReferenceLowering<JsCommonBackendContext>(context) {
    private val IrRichFunctionReference.isLambda: Boolean
        get() = reflectionTargetSymbol == null

    private val IrRichFunctionReference.shouldAddContinuation: Boolean
        get() = isLambda && invokeFunction.isSuspend && context.compileSuspendAsJsGenerator

    private val IrRichFunctionReference.isKReference: Boolean
        get() = type.let { it.isKFunction() || it.isKSuspendFunction() }

    private val nothingType = context.irBuiltIns.nothingType
    private val stringType = context.irBuiltIns.stringType

    override fun IrBuilderWithScope.generateSuperClassConstructorCall(
        superClassType: IrType,
        functionReference: IrRichFunctionReference,
    ): IrDelegatingConstructorCall {
        val superConstructor = superClassType.classOrFail.owner.primaryConstructor
            ?: compilationException("Missing primary constructor", superClassType.classOrFail.owner)
        return irDelegatingConstructorCall(superConstructor).apply {
            if (superConstructor.parameters.isNotEmpty()) {
                arguments[0] = irNull()
            }
        }
    }

    private fun StringBuilder.collectNamesForLambda(d: IrDeclarationWithName) {
        val parent = d.parent

        if (parent is IrPackageFragment) {
            append(d.name.asString())
            return
        }

        collectNamesForLambda(parent as IrDeclarationWithName)

        if (d is IrAnonymousInitializer) return

        fun IrDeclaration.isLambdaFun(): Boolean = origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA

        when {
            d.isLambdaFun() -> {
                append('$')
                if (d is IrSimpleFunction && d.isSuspend) append('s')
                append("lambda")
            }
            d.name == SpecialNames.NO_NAME_PROVIDED -> append("\$o")
            else -> {
                append('$')
                append(d.name.asString())
            }
        }
    }

    override fun getReferenceClassName(reference: IrRichFunctionReference): Name {
        val sb = StringBuilder()
        sb.collectNamesForLambda(reference.reflectionTargetSymbol?.owner ?: reference.invokeFunction)
        if (!reference.isLambda) sb.append("\$ref")
        return Name.identifier(sb.toString())
    }

    override fun getSuperClassType(reference: IrRichFunctionReference): IrType {
        return if (reference.shouldAddContinuation) {
            context.symbols.coroutineImpl.owner.defaultType
        } else {
            context.irBuiltIns.anyType
        }
    }

    override fun IrBuilderWithScope.getExtraConstructorArgument(
        parameter: IrValueParameter,
        reference: IrRichFunctionReference,
    ) = irNull()

    override fun getExtraConstructorParameters(constructor: IrConstructor, reference: IrRichFunctionReference): List<IrValueParameter> {
        if (!reference.shouldAddContinuation) return emptyList()
        return listOf(
            buildValueParameter(constructor) {
                val superContinuation = context.symbols.coroutineImpl.owner.primaryConstructor!!.parameters.single()
                name = superContinuation.name
                type = superContinuation.type
                origin = IrDeclarationOrigin.CONTINUATION
                kind = IrParameterKind.Regular
            }
        )
    }

    override fun getClassOrigin(reference: IrRichFunctionReference): IrDeclarationOrigin {
        return if (reference.isKReference || !reference.isLambda) FUNCTION_REFERENCE_IMPL else LAMBDA_IMPL
    }

    override fun getConstructorOrigin(reference: IrRichFunctionReference): IrDeclarationOrigin {
        return GENERATED_MEMBER_IN_CALLABLE_REFERENCE
    }

    override fun getInvokeMethodOrigin(reference: IrRichFunctionReference): IrDeclarationOrigin {
        return reference.invokeFunction.origin
    }

    override fun getConstructorCallOrigin(reference: IrRichFunctionReference) = JsStatementOrigins.CALLABLE_REFERENCE_CREATE

    private fun createNameProperty(clazz: IrClass, reflectionTargetSymbol: IrFunctionSymbol) {
        val superProperty = context.irBuiltIns.kCallableClass.owner.declarations
            .filterIsInstance<IrProperty>()
            .single { it.name == StandardNames.NAME }  // In K/Wasm interfaces can have fake overridden properties from Any

        val supperGetter = superProperty.getter
            ?: compilationException(
                "Expected getter for KFunction.name property",
                superProperty
            )

        val nameProperty = clazz.addProperty() {
            visibility = superProperty.visibility
            name = superProperty.name
            origin = GENERATED_MEMBER_IN_CALLABLE_REFERENCE
        }

        val getter = nameProperty.addGetter() {
            returnType = stringType
        }
        getter.overriddenSymbols = getter.overriddenSymbols memoryOptimizedPlus supperGetter.symbol
        getter.createDispatchReceiverParameterWithClassParent()

        // TODO: What name should be in case of constructor? <init> or class name?
        getter.body = context.irFactory.createBlockBody(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(
                IrReturnImpl(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET, nothingType, getter.symbol, IrConstImpl.string(
                        UNDEFINED_OFFSET, UNDEFINED_OFFSET, stringType, reflectionTargetSymbol.owner.name.asString()
                    )
                )
            )
        )

        clazz.reflectedNameAccessor = getter
    }

    override fun generateExtraMethods(functionReferenceClass: IrClass, reference: IrRichFunctionReference) {
        val reflectionTargetSymbol = reference.reflectionTargetSymbol ?: return
        if (reference.isKReference) {
            createNameProperty(functionReferenceClass, reflectionTargetSymbol)
        }
    }

    companion object {
        val LAMBDA_IMPL by IrDeclarationOriginImpl
        val FUNCTION_REFERENCE_IMPL by IrDeclarationOriginImpl
        val GENERATED_MEMBER_IN_CALLABLE_REFERENCE by IrDeclarationOriginImpl
    }
}
