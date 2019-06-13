private fun outer() {
    private fun local() {

    }

    @Suppress("unused")
    private fun localNoWarn() {

    }
}

@Suppress("unused")
private fun otherFun() {
    fun localNoWarn() {

    }
}