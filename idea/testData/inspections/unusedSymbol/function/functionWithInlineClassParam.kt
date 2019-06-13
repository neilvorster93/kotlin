private inline class InlineClass(val x: Int)

// Unused
private fun foo(arg: InlineClass) {
    arg.x.hashCode()
}