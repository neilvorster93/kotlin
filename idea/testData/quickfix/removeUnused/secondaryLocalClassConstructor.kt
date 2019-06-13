// "Safe delete constructor" "true"
fun main() {
    class LocalClass(val number: Int) {
        private <caret>constructor(s: String) : this(s.toInt())
    }

    val l = LocalClass(42)
}