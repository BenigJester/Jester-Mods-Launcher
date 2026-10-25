package com.moodtools.hub.directpatch

import com.moodtools.hub.GmsCoreSupport
import com.moodtools.hub.LauncherGmsCoreTranslations
import com.moodtools.hub.LauncherLocalization
import com.moodtools.hub.modules.LauncherLanguage
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.MethodImplementation
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction
import org.jf.dexlib2.iface.instruction.OffsetInstruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.immutable.*
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.rewriter.*
import org.jf.dexlib2.builder.MutableMethodImplementation
import org.jf.dexlib2.builder.instruction.BuilderInstruction31c
import org.jf.dexlib2.writer.io.MemoryDataStore
import org.jf.dexlib2.writer.pool.DexPool
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class GmsCoreDexPatcherTest {
    private val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }

    @Test fun `module config controls eligibility without a package allowlist`() {
        val module = com.moodtools.hub.modules.ModuleConfig("com.example.futuregame", "Future game",
            setOf("2.0"), setOf("arm64-v8a"), "Main", "classes.dex", "libmenu_native.so", null,
            nonRootMethod = com.moodtools.hub.modules.NonRootMethod.DIRECT_PATCH, gmsCoreSupport = true)
        assertTrue(GmsCoreSupport.supports(module))
        assertFalse(GmsCoreSupport.supports(module.copy(gmsCoreSupport = false)))
        assertFalse(GmsCoreSupport.supports(module.copy(
            installedNonRootMethod = com.moodtools.hub.modules.NonRootMethod.IDENTITY_SHELL)))
        assertThrows(IllegalArgumentException::class.java) { module.copy(
            nonRootMethod = com.moodtools.hub.modules.NonRootMethod.INJECTION,
            nonRootMethods = listOf(com.moodtools.hub.modules.NonRootMethod.INJECTION)) }
        assertTrue(runCatching { GmsCoreDexPatcher.rewriteSet(mapOf("classes.dex" to ByteArray(128))) }.isFailure)
    }

    @Test fun `companion requires the trusted signer enabled state and minimum version`() {
        assertEquals(GmsCoreSupport.State.READY, GmsCoreSupport.classify(listOf(GmsCoreSupport.SIGNER), true, GmsCoreSupport.MIN_VERSION))
        assertEquals(GmsCoreSupport.State.OLD, GmsCoreSupport.classify(listOf(GmsCoreSupport.SIGNER), true, GmsCoreSupport.MIN_VERSION - 1))
        assertEquals(GmsCoreSupport.State.DISABLED, GmsCoreSupport.classify(listOf(GmsCoreSupport.SIGNER), false, GmsCoreSupport.MIN_VERSION))
        assertEquals(GmsCoreSupport.State.UNTRUSTED, GmsCoreSupport.classify(listOf("wrong"), true, Long.MAX_VALUE))
        assertEquals(GmsCoreSupport.State.UNTRUSTED, GmsCoreSupport.classify(emptyList(), true, Long.MAX_VALUE))
        assertEquals(GmsCoreSupport.State.UNTRUSTED, GmsCoreSupport.classify(listOf(GmsCoreSupport.SIGNER, "other"), true, Long.MAX_VALUE))
    }

    @Test fun `per-game toggle gates routing without weakening module eligibility or companion checks`() {
        fun source(path: String) = File(root, "app/src/$path").readText()
        val support = source("main/java/com/moodtools/hub/GmsCoreSupport.kt")
        assertTrue(support.contains("getSharedPreferences(\"gmscore_support\""))
        assertTrue(support.contains("getBoolean(packageName, false)"))
        assertTrue(support.contains("require(supports(module))"))
        val manager = source("nonroot/java/com/moodtools/hub/directpatch/DirectPackagePatchManager.kt")
        val profile = manager.substringAfter("private fun gmsProfile(): String {").substringBefore("private fun gmsRules()")
        assertTrue(profile.contains("moduleGmsCoreSupport(config, \"gmscore_support\")"))
        assertTrue(profile.contains("GmsCoreSupport.PROFILE"))
        assertTrue(profile.contains("if (!GmsCoreSupport.enabled(context, targetPackage)) return \"\""))
        assertTrue(manager.contains("val companionState = GmsCoreSupport.state(context)"))
        assertTrue(manager.contains("require(companionState == GmsCoreSupport.State.READY)"))
        assertTrue(manager.contains("GmsCoreSupport.setupFailureDetail(companionState)"))
        val ui = source("main/java/com/moodtools/hub/modules/GmsCoreSetupUi.kt")
        assertTrue(ui.contains("Switch("))
        assertTrue(ui.contains("canChange && GmsCoreSupport.supports(module)"))
        assertFalse(ui.contains("Experimental", ignoreCase = true))
        assertTrue(ui.contains("After changing this setting, update the patched game from Library."))
        assertTrue(ui.contains("Get GmsCore") && ui.contains("Open GmsCore"))
        assertFalse(LauncherGmsCoreTranslations.entries.keys.any {
            it.contains("Experimental", ignoreCase = true)
        })
        assertTrue(LauncherGmsCoreTranslations.entries.containsKey("Use GmsCore"))
    }

    @Test fun `production bootstrap packages the exact bridge entry points`() {
        val template = File(root, "diagnostic-bootstrap/build/outputs/apk/release").listFiles()!!.single { it.extension == "apk" }
        ZipFile(template).use { archive ->
            val bridge = archive.entries().asSequence().filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }
                .flatMap { entry -> DexBackedDexFile(Opcodes.getDefault(), archive.getInputStream(entry).readBytes()).classes.asSequence() }
                .single { it.type == "Lcom/moodtools/gmscore/GmsCoreBridge;" }
            assertEquals(setOf("available", "trusted"), bridge.methods.filter { it.name != "<init>" && it.name != "<clinit>" }.map { it.name }.toSet())
        }
    }

    @Test fun `all setup text is translated in every launcher language`() {
        for (key in LauncherGmsCoreTranslations.entries.keys) {
            assertEquals(key, 9, LauncherGmsCoreTranslations.entries.getValue(key).size)
            for (language in LauncherLanguage.entries.drop(1))
                assertNotEquals("$key / $language", key, LauncherLocalization.translate(key, language))
        }
    }

    private fun dex(file: File) = ZipFile(file).use { it.getInputStream(it.getEntry("classes2.dex")).readBytes() }

    @Test fun `exact game profile matches independently tested experiment and is deterministic`() {
        val original = File(root, "../../Fast_analysis/Tests/soul-knight/Update-8.7.0/base.apk")
        val experiment = File(root, "temp/gmscore-soulknight-8.7.0/build/signed/base.apk")
        // Proprietary fixtures remain local, never committed. Other tests are self-contained.
        assumeTrue("Local exact-build fixtures required", original.isFile && experiment.isFile)
        val input = dex(original)
        val config = org.json.JSONObject(File(root, "modules/com.ChillyRoom.DungeonShooter/config.json").readText())
        val rules = requireNotNull(com.moodtools.hub.modules.GmsCorePatchRules.fromJson(config, "gmscore_patch"))
        fun rewrite(bytes: ByteArray) = GmsCoreDexPatcher.rewriteSet(mapOf("classes2.dex" to bytes), rules).getValue("classes2.dex")
        assertEquals(rules.dexSha256.getValue("classes2.dex"), GmsCoreDexPatcher.hash(input))
        val output = rewrite(input)
        assertArrayEquals(output, rewrite(input))
        assertTrue(runCatching { rewrite(output) }.isFailure)
        val parsed = DexBackedDexFile(Opcodes.getDefault(), output)
        val verifier = parsed.classes.single { it.type.endsWith("/GoogleSignatureVerifier;") }
            .methods.single { it.name == "zzb" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/pm/PackageInfo;", "Z") }
        val instructions = verifier.implementation!!.instructions.toList()
        assertEquals(Opcode.INVOKE_STATIC, instructions[0].opcode)
        assertEquals(Opcode.IF_EQZ, instructions[2].opcode)
        assertEquals(3, (instructions[2] as OffsetInstruction).codeOffset)
        // Normalize const-string width and the bridge's package only; every class, method,
        // exception table, debug item, service action and unrelated instruction must match.
        val expectedCanonical = canonical(dex(experiment))
        val actualCanonical = canonical(output)
        fun classHashes(bytes: ByteArray) = DexBackedDexFile(Opcodes.getDefault(), bytes).classes.associate { cls ->
            val store = MemoryDataStore()
            DexPool.writeTo(store, ImmutableDexFile(Opcodes.getDefault(), listOf(cls)))
            cls.type to GmsCoreDexPatcher.hash(store.data.copyOf(store.size))
        }
        val expectedClasses = classHashes(expectedCanonical)
        val actualClasses = classHashes(actualCanonical)
        val changedClasses = (expectedClasses.keys + actualClasses.keys).filter { expectedClasses[it] != actualClasses[it] }
        assertTrue("Different canonical classes: $changedClasses", changedClasses.isEmpty())
        assertArrayEquals(expectedCanonical, actualCanonical)
        fun availabilityAnnotations(bytes: ByteArray) = DexBackedDexFile(Opcodes.getDefault(), bytes).classes
            .single { it.type.endsWith("/GooglePlayServicesUtilLight;") }.methods
            .single { it.name == "isGooglePlayServicesAvailable" && it.parameterTypes.size == 2 }.annotations.map(ImmutableAnnotation::of).toSet()
        assertEquals(availabilityAnnotations(input), availabilityAnnotations(output))
    }

    private fun canonical(bytes: ByteArray): ByteArray {
        val module = object : RewriterModule() {
            override fun getMethodRewriter(rewriters: Rewriters): Rewriter<Method> = Rewriter { source ->
                val method = MethodRewriter(rewriters).rewrite(source)
                // The old smali experiment dropped this method's annotations. Production preserves
                // the original annotations; that preservation is asserted separately above.
                val annotations = if (method.definingClass.endsWith("/GooglePlayServicesUtilLight;") &&
                    method.name == "isGooglePlayServicesAvailable" && method.parameterTypes.size == 2) emptySet() else method.annotations
                ImmutableMethod(method.definingClass, method.name, method.parameters, method.returnType,
                    method.accessFlags, annotations, method.hiddenApiRestrictions, method.implementation)
            }
            override fun getMethodReferenceRewriter(rewriters: Rewriters): Rewriter<MethodReference> = Rewriter { ref ->
                if (ref.definingClass == "Lcom/jester/experiment/GmsCoreBridge;")
                    ImmutableMethodReference("Lcom/moodtools/gmscore/GmsCoreBridge;", ref.name, ref.parameterTypes, ref.returnType)
                else ref
            }
            override fun getMethodImplementationRewriter(rewriters: Rewriters): Rewriter<MethodImplementation> = Rewriter { impl ->
                val rewritten = MethodImplementationRewriter(rewriters).rewrite(impl)
                val mutable = MutableMethodImplementation(rewritten)
                rewritten.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode == Opcode.CONST_STRING) mutable.replaceInstruction(index,
                        BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, (instruction as OneRegisterInstruction).registerA,
                            (instruction as ReferenceInstruction).reference))
                }
                mutable
            }
        }
        val dex = DexBackedDexFile(Opcodes.getDefault(), bytes)
        val rewriter = DexRewriter(module)
        val result = ImmutableDexFile(dex.opcodes, dex.classes.map { ImmutableClassDef.of(rewriter.classDefRewriter.rewrite(it)) })
        val store = MemoryDataStore()
        DexPool.writeTo(store, result)
        return store.data.copyOf(store.size)
    }
}
