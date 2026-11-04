package com.moodtools.hub.directpatch

import com.moodtools.hub.GmsCoreSupport
import com.moodtools.hub.modules.GmsCorePatchRules
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.instruction.*
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import org.jf.dexlib2.rewriter.*
import org.jf.dexlib2.writer.io.MemoryDataStore
import org.jf.dexlib2.writer.pool.DexPool
import java.security.MessageDigest

/** Known Google SDK adapter. Availability/signature shapes must be unambiguous across all DEX. */
internal object GmsCoreDexPatcher {
    private const val BASE = "Lcom/google/android/gms/"
    private const val BRIDGE = "Lcom/moodtools/gmscore/GmsCoreBridge;"
    private const val AVAILABILITY = "${BASE}common/GooglePlayServicesUtilLight;"
    private const val VERIFIER = "${BASE}common/GoogleSignatureVerifier;"
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** The caller pins the game's supported build and retains/hash-verifies each original DEX. */
    fun rewriteSet(inputs: Map<String, ByteArray>, rules: GmsCorePatchRules? = null): Map<String, ByteArray> =
        rewriteSet(inputs.keys, rules) { inputs.getValue(it) }

    /** Read one DEX at a time; retain original bytes only for DEX that actually change. */
    fun rewriteSet(names: Set<String>, rules: GmsCorePatchRules? = null,
        onChanged: (String, ByteArray) -> Unit = { _, _ -> },
        read: (String) -> ByteArray): Map<String, ByteArray> {
        require(names.isNotEmpty() && names.all { it.matches(Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) })
        rules?.dexSha256?.forEach { (name, expected) ->
            if (name !in names) throw GmsCorePatchFailure(GmsCorePatchFailure.Check.DEX_MISSING,
                name, expected, null)
        }
        val changed = linkedMapOf<String, ByteArray>()
        val counts = IntArray(4)
        names.forEach { name ->
            val bytes = read(name)
            rules?.dexSha256?.get(name)?.let { expected ->
                val actual = hash(bytes)
                if (actual != expected) throw GmsCorePatchFailure(GmsCorePatchFailure.Check.DEX_HASH_MISMATCH,
                    name, expected, actual)
            }
            val result = rewriteDex(bytes, counts, rules)
            if (result != null) {
                changed[name] = result
                onChanged(name, bytes)
            }
        }
        if (counts[0] != 1 || counts[1] != 1 || counts[2] <= 0)
            throw GmsCorePatchFailure(GmsCorePatchFailure.Check.SDK_SHAPE_UNSUPPORTED)
        if (rules != null) {
            changed.keys.firstOrNull { it !in rules.dexSha256 }?.let {
                throw GmsCorePatchFailure(GmsCorePatchFailure.Check.UNPINNED_DEX_CHANGED, it)
            }
            if (rules.accountType != null && counts[3] <= 0)
                throw GmsCorePatchFailure(GmsCorePatchFailure.Check.ACCOUNT_ROUTE_MISSING)
        }
        return changed
    }

