@file:kotlin.native.internal.objc.BindClassToObjCName(overload.Foo::class, "22ExportedKotlinPackages8overloadO4mainE3FooC")

import kotlin.native.internal.ExportedBridge
import kotlinx.cinterop.*

@ExportedBridge("__root___foo__TypesOfArguments__Swift_Int32__")
public fun __root___foo__TypesOfArguments__Swift_Int32__(self: Int): Unit {
    val __self = self
    __self.foo()
}

@ExportedBridge("__root___foo")
public fun __root___foo(): Int {
    val _result = foo()
    return _result
}

@ExportedBridge("__root___return_any_should_append_runtime_import")
public fun __root___return_any_should_append_runtime_import(): kotlin.native.internal.NativePtr {
    val _result = return_any_should_append_runtime_import()
    return kotlin.native.internal.ref.createRetainedExternalRCRef(_result)
}

@ExportedBridge("namespace1_bar")
public fun namespace1_bar(): Int {
    val _result = namespace1.bar()
    return _result
}

@ExportedBridge("namespace1_local_functions_foo")
public fun namespace1_local_functions_foo(): Unit {
    namespace1.local_functions.foo()
}

@ExportedBridge("namespace1_main_all_args__TypesOfArguments__Swift_Bool_Swift_Int8_Swift_Int16_Swift_Int32_Swift_Int64_Swift_UInt8_Swift_UInt16_Swift_UInt32_Swift_UInt64_Swift_Float_Swift_Double_Swift_Unicode_UTF16_CodeUnit__")
public fun namespace1_main_all_args__TypesOfArguments__Swift_Bool_Swift_Int8_Swift_Int16_Swift_Int32_Swift_Int64_Swift_UInt8_Swift_UInt16_Swift_UInt32_Swift_UInt64_Swift_Float_Swift_Double_Swift_Unicode_UTF16_CodeUnit__(arg1: Boolean, arg2: Byte, arg3: Short, arg4: Int, arg5: Long, arg6: UByte, arg7: UShort, arg8: UInt, arg9: ULong, arg10: Float, arg11: Double, arg12: Char): Unit {
    val __arg1 = arg1
    val __arg2 = arg2
    val __arg3 = arg3
    val __arg4 = arg4
    val __arg5 = arg5
    val __arg6 = arg6
    val __arg7 = arg7
    val __arg8 = arg8
    val __arg9 = arg9
    val __arg10 = arg10
    val __arg11 = arg11
    val __arg12 = arg12
    namespace1.main.all_args(__arg1, __arg2, __arg3, __arg4, __arg5, __arg6, __arg7, __arg8, __arg9, __arg10, __arg11, __arg12)
}

@ExportedBridge("namespace1_main_foobar__TypesOfArguments__Swift_Int32__")
public fun namespace1_main_foobar__TypesOfArguments__Swift_Int32__(param: Int): Int {
    val __param = param
    val _result = namespace1.main.foobar(__param)
    return _result
}

@ExportedBridge("namespace2_foo__TypesOfArguments__Swift_Int32__")
public fun namespace2_foo__TypesOfArguments__Swift_Int32__(arg1: Int): Int {
    val __arg1 = arg1
    val _result = namespace2.foo(__arg1)
    return _result
}

@ExportedBridge("overload_Foo_init_allocate")
public fun overload_Foo_init_allocate(): kotlin.native.internal.NativePtr {
    val _result = kotlin.native.internal.createUninitializedInstance<overload.Foo>()
    return kotlin.native.internal.ref.createRetainedExternalRCRef(_result)
}

@ExportedBridge("overload_Foo_init_initialize__TypesOfArguments__Swift_UInt__")
public fun overload_Foo_init_initialize__TypesOfArguments__Swift_UInt__(__kt: kotlin.native.internal.NativePtr): Unit {
    val ____kt = kotlin.native.internal.ref.dereferenceExternalRCRef(__kt)
    kotlin.native.internal.initInstance(____kt, overload.Foo())
}

@ExportedBridge("overload_foo__TypesOfArguments__Swift_Int32__")
public fun overload_foo__TypesOfArguments__Swift_Int32__(arg1: Int): Int {
    val __arg1 = arg1
    val _result = overload.foo(__arg1)
    return _result
}

@ExportedBridge("overload_foo__TypesOfArguments__Swift_Double__")
public fun overload_foo__TypesOfArguments__Swift_Double__(arg1: Double): Int {
    val __arg1 = arg1
    val _result = overload.foo(__arg1)
    return _result
}

@ExportedBridge("overload_foo__TypesOfArguments__ExportedKotlinPackages_overload_Foo__")
public fun overload_foo__TypesOfArguments__ExportedKotlinPackages_overload_Foo__(arg1: kotlin.native.internal.NativePtr): Unit {
    val __arg1 = kotlin.native.internal.ref.dereferenceExternalRCRef(arg1) as overload.Foo
    overload.foo(__arg1)
}

@ExportedBridge("overload_foo__TypesOfArguments__ExportedKotlinPackages_overload_Foo_opt___")
public fun overload_foo__TypesOfArguments__ExportedKotlinPackages_overload_Foo_opt___(arg1: kotlin.native.internal.NativePtr): Unit {
    val __arg1 = if (arg1 == kotlin.native.internal.NativePtr.NULL) null else kotlin.native.internal.ref.dereferenceExternalRCRef(arg1) as overload.Foo
    overload.foo(__arg1)
}

