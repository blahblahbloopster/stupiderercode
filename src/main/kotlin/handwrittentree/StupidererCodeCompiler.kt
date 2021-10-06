package handwrittentree

import handwrittentree.StupidererCodeInterpreter.*

class StupidererCodeCompiler {

    private var instructionCount = 0

    private var nextStackIndex = 0
    val variables = mutableMapOf<String, Int>()
    val functions = mutableMapOf<String, IntRange>()
//    private val output = mutableListOf<Instruction>()
//
//    fun add(instruction: Instruction) {
//        output.add(instruction)
//        instructionCount++
//    }

    fun compile(code: Node): List<Instruction> {
        return when (code) {
            is VariableDefNode -> {
                val output = compile(code.value)
                variables[code.variable] = nextStackIndex - 1
                output
            }
            is AttributeGetNode -> {
                code.obj.ifA {
                    val expression = compile(it)
                    instructionCount++
                    expression + GetOp(Union.a(StackAddr(0, nextStackIndex++)), code.attrName)
                } el {
                    nextStackIndex++
                    instructionCount++
                    listOf(GetOp(Union.b(it), code.attrName))
                }
            }
            is ConstantNode -> {
                nextStackIndex++
                listOf(ConstantOp(code.type, code.value).apply { instructionCount++ })
            }
            is ConstructNode -> {
                val original = nextStackIndex
                val argsInstructionsPointers = code.args.map { expr -> Pair(compile(expr), nextStackIndex - 1) }
                nextStackIndex++

                argsInstructionsPointers.fold(emptyList<Instruction>()) { acc, lst -> acc + lst.first } + ConstructOp(code.obj, argsInstructionsPointers.map { p -> StackAddr(0, p.second) }).apply { instructionCount++ } + pop(original until nextStackIndex - 1).apply { instructionCount++ }
            }
            is MethodInvokeNode -> {
                val original = nextStackIndex
                code.obj.ifA {
                    val interior = compile(it)
                    val objAddr = nextStackIndex - 1

                    val argsInstructionsPointers = code.args.map { expr -> Pair(compile(expr), nextStackIndex - 1) }

                    nextStackIndex++
                    interior + argsInstructionsPointers.fold(emptyList()) { acc, lst -> acc + lst.first } + InvokeOp(Union.a(StackAddr(0, objAddr)), code.methodName, argsInstructionsPointers.map { p -> StackAddr(0, p.second) }).apply { instructionCount++ } /*+ pop(original until nextStackIndex - 1).apply { instructionCount++ }*/
                } el {
                    val argsInstructionsPointers = code.args.map { expr -> Pair(compile(expr), nextStackIndex - 1) }

                    nextStackIndex++
                    argsInstructionsPointers.fold(emptyList<Instruction>()) { acc, lst -> acc + lst.first } + InvokeOp(Union.b(it), code.methodName, argsInstructionsPointers.map { p -> StackAddr(0, p.second) }).apply { instructionCount++ } /*+ pop(original until nextStackIndex - 1).apply { instructionCount++ }*/
                }
            }
            is VariableNode -> {
                nextStackIndex++
                instructionCount++
                listOf(CopyOp(StackAddr(0, variables[code.variable]!!)))
            }
            is IfStatementNode -> {
                val original = nextStackIndex

                val expression = compile(code.condition)

                val initialPop = pop(original until nextStackIndex - 1)
                instructionCount++

                val expressionResultAddr = nextStackIndex - 1

                val jumpPlaceholder: Instruction
                instructionCount++
                nextStackIndex--

                val block = compile(code.block)

                val pop = pop(original until nextStackIndex)
                instructionCount++

                jumpPlaceholder = NotJumpOp(instructionCount, StackAddr(0, expressionResultAddr))

                expression + initialPop + jumpPlaceholder + block + pop
            }
            is StatementListNode -> {
                code.children.fold(emptyList()) { acc, node -> acc + compile(node) }
            }
            is FunctionDefNode -> {
                val start = instructionCount
                val code = compile(code.block)
                val end = instructionCount
                TODO()
            }
            is WhileLoopNode -> {  // TODO: fix
                val jumpBackTo = instructionCount
                val original = nextStackIndex

                val expression = compile(code.expression)
                val expressionResultAddr = nextStackIndex - 1

                val firstJumpPlaceholder: Instruction
                instructionCount++
                nextStackIndex--

                val block = compile(code.block)

                val pop = pop(original until nextStackIndex)
                instructionCount++

                val jump = AlwaysJumpOp(jumpBackTo)
                instructionCount++

                firstJumpPlaceholder = NotJumpOp(instructionCount, StackAddr(0, expressionResultAddr))

                instructionCount++

                expression + firstJumpPlaceholder + block + pop + jump + pop(original until expressionResultAddr).apply { nextStackIndex = original }
            }
            is VariableSetNode -> {
                val expr = compile(code.value)
                instructionCount++
                expr + SetOp(StackAddr(0, nextStackIndex - 1), StackAddr(0, variables[code.variable]!!))
            }
        }
    }

    private fun pop(range: IntRange): StackPopOp {
        nextStackIndex -= range.count()
        return StackPopOp(range)
    }
}
