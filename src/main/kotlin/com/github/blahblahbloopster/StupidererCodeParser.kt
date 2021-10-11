package com.github.blahblahbloopster

import com.github.blahblahbloopster.ConstantNode.Type.*
import com.github.blahblahbloopster.StupidererCodeInterpreter.Union
import java.io.File
import java.net.URL
import kotlin.system.exitProcess

class StupidererCodeParser {
    private val keywords = listOf(
        "import",
        "var",
        "new",
        "fun",  // TODO
        "if",
        "while",
    )

    private val symbols = listOf(
        '=',
        '.',
        '(',
        ')',
        ',',
        '{',
        '}'
    )

    class SyntaxError(msg: String) : Exception(msg)

    fun parse(inp: String): Node {
        val stringLiterals = mutableListOf<Pair<IntRange, String>>()
        val currentBuilder = StringBuilder()
        var lastIsEscape = false
        var inString = false
        var startIndex = 0
        for ((index, c) in inp.withIndex()) {
            if (inString) {
                if (c == '\\') {
                    lastIsEscape = !lastIsEscape
                }
                if (c == '"') {
                    if (lastIsEscape) {
                        lastIsEscape = false
                    } else {
                        inString = false
                        stringLiterals.add(Pair(startIndex..index, currentBuilder.toString()))
                        continue
                    }
                }
                currentBuilder.append(c)
                if (c != '\\') {
                    lastIsEscape = false
                }
            } else {
                if (c == '"') {
                    inString = true
                    startIndex = index
                    currentBuilder.clear()
                    lastIsEscape = false
                }
            }
        }

        class StageOneParsedLine(vararg val tokens: CharSequence) {
            override fun toString(): String {
                return "StageOneParsedLine(tokens=${tokens.contentToString()})"
            }
        }

        class Keyword(name: String) : CharSequence by name {
            override fun toString(): String = "keyword ${substring(indices)}"

            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other is CharSequence) return substring(indices) == other.substring(other.indices)
                return false
            }

