// "Suppress unused warning if annotated by 'xxx.XXX'" "false"
// ACTION: Create test
// ACTION: Extract 'UnusedClass' from current file
// ACTION: Rename file to UnusedClass.kt
package xxx

annotation class XXX

@XXX
class <caret>UnusedClass
