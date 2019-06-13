// "Safe delete constructor" "true"
class Owner(val x: Int) {
    private <caret>constructor(): this(42)
}