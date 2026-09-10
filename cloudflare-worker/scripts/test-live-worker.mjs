// Comprehensive live backend API tester for jester-mods-worker
const BASE_URL = "https://jester-mods-worker.uncledrew697.workers.dev";
const ADMIN_TOKEN = "test-admin-secret-token";

let passed = 0;
let failed = 0;

function assert(condition, testName) {
  if (condition) {
    console.log(`  ✅ ${testName}`);
    passed++;
  } else {
    console.error(`  ❌ FAIL: ${testName}`);
    failed++;
  }
}

async function runLiveTests() {
  console.log(`\nTesting LIVE Worker Backend APIs at: ${BASE_URL}\n`);

  // 1. GET /admin
  try {
    const res = await fetch(`${BASE_URL}/admin`);
    const text = await res.text();
    assert(res.status === 200 && text.includes("Jester Mods Control Panel"), "GET /admin (Web Admin UI)");
  } catch (e) {
    assert(false, `GET /admin failed: ${e.message}`);
  }

  // 2. GET /launcher/unlock
  try {
    const res = await fetch(`${BASE_URL}/launcher/unlock?challenge=chal_123&deviceId=dev_test`);
    const text = await res.text();
    assert(res.status === 200 && text.includes("Activate Launcher"), "GET /launcher/unlock (Mobile Unlock UI)");
  } catch (e) {
    assert(false, `GET /launcher/unlock failed: ${e.message}`);
  }

  // 3. POST /api/launcher/proof/challenge
  let nonce = "";
  try {
    const res = await fetch(`${BASE_URL}/api/launcher/proof/challenge`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ keyId: "live-test-key", deviceId: "live-device-1" }),
    });
    const json = await res.json();
    nonce = json.nonce;
    assert(res.status === 200 && json.ok && json.nonce.length > 20, "POST /api/launcher/proof/challenge");
  } catch (e) {
    assert(false, `POST /api/launcher/proof/challenge failed: ${e.message}`);
  }

  // 4. POST /api/launcher/proof/register
  try {
    const res = await fetch(`${BASE_URL}/api/launcher/proof/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ keyId: "live-test-key", deviceId: "live-device-1", publicKey: "live-test-pubkey" }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok && json.registered === true, "POST /api/launcher/proof/register");
  } catch (e) {
    assert(false, `POST /api/launcher/proof/register failed: ${e.message}`);
  }

  // 5. POST /api/launcher/recovery/bind
  try {
    const res = await fetch(`${BASE_URL}/api/launcher/recovery/bind`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ proofKeyId: "live-test-key" }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok && json.recoveryBound === true, "POST /api/launcher/recovery/bind");
  } catch (e) {
    assert(false, `POST /api/launcher/recovery/bind failed: ${e.message}`);
  }

  // 6. POST /api/launcher/access
  try {
    const res = await fetch(`${BASE_URL}/api/launcher/access`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: "live-device-1", digitalKey: "VIP-MEMBER-ACCESS", flavor: "nonroot" }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok && json.approved && json.offlineLease.signature, "POST /api/launcher/access (Lease & Key)");
  } catch (e) {
    assert(false, `POST /api/launcher/access failed: ${e.message}`);
  }

  // 7. POST /api/launcher/recover
  try {
    const res = await fetch(`${BASE_URL}/api/launcher/recover`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        installationId: "inst_live_1",
        deviceId: "dev_live_first_launch",
        recoveryId: "rec_live_1",
        flavor: "nonroot",
        accessVersion: 4,
        proofKeyId: "key_live_first_launch",
      }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok && json.digitalKey.length >= 80, "POST /api/launcher/recover (First-Launch Auto Recover)");
  } catch (e) {
    assert(false, `POST /api/launcher/recover failed: ${e.message}`);
  }

  // 8. POST /api/launcher/redeem
  try {
    const res = await fetch(`${BASE_URL}/api/launcher/redeem`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        token: "A".repeat(43),
        challenge: "B".repeat(43),
        deviceId: "dev_live_redeem",
        proofKeyId: "key_live_redeem",
      }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok && json.recoveryBound && json.digitalKey.length >= 80, "POST /api/launcher/redeem (Unlock Redemption)");
  } catch (e) {
    assert(false, `POST /api/launcher/redeem failed: ${e.message}`);
  }

  // 9. GET /api/launcher-modules (Catalog)
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-modules`);
    const json = await res.json();
    const payload = JSON.parse(Buffer.from(json.payload, "base64").toString("utf-8"));
    const modulesValid = payload.modules.every(m => m.supportedVersions.length === m.supportedVersionCodes.length);
    assert(res.status === 200 && payload.audience === "moodtools-standalone" && modulesValid, "GET /api/launcher-modules (Signed Module Catalog)");
  } catch (e) {
    assert(false, `GET /api/launcher-modules failed: ${e.message}`);
  }

  // 10. POST /api/launcher-module (Authorization)
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-module`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ slug: "com-example-module", keyId: "live-test-key" }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok && json.capability.length >= 80, "POST /api/launcher-module (Module Authorization)");
  } catch (e) {
    assert(false, `POST /api/launcher-module failed: ${e.message}`);
  }

  // 11. GET /api/launcher-release
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-release`);
    const json = await res.json();
    const payload = JSON.parse(Buffer.from(json.payload, "base64").toString("utf-8"));
    assert(res.status === 200 && payload.audience === "moodtools-standalone-launcher", "GET /api/launcher-release (Signed App Releases)");
  } catch (e) {
    assert(false, `GET /api/launcher-release failed: ${e.message}`);
  }

  // 12. GET /api/launcher-changelog
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-changelog`);
    const json = await res.json();
    const payload = JSON.parse(Buffer.from(json.payload, "base64").toString("utf-8"));
    assert(res.status === 200 && payload.audience === "moodtools-standalone-launcher-changelog", "GET /api/launcher-changelog (App Changelog)");
  } catch (e) {
    assert(false, `GET /api/launcher-changelog failed: ${e.message}`);
  }

  // 13. GET /api/launcher-module-changelog/:slug/:build
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-module-changelog/com-example-module/1`);
    const json = await res.json();
    const payload = JSON.parse(Buffer.from(json.payload, "base64").toString("utf-8"));
    assert(res.status === 200 && payload.audience === "moodtools-standalone-module-changelog" && payload.slug === "com-example-module", "GET /api/launcher-module-changelog/:slug/:build");
  } catch (e) {
    assert(false, `GET /api/launcher-module-changelog failed: ${e.message}`);
  }

  // 14. GET /api/launcher-module-features/:slug/:build
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-module-features/com-example-module/1`);
    const json = await res.json();
    const payload = JSON.parse(Buffer.from(json.payload, "base64").toString("utf-8"));
    assert(res.status === 200 && payload.audience === "moodtools-standalone-module-features" && Array.isArray(payload.groups), "GET /api/launcher-module-features/:slug/:build");
  } catch (e) {
    assert(false, `GET /api/launcher-module-features failed: ${e.message}`);
  }

  // 15. POST /api/launcher-play-store-versions
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-play-store-versions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ packageNames: ["com.example.module"] }),
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok === true && Array.isArray(json.results) && json.results.length === 1, "POST /api/launcher-play-store-versions");
  } catch (e) {
    assert(false, `POST /api/launcher-play-store-versions failed: ${e.message}`);
  }

  // 16. GET /api/launcher-play-store-version/:packageName
  try {
    const res = await fetch(`${BASE_URL}/api/launcher-play-store-version/com.example.module`);
    const json = await res.json();
    assert(res.status === 200 && json.ok === true && json.packageName === "com.example.module", "GET /api/launcher-play-store-version/:packageName");
  } catch (e) {
    assert(false, `GET /api/launcher-play-store-version/:packageName failed: ${e.message}`);
  }

  // 17. GET /api/admin/stats (with Bearer token)
  try {
    const res = await fetch(`${BASE_URL}/api/admin/stats`, {
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok === true && json.stats.totalModules >= 2, "GET /api/admin/stats");
  } catch (e) {
    assert(false, `GET /api/admin/stats failed: ${e.message}`);
  }

  // 18. GET /api/admin/modules
  try {
    const res = await fetch(`${BASE_URL}/api/admin/modules`, {
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok === true && Array.isArray(json.modules), "GET /api/admin/modules");
  } catch (e) {
    assert(false, `GET /api/admin/modules failed: ${e.message}`);
  }

  // 19. GET /api/admin/keys
  try {
    const res = await fetch(`${BASE_URL}/api/admin/keys`, {
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok === true && Array.isArray(json.keys), "GET /api/admin/keys");
  } catch (e) {
    assert(false, `GET /api/admin/keys failed: ${e.message}`);
  }

  // 20. GET /api/admin/devices
  try {
    const res = await fetch(`${BASE_URL}/api/admin/devices`, {
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    const json = await res.json();
    assert(res.status === 200 && json.ok === true && Array.isArray(json.devices), "GET /api/admin/devices");
  } catch (e) {
    assert(false, `GET /api/admin/devices failed: ${e.message}`);
  }

  console.log(`\nLive Worker Tests Complete: ${passed} passed, ${failed} failed.\n`);
  if (failed > 0) process.exit(1);
}

runLiveTests().catch(err => {
  console.error("Live test suite crashed:", err);
  process.exit(1);
});