            override fun hashCode(): Int {
                return super.hashCode()
            }
        }

        class Symbol(name: String) : CharSequence by name {
            override fun toString(): String = "symbol ${substring(indices)}"

            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other is CharSequence) return substring(indices) == other.substring(other.indices)
                return false
            }

            override fun hashCode(): Int {
                return super.hashCode()
            }
        }

        class Identifier(name: String) : CharSequence by name {
            override fun toString(): String = "identifier ${substring(indices)}"

            override fun equals(other: Any?): Boolean {
                if (other === this) return true
                if (other is CharSequence) return substring(indices) == other.substring(other.indices)
                return false
            }

            override fun hashCode(): Int {
                return super.hashCode()
            }
        }

        // TODO: 5.0 is not 5 symbol . 0
        val lines = mutableListOf<StageOneParsedLine>()
        val tokens = mutableListOf<String>()
        val tokenBuilder = StringBuilder()
        var lastWasWhitespace = false
        for ((index, c) in inp.withIndex()) {
            val inStringLiteral = stringLiterals.any { index in it.first }

            if (!inStringLiteral) {
                if (lastWasWhitespace && c.isWhitespace()) {
                    continue
                } else if (lastWasWhitespace) {
                    lastWasWhitespace = false
                }

                if (c in symbols) {
                    if (tokenBuilder.isNotBlank()) tokens.add(tokenBuilder.toString())
                    tokenBuilder.clear()
                    tokens.add(c.toString())
                    continue
                } else if (tokenBuilder.toString() in keywords) {
                    tokens.add(tokenBuilder.toString())
                    tokenBuilder.clear()
                    continue
                } else if (c.isWhitespace()) {
                    if (tokenBuilder.isNotBlank()) tokens.add(tokenBuilder.toString())
                    tokenBuilder.clear()
                    lastWasWhitespace = true
                    continue
                } else if (c == ';') {
                    if (tokenBuilder.isNotBlank()) tokens.add(tokenBuilder.toString())
                    lines.add(StageOneParsedLine(*tokens.map { if (it in keywords) { Keyword(it) } else if (it in symbols.map { s -> s.toString() }) { Symbol(it) } else it }.toTypedArray()))
                    tokens.clear()
                    tokenBuilder.clear()
                    continue
                }
            }

            if (!c.isWhitespace() || inStringLiteral) {
                tokenBuilder.append(c)
            }

            lastWasWhitespace = c == ' '
        }

        val declaredVariables = mutableSetOf<String>()
        val imports = mutableMapOf<String, String>()

        fun compileExpression(exp: List<CharSequence>): ExpressionNode {
            var output: ExpressionNode? = null
            var isSearchingForArgs = false
            var parenCount = 0
            val argsAccumulator = mutableListOf<List<CharSequence>>()
            val builder = mutableListOf<CharSequence>()
            var methodName = ""
            var isFindingStatic: String? = null
            var isConstructing: String? = null
            for ((index, item) in exp.withIndex()) {
                if (isConstructing != null && exp[index - 1] == Keyword("new")) continue
                if (isSearchingForArgs) {
                    if (item is Symbol && item == "," && parenCount == 1) {
                        if (builder.isNotEmpty()) argsAccumulator.add(builder.toList())
                        builder.clear()
                        continue
                    }
                    if (item is Symbol && item == "(") {
                        if (parenCount++ == 0) continue
                    }
                    if (item is Symbol && item == ")") parenCount--
                    if (item is Symbol && item == ")" && parenCount == 0) {
                        // done
                        if (builder.isNotEmpty()) argsAccumulator.add(builder.toList())
                        builder.clear()
                        isSearchingForArgs = false
                        output = if (isConstructing != null) {
                            ConstructNode(isConstructing, argsAccumulator.map { compileExpression(it) })
                        } else {
                            MethodInvokeNode(if (isFindingStatic != null) Union.b<ExpressionNode, String>(isFindingStatic).apply { isFindingStatic = null } else Union.a(output!!), methodName, argsAccumulator.map { compileExpression(it) })
                        }
                        argsAccumulator.clear()
                        isConstructing = null
                        continue
                    }
                    builder.add(item)
                    continue
                }
                if (index == 0) {
                    output = if (declaredVariables.contains(item.toString())) {
                        VariableNode(item.toString())
                    } else {
                        val value = exp.first().toString()
                        when {
                            "\\d+".toRegex().matchEntire(value) != null -> ConstantNode(if (value.toLong() > Int.MAX_VALUE || value.toLong() < Int.MIN_VALUE) LONG else INT, value)
                            "\\d+.\\d+".toRegex().matchEntire(value) != null -> ConstantNode(DOUBLE, value)
                            "\\d+.\\d+f".toRegex().matchEntire(value) != null -> ConstantNode(FLOAT, value)
                            value in listOf("true", "false") -> ConstantNode(BOOLEAN, value)
                            value in declaredVariables -> VariableNode(value)
                            value.run { startsWith('"') && endsWith('"') } -> ConstantNode(STRING, value.removeSurrounding("\""))
                            value in imports.keys -> {
                                isFindingStatic = imports[value]
                                continue
                            }
                            value == "keyword new" -> {
                                isConstructing = imports[exp[index + 1]]
                                isSearchingForArgs = true
                                parenCount = 0
                                builder.clear()
                                continue
                            }
                            else -> ConstantNode(STRING, value.removeSurrounding("\""))
                        }
                    }
                    continue
                }
                if (item == ".") continue
                if (exp.getOrNull(index + 1) == "(") {  // method
                    isSearchingForArgs = true
                    parenCount = 0
                    builder.clear()
                    methodName = item.toString()
                } else { // attribute
                    if (isFindingStatic != null) {
                        output = AttributeGetNode(Union.b(isFindingStatic!!), item.toString())
                        isFindingStatic = null
                    } else {
                        output = AttributeGetNode(Union.a(output!!), item.toString())
                    }
                }
            }

            return output!!
        }

        fun compileStatements(lines: List<List<CharSequence>>): StatementListNode {
            val nodes = mutableListOf<Node>()

            var skipUntil = 0

            for ((index, line) in lines.withIndex()) {
                if (index < skipUntil) continue
                if (line.first() in declaredVariables) {
                    if (line.component2() == Symbol("=")) {
                        nodes.add(VariableSetNode(line.first().toString(), compileExpression(line.drop(2))))
                        continue
                    }
                }
                when (line.first()) {
                    "import" -> {
                        // TODO shorten to joinToString and validate with regex
                        val qualifiedName = StringBuilder()
                        for (t in line.drop(1)) {
                            if (qualifiedName.lastOrNull() == '.' || qualifiedName.isEmpty()) {
                                if (t == ".") throw SyntaxError("Package names must be separated by dots!")
                                qualifiedName.append(t)
                            } else if (qualifiedName.isNotEmpty()) {
                                if (t != ".") throw SyntaxError("Package names must be separated by dots!")
                                qualifiedName.append('.')
                            }
                        }
                        val importAs = qualifiedName.toString().substringAfterLast('.')
                        imports[importAs] = qualifiedName.toString()
                    }
                    Keyword("var") -> {
                        val variableName = line[1]
                        if (variableName.matches("^\\d+".toRegex())) throw SyntaxError("Variable names must not start with a number!")
                        if (line[2] != "=") throw SyntaxError("Assignment statements must consist of the var keyword, an equal sign, and an expression!")
                        nodes.add(VariableDefNode(variableName.toString(), compileExpression(line.drop(3))))
                        declaredVariables.add(variableName.toString())
                    }
                    Keyword("define") -> {
                        TODO()
                    }
                    Keyword("while") -> {
                        val expression = mutableListOf<CharSequence>()
                        var parenCount = 0
                        for (i in line.drop(1)) {
                            if (i == Symbol("(")) parenCount++
                            else if (i == Symbol(")")) parenCount--

                            if (parenCount == 0) {
                                break
                            }
                            expression.add(i)
                        }
                        expression.removeFirst()

                        val compiledExpression = compileExpression(expression)

                        val block = mutableListOf<List<CharSequence>>()

                        parenCount = 1
                        var endingIndex = index
                        for (i in lines.drop(index + 1)) {
                            endingIndex++
                            if (i.contains(Symbol("{"))) parenCount++
                            else if (i.contains(Symbol("}"))) parenCount--

                            if (parenCount == 0) {
                                break
                            }
                            block.add(i)
                        }

                        nodes.add(WhileLoopNode(compiledExpression, compileStatements(block)))
                        skipUntil = endingIndex + 1
                    }

                    Keyword("if") -> {
                        val expression = mutableListOf<CharSequence>()
                        var parenCount = 0
                        for (i in line.drop(1)) {
                            if (i == Symbol("(")) parenCount++
                            else if (i == Symbol(")")) parenCount--

                            if (parenCount == 0) {
                                break
                            }
                            expression.add(i)
                        }
                        expression.removeFirst()

                        val compiledExpression = compileExpression(expression)

                        val block = mutableListOf<List<CharSequence>>()

                        parenCount = 1
                        var endingIndex = index
                        for (i in lines.drop(index + 1)) {
                            endingIndex++
                            if (i.contains(Symbol("{"))) parenCount++
                            else if (i.contains(Symbol("}"))) parenCount--

                            if (parenCount == 0) {
                                break
                            }
                            block.add(i)
                        }

                        nodes.add(IfStatementNode(compiledExpression, compileStatements(block)))
                        skipUntil = endingIndex + 1
                    }
                    "//" -> break
                    else -> {
                        nodes.add(compileExpression(line.toList()))
                    }
                }
            }

            return StatementListNode(nodes)
        }

        return compileStatements(lines.map { it.tokens.toList() })
    }
}

fun main() {
    val parser = StupidererCodeParser()
    val url = URL("https://raw.githubusercontent.com/blahblahbloopster/stupiderercode/master/src/main/resources/madlibs.stpdrr")
    val stream = url.openStream()
    val node = parser.parse(stream.readAllBytes().decodeToString())

//    println(ClassLoader.getSystemClassLoader().getResourceAsStream("madlibs.txt"))
//    exitProcess(0)

    node.print()
    println("========Compiled:========")

    val compiled = StupidererCodeCompiler().compile(node)
    var i = 0
    println(compiled.joinToString("\n") { "${i++.toString().padStart(2, ' ')} $it" })
    println("========Output:========")
    val interpreter = StupidererCodeInterpreter(compiled)
    while (interpreter.update()) {}
}
