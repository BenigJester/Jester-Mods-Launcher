import fs from "node:fs";
import path from "node:path";
import worker from "../src/index.ts";

const privateKeyPem = fs.readFileSync(
  path.join(import.meta.dirname, "../keys/private_key.pkcs8.pem"),
  "utf-8"
);

// Mock KV Store for testing
class MockKV {
  constructor() {
    this.store = new Map();
  }
  async get(key, type = "text") {
    const val = this.store.get(key);
    if (val === undefined) return null;
    if (type === "json") {
      try { return JSON.parse(val); } catch { return null; }
    }
    return val;
  }
  async put(key, value) {
    this.store.set(key, typeof value === "string" ? value : JSON.stringify(value));
  }
  async delete(key) {
    this.store.delete(key);
  }
}

const mockKv = new MockKV();
const env = {
  RSA_PRIVATE_KEY: privateKeyPem,
  LAUNCHER_KV: mockKv,
  ADMIN_TOKEN: "test-admin-secret-token",
};

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log("  ✅ " + message);
    passed++;
  } else {
    console.error("  ❌ FAIL: " + message);
    failed++;
  }
}

async function runTests() {
  console.log("Starting Full-Stack Cloudflare Worker Verification Suite...\n");

  // Test 1: HTML Admin Dashboard
  console.log("[1] Web Frontend: Admin Dashboard");
  const resDash = await worker.fetch(new Request("https://worker.test/admin"), env);
  assert(resDash.status === 200, "Dashboard returns HTTP 200");
  assert((await resDash.text()).includes("Jester Mods Control Panel"), "Dashboard contains Mission Control title");

  // Test 2: HTML Unlock Page
  console.log("\n[2] Web Frontend: Mobile Unlock UI");
  const resUnlock = await worker.fetch(new Request("https://worker.test/launcher/unlock?challenge=chal_123&deviceId=dev_456"), env);
  assert(resUnlock.status === 200, "Unlock UI returns HTTP 200");
  assert((await resUnlock.text()).includes("Digital Pass Gateway"), "Unlock UI contains Digital Pass Gateway title");

  // Test 3: Challenge Endpoint
  console.log("\n[3] Android Proof Challenge");
  const resChal = await worker.fetch(
    new Request("https://worker.test/api/launcher/proof/challenge", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ keyId: "test-key-id-123", deviceId: "device-abc" }),
    }),
    env
  );
  const jsonChal = await resChal.json();
  assert(jsonChal.ok === true && jsonChal.keyId === "test-key-id-123", "Echoes exact client keyId");
  assert(typeof jsonChal.nonce === "string" && jsonChal.nonce.length > 20, "Generates secure cryptographic nonce");

  // Test 4: Proof Register
  console.log("\n[4] Android Proof Registration");
  const resReg = await worker.fetch(
    new Request("https://worker.test/api/launcher/proof/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ keyId: "test-key-id-123", deviceId: "device-abc", publicKey: "mock-pubkey" }),
    }),
    env
  );
  const jsonReg = await resReg.json();
  assert(jsonReg.ok && jsonReg.registered && jsonReg.proofVersion === 1, "Proof registration returns registered: true & proofVersion: 1");

  // Test 5: Recovery Bind
  console.log("\n[5] Android Recovery Bind");
  const resBind = await worker.fetch(
    new Request("https://worker.test/api/launcher/recovery/bind", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ proofKeyId: "test-key-id-123" }),
    }),
    env
  );
  const jsonBind = await resBind.json();
  assert(jsonBind.ok && jsonBind.recoveryBound && jsonBind.proofKeyId === "test-key-id-123", "Recovery bind validates identity");

  // Test 6: Access & Offline Lease
  console.log("\n[6] Android Access & Offline Lease Granting");
  const resAccess = await worker.fetch(
    new Request("https://worker.test/api/launcher/access", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: "device-abc", digitalKey: "VIP-MEMBER-ACCESS", flavor: "nonroot" }),
    }),
    env
  );
  const jsonAccess = await resAccess.json();
  assert(jsonAccess.ok && jsonAccess.approved, "Digital key approved");
  assert(jsonAccess.offlineLease && jsonAccess.offlineLease.signature, "Returns signed RSA-2048 offline lease envelope");
  assert(jsonAccess.digitalKey.length >= 80 && jsonAccess.digitalKey.length <= 4096, "Digital key satisfies Android requirement 80..4096 chars");

  // Decode offline lease payload to verify audience
  const leasePayload = JSON.parse(Buffer.from(jsonAccess.offlineLease.payload, "base64").toString("utf-8"));
  assert(leasePayload.audience === "moodtools-launcher-offline-lease", "Lease payload audience matches Android client verifier");

  // Test 6b: Recover Flow on App First Launch (no digitalKey in body)
  console.log("\n[6b] Android First-Launch Recover Flow");
  const resRecover = await worker.fetch(
    new Request("https://worker.test/api/launcher/recover", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        installationId: "inst_123",
        deviceId: "dev_first_launch",
        recoveryId: "rec_123",
        flavor: "nonroot",
        accessVersion: 4,
        proofKeyId: "key_123"
      }),
    }),
    env
  );
  const jsonRecover = await resRecover.json();
  assert(jsonRecover.ok && jsonRecover.recoveryBound, "First-launch recover succeeds automatically");
  assert(jsonRecover.digitalKey.length >= 80, "Recovered digital key has length >= 80 chars");

  // Test 6c: Redeem Flow
  console.log("\n[6c] Android Unlock Redemption Flow");
  const resRedeem = await worker.fetch(
    new Request("https://worker.test/api/launcher/redeem", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        token: "A".repeat(43),
        challenge: "B".repeat(43),
        deviceId: "dev_redeem",
        proofKeyId: "key_redeem"
      }),
    }),
    env
  );
  const jsonRedeem = await resRedeem.json();
  assert(jsonRedeem.ok && jsonRedeem.recoveryBound, "Redemption returns recoveryBound");
  assert(jsonRedeem.digitalKey.length >= 80, "Redeemed digital key has length >= 80 chars");

  // Test 7: Module Catalog
  console.log("\n[7] Dynamic Module Catalog");
  const resCat = await worker.fetch(new Request("https://worker.test/api/launcher-modules"), env);
  const jsonCat = await resCat.json();
  assert(jsonCat.signature && jsonCat.payload, "Returns signed catalog envelope");
  const catPayload = JSON.parse(Buffer.from(jsonCat.payload, "base64").toString("utf-8"));
  assert(catPayload.audience === "moodtools-standalone", "Catalog payload audience matches moodtools-standalone");
  assert(Array.isArray(catPayload.modules) && catPayload.modules.length >= 2, "Returns at least 2 catalog modules");
  assert(typeof catPayload.modules[0].version === "string", "Each module includes mandatory version string");

  // Test 8: Module Authorization & Manifest
  console.log("\n[8] Module Authorization Capability & Manifest");
  const resMod = await worker.fetch(
    new Request("https://worker.test/api/launcher-module", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ slug: "opbr-bounty-rush-mod", keyId: "test-key-id-123" }),
    }),
    env
  );
  const jsonMod = await resMod.json();
  assert(jsonMod.ok && jsonMod.proofRequired === true && jsonMod.proofVersion === 1, "Module authorization requires proof v1");
  assert(jsonMod.capability.length >= 80 && /^[A-Za-z0-9_.-]+$/.test(jsonMod.capability), "Capability length is >= 80 chars matching safe regex");

  // Test 9: Launcher Release & Changelog
  console.log("\n[9] Launcher Releases & Changelog");
  const resRel = await worker.fetch(new Request("https://worker.test/api/launcher-release"), env);
  const jsonRel = await resRel.json();
  const relPayload = JSON.parse(Buffer.from(jsonRel.payload, "base64").toString("utf-8"));
  assert(relPayload.audience === "moodtools-standalone-launcher", "Release audience is moodtools-standalone-launcher");
  assert(relPayload.files && relPayload.files.root && relPayload.files.nonroot, "Release contains root & nonroot file specifications");

  const resChangelog = await worker.fetch(new Request("https://worker.test/api/launcher-changelog"), env);
  const jsonChangelog = await resChangelog.json();
  const clPayload = JSON.parse(Buffer.from(jsonChangelog.payload, "base64").toString("utf-8"));
  assert(clPayload.audience === "moodtools-standalone-launcher-changelog", "Changelog audience is moodtools-standalone-launcher-changelog");
  assert(typeof clPayload.currentBuild === "number", "Changelog includes numeric currentBuild");

  // Test 10: Module Features & Changelog
  console.log("\n[10] Module Features & Changelog");
  const resModCl = await worker.fetch(new Request("https://worker.test/api/launcher-module-changelog/opbr-bounty-rush-mod/93010"), env);
  const jsonModCl = await resModCl.json();
  const modClPayload = JSON.parse(Buffer.from(jsonModCl.payload, "base64").toString("utf-8"));
  assert(modClPayload.audience === "moodtools-standalone-module-changelog", "Module changelog audience is valid");
  assert(modClPayload.slug === "opbr-bounty-rush-mod", "Module changelog echoes slug");

  const resModFeat = await worker.fetch(new Request("https://worker.test/api/launcher-module-features/opbr-bounty-rush-mod/93010"), env);
  const jsonModFeat = await resModFeat.json();
  const modFeatPayload = JSON.parse(Buffer.from(jsonModFeat.payload, "base64").toString("utf-8"));
  assert(modFeatPayload.audience === "moodtools-standalone-module-features", "Module features audience is valid");
  assert(Array.isArray(modFeatPayload.groups) && modFeatPayload.groups.length > 0, "Module features contains groups array");

  // Test 11: Admin REST APIs
  console.log("\n[11] Admin REST APIs (Protected)");
  // Unauthenticated test
  const resUnauth = await worker.fetch(new Request("https://worker.test/api/admin/stats"), env);
  assert(resUnauth.status === 401, "Rejects request without ADMIN_TOKEN");

  // Authenticated test
  const adminHeaders = {
    Authorization: "Bearer test-admin-secret-token",
    "Content-Type": "application/json",
  };
  const resStats = await worker.fetch(new Request("https://worker.test/api/admin/stats", { headers: adminHeaders }), env);
  const jsonStats = await resStats.json();
  assert(jsonStats.ok && jsonStats.stats.totalModules >= 2, "Admin stats returns active module count");

  // Create Key via Admin
  const resNewKey = await worker.fetch(
    new Request("https://worker.test/api/admin/keys", {
      method: "POST",
      headers: adminHeaders,
      body: JSON.stringify({ tier: "lifetime", note: "VIP Patron Key", maxDevices: 5 }),
    }),
    env
  );
  const jsonNewKey = await resNewKey.json();
  assert(jsonNewKey.ok && jsonNewKey.key.tier === "lifetime", "Admin successfully generates VIP pass");

  // Toggle Key via Admin
  const resToggle = await worker.fetch(
    new Request("https://worker.test/api/admin/keys/toggle", {
      method: "POST",
      headers: adminHeaders,
      body: JSON.stringify({ key: jsonNewKey.key.key, active: false }),
    }),
    env
  );
  const jsonToggle = await resToggle.json();
  assert(jsonToggle.ok && jsonToggle.key.active === false, "Admin successfully revokes pass");

  // Verify revoked key rejection
  const resRevokedAccess = await worker.fetch(
    new Request("https://worker.test/api/launcher/access", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: "hacker-device", digitalKey: jsonNewKey.key.key }),
    }),
    env
  );
  assert(resRevokedAccess.status === 403, "Revoked pass is blocked by /api/launcher/access");

  // Add custom module via Admin
  const resAddMod = await worker.fetch(
    new Request("https://worker.test/api/admin/modules", {
      method: "POST",
      headers: adminHeaders,
      body: JSON.stringify({
        slug: "custom-test-mod",
        packageName: "com.test.mod",
        title: "Test Custom Mod",
        version: "1.2.0",
        build: 12,
        nonrootMethod: "injection",
      }),
    }),
    env
  );
  const jsonAddMod = await resAddMod.json();
  assert(jsonAddMod.ok && jsonAddMod.module.slug === "custom-test-mod", "Admin creates new module in KV");

  // Verify that new module is immediately visible in /api/launcher-modules
  const resCatAfter = await worker.fetch(new Request("https://worker.test/api/launcher-modules"), env);
  const jsonCatAfter = await resCatAfter.json();
  const catAfterPayload = JSON.parse(Buffer.from(jsonCatAfter.payload, "base64").toString("utf-8"));
  assert(catAfterPayload.modules.some(m => m.slug === "custom-test-mod"), "New module is instantly live in client catalog API");

  // List Devices via Admin
  const resDevs = await worker.fetch(new Request("https://worker.test/api/admin/devices", { headers: adminHeaders }), env);
  const jsonDevs = await resDevs.json();
  assert(jsonDevs.ok && Array.isArray(jsonDevs.devices) && jsonDevs.devices.length > 0, "Admin lists registered devices");

  console.log(`\nVerification Suite Complete: ${passed} passed, ${failed} failed.`);
  if (failed > 0) process.exit(1);
}

runTests().catch(err => {
  console.error("Test error:", err);
  process.exit(1);
});
