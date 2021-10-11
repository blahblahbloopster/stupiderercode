package com.github.blahblahbloopster

import java.lang.reflect.Method
import java.util.HashSet

class StupidererCodeInterpreter(private val instructions: List<Instruction>) {
    private var instructionPointer = 0

    val stack = Stack()

    data class StackAddr(val frame: Int, val offset: Int)

    class Stack : MutableList<Frame> by mutableListOf() {
        operator fun get(addr: StackAddr) = get(addr.frame, addr.offset)
        operator fun set(addr: StackAddr, value: Any?) { set(addr.frame, addr.offset, value) }
        operator fun get(frame: Int, offset: Int) = get(size - frame - 1)[offset]
        operator fun set(frame: Int, offset: Int, value: Any?) { get(size - frame - 1)[offset] = value }

        fun addLast(item: Any?) { if (isEmpty()) add(Frame()); last().add(item) }

        override fun toString(): String {
            return "Stack {\n${joinToString("\n")}\n}"
        }
    }

    class Frame : MutableList<Any?> by mutableListOf() {
        override fun toString(): String {
            return joinToString("\n") { "    $it" }
        }
    }

    companion object {
        /** from https://coderwall.com/p/wrqcsg/java-reflection-get-all-methods-in-hierarchy */
        private fun getAllMethodsInHierarchy(objectClass: Class<*>): Set<Method> {
            val allMethods: MutableSet<Method> = HashSet<Method>()
            val declaredMethods: Array<Method> = objectClass.declaredMethods
            val methods: Array<Method> = objectClass.methods
            if (objectClass.superclass != null) {
                val superClass = objectClass.superclass
                val superClassMethods = getAllMethodsInHierarchy(superClass)
                allMethods.addAll(superClassMethods)
            }
            allMethods.addAll(declaredMethods)
            allMethods.addAll(methods)
            return allMethods
        }
    }

    interface Instruction {
        fun execute(interpreter: StupidererCodeInterpreter)
    }

    class Union<A, B> private constructor(val a: A?, val b: B?) {
        companion object {
            fun <A, B> a(a: A) = Union<A, B>(a, null)
            fun <A, B> b(b: B) = Union<A, B>(null, b)
        }

        fun a() = a!!
        fun b() = b!!

        fun isA() = a != null
        fun isB() = b != null

        fun isA(block: (A) -> Unit) {
            if (isA()) block(a())
        }

        fun isB(block: (B) -> Unit) {
            if (isB()) block(b())
        }

        fun <R> ifA(block: (A) -> R): UnionElse<B, R> {
            return UnionElse(this, a?.run(block))
        }

        override fun toString(): String {
            return ifA { "Union(a=$a)" } el { "Union(b=$b)" }
        }

        class UnionElse<B, R> internal constructor(val value: Union<*, B>, val result: R?) {
            infix fun el(block: (B) -> R): R {
                if (result != null) return result
                return block(value.b())
            }
        }
    }

