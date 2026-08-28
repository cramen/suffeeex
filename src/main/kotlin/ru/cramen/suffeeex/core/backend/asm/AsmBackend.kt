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
import ru.cramen.suffeeex.core.node.EmissionLabel
import ru.cramen.suffeeex.core.node.NumericOp
import ru.cramen.suffeeex.core.node.StackCategory
import ru.cramen.suffeeex.core.node.TypeEmissions
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
 *
 * Type knowledge (descriptors, boxing, load/store opcodes) comes from the
 * [TypeEmissions] registry, so extensions can add types without touching
 * this backend. Every generated class is defined in its own child
 * classloader, so it is unloaded once the compiled expression is GC'd.
 */
object AsmBackend : ExpressionBackend, SpecializedBackend {
    private val counter = AtomicLong()

    /** One per generated class: the class unloads with its expression. */
    private class ExpressionClassLoader : ClassLoader(AsmBackend::class.java.classLoader) {
        fun define(binaryName: String, bytecode: ByteArray): Class<*> =
            defineClass(binaryName, bytecode, 0, bytecode.size)
    }

    override fun compile(root: TypedNode, types: TypeEmissions): Expression {
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
            // local slots: 0 = this, 1 = context
            val emission = AsmEmission(this, types, nextLocalSlot = 2)
            emission.push(root)
            emission.box(root.type)
            visitInsn(Opcodes.ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return ExpressionClassLoader().define(binaryName, writer.toByteArray())
            .getDeclaredConstructor()
            .newInstance() as Expression
    }

    override fun compile(root: TypedNode, target: KClass<*>, types: TypeEmissions): Any {
        val signature = specializedSignature(target)
        val binaryName = "ru.cramen.suffeeex.generated.SpecializedExpression${counter.getAndIncrement()}"

        val writer = classHeader(binaryName, arrayOf(target.java.name.replace('.', '/')))

        val methodDescriptor = signature.parameters.joinToString("", "(", ")") { descriptor(types, it.type) } +
            if (signature.referenceReturn) referenceDescriptor(types, signature.returnType)
            else descriptor(types, signature.returnType)

        writer.visitMethod(Opcodes.ACC_PUBLIC, signature.methodName, methodDescriptor, null, null).apply {
            visitCode()
            val variableSlots = signature.parameters.associate { it.name to it.slot }
            // locals are allocated after this + the parameters
            val nextLocalSlot = signature.parameters.lastOrNull()
                ?.let { it.slot + slotSize(types, it.type) }
                ?: 1
            val emission = AsmEmission(this, types, variableSlots, nextLocalSlot)
            emission.push(root)
            if (signature.referenceReturn) {
                emission.box(root.type)
                visitInsn(Opcodes.ARETURN)
            } else {
                visitInsn(returnOpcode(types, root.type))
            }
            visitMaxs(0, 0)
            visitEnd()
        }

        writer.visitEnd()
        return ExpressionClassLoader().define(binaryName, writer.toByteArray())
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

    private class AsmLabel(val label: Label) : EmissionLabel

    private class AsmEmission(
        private val mv: MethodVisitor,
        private val types: TypeEmissions,
        // null: expression mode (variables load from the EvaluationContext);
        // non-null: specialized mode (variables must be method parameters)
        private val variableSlots: Map<String, Int>? = null,
        // first local slot not taken by this/parameters; grows with newLocal
        private var nextLocalSlot: Int,
    ) : Emission {
        override fun constant(type: KClass<*>, value: Any) {
            types.of(type).pushConstant(this, value)
        }

        override fun ldc(value: Any) {
            mv.visitLdcInsn(value)
        }

        override fun loadVariable(name: String, type: KClass<*>) {
            val slots = variableSlots
            if (slots != null) {
                // specialized mode: the variable is a method parameter, no context
                val slot = slots[name]
                    ?: throw ExpressionException(
                        "variable '$name' is not a parameter of the specialized compile target"
                    )
                mv.visitVarInsn(loadOpcode(types, type), slot)
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
            val support = types.of(type)
            val wrapper = support.wrapperInternalName
            if (wrapper != null) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper)
                mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    wrapper,
                    support.unboxMethod,
                    "()" + support.descriptor,
                    false,
                )
            } else {
                mv.visitTypeInsn(Opcodes.CHECKCAST, internalName(type))
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
            mv.visitInsn(base + numericOffset(types, type))
        }

        override fun numericNegate(type: KClass<*>) {
            mv.visitInsn(Opcodes.INEG + numericOffset(types, type))
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
            invokeStatic(java.lang.Math::class, mathName, argTypes, resultType)
        }

        override fun compare(op: CompareOp, type: KClass<*>) {
            val trueLabel = Label()
            val endLabel = Label()
            when (types.of(type).category) {
                StackCategory.INT -> mv.visitJumpInsn(ifIcmpOpcode(op), trueLabel)
                StackCategory.LONG -> {
                    mv.visitInsn(Opcodes.LCMP)
                    mv.visitJumpInsn(ifZeroOpcode(op), trueLabel)
                }
                StackCategory.FLOAT -> {
                    // javac NaN convention: comparisons with NaN are false for
                    // </<=/>=/> — G for LT/LE (NaN reads as "greater"), L for
                    // GT/GE (NaN reads as "less"); EQ/NE use G like javac
                    mv.visitInsn(if (usesLessCmp(op)) Opcodes.FCMPL else Opcodes.FCMPG)
                    mv.visitJumpInsn(ifZeroOpcode(op), trueLabel)
                }
                StackCategory.DOUBLE -> {
                    mv.visitInsn(if (usesLessCmp(op)) Opcodes.DCMPL else Opcodes.DCMPG)
                    mv.visitJumpInsn(ifZeroOpcode(op), trueLabel)
                }
                StackCategory.REFERENCE ->
                    throw ExpressionException("cannot compare values of type ${type.simpleName}")
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
            invokeStatic(
                java.util.Objects::class,
                "equals",
                listOf(Any::class, Any::class),
                Boolean::class,
            )
        }

        override fun invokeStringMethod(name: String, argTypes: List<KClass<*>>, resultType: KClass<*>) {
            invokeVirtual(String::class, name, argTypes, resultType)
        }

        override fun newObject(type: KClass<*>) {
            mv.visitTypeInsn(Opcodes.NEW, internalName(type))
            mv.visitInsn(Opcodes.DUP)
        }

        override fun invokeConstructor(type: KClass<*>, argTypes: List<KClass<*>>) {
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                internalName(type),
                "<init>",
                methodDescriptor(types, argTypes, null),
                false,
            )
        }

        override fun invokeStatic(
            owner: KClass<*>,
            name: String,
            argTypes: List<KClass<*>>,
            resultType: KClass<*>,
        ) {
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                internalName(owner),
                name,
                methodDescriptor(types, argTypes, resultType),
                false,
            )
        }

        override fun invokeVirtual(
            owner: KClass<*>,
            name: String,
            argTypes: List<KClass<*>>,
            resultType: KClass<*>,
        ) {
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                internalName(owner),
                name,
                methodDescriptor(types, argTypes, resultType),
                false,
            )
        }

