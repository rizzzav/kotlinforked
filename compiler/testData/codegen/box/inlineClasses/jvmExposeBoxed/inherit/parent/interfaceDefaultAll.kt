// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// JVM_DEFAULT_MODE: all
// CHECK_BYTECODE_LISTING

// FILE: IC.kt
@file:OptIn(ExperimentalStdlibApi::class)

interface A {
    @JvmExposeBoxed
    fun abstractMethod(a: UInt): String
}

class B() : A {
    override fun abstractMethod(a: UInt): String {
        if (a == 1u) return "OK"
        else return "FAIL $a"
    }
}

@JvmExposeBoxed("create")
fun createUInt(): UInt = 1u

// FILE: Main.java
public class Main {
    public String test() {
        return new B().abstractMethod(ICKt.create());
    }
}

// FILE: Box.kt
fun box(): String {
    return Main().test()
}
