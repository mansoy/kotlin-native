package org.jetbrains.kotlin.backend.common.ir.cfg

//-----------------------------------------------------------------------------//

sealed class Type {
    abstract val byteSize: Int

    object boolean: Type() { override val byteSize: Int get() = 1 }
    object byte   : Type() { override val byteSize: Int get() = 2 }
    object short  : Type() { override val byteSize: Int get() = 2 }
    object int    : Type() { override val byteSize: Int get() = 4 }
    object long   : Type() { override val byteSize: Int get() = 8 }
    object float  : Type() { override val byteSize: Int get() = 4 }
    object double : Type() { override val byteSize: Int get() = 8 }
    object char   : Type() { override val byteSize: Int get() = 2 }
    class ptr<out T>(val value: T) : Type() { override val byteSize: Int get() = 8 }

    override fun toString() = asString()
}

//--- Predefined types --------------------------------------------------------//

val TypeUnit     = Type.ptr("unit")             // Invalid pointer
val TypeString   = Type.ptr("string")           // Pointer to string constant
val TypeFunction = Type.ptr("function")         // Pointer to function
val TypeBlock    = Type.ptr("block")            // Pointer to basic block
val TypePtr      = Type.ptr("ptr")              // Pointer to any object we know nothing about

//--- Type usage examples -----------------------------------------------------//

/*
 42                          -> Constant(Type.int, 42)
 "Hello world"               -> Constant(TypeString, "Hello world")
 foo()                       -> Constant(TypeFunction, "foo")

 isOn: Boolean               -> Variable(Type.boolean, "isOn")
 obj: A                      -> Variable(Type.ptr(Klass("A")), "obj")
 body: (a: Int) -> Unit      -> Variable(TypeFunction, "body")
*/
