plugins {
    kotlin("jvm")
    id("jps-compatible")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:compiler.common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(kotlin("reflect"))

    testImplementation(libs.junit4)
    testCompileOnly(kotlinTest("junit"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

