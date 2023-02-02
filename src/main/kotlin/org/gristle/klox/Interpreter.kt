package org.gristle.klox

class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    class RuntimeError(val token: Token, message: String) : RuntimeException(message)

    val globals = Environment().apply {
        define("clock", object : LoxCallable {
            override fun call(interpreter: Interpreter, arguments: List<Any?>) =
                (System.currentTimeMillis().toDouble()) / 1000.0

            override fun arity(): Int = 0
        })
    }

    private var environment = globals

    private val locals: MutableMap<Expr, Int> = HashMap()

    private fun stringify(obj: Any?): String = when (obj) {
        null -> "nil"
        is Double -> {
            val text = obj.toString()
            if (text.endsWith(".0")) {
                text.substring(0, text.length - 2)
            } else {
                text
            }
        }

        else -> obj.toString()
    }

    fun interpret(statements: List<Stmt>) {
        try {
            statements.forEach { execute(it) }
        } catch (error: RuntimeError) {
            Lox.runtimeError(error)
        }
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)
        val distance = locals[expr]
        if (distance != null) {
            environment.assignAt(distance, expr.name, value)
        } else {
            globals.assign(expr.name, value)
        }
        return value
    }


    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            // arithmetic operators
            TokenType.MINUS -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a - b }
            TokenType.SLASH -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a / b }
            TokenType.STAR -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a * b }
            TokenType.PLUS -> {
                if (left is Double && right is Double) {
                    left + right
                } else if (left is String && right is String) {
                    left + right
                } else {
                    throw RuntimeError(expr.operator, "Expect two numbers or two strings.")
                }
            }

            // comparison operators
            TokenType.GREATER -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a > b }
            TokenType.GREATER_EQUAL -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a >= b }
            TokenType.LESS -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a < b }
            TokenType.LESS_EQUAL -> evaluateNumbers(expr.operator, left, right) { (a, b) -> a <= b }

            // equality operators
            TokenType.BANG_EQUAL -> !isEqual(left, right)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            else -> throw RuntimeError(expr.operator, "Expect binary operator.")
        }
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)

        val arguments = expr.arguments.map { evaluate(it) }

        return if (callee !is LoxCallable) {
            throw RuntimeError(expr.paren, "Can only call functions an classes.")
        } else if (arguments.size != callee.arity()) {
            throw RuntimeError(
                expr.paren, "Expected ${callee.arity()} arguments " +
                        "but got ${arguments.size}."
            )
        } else {
            callee.call(this, arguments)
        }
    }

    override fun visitGetExpr(expr: Expr.Get): Any? {
        val obj = evaluate(expr.obj)
        return if (obj is LoxInstance) obj[expr.name] else {
            throw RuntimeError(expr.name, "Only instances have properties.")
        }
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? = evaluate(expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): Any? = expr.value

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.left)

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left
        } else {
            if (!isTruthy(left)) return left
        }

        return evaluate(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val obj = evaluate(expr.obj)

        if (obj is LoxInstance) {
            val value = evaluate(expr.value)
            obj[expr.name] = value
            return value
        } else {
            throw RuntimeError(expr.name, "Only instances have fields.")
        }
    }

    override fun visitSuperExpr(expr: Expr.Super): Any? {
        val distance = locals[expr] ?: throw RuntimeError(expr.method, "'Super' not found.")
        val superclass = environment.getAt(distance, "super") as LoxClass // unchecked cast!

        val obj = environment.getAt(distance - 1, "this") as LoxInstance // again!!

        val method = superclass.findMethod(expr.method.lexeme)

        return method?.bind(obj) ?: throw RuntimeError(expr.method, "Method not found.")
    }

    override fun visitThisExpr(expr: Expr.This): Any? {
        return lookupVariable(expr.keyword, expr)
    }

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            TokenType.MINUS -> evaluateNumbers(expr.operator, right) { (operand) -> -operand }
            TokenType.BANG -> !isTruthy(right)
            else -> throw RuntimeError(expr.operator, "Expect '!' or '-'.")
        }
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? = lookupVariable(expr.name, expr)

    private fun lookupVariable(name: Token, expr: Expr): Any? =
        locals[expr]
            ?.let { distance -> environment.getAt(distance, name.lexeme) }
            ?: globals[name]

    private inline fun <R> evaluateNumbers(
        operator: Token,
        vararg operands: Any?,
        evaluation: (List<Double>) -> R
    ): R {
        val numbers = operands.map {
            if (it is Double) it else throw RuntimeError(operator, "Operands must be numbers.")
        }
        return evaluation(numbers)
    }

    private fun isEqual(a: Any?, b: Any?): Boolean = when {
        a == null && b == null -> true
        a == null -> false
        else -> a == b
    }

    private fun isTruthy(obj: Any?): Boolean = when (obj) {
        null -> false
        is Boolean -> obj
        else -> true
    }

    private fun evaluate(expr: Expr) = expr.accept(this)

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment(environment))
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previousEnvironment = this.environment
        try {
            this.environment = environment
            statements.forEach { execute(it) }
        } finally {
            this.environment = previousEnvironment
        }
    }

    override fun visitClassStmt(stmt: Stmt.Class) {

        val superclass: LoxClass? = stmt.superclass?.let {
            evaluate(it).let { candidate ->
                if (candidate is LoxClass) {
                    candidate
                } else {
                    throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
                }
            }
        }

        environment.define(stmt.name.lexeme, null)

        if (superclass != null) {
            environment = Environment(environment)
            environment.define("super", superclass)
        }

        val methods: MutableMap<String, LoxFunction> = HashMap()
        stmt.methods.forEach { method ->
            val isInitializer = method.name.lexeme == "init"
            val function = LoxFunction(method, environment, isInitializer)
            methods[method.name.lexeme] = function
        }

        val klass = LoxClass(stmt.name.lexeme, superclass, methods)

        if (superclass != null) {
            // we know not null because we assigned the OG non-nullable environment to enclosing 
            environment = environment.enclosing!!
        }

        environment.assign(stmt.name, klass)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val function = LoxFunction(stmt, environment, false)
        environment.define(stmt.name.lexeme, function)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch)
        }
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        var value: Any? = null
        if (stmt.value != null) value = evaluate(stmt.value)

        throw Return(value)
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        var value: Any? = null
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer)
        }

        environment.define(stmt.name.lexeme, value)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }
}