    class NewStackFrameOp : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            interpreter.stack.add(Frame())
        }
    }

    class PopStackFrameOp : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            interpreter.stack.removeLast()
        }
    }

    data class StackPopOp(val range: IntRange) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            repeat(range.count()) {
                interpreter.stack.last().removeAt(range.first)
            }
        }
    }

    data class InvokeOp(val pointer: Union<StackAddr, String>, val name: String, val args: List<StackAddr>) :
        Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            val resolvedArgs = args.map { interpreter.stack[it] }

            pointer.isA { addr ->
                val obj = interpreter.stack[addr]!!

                val namedCorrectly = getAllMethodsInHierarchy(obj::class.java).filter { it.name == name && it.parameterCount == resolvedArgs.size }
                val correctTypes = namedCorrectly.firstOrNull {
                    it.parameterTypes.mapIndexed { index, clazz ->
                        when (clazz.name) {
                            "byte" -> java.lang.Byte::class.java
                            "short" -> java.lang.Short::class.java
                            "int" -> Integer::class.java
                            "long" -> java.lang.Long::class.java
                            "double" -> java.lang.Double::class.java
                            "boolean" -> java.lang.Boolean::class.java
                            else -> clazz
                        }.isInstance(resolvedArgs.getOrNull(index))
                    }.all { value -> value }
                } ?: throw NoSuchMethodError("Couldn't find a method matching '$name' on '$obj'!")

                correctTypes.isAccessible = true

                val result = correctTypes.invoke(obj, *resolvedArgs.toTypedArray())

                interpreter.stack.addLast(result)
            }
            pointer.isB { obj ->
                val cls = Class.forName(obj)

                val namedCorrectly = cls.methods.filter { it.name == name }
                val method = namedCorrectly.firstOrNull {
                    it.parameterTypes.mapIndexed { index, clazz -> when (clazz.name) {
                        "byte" -> java.lang.Byte::class.java
                        "short" -> java.lang.Short::class.java
                        "int" -> Integer::class.java
                        "long" -> java.lang.Long::class.java
                        "double" -> java.lang.Double::class.java
                        "boolean" -> java.lang.Boolean::class.java
                        else -> clazz
                    }.isInstance(resolvedArgs.getOrNull(index)) }
                        .all { value -> value }
                } ?: throw NoSuchMethodError("Couldn't find a method matching '$name' with parameters '$resolvedArgs' on '$obj'!")

                method.isAccessible = true

                val result = method.invoke(null, *resolvedArgs.toTypedArray())

                interpreter.stack.addLast(result)
            }
        }
    }

    data class GetOp(val pointer: Union<StackAddr, String>, val name: String) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            pointer.isA {
                val obj = interpreter.stack[it]!!

                val field = obj::class.java.getField(name)

                interpreter.stack.addLast(field.get(obj))
            }
            pointer.isB {
                val field = Class.forName(it).getField(name)

                interpreter.stack.addLast(field.get(null))
            }
        }
    }

    data class ConstructOp(val clazz: String, val args: List<StackAddr>) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            val resolvedArgs = args.map { interpreter.stack[it] }

            val correctTypes = Class.forName(clazz).constructors.first {
                it.parameterCount == resolvedArgs.size &&
                it.parameterTypes.mapIndexed { index, clazz -> clazz.isInstance(resolvedArgs[index]) }
                    .all { value -> value }
            }

            val result = correctTypes.newInstance(*resolvedArgs.toTypedArray())
            interpreter.stack.addLast(result)
        }
    }

    data class ConstantOp(val type: ConstantNode.Type, val value: String) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            interpreter.stack.addLast(type.converter(value))
        }
    }

    data class CopyOp(val pointer: StackAddr) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            interpreter.stack.addLast(interpreter.stack[pointer])
        }
    }

    data class JumpOp(val address: Int, val pointer: StackAddr) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            val jmp = interpreter.stack[pointer] as Boolean
            if (jmp) {
                interpreter.instructionPointer = address
            }
            interpreter.stack.last().removeLast()
        }
    }

    data class AlwaysJumpOp(val address: Int) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            interpreter.instructionPointer = address
        }
    }

    data class NotJumpOp(val address: Int, val pointer: StackAddr) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            val jmp = interpreter.stack[pointer] as Boolean
            if (!jmp) {
                interpreter.instructionPointer = address
            }
            interpreter.stack.last().removeLast()
        }
    }

    data class SetOp(val source: StackAddr, val dest: StackAddr) : Instruction {
        override fun execute(interpreter: StupidererCodeInterpreter) {
            interpreter.stack[dest] = interpreter.stack[source]
        }
    }

    fun update(): Boolean {
        if (instructionPointer >= instructions.size) {
            return false
        }
        try {
            instructions[instructionPointer++]/*.apply { println("\nExecuting $this") }*/.execute(this)
//            println(stack)
        } catch (e: Exception) {
            throw RuntimeException("Exception on instruction $instructionPointer (${instructions[instructionPointer]})\n${e.stackTraceToString()}")
        }
        return true
    }
}
