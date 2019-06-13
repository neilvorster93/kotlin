// "Safe delete 'NamedObject'" "false"
// ACTION: Create test
// ACTION: Move to companion object
import TestClass.NamedObject.CONST

class TestClass{
    fun method(){
        CONST
    }

    private object NamedObject<caret> {
        const val CONST = "abc"
    }
}

