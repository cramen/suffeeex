package ru.cramen.suffeeex.core.backend.asm

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import ru.cramen.suffeeex.core.Expression
import ru.cramen.suffeeex.core.ExpressionException
import ru.cramen.suffeeex.core.backend.ExpressionBackend
import ru.cramen.suffeeex.core.backend.SpecializedBackend
import ru.cramen.suffeeex.core.backend.specializedSignature
import ru.cramen.suffeeex.core.node.CompareOp
import ru.cramen.suffeeex.core.node.Emission
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.TypedNode
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * Backend that compiles the node tree to JVM bytecode with ASM: every
 * expression becomes a freshly generated `Expression` implementation whose
 * `eval` computes on primitives and boxes only the final result.
 *
 * It also implements [SpecializedBackend]: the generated class implements
 * the user's fun interface directly, with method parameters as the
 * expression variables (no `EvaluationContext` involved at runtime).
 */
object AsmBackend : ExpressionBackend, SpecializedBackend {
    private val counter = AtomicLong()
    private val classLoader = object : ClassLoader(AsmBackend::class.java.classLoader) {
        fun define(binaryName: String, bytecode: ByteArray): Class<*> =
            defineClass(binaryName, bytecode, 0, bytecode.size)
    }

    override fun compile(root: TypedNode): Expression {
        val binaryName = "ru.cramen.suffeeex.generated.CompiledExpression${counter.getAndIncrement()}"

        val writer = classHeader(binaryName, arrayOf("ru/cramen/suffeeex/core/Expression"))

        writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "eval",
            "(Lru/cramen/suffeeex/core/EvaluationContext;)Ljava/lang/Object;",
            null,
            null,
        ).apply {
            visitCode()
            val emission = AsmEmission(this)
            emission.push(root)
            emission.box(root.type)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return classLoader.define(binaryName, writer.toByteArray())
            .getDeclaredConstructor()
            .newInstance() as Expression
    }

    override fun compile(root: TypedNode, target: KClass<*>): Any {
        val signature = specializedSignature(target)
        val binaryName = "ru.cramen.suffeeex.generated.SpecializedExpression${counter.getAndIncrement()}"

        val writer = classHeader(binaryName, arrayOf(target.java.name.replace('.', '/')))

        val methodDescriptor = signature.parameters.joinToString("", "(", ")") { descriptor(it.type) } +
            if (signature.referenceReturn) referenceDescriptor(signature.returnType)
            else descriptor(signature.returnType)

        writer.visitMethod(Opcodes.ACC_PUBLIC, signature.methodName, methodDescriptor, null, null).apply {
            visitCode()
            val variableSlots = signature.parameters.associate { it.name to it.slot }
            val emission = AsmEmission(this, variableSlots)
            emission.push(root)
            if (signature.referenceReturn) {
                emission.box(root.type)
                visitInsn(Opcodes.ARETURN)
            } else {
                visitInsn(returnOpcode(root.type))
            }
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return classLoader.define(binaryName, writer.toByteArray())
            .getDeclaredConstructor()
            .newInstance()
    }

    private fun classHeader(binaryName: String, interfaces: Array<String>): ClassWriter {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
            binaryName.replace('.', '/'),
            null,
            "java/lang/Object",
            interfaces,
        )
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        return writer
    }

