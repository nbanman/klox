package org.gristle.klox


class Parser(private val tokens: List<Token>) {
    private class ParseError : RuntimeException()

    private var current = 0

    // todo deviation from Java impl. Not adding a null stmt, need to error catch somehow. All this 
    // happens around 8.2.2
    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }
        return statements
    }

    private fun declaration(): Stmt? {
        return try {
            when {
                match(TokenType.CLASS) -> classDeclaration()
                match(TokenType.FUN) -> function("function")
                match(TokenType.VAR) -> varDeclaration()
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun classDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect class name.")

        val superclass = if (match(TokenType.LESS)) {
            consume(TokenType.IDENTIFIER, "Expect superclass name.")
            Expr.Variable(previous())
        } else {
            null
        }

        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<Stmt.Function>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"))
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")

        return Stmt.Class(name, superclass, methods)
    }

    private fun function(kind: String): Stmt.Function {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")

        consume(TokenType.LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")
        var initializer: Expr? = null
        if (match(TokenType.EQUAL)) {
            initializer = expression()
        }

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.")
        return Stmt.Var(name, initializer)
    }

    private fun statement(): Stmt = when {
        match(TokenType.FOR) -> forStatement()
        match(TokenType.IF) -> ifStatement()
        match(TokenType.PRINT) -> printStatement()
        match(TokenType.RETURN) -> returnStatement()
        match(TokenType.WHILE) -> whileStatement()
        match(TokenType.LEFT_BRACE) -> Stmt.Block(block())
        else -> expressionStatement()
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        var value: Expr? = null
        if (!check(TokenType.SEMICOLON)) {
            value = expression()
        }
        consume(TokenType.SEMICOLON, "Expect ';' after return value.")
        return Stmt.Return(keyword, value)
    }

    private fun forStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.")
        val initializer = when {
            match(TokenType.SEMICOLON) -> null
            match(TokenType.VAR) -> varDeclaration()
            else -> expressionStatement()
        }
        var condition = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")
        val increment = if (!check(TokenType.RIGHT_PAREN)) expression() else null
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")
        var body = statement()

        if (increment != null) {
            body = Stmt.Block(listOf(body, Stmt.Expression(increment)))
        }

        if (condition == null) condition = Expr.Literal(true)

        body = Stmt.While(condition, body)

        if (initializer != null) {
            body = Stmt.Block(listOf(initializer, body))
        }

        return body
    }

    private fun whileStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")
        val body = statement()
        return Stmt.While(condition, body)
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.")

        val thenBranch = statement()
        val elseBranch = if (match(TokenType.ELSE)) statement() else null

        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            declaration()?.let { statements.add(it) } // TODO check if this null-handling makes sense 8.5.2
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Stmt.Print(value)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Stmt.Expression(expr)
    }

    private inline fun expressBinary(
        vararg tokens: TokenType,
        higherPrecedence: () -> Expr,
    ): Expr {
        var expr = higherPrecedence()
        while (match(*tokens)) {
            val operator = previous()
            val right = higherPrecedence()
            expr = Expr.Binary(expr, operator, right)
        }
        return expr
    }

    private inline fun expressLogical(
        vararg tokens: TokenType,
        higherPrecedence: () -> Expr,
    ): Expr {
        var expr = higherPrecedence()
        while (match(*tokens)) {
            val operator = previous()
            val right = higherPrecedence()
            expr = Expr.Logical(expr, operator, right)
        }
        return expr
    }

    private fun match(vararg types: TokenType): Boolean {
        return if (peek().type in types) {
            advance()
            true
        } else false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd() = peek().type == TokenType.EOF

    private fun peek() = tokens[current]

    private fun previous() = tokens[current - 1]

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val expr = or()

        if (match(TokenType.EQUAL)) {
            val equals = previous()
            val value = assignment()

            when (expr) {
                is Expr.Variable -> return Expr.Assign(expr.name, value)
                is Expr.Get -> return Expr.Set(expr.obj, expr.name, value)
                else -> error(equals, "Invalid assignment target.")
            }
        }
        return expr
    }

    private fun or(): Expr = expressLogical(TokenType.OR) { and() }

    private fun and(): Expr = expressLogical(TokenType.AND) { equality() }

    private fun equality() = expressBinary(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL) { comparison() }

    private fun comparison() = expressBinary(
        TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL
    ) { term() }

    private fun term() = expressBinary(TokenType.MINUS, TokenType.PLUS) { factor() }

    private fun factor() = expressBinary(TokenType.STAR, TokenType.SLASH) { unary() }

    private fun unary(): Expr {
        return if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            Expr.Unary(operator, right)
        } else {
            call()
        }
    }

    private fun call(): Expr {
        var expr = primary()

        while (true) {
            expr = when {
                match(TokenType.LEFT_PAREN) -> finishCall(expr)
                match(TokenType.DOT) -> {
                    val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.")
                    Expr.Get(expr, name)
                }

                else -> break
            }
        }
        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments.")
                }
                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }

        val paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")
        return Expr.Call(callee, paren, arguments)
    }

    private fun primary(): Expr = when {
        match(TokenType.FALSE) -> Expr.Literal(false)
        match(TokenType.TRUE) -> Expr.Literal(true)
        match(TokenType.NIL) -> Expr.Literal(null)
        match(TokenType.NUMBER, TokenType.STRING) -> Expr.Literal(previous().literal)
        match(TokenType.SUPER) -> {
            val keyword = previous()
            consume(TokenType.DOT, "Expect '.' after 'super'.")
            val method = consume(TokenType.IDENTIFIER, "Expect superclass method name.")
            Expr.Super(keyword, method)
        }

        match(TokenType.THIS) -> Expr.This(previous())
        match(TokenType.IDENTIFIER) -> Expr.Variable(previous())
        match(TokenType.LEFT_PAREN) -> Expr.Grouping(expression()).also {
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
        }

        else -> throw error(peek(), "Expect expression.")
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun error(token: Token, message: String): ParseError {
        Lox.error(token, message)
        return ParseError()
    }

    private fun synchronize() {
        val statementBoundaries = listOf(
            TokenType.CLASS, TokenType.FUN, TokenType.VAR, TokenType.FOR, TokenType.IF,
            TokenType.WHILE, TokenType.PRINT, TokenType.RETURN,
        )

        advance()

        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON || peek().type in statementBoundaries) return
            advance()
        }
    }
}