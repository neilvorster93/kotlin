// "Safe delete 'NamedObject'" "true"
class TestClass{
    private object NamedObject<caret> {
        const val CONST = "abc"
    }
}