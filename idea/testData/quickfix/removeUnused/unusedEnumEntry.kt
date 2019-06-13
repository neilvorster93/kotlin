// "Safe delete 'WORLD'" "false"
// ACTION: Add names to call arguments
// ACTION: Do not show hints for current method
enum class MyEnum(val i: Int) {
    HELLO(42),
    WORLD<caret>("42"),
    E(24)
    ;

    constructor(s: String): this(42)
}

fun test() {
    MyEnum.HELLO
}