plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        binaries.executable()
        browser {
            commonWebpackConfig {
                // don't auto-open the browser, it's annoying
                devServer?.open = false
            }
        }
    }
}

//plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin>().configureEach {
//    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension> {
//        yarnLockMismatchReport = org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport.NONE
//    }
//}
