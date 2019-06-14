class Owner(val name: String, val code: Int) {
    constructor(name: String): this(name, 0)
    private constructor(code: Int): this("", code)
    constructor(): this(42)
}

@test.anno.EntryPoint
private fun use(): Int {
    val owner = Owner("xyz")
    return if (owner.name != "") owner.code else -1
}