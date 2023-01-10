/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.native.interop.gen

import org.jetbrains.kotlin.native.interop.indexer.*
import kotlin.test.*

class IndexerTests : InteropTestsBase() {

    private fun IndexerResult.getObjCClass(name: String) =
            index.objCClasses.first { it.name == name }

    private fun IndexerResult.getObjCCategory(name: String) =
            index.objCCategories.first { it.name == name }

    @Test
    fun `smoke 0`() {
        val index = buildNativeIndex("includeCategories", "includeCategory0.def")
        val myClass = index.getObjCClass("MyClass")
        val myClassCategories = myClass.includedCategories.map { it.name }
        assertContains(myClassCategories, "IncludeCategory")
        assertContains(myClassCategories, "IncludeCategory2")
    }

    @Test
    fun `only specific classes include categories`() {
        val index = buildNativeIndex("includeCategories", "includeCategory0.def")
        val skipClass = index.getObjCClass("SkipClass")
        assertTrue(skipClass.includedCategories.isEmpty())
    }

    @Test
    fun `category from another header is not included`() {
        val index = buildNativeIndex("includeCategories", "includeCategory0.def")
        val myClass = index.getObjCClass("MyClass")
        val myClassCategories = myClass.includedCategories.map { it.name }
        assertContains(myClassCategories, "IncludeCategory")
        assertFalse("SkipCategory" in myClassCategories)
    }

    @Test
    fun `external category is not included into index`() {
        val dependencyIndex = buildNativeIndex("includeCategories", "includeCategory0.def")
        val index = buildNativeIndex("includeCategories", "includeCategory1.def", mockImports(dependencyIndex))

        val derivedClass = index.getObjCClass("Derived")
        val myClass = derivedClass.baseClass!!
        assertEquals("MyClass", myClass.name)
        assertFalse(myClass in index.index.objCClasses)
        val myClassCategories = myClass.includedCategories
        assertTrue(myClassCategories.isNotEmpty())
        assertTrue(myClassCategories.all { it !in index.index.objCCategories })
    }

    @Test
    fun `category is not included into dependency class`() {
        val dependencyIndex = buildNativeIndex("includeCategories", "includeCategory0.def")
        val index = buildNativeIndex("includeCategories", "includeCategory2.def", mockImports(dependencyIndex))
        val category = index.getObjCCategory("ChildCategory")
        assertFalse(category in category.clazz.includedCategories)
    }
}