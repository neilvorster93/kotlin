class Klass {
    private fun unusedFun() {
    }

    @Suppress("unused")
    private fun unusedNoWarn() {

    }
}

@Suppress("unused")
class OtherKlass {
    private fun unusedNoWarn() {

    }
}

fun main(args: Array<String>) {
    Klass()
    OtherKlass()
}