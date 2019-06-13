fun main(args: Array<String>) {
    class LocalClass {
        fun f() {
        }

        @Suppress("unused")
        private fun fNoWarn() {}

        val p = 5
    }

    @Suppress("unused")
    class OtherClass {
        private fun fNoWarn() {}
    }


    LocalClass().f()
    LocalClass().p
}

@Suppress("unused")
fun other() {
    class OtherClass {
        private fun fNoWarn() {}
    }
}