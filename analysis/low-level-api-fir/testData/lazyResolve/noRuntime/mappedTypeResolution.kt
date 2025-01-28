// SKIP_WHEN_OUT_OF_CONTENT_ROOT

// MODULE: lib
// ALLOW_KOTLIN_PACKAGE
// FILE: lib.kt
package kotlin.collections

interface Iterator<out T> {
    operator fun next(): T
    operator fun hasNext(): Boolean
}

interface MutableIterator<out T> : Iterator<T> {
    fun remove(): Unit
}

interface Iterable<out T> {
    operator fun iterator(): Iterator<T>
}

interface MutableIterable<out T> : kotlin.collections.Iterable<T> {
    fun iterator(): MutableIterator<T>
}

interface Collection<out E> : Iterable<E> {
    override fun iterator(): Iterator<E>
}


// MODULE: main(lib)
// FILE: main.kt
package main

fun fo<caret>o(collection: Collection<String>) {
    collection.iterator()
}