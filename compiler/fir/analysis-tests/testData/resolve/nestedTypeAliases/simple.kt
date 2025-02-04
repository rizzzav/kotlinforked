// RUN_PIPELINE_TILL: BACKEND

class A

class C {
    typealias TA = A

    fun test(): TA = TA()

    open class Nested

    typealias NestedTA = Nested

    class SubNested: NestedTA()

    fun test1(): Nested = SubNested()
}
