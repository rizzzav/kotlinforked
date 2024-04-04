/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/ir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

package org.jetbrains.kotlin.ir.expressions

import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.types.IrType

/**
 * Generated from: [org.jetbrains.kotlin.ir.generator.IrTree.fieldAccessExpression]
 */
abstract class IrFieldAccessExpression(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    override var symbol: IrFieldSymbol,
    var superQualifierSymbol: IrClassSymbol?,
    var origin: IrStatementOrigin?,
) : IrDeclarationReference(
    startOffset = startOffset,
    endOffset = endOffset,
    type = type,
) {
    var receiver: IrExpression? = null
}
