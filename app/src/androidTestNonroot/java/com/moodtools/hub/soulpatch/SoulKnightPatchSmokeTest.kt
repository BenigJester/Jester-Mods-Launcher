package com.moodtools.hub.soulpatch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moodtools.hub.LauncherActivity
import com.moodtools.hub.discovery.GameScanner
import com.moodtools.hub.modules.ModuleRepository
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val SOUL_KNIGHT_PACKAGE = "com.ChillyRoom.DungeonShooter"

/** Non-destructive device smoke test: prepares and verifies APKs but never uninstalls the game. */
@RunWith(AndroidJUnit4::class)
class SoulKnightPatchSmokeTest {
    @Test
    fun preparesVerifiedSplitClusterWithoutChangingInstalledGame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = ActivityScenario.launch(LauncherActivity::class.java).also { scenario ->
            scenario.onActivity { launcher ->
                launcher.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        try {
            val module = ModuleRepository(context).loadModules().single {
                it.packageName == SOUL_KNIGHT_PACKAGE
            }
            val game = GameScanner(context).scan(listOf(module)).single()
            val manager = DirectPackagePatchManager(context, SOUL_KNIGHT_PACKAGE)
            assertTrue(manager.requiresReplacement(game))

            val request = manager.prepare(game)

            assertEquals(SOUL_KNIGHT_PACKAGE, request.packageName)
            assertTrue(request.requiresUninstall)
            assertTrue(request.apks.size >= 2)
            val base = request.apks.single { it.name == "base.apk" }
            @Suppress("DEPRECATION")
            val archive = context.packageManager.getPackageArchiveInfo(
                base.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            assertNotNull(archive)
            assertEquals(
                DirectPackagePatchManager.PATCH_FACTORY,
                archive?.applicationInfo?.appComponentFactory
            )
            ZipFile(base).use { apk ->
                assertNotNull(apk.getEntry("lib/arm64-v8a/libYourSaviour.so"))
                val markerEntry = apk.getEntry("assets/moodtools/soul-patch.json")
                assertNotNull(markerEntry)
                val marker = apk.getInputStream(markerEntry).use {
                    JSONObject(String(it.readBytes(), Charsets.UTF_8))
                }
                assertEquals(1, marker.getInt("launchGuardSchema"))
                assertTrue(marker.getString("launchGuardPublicKey").isNotBlank())

                val launch = manager.authorizeLaunch(Intent(Intent.ACTION_MAIN))
                val issuedAt = launch.getLongExtra("com.moodtools.directpatch.guard.ISSUED_AT", 0L)
                val nonce = launch.getStringExtra("com.moodtools.directpatch.guard.NONCE").orEmpty()
                val signature = launch.getStringExtra("com.moodtools.directpatch.guard.SIGNATURE").orEmpty()
                val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    X509EncodedKeySpec(Base64.decode(marker.getString("launchGuardPublicKey"), Base64.NO_WRAP))
                )
                assertTrue(Signature.getInstance("SHA256withRSA").run {
                    initVerify(publicKey)
                    update(
                        "jester-direct-patch-launch-v1\n$SOUL_KNIGHT_PACKAGE\n$issuedAt\n$nonce"
                            .toByteArray(Charsets.UTF_8)
                    )
                    verify(Base64.decode(signature, Base64.NO_WRAP))
                })
            }
            assertTrue(manager.requiresReplacement(game))
        } finally {
            activity.close()
        }
    }
}
