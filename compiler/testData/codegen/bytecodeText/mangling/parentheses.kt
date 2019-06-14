// !SANITIZE_PARENTHESES

class `(X)` {
    fun `(Y)`(): String {
        fun foo(): String {
            return bar { baz() }
        }
        return foo()
    }

    fun baz() = "OK"
}

fun bar(p: () -> String) = p()

fun box(): String {
    return `(X)`().`(Y)`()
}

// One instance of each is in kotlin.Metadata.d2
// The [^\\u0003] expression excludes mentions in the serialized IR

// 1 [^\\u0003]\(X\)
// 1 [^\\u0003]\(Y\)