    private class AsmEmission(
        private val mv: MethodVisitor,
        private val variableSlots: Map<String, Int> = emptyMap(),
    ) : Emission {
        override fun constant(type: KClass<*>, value: Any) {
            // LDC cannot push booleans; emit them as int constants
            if (type == Boolean::class) {
                mv.visitInsn(if (value as Boolean) Opcodes.ICONST_1 else Opcodes.ICONST_0)
            } else {
                mv.visitLdcInsn(value)
            }
        }

        override fun loadVariable(name: String, type: KClass<*>) {
            val slot = variableSlots[name]
            if (slot != null) {
                // specialized mode: the variable is a method parameter, no context
                mv.visitVarInsn(loadOpcode(type), slot)
                return
            }
            // Minimal route: a missing value fails with a JVM NPE on unboxing,
            // a wrong-typed value with a ClassCastException from the checkcast.
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            mv.visitLdcInsn(name)
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "ru/cramen/suffeeex/core/EvaluationContext",
                "resolve",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                true,
            )
            if (type == String::class) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String")
            } else {
                mv.visitTypeInsn(Opcodes.CHECKCAST, wrapperType(type))
                mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    wrapperType(type),
                    unboxMethod(type),
                    "()" + descriptor(type),
                    false,
                )
            }
        }

        override fun numericBinary(op: NumericOp, type: KClass<*>) {
            val base = when (op) {
                NumericOp.ADD -> Opcodes.IADD
                NumericOp.SUB -> Opcodes.ISUB
                NumericOp.MUL -> Opcodes.IMUL
                NumericOp.DIV -> Opcodes.IDIV
                NumericOp.REM -> Opcodes.IREM
            }
            mv.visitInsn(base + typeOffset(type))
        }

        override fun numericNegate(type: KClass<*>) {
            mv.visitInsn(Opcodes.INEG + typeOffset(type))
        }

        override fun convertNumeric(from: KClass<*>, to: KClass<*>) {
            if (from == to) return
            val opcode = when (from to to) {
                Int::class to Long::class -> Opcodes.I2L
                Int::class to Float::class -> Opcodes.I2F
                Int::class to Double::class -> Opcodes.I2D
                Long::class to Int::class -> Opcodes.L2I
                Long::class to Float::class -> Opcodes.L2F
                Long::class to Double::class -> Opcodes.L2D
                Float::class to Int::class -> Opcodes.F2I
                Float::class to Long::class -> Opcodes.F2L
                Float::class to Double::class -> Opcodes.F2D
                Double::class to Int::class -> Opcodes.D2I
                Double::class to Long::class -> Opcodes.D2L
                Double::class to Float::class -> Opcodes.D2F
                else -> throw ExpressionException(
                    "cannot convert ${from.simpleName} to ${to.simpleName}"
                )
            }
            mv.visitInsn(opcode)
        }

        override fun invokeMath(name: String, argTypes: List<KClass<*>>, resultType: KClass<*>) {
            val mathName = when (name) {
                "ln" -> "log"
                "round" -> "rint" // kotlin.math.round semantics: ties to even
                else -> name
            }
            val descriptor = argTypes.joinToString("", "(", ")") { descriptor(it) } + descriptor(resultType)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", mathName, descriptor, false)
        }

        override fun compare(op: CompareOp, type: KClass<*>) {
            val trueLabel = Label()
            val endLabel = Label()
            when (type) {
                Int::class, Boolean::class -> mv.visitJumpInsn(ifIcmpOpcode(op), trueLabel)
                Long::class -> {
                    mv.visitInsn(Opcodes.LCMP)
                    mv.visitJumpInsn(ifZeroOpcode(op), trueLabel)
                }
                Float::class -> {
                    // javac NaN convention: comparisons with NaN are false for
                    // </<=/>=/> — G for LT/LE (NaN reads as "greater"), L for
                    // GT/GE (NaN reads as "less"); EQ/NE use G like javac
                    mv.visitInsn(if (usesLessCmp(op)) Opcodes.FCMPL else Opcodes.FCMPG)
                    mv.visitJumpInsn(ifZeroOpcode(op), trueLabel)
                }
                Double::class -> {
                    mv.visitInsn(if (usesLessCmp(op)) Opcodes.DCMPL else Opcodes.DCMPG)
                    mv.visitJumpInsn(ifZeroOpcode(op), trueLabel)
                }
                else -> throw ExpressionException("cannot compare values of type ${type.simpleName}")
            }
            // 0; goto end; true: 1; end:
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(trueLabel)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitLabel(endLabel)
        }

        override fun branch(condition: TypedNode, ifTrue: TypedNode, ifFalse: TypedNode) {
            val elseLabel = Label()
            val endLabel = Label()
            push(condition)
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel)
            push(ifTrue)
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(elseLabel)
            push(ifFalse)
            mv.visitLabel(endLabel)
        }

        override fun logicalAnd(left: TypedNode, right: TypedNode) {
            val falseLabel = Label()
            val endLabel = Label()
            push(left)
            mv.visitJumpInsn(Opcodes.IFEQ, falseLabel)
            push(right)
            mv.visitJumpInsn(Opcodes.IFEQ, falseLabel)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(falseLabel)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitLabel(endLabel)
        }

        override fun logicalOr(left: TypedNode, right: TypedNode) {
            val trueLabel = Label()
            val endLabel = Label()
            push(left)
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel)
            push(right)
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitJumpInsn(Opcodes.GOTO, endLabel)
            mv.visitLabel(trueLabel)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitLabel(endLabel)
        }

        override fun logicalNot() {
            // booleans are 0/1 ints, so x ^ 1 inverts
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitInsn(Opcodes.IXOR)
        }

        override fun stringConcat() {
            // StringConcatFactory.makeConcat (the plain pre-constant-recipe
            // form javac used before JDK 9.0.1): simpler than
            // makeConcatWithConstants and no bootstrap arguments needed
            val bootstrap = Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/StringConcatFactory",
                "makeConcat",
                "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;" +
                    "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false,
            )
            mv.visitInvokeDynamicInsn(
                "makeConcat",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                bootstrap,
            )
        }

        override fun objectsEquals() {
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/util/Objects",
                "equals",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                false,
            )
        }

        override fun invokeStringMethod(name: String, argTypes: List<KClass<*>>, resultType: KClass<*>) {
            val descriptor = argTypes.joinToString("", "(", ")") { descriptor(it) } + descriptor(resultType)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", name, descriptor, false)
        }

        /** Boxes the primitive on top of the stack into its wrapper type. */
        fun box(type: KClass<*>) {
            if (type == String::class) return // already a reference
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                wrapperType(type),
                "valueOf",
                "(" + descriptor(type) + ")L" + wrapperType(type) + ";",
                false,
            )
        }
    }
}

