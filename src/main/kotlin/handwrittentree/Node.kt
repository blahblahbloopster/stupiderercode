package handwrittentree

import handwrittentree.StupidererCodeInterpreter.Union

sealed interface Node {
    val children: List<Node>

    fun print(indention: Int = 0) {
        val str = " ".repeat(indention * 4)
        println(str + this::class.simpleName)
        for (child in children) {
            child.print(indention + 1)
        }
    }
}

sealed interface ExpressionNode : Node {
    override val children: List<ExpressionNode>
}

class ConstantNode(val type: Type, val value: String) : ExpressionNode {
    override val children: List<ExpressionNode> = emptyList()

    enum class Type(val converter: (String) -> Any) {
        BYTE({it.toByte()}), SHORT({it.toShort()}), INT({it.toInt()}), LONG({it.toLong()}),
        FLOAT({it.toFloat()}), DOUBLE({it.toDouble()}),
        BOOLEAN({it.toBoolean()}),
        STRING({it})
    }

    override fun print(indention: Int) {
        println(" ".repeat(indention * 4) + "const " + value)
    }
}

data class VariableNode(val variable: String) : ExpressionNode {
    override val children: List<ExpressionNode> = emptyList()

    override fun print(indention: Int) {
        println(" ".repeat(indention * 4) + "VariableNode $variable")
    }
}

data class AttributeGetNode(val obj: Union<ExpressionNode, String>, val attrName: String) : ExpressionNode {
    override val children: List<ExpressionNode> = obj.ifA { listOf(it) } el { emptyList() }

    override fun print(indention: Int) {
        println(" ".repeat(indention * 4) + "AttributeGetNode ${obj.ifA { "<see below>" } el { it }}.$attrName")
        for (child in children) {
            child.print(indention + 1)
        }
    }
}

class MethodInvokeNode(val obj: Union<ExpressionNode, String>, val methodName: String, val args: List<ExpressionNode>) : ExpressionNode {
    override val children: List<ExpressionNode> = obj.ifA { listOf(it) + args } el { args }

    override fun print(indention: Int) {
        println(" ".repeat(indention * 4) + "MethodInvokeNode ${obj.ifA { "<see below>" } el { it }}.$methodName")
        for (child in children) {
            child.print(indention + 1)
        }
    }
}

class ConstructNode(val obj: String, val args: List<ExpressionNode>) : ExpressionNode {
    override val children: List<ExpressionNode> = args
}

class IfStatementNode(val condition: ExpressionNode, val block: StatementListNode) : Node {
    override val children: List<Node> = listOf(condition, block)
}

class VariableDefNode(val variable: String, val value: ExpressionNode) : Node {
    override val children: List<Node> = listOf(value)

    override fun print(indention: Int) {
        println(" ".repeat(indention * 4) + "VariableDefNode $variable = ")
        for (child in children) {
            child.print(indention + 1)
        }
    }
}

class VariableSetNode(val variable: String, val value: ExpressionNode) : Node {
    override val children: List<Node> = listOf(value)

    override fun print(indention: Int) {
        println(" ".repeat(indention * 4) + "VariableSetNode $variable = ")
        for (child in children) {
            child.print(indention + 1)
        }
    }
}

class StatementListNode(override val children: List<Node>) : Node {
    constructor(vararg nodes: Node) : this(nodes.toList())
}

class FunctionDefNode(val name: String, val arguments: List<String>, val block: StatementListNode) : Node {
    override val children: List<Node> = listOf(block)
}

class WhileLoopNode(val expression: ExpressionNode, val block: StatementListNode) : Node {
    override val children: List<Node> = listOf(expression, block)
}

fun main() {
    val tree = MethodInvokeNode(Union.a(AttributeGetNode(Union.b("java.lang.System"), "out")), "println", listOf(ConstantNode(ConstantNode.Type.STRING, "hello, world!")))

    val compiler = StupidererCodeCompiler()

    val instructions = compiler.compile(tree)
    println(instructions)

    println("\nExecuting...")
    val interpreter = StupidererCodeInterpreter(instructions)

    while (interpreter.update()) {}
}
