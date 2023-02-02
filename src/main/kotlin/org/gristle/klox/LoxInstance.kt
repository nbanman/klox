package org.gristle.klox

class LoxInstance(private val klass: LoxClass) {

    private val fields: MutableMap<String, Any?> = HashMap()

    override fun toString(): String {
        return "LoxInstance(${klass.name})"
    }

    operator fun get(name: Token): Any? {
        fields[name.lexeme]?.let { return it }

        val method = klass.findMethod(name.lexeme)
        method?.let { return it.bind(this) }

        throw Interpreter.RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    operator fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }
}