        override fun invokeInterface(
            owner: KClass<*>,
            name: String,
            argTypes: List<KClass<*>>,
            resultType: KClass<*>,
        ) {
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                internalName(owner),
                name,
                methodDescriptor(types, argTypes, resultType),
                true,
            )
        }

        override fun getStaticField(owner: KClass<*>, name: String, type: KClass<*>) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, internalName(owner), name, descriptor(types, type))
        }

        override fun getField(owner: KClass<*>, name: String, type: KClass<*>) {
            mv.visitFieldInsn(Opcodes.GETFIELD, internalName(owner), name, descriptor(types, type))
        }

        override fun pop(type: KClass<*>) {
            mv.visitInsn(if (slotSize(types, type) == 2) Opcodes.POP2 else Opcodes.POP)
        }

        override fun newLabel(): EmissionLabel = AsmLabel(Label())

        override fun mark(label: EmissionLabel) {
            mv.visitLabel(asmLabel(label))
        }

        override fun jump(label: EmissionLabel) {
            mv.visitJumpInsn(Opcodes.GOTO, asmLabel(label))
        }

        override fun jumpIfFalse(label: EmissionLabel) {
            mv.visitJumpInsn(Opcodes.IFEQ, asmLabel(label))
        }

        override fun jumpIfTrue(label: EmissionLabel) {
            mv.visitJumpInsn(Opcodes.IFNE, asmLabel(label))
        }

        override fun newLocal(type: KClass<*>): Int {
            val slot = nextLocalSlot
            nextLocalSlot += slotSize(types, type)
            return slot
        }

        override fun loadLocal(slot: Int, type: KClass<*>) {
            mv.visitVarInsn(loadOpcode(types, type), slot)
        }

        override fun storeLocal(slot: Int, type: KClass<*>) {
            mv.visitVarInsn(storeOpcode(types, type), slot)
        }

        /** Boxes the primitive on top of the stack into its wrapper type. */
        fun box(type: KClass<*>) {
            val support = types.of(type)
            val wrapper = support.wrapperInternalName ?: return // already a reference
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                wrapper,
                "valueOf",
                "(" + support.descriptor + ")L" + wrapper + ";",
                false,
            )
        }

        private fun asmLabel(label: EmissionLabel): Label =
            (label as? AsmLabel)?.label
                ?: throw ExpressionException("label was not created by this emission: $label")
    }
}

