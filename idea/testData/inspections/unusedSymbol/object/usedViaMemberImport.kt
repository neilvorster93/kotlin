package xxx

object O1 {
    private fun foo(){}
    fun f() {
        foo()
    }
}

object O2 {
    private fun bar(){}
}

private fun f() {
    foo()
}
