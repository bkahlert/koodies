package koodies

import koodies.BuilderClass.Context
import koodies.builder.Builder
import koodies.test.test
import koodies.test.testEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD
import strikt.assertions.isEqualTo

@Execution(SAME_THREAD)
class CallablesTest {

    @TestFactory
    fun `should have valid test data`() = testEach(Object()) {
        expect { function("-") }.that { isEqualTo("function(-)") }
        expect { ForeignClass().extensionFunction(1) }.that { isEqualTo("ForeignClass(id=default).function(default-1)") }
        expect { SomeClass().memberFunction(2) }.that { isEqualTo("SomeClass(id=default).function(default-2)") }
    }

    @Nested
    inner class UsingCallableMethodWithImplementation {
        inner class Host {
            val implementedFunction by callable { p1: String, p2: Int? -> p1.repeat(p2 ?: 2) }
            val implementedSamInterface by callable(Function2<String, Int?, String> { p1, p2 -> p1.repeat(p2 ?: 2) })
        }

        @TestFactory
        fun `should delegate`() = test(Host()) {
            expect { implementedFunction("test,", 3) }.that { isEqualTo("test,test,test,") }
            expect { implementedFunction("test,", null) }.that { isEqualTo("test,test,") }
            expect { implementedSamInterface("test,", 3) }.that { isEqualTo("test,test,test,") }
            expect { implementedSamInterface("test,", null) }.that { isEqualTo("test,test,") }
        }
    }

    @Nested
    inner class UsingCallableMethod {
        inner class Host {
            val delegatedFunction by callable(::function) // type-function reference (type = CallablesTestKt?)
            val delegatedExtensionFunction by callable(ForeignClass()::extensionFunction) // value-function reference
            val delegatedMemberFunction by callable(SomeClass()::memberFunction) // value-function reference
            val unboundDelegatedExtensionFunction by callable(ForeignClass::extensionFunction) // type-function reference
            val unboundDelegatedMemberFunction by callable(SomeClass::memberFunction) // type-function reference
            val delegatedBuilder by callable(BuilderClass::invoke) // value-property reference
            val delegatedBuilderInstance by callable(BuilderClass) // indirectly value-property reference
        }

        @TestFactory
        fun `should delegate`() = test(Host()) {
            expect { delegatedFunction("-", emptyArray()) }.that { isEqualTo("function(-)") }
            expect { delegatedExtensionFunction(1) }.that { isEqualTo("ForeignClass(id=default).function(default-1)") }
            expect { delegatedMemberFunction(2) }.that { isEqualTo("SomeClass(id=default).function(default-2)") }
            expect { unboundDelegatedExtensionFunction(ForeignClass("invocation-based"), 3) }
                .that { isEqualTo("ForeignClass(id=invocation-based).function(invocation-based-3)") }
            expect { unboundDelegatedMemberFunction(SomeClass("invocation-based"), 4) }
                .that { isEqualTo("SomeClass(id=invocation-based).function(invocation-based-4)") }
            expect { delegatedBuilder { count();count() } }.that { isEqualTo(2) }
            expect { delegatedBuilderInstance { count();count() } }.that { isEqualTo(2) }
        }
    }

    @Nested
    inner class UsingProvideDelegate {

        inner class Host {
            val delegatedFunction by ::function
            val delegatedExtensionFunction by ForeignClass()::extensionFunction
            val delegatedMemberFunction by SomeClass()::memberFunction
            val unboundDelegatedExtensionFunction by ForeignClass::extensionFunction
            val unboundDelegatedMemberFunction by SomeClass::memberFunction
            val delegatedBuilder by BuilderClass::invoke
            val delegatedBuilderInstance by BuilderClass
        }

        @TestFactory
        fun `should delegate using delegate provider`() = test(Host()) {
            expect { delegatedFunction("-", emptyArray()) }.that { isEqualTo("function(-)") }
            expect { delegatedExtensionFunction(1) }.that { isEqualTo("ForeignClass(id=default).function(default-1)") }
            expect { delegatedMemberFunction(2) }.that { isEqualTo("SomeClass(id=default).function(default-2)") }
            expect { unboundDelegatedExtensionFunction(ForeignClass("invocation-based"), 3) }
                .that { isEqualTo("ForeignClass(id=invocation-based).function(invocation-based-3)") }
            expect { unboundDelegatedMemberFunction(SomeClass("invocation-based"), 4) }
                .that { isEqualTo("SomeClass(id=invocation-based).function(invocation-based-4)") }
            expect { delegatedBuilder { count();count() } }.that { isEqualTo(2) }
            expect { delegatedBuilderInstance { count();count() } }.that { isEqualTo(2) }
        }
    }
}

data class ForeignClass(val id: String = "default")

fun function(id: String = "default", vararg receivers: Any): String {
    return receivers.joinToString("") { "$it." } + "function($id)"
}

fun ForeignClass.extensionFunction(argument: Int): String = function("$id-$argument", this)

class SomeClass(val id: String = "default") {
    fun memberFunction(argument: Int): String = function("$id-$argument", this)
    override fun toString(): String = asString(::id)
}

object BuilderClass : Builder<Context.() -> Unit, Int> {
    fun interface Context {
        fun count()
    }

    override fun invoke(init: Context.() -> Unit): Int {
        var counter = 0
        Context { counter++ }.apply(init)
        return counter
    }
}