@file:kotlin.native.internal.objc.BindClassToObjCName(Foo::class, "4main3FooC")
@file:kotlin.native.internal.objc.BindClassToObjCName(Foo.Nested::class, "4main3FooC6NestedC")

import kotlin.native.internal.ExportedBridge
import kotlinx.cinterop.*

@ExportedBridge("Foo_Nested_init_allocate")
public fun Foo_Nested_init_allocate(): kotlin.native.internal.NativePtr {
    val _result = kotlin.native.internal.createUninitializedInstance<Foo.Nested>()
    return kotlin.native.internal.ref.createRetainedExternalRCRef(_result)
}

@ExportedBridge("Foo_Nested_init_initialize__TypesOfArguments__Swift_UInt__")
public fun Foo_Nested_init_initialize__TypesOfArguments__Swift_UInt__(__kt: kotlin.native.internal.NativePtr): Unit {
    val ____kt = kotlin.native.internal.ref.dereferenceExternalRCRef(__kt)
    kotlin.native.internal.initInstance(____kt, Foo.Nested())
}

@ExportedBridge("__root___Foo_init_allocate")
public fun __root___Foo_init_allocate(): kotlin.native.internal.NativePtr {
    val _result = kotlin.native.internal.createUninitializedInstance<Foo>()
    return kotlin.native.internal.ref.createRetainedExternalRCRef(_result)
}

@ExportedBridge("__root___Foo_init_initialize__TypesOfArguments__Swift_UInt__")
public fun __root___Foo_init_initialize__TypesOfArguments__Swift_UInt__(__kt: kotlin.native.internal.NativePtr): Unit {
    val ____kt = kotlin.native.internal.ref.dereferenceExternalRCRef(__kt)
    kotlin.native.internal.initInstance(____kt, Foo())
}

@ExportedBridge("__root___ext__TypesOfArguments__main_Foo__")
public fun __root___ext__TypesOfArguments__main_Foo__(self: kotlin.native.internal.NativePtr): Unit {
    val __self = kotlin.native.internal.ref.dereferenceExternalRCRef(self) as Foo
    __self.ext()
}

@ExportedBridge("__root___extProp_get__TypesOfArguments__main_Foo__")
public fun __root___extProp_get__TypesOfArguments__main_Foo__(self: kotlin.native.internal.NativePtr): Unit {
    val __self = kotlin.native.internal.ref.dereferenceExternalRCRef(self) as Foo
    __self.extProp
}

