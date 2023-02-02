package org.gristle.klox

class AstPrinter : Expr.Visitor<String>, Stmt.Visitor<String> {
    fun print(expr: Expr): String = expr.accept(this)

    override fun visitAssignExpr(expr: Expr.Assign): String =
        parenthesize2("=", expr.name.lexeme, expr.value)

    override fun visitBinaryExpr(expr: Expr.Binary): String =
        parenthesize(expr.operator.lexeme, expr.left, expr.right)

    override fun visitCallExpr(expr: Expr.Call): String =
        parenthesize2("call", expr.callee, expr.arguments)

    override fun visitGetExpr(expr: Expr.Get): String {
        TODO("Not yet implemented")
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): String =
        parenthesize("group", expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): String = expr.value?.toString() ?: "nil"

    override fun visitLogicalExpr(expr: Expr.Logical): String =
        parenthesize(expr.operator.lexeme, expr.left, expr.right)

    override fun visitSetExpr(expr: Expr.Set): String {
        TODO("Not yet implemented")
    }

    override fun visitThisExpr(expr: Expr.This): String {
        TODO("Not yet implemented")
    }

    override fun visitUnaryExpr(expr: Expr.Unary): String =
        parenthesize(expr.operator.lexeme, expr.right)

    override fun visitVariableExpr(expr: Expr.Variable): String = expr.name.lexeme

    override fun visitBlockStmt(stmt: Stmt.Block): String = buildString {
        append("(block ")
        stmt.statements.forEach { append(it.accept(this@AstPrinter)) }
        append(")")
    }

    override fun visitClassStmt(stmt: Stmt.Class): String {
        return parenthesize2(stmt.name.lexeme, stmt.methods)
    }

    override fun visitIfStmt(stmt: Stmt.If): String = if (stmt.elseBranch == null) {
        parenthesize2("if", stmt.condition, stmt.thenBranch)
    } else {
        parenthesize2("if-else", stmt.condition, stmt.thenBranch, stmt.elseBranch)
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression): String =
        parenthesize(";", stmt.expression)

    override fun visitFunctionStmt(stmt: Stmt.Function): String = buildString {
        append("(fun ${stmt.name.lexeme}(")

        append(stmt.params.joinToString(", ") { it.lexeme })

        append(")")
    }

    override fun visitPrintStmt(stmt: Stmt.Print): String =
        parenthesize("print", stmt.expression)

    override fun visitReturnStmt(stmt: Stmt.Return): String =
        if (stmt.value == null) {
            "(return)"
        } else {
            parenthesize("return", stmt.value)
        }

    override fun visitVarStmt(stmt: Stmt.Var): String = if (stmt.initializer == null) {
        parenthesize2("var", stmt.name)
    } else {
        parenthesize2("var", stmt.name, "=", stmt.initializer)
    }

    override fun visitWhileStmt(stmt: Stmt.While): String =
        parenthesize2("while", stmt.condition, stmt.body)

    private fun parenthesize(name: String, vararg exprs: Expr) = buildString {
        append("($name")
        exprs.forEach { expr -> append(" ${expr.accept(this@AstPrinter)}") }
        append(")")
    }

    private fun parenthesize2(name: String, vararg parts: Any?) = buildString {
        append("($name")
        transform(parts)
        append(")")
    }

    private fun StringBuilder.transform(parts: Array<out Any?>) {
        parts.forEach { part ->
            append(" ")
            when (part) {
                is Expr -> append(part.accept(this@AstPrinter))
                is Stmt -> append(part.accept(this@AstPrinter))
                is Token -> append(part.lexeme)
                is List<*> -> transform(part.toTypedArray())
                else -> append(part)
            }
        }
    }
}