    private fun rewriteDex(bytes: ByteArray, counts: IntArray, rules: GmsCorePatchRules?): ByteArray? {
        val source = DexBackedDexFile(Opcodes.getDefault(), bytes)
        var available = 0
        var trusted = 0
        var routes = 0
        var accountRoutes = 0
        val module = object : RewriterModule() {
            override fun getMethodRewriter(rewriters: Rewriters): Rewriter<Method> = Rewriter { method ->
                val implementation = method.implementation ?: return@Rewriter method
                val symbols = rules?.sdkSymbols
                if (!scoped(method.definingClass, rules)) return@Rewriter method
                if (implementation.instructions.any { instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? org.jf.dexlib2.iface.reference.MethodReference
                    ref?.definingClass in setOf(BRIDGE, "Lcom/jester/experiment/GmsCoreBridge;")
                }) throw GmsCorePatchFailure(GmsCorePatchFailure.Check.ALREADY_ROUTED)
                val parameters = method.parameterTypes.map { it.toString() }
                val body = when {
                    method.definingClass == (symbols?.availabilityClass ?: AVAILABILITY) &&
                        method.name == (symbols?.availabilityMethod ?: "isGooglePlayServicesAvailable") &&
                        parameters == listOf("Landroid/content/Context;", "I") && method.returnType == "I" -> {
                        available++
                        if ((method.accessFlags and 8 != 0) != (symbols?.availabilityStatic ?: true))
                            throw GmsCorePatchFailure(GmsCorePatchFailure.Check.AVAILABILITY_SHAPE_MISMATCH)
                        ImmutableMethodImplementation(3, listOf(
                            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 1, 2, 0, 0, 0,
                                ImmutableMethodReference(BRIDGE, "available", parameters, "I")),
                            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0), ImmutableInstruction11x(Opcode.RETURN, 0)
                        ), emptyList(), emptyList())
                    }
                    else -> {
                        val instructions = implementation.instructions.map { instruction ->
                            val value = ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string
                            val replacement = when (value) {
                                "com.google.android.gms", "com.google.android.gms.chimera" -> value.replace("com.google.android.gms", GmsCoreSupport.PACKAGE)
                                "com.google" -> rules?.accountType
                                else -> null
                            }
                            if (replacement != null && instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)) {
                                if (value == "com.google") accountRoutes++ else routes++
                                // Always use jumbo: changing string indices must not overflow a 16-bit const-string.
                                ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO,
                                    (instruction as OneRegisterInstruction).registerA,
                                    ImmutableStringReference(replacement))
                            } else instruction
                        }
                        // MutableMethodImplementation relocates branches, switches, debug items and try blocks
                        // when a const-string grows to jumbo. Replacing raw instruction lists would not.
                        val mutable = org.jf.dexlib2.builder.MutableMethodImplementation(implementation)
                        instructions.forEachIndexed { index, instruction ->
                            // Only the new routing instructions are immutable jumbo instances.
                            if (instruction is ImmutableInstruction31c) mutable.replaceInstruction(index,
                                    org.jf.dexlib2.builder.instruction.BuilderInstruction31c(instruction.opcode,
                                        instruction.registerA, instruction.reference))
                        }
                        if (method.definingClass == (symbols?.signatureClass ?: VERIFIER) &&
                            method.name == (symbols?.signatureMethod ?: "zzb") &&
                            parameters == listOf("Landroid/content/pm/PackageInfo;", "Z") && method.returnType == "Z") {
                            trusted++
                            if (method.accessFlags and 8 == 0 || implementation.registerCount !in 3..16)
                                throw GmsCorePatchFailure(GmsCorePatchFailure.Check.SIGNATURE_SHAPE_MISMATCH)
                            // Insert the branch after constructing its destination at the shifted original entry.
                            mutable.addInstruction(0, org.jf.dexlib2.builder.instruction.BuilderInstruction35c(
                                Opcode.INVOKE_STATIC, 1, implementation.registerCount - 2, 0, 0, 0, 0,
                                ImmutableMethodReference(BRIDGE, "trusted", listOf(parameters[0]), "Z")))
                            mutable.addInstruction(1, org.jf.dexlib2.builder.instruction.BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
                            mutable.addInstruction(2, org.jf.dexlib2.builder.instruction.BuilderInstruction11x(Opcode.RETURN, 0))
                            val originalEntry = mutable.newLabelForIndex(3)
                            mutable.addInstruction(2, org.jf.dexlib2.builder.instruction.BuilderInstruction21t(Opcode.IF_EQZ, 0, originalEntry))
                        }
                        mutable
                    }
                }
                ImmutableMethod(method.definingClass, method.name, method.parameters, method.returnType,
                    method.accessFlags, method.annotations, method.hiddenApiRestrictions, body)
            }
        }
        val rewriter = DexRewriter(module)
        // Materialize exactly once: dexlib's rewritten views are lazy and may be iterated repeatedly.
        // Do not expand unrelated game classes into immutable instruction graphs. Keep their
        // compact DEX-backed views, and do not build a DexPool at all for unchanged DEX.
        val rewritten = source.classes.map {
            if (scoped(it.type, rules)) ImmutableClassDef.of(rewriter.classDefRewriter.rewrite(it)) else it
        }
        counts[0] += available; counts[1] += trusted; counts[2] += routes
        counts[3] += accountRoutes
        if (available == 0 && trusted == 0 && routes == 0 && accountRoutes == 0) return null
        val store = MemoryDataStore()
        val pool = DexPool(source.opcodes)
        rewritten.forEach(pool::internClass)
        pool.writeTo(store)
        return store.data.copyOf(store.size)
    }

    private fun scoped(type: String, rules: GmsCorePatchRules?): Boolean =
        type in rules?.sdkSymbols?.routingClasses.orEmpty() ||
            listOf("common/", "auth/", "signin/", "games/").any { type.startsWith(BASE + it) }
}
