// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR
// WITH_REFLECT

// JVM_IR disabled until we start serializing local delegated properties

import kotlin.reflect.*
import kotlin.test.assertEquals

object Delegate {
    lateinit var property: KProperty<*>

    operator fun getValue(instance: Any?, kProperty: KProperty<*>) {
        property = kProperty
    }
}

class Foo {
    inline fun foo() {
        val x by Delegate
        x
    }
}

fun box(): String {
    Foo().foo()
    assertEquals("val x: kotlin.Unit", Delegate.property.toString())
    return "OK"
}