private fun descriptor(types: TypeEmissions, type: KClass<*>): String = types.of(type).descriptor

/** Reference (wrapper) descriptor used for return types declared boxed. */
private fun referenceDescriptor(types: TypeEmissions, type: KClass<*>): String =
    types.of(type).wrapperInternalName?.let { "L$it;" } ?: descriptor(types, type)

private fun internalName(type: KClass<*>): String = type.java.name.replace('.', '/')

private fun methodDescriptor(types: TypeEmissions, argTypes: List<KClass<*>>, resultType: KClass<*>?): String =
    argTypes.joinToString("", "(", ")") { descriptor(types, it) } +
        (resultType?.let { descriptor(types, it) } ?: "V")

private fun loadOpcode(types: TypeEmissions, type: KClass<*>): Int = when (types.of(type).category) {
    StackCategory.INT -> Opcodes.ILOAD
    StackCategory.LONG -> Opcodes.LLOAD
    StackCategory.FLOAT -> Opcodes.FLOAD
    StackCategory.DOUBLE -> Opcodes.DLOAD
    StackCategory.REFERENCE -> Opcodes.ALOAD
}

private fun storeOpcode(types: TypeEmissions, type: KClass<*>): Int = when (types.of(type).category) {
    StackCategory.INT -> Opcodes.ISTORE
    StackCategory.LONG -> Opcodes.LSTORE
    StackCategory.FLOAT -> Opcodes.FSTORE
    StackCategory.DOUBLE -> Opcodes.DSTORE
    StackCategory.REFERENCE -> Opcodes.ASTORE
}

private fun returnOpcode(types: TypeEmissions, type: KClass<*>): Int = when (types.of(type).category) {
    StackCategory.INT -> Opcodes.IRETURN
    StackCategory.LONG -> Opcodes.LRETURN
    StackCategory.FLOAT -> Opcodes.FRETURN
    StackCategory.DOUBLE -> Opcodes.DRETURN
    StackCategory.REFERENCE -> Opcodes.ARETURN
}

/** Local slots taken by a value of [type]: 2 for LONG/DOUBLE, else 1. */
private fun slotSize(types: TypeEmissions, type: KClass<*>): Int = when (types.of(type).category) {
    StackCategory.LONG, StackCategory.DOUBLE -> 2
    else -> 1
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

// Numeric opcodes order int, long, float, double consecutively, matching
// the StackCategory ordinals.
private fun numericOffset(types: TypeEmissions, type: KClass<*>): Int {
    val category = types.of(type).category
    if (category == StackCategory.REFERENCE) {
        throw ExpressionException("type ${type.simpleName} is not numeric")
    }
    return category.ordinal
}