private fun descriptor(type: KClass<*>): String = when (type) {
    Int::class -> "I"
    Long::class -> "J"
    Float::class -> "F"
    Double::class -> "D"
    Boolean::class -> "Z"
    String::class -> "Ljava/lang/String;"
    else -> throw ExpressionException("unsupported type for bytecode emission: ${type.simpleName}")
}

/** Reference (wrapper) descriptor used for return types declared boxed. */
private fun referenceDescriptor(type: KClass<*>): String =
    if (type == String::class) "Ljava/lang/String;" else "L" + wrapperType(type) + ";"

private fun wrapperType(type: KClass<*>): String = when (type) {
    Int::class -> "java/lang/Integer"
    Long::class -> "java/lang/Long"
    Float::class -> "java/lang/Float"
    Double::class -> "java/lang/Double"
    Boolean::class -> "java/lang/Boolean"
    else -> throw ExpressionException("unsupported type for bytecode emission: ${type.simpleName}")
}

private fun unboxMethod(type: KClass<*>): String = when (type) {
    Int::class -> "intValue"
    Long::class -> "longValue"
    Float::class -> "floatValue"
    Double::class -> "doubleValue"
    Boolean::class -> "booleanValue"
    else -> throw ExpressionException("unsupported type for bytecode emission: ${type.simpleName}")
}

private fun loadOpcode(type: KClass<*>): Int = when (type) {
    Int::class, Boolean::class -> Opcodes.ILOAD
    Long::class -> Opcodes.LLOAD
    Float::class -> Opcodes.FLOAD
    Double::class -> Opcodes.DLOAD
    String::class -> Opcodes.ALOAD
    else -> throw ExpressionException("unsupported type for bytecode emission: ${type.simpleName}")
}

private fun returnOpcode(type: KClass<*>): Int = when (type) {
    Int::class, Boolean::class -> Opcodes.IRETURN
    Long::class -> Opcodes.LRETURN
    Float::class -> Opcodes.FRETURN
    Double::class -> Opcodes.DRETURN
    String::class -> Opcodes.ARETURN
    else -> throw ExpressionException("unsupported type for bytecode emission: ${type.simpleName}")
}

private fun ifIcmpOpcode(op: CompareOp): Int = when (op) {
    CompareOp.LT -> Opcodes.IF_ICMPLT
    CompareOp.LE -> Opcodes.IF_ICMPLE
    CompareOp.GT -> Opcodes.IF_ICMPGT
    CompareOp.GE -> Opcodes.IF_ICMPGE
    CompareOp.EQ -> Opcodes.IF_ICMPEQ
    CompareOp.NE -> Opcodes.IF_ICMPNE
}

private fun ifZeroOpcode(op: CompareOp): Int = when (op) {
    CompareOp.LT -> Opcodes.IFLT
    CompareOp.LE -> Opcodes.IFLE
    CompareOp.GT -> Opcodes.IFGT
    CompareOp.GE -> Opcodes.IFGE
    CompareOp.EQ -> Opcodes.IFEQ
    CompareOp.NE -> Opcodes.IFNE
}

private fun usesLessCmp(op: CompareOp): Boolean = op == CompareOp.GT || op == CompareOp.GE

// Numeric opcodes order int, long, float, double consecutively.
private fun typeOffset(type: KClass<*>): Int = when (type) {
    Int::class -> 0
    Long::class -> 1
    Float::class -> 2
    Double::class -> 3
    else -> throw ExpressionException("unsupported type for bytecode emission: ${type.simpleName}")
}
