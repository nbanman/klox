package org.gristle.klox

class LoxClass(val name: String, private val superclass: LoxClass?, private val methods: Map<String, LoxFunction>) :
    LoxCallable {

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
        val instance = LoxInstance(this)
        val initializer = findMethod("init")
        initializer?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun arity(): Int {
        val initializer = findMethod("init")
        return initializer?.arity() ?: 0
    }

    override fun toString(): String {
        return "LoxClass($name)"
    }

    fun findMethod(name: String): LoxFunction? = when {
        methods.containsKey(name) -> methods.getValue(name)
        superclass != null -> superclass.findMethod(name)
        else -> null
    }
}
