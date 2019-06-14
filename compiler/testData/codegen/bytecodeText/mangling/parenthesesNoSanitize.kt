class `(X)` {
    fun `(Y)`() {}
}

// The [^\\u0003] expression excludes mentions in the serialized IR

// One in the file name, one in the class header, two in local variables in the constructor and the method, and one in kotlin.Metadata.d2
// 5 [^\\u0003]\(X\)

// One in the method header and one in kotlin.Metadata.d2
// 2 [^\\u0003]\(Y\)
