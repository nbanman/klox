package org.gristle.klox

class Environment(val enclosing: Environment? = null) {
    private val values = HashMap<String, Any?>()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun ancestor(distance: Int): Environment? {
        var environment = this
        repeat(distance) {
            environment = environment.enclosing ?: return null // todo make sure count is accurate 11.4.1
        }
        return environment
    }

    operator fun get(name: Token): Any? = when {
        values.containsKey(name.lexeme) -> values[name.lexeme]
        enclosing != null -> enclosing[name]
        else -> throw Interpreter.RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun assign(name: Token, value: Any?): Unit = when {
        values.containsKey(name.lexeme) -> values[name.lexeme] = value
        enclosing != null -> enclosing.assign(name, value)
        else -> throw Interpreter.RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun getAt(distance: Int, name: String) = ancestor(distance)?.values?.get(name)

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance)?.values?.put(name.lexeme, value)
            ?: throw Interpreter.RuntimeError(name, "Ancestor not found at distance $distance.")
    }
}