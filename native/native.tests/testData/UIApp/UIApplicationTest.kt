/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.example.project

import platform.UIKit.UIApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class UIApplicationInstance {
    @Test
    fun ensureUIApplication() {
        // Should pass the test if run properly
        assertNotNull(UIApplication.sharedApplication)
    }
}
