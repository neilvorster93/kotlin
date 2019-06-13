private interface Interface<T>
private interface Interface2<T>
private class A<T> : Interface2<T>

private abstract class Abstract<T>
private abstract class Abstract2<T>
private class B<T> : Abstract2<T>()

private sealed class Sealed<T>
private sealed class Sealed2<T>
private class C<T> : Sealed2<T>()

private open class Open<T>
private open class Open2<T>
private class D<T> : Open2<T>()

fun main(args: Array<String>) {
    Interface::class
    Interface2::class
    A::class

    Abstract::class
    Abstract2::class
    B::class

    Sealed::class
    Sealed2::class
    C::class

    Open::class
    Open2::class
    D::class
}