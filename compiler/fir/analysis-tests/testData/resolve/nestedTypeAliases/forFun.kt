// RUN_PIPELINE_TILL: BACKEND

class Generic<T>

class TAOwner {
    typealias GenericTA<T> = Generic<T>

    typealias FunTA = (Int, GenericTA<String>) -> Unit

    typealias GenericFunTA<T> = (T) -> Boolean

    fun testTypeAnnotationPosition(funArg: FunTA, genericFunArg: GenericFunTA<Int>) {
        funArg(1, GenericTA<String>())
        genericFunArg(1)
    }

    fun testReturnPosition(): FunTA = {i: Int, s: GenericTA<String> -> }

    fun testReturnPosition2(): GenericFunTA<String> = {s: String -> true}
}
