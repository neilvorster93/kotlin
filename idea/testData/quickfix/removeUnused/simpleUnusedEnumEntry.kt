// "Safe delete 'WORLD'" "false"
enum class MyEnum {
    HELLO,
    WORLD<caret>
}

fun main() {
    MyEnum.HELLO
}