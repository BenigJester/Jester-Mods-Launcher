export interface Env {
  LAUNCHER_KV?: KVNamespace;
  PAYLOAD_BUCKET?: R2Bucket;
  RSA_PRIVATE_KEY: string;
  ADMIN_TOKEN?: string;
}

// ============================================================================
// 1. Cryptography Engine: RSA SHA-256 Web Crypto with Canonical Base64
// ============================================================================

async function getPrivateKey(pkcs8Pem: string): Promise<CryptoKey> {
  const clean = pkcs8Pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binaryDer = Uint8Array.from(atob(clean), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

function toCanonicalBase64(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    bin += String.fromCharCode(bytes[i]);
  }
  return btoa(bin);
}

// Produces signed envelopes matching SignedEnvelopeVerifier.kt & LauncherPrivateLeaseVerifier.kt
async function signEnvelope(payloadObj: object, privateKey: CryptoKey, keyId?: string) {
  const encoder = new TextEncoder();
  const payloadBytes = encoder.encode(JSON.stringify(payloadObj));
  const signatureBytes = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    privateKey,
    payloadBytes
  );

  const envelope: any = {
    algorithm: "SHA256withRSA",
    payload: toCanonicalBase64(payloadBytes),
    signature: toCanonicalBase64(new Uint8Array(signatureBytes)),
  };
  if (keyId) {
    envelope.keyId = keyId;
  }
  return envelope;
}

function generateRandomId(bytesLength = 32): string {
  const bytes = new Uint8Array(bytesLength);
  crypto.getRandomValues(bytes);
  return toCanonicalBase64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function computeSha256UrlSafe(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return toCanonicalBase64(new Uint8Array(digest)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// ============================================================================
// 2. Main Worker Router
// ============================================================================

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method;

    if (!env.RSA_PRIVATE_KEY) {
      return new Response(
        JSON.stringify({ error: "RSA_PRIVATE_KEY is not configured in worker secrets." }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    const privateKey = await getPrivateKey(env.RSA_PRIVATE_KEY);
    const jsonHeaders = { "Content-Type": "application/json; charset=utf-8" };
    const okJson = (data: any) => new Response(JSON.stringify(data), { headers: jsonHeaders });

    // ------------------------------------------------------------------------
    // FEATURE 1: PROOF NONCES & CHALLENGES
    // ------------------------------------------------------------------------
    if (
      url.pathname === "/api/launcher/proof/challenge" ||
      url.pathname === "/api/launcher/proof/attestation/challenge"
    ) {
      const body: any = await request.clone().json().catch(() => ({}));
      const nonce = generateRandomId(32);

      if (env.LAUNCHER_KV) {
        await env.LAUNCHER_KV.put(`nonce:${nonce}`, body.keyId || "default", {
          expirationTtl: 120,
        });
      }

      return okJson({
        ok: true,
        success: true,
        nonce: nonce,
        proofVersion: 1,
        keyId: body.keyId || "key_default",
        registered: false,
        accepted: true,
        expiresAt: Math.floor(Date.now() / 1000) + 120,
      });
    }

    // ------------------------------------------------------------------------
    // FEATURE 2: PROOF REGISTRATION & ATTESTATION ACCEPTANCE
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher/proof/register") {
      const body: any = await request.json().catch(() => ({}));
      const keyId = body.proof?.keyId || body.keyId || "default-key";

      if (env.LAUNCHER_KV && body.publicKey) {
        await env.LAUNCHER_KV.put(`proof_key:${keyId}`, JSON.stringify(body));
      }

      return okJson({
        ok: true,
        registered: true,
        proofVersion: 1,
        keyId: keyId,
      });
    }

    if (url.pathname === "/api/launcher/proof/attest") {
      const body: any = await request.json().catch(() => ({}));
      return okJson({
        ok: true,
        registered: true,
        accepted: true,
        attestationVersion: 1,
        proofKeyId: body.proof?.keyId || "key",
        chainHash: body.attestation?.chainHash || "hash",
      });
    }

    if (url.pathname === "/api/launcher/recovery/bind") {
      const body: any = await request.json().catch(() => ({}));
      return okJson({
        ok: true,
        recoveryBound: true,
        proofKeyId: body.proof?.keyId || "key",
      });
    }

    // ------------------------------------------------------------------------
    // FEATURE 3: ACCESS LEASES & KEY RECOVERY
    // ------------------------------------------------------------------------
    if (
      url.pathname === "/api/launcher/access" ||
      url.pathname === "/api/launcher/recover"
    ) {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + 7 * 86400; // 7 days offline lease

      const digitalKey = body.digitalKey || ("jm_" + generateRandomId(64));
      const digitalKeySha256 = await computeSha256UrlSafe(digitalKey);

      const leasePayload = {
        schema: 1,
        audience: "moodtools-launcher-offline-lease",
        leaseVersion: 1,
        accessVersion: 4,
        proofVersion: body.proofVersion || 1,
        grantId: "grant_" + generateRandomId(20),
        deviceId: body.deviceId || "dev-01",
        flavor: body.flavor || "nonroot",
        proofKeyId: body.proofKeyId || "proof-key-id",
        digitalKeySha256: digitalKeySha256,
        issuedAt: now,
        expiresAt: expiresAt,
      };

      const offlineLease = await signEnvelope(
        leasePayload,
        privateKey,
        "launcher-lease-rsa-2026-01"
      );

      return okJson({
        ok: true,
        approved: true,
        recoveryBound: true,
        proofKeyId: body.proofKeyId || "proof-key-id",
        digitalKey: digitalKey,
        issuedAt: now,
        expiresAt: expiresAt,
        offlineLease: offlineLease,
      });
    }

    if (url.pathname === "/api/launcher/private-access") {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + 7 * 86400;
      const grantExpiresAt = expiresAt + 365 * 86400;

      const leasePayload = {
        schema: 1,
        audience: "moodtools-private-module-lease",
        leaseVersion: 1,
        accessVersion: 4,
        proofVersion: body.proofVersion || 1,
        scope: body.scope || "global",
        deviceId: body.deviceId || "dev-01",
        recoveryId: body.recoveryId || "rec-01",
        flavor: body.flavor || "nonroot",
        proofKeyId: body.proofKeyId || "proof-key-id",
        grantId: "grant_" + generateRandomId(20),
        issuedAt: now,
        expiresAt: expiresAt,
        grantExpiresAt: grantExpiresAt,
      };

      const offlineLease = await signEnvelope(
        leasePayload,
        privateKey,
        "launcher-lease-rsa-2026-01"
      );

      return okJson({
        ok: true,
        approved: true,
        recoveryBound: true,
        digitalKey: "jm_" + generateRandomId(64),
        issuedAt: now,
        expiresAt: expiresAt,
        offlineLease: offlineLease,
      });
    }

    if (url.pathname === "/api/launcher/redeem") {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + 7 * 86400;

      const digitalKey = body.digitalKey || ("jm_" + generateRandomId(64));
      const digitalKeySha256 = await computeSha256UrlSafe(digitalKey);

      const leasePayload = {
        schema: 1,
        audience: "moodtools-launcher-offline-lease",
        leaseVersion: 1,
        accessVersion: 4,
        proofVersion: body.proofVersion || 1,
        grantId: "grant_" + generateRandomId(20),
        deviceId: body.deviceId || "dev-01",
        flavor: body.flavor || "nonroot",
        proofKeyId: body.proofKeyId || "proof-key-id",
        digitalKeySha256: digitalKeySha256,
        issuedAt: now,
        expiresAt: expiresAt,
      };

      const offlineLease = await signEnvelope(
        leasePayload,
        privateKey,
        "launcher-lease-rsa-2026-01"
      );

      return okJson({
        ok: true,
        recoveryBound: true,
        proofKeyId: body.proofKeyId || "proof-key-id",
        digitalKey: digitalKey,
        issuedAt: now,
        expiresAt: expiresAt,
        offlineLease: offlineLease,
      });
    }

    // ------------------------------------------------------------------------
    // FEATURE 4: DYNAMIC MODULE CATALOGS
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher-modules") {
      const catalogData = {
        schema: 1,
        audience: "moodtools-standalone",
        modules: [
          {
            packageName: "com.example.module",
            slug: "com-example-module",
            title: "Example Game Mod",
            version: "1.0.0",
            notes: "Initial release for testing",
            category: "Other",
            tags: ["mod", "custom"],
            publishedAt: 1700000000,
            updatedAt: 1700000000,
            build: 1,
            supportedVersions: ["1.0.0", "5.4"],
            supportedVersionCodes: [100, 101],
            supportedAbis: ["arm64-v8a"],
            downloadSizeByAbi: { "arm64-v8a": 10240 },
            nonrootMethod: "injection",
            nonrootMethods: ["injection"],
            features: [],
            source: {
              path: "/api/launcher-module-payload/com.example.module/1/module.zip",
              sizeBytes: 10240,
              sha256: "0".repeat(64),
            },
          },
        ],
      };

      return okJson(await signEnvelope(catalogData, privateKey));
    }

    if (url.pathname === "/api/launcher/private-catalog") {
      const privateData = {
        schema: 1,
        audience: "moodtools-standalone-private",
        scope: "vip",
        modules: [],
      };
      const signedPrivate = await signEnvelope(privateData, privateKey);
      return okJson({
        ok: true,
        capability: generateRandomId(64),
        expiresAt: Math.floor(Date.now() / 1000) + 600,
        catalogs: [signedPrivate],
      });
    }

    // ------------------------------------------------------------------------
    // FEATURE 5: MODULE AUTHORIZATION & PAYLOAD DOWNLOADING
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher-module") {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);

      const manifestPayload = {
        schema: 1,
        audience: "moodtools-standalone",
        packageName: body.packageName || "com.example.module",
        slug: body.slug || "com-example-module",
        build: 1,
        version: "1.0.0",
        minimumBootstrap: 1,
        moduleConfig: {
          packageName: body.packageName || "com.example.module",
          dexFile: "classes.dex",
          nativeFile: "libmenu_native.so",
          title: "Example Game Mod",
          entryPoint: "com.android.support.Main",
          supportedVersions: ["1.0.0", "5.4"],
          supportedAbis: ["arm64-v8a"],
          nonrootMethod: "injection",
        },
        files: {
          dex: { path: "classes.dex", size: 4096, sha256: "0".repeat(64) },
          native: {
            "arm64-v8a": { path: "libmenu_native.so", size: 8192, sha256: "0".repeat(64) },
          },
        },
      };

      return okJson({
        ok: true,
        capability: generateRandomId(48),
        expiresAt: now + 600,
        proofRequired: true,
        proofVersion: 1,
        attestationRequired: false,
        proofKeyId: body.proof?.keyId || "test-key",
        manifest: await signEnvelope(manifestPayload, privateKey),
      });
    }

    if (url.pathname === "/api/launcher-module-proof") {
      return okJson({ ok: true, nonce: generateRandomId(32) });
    }

    if (url.pathname.startsWith("/api/launcher-module-payload/")) {
      const objectKey = url.pathname.replace("/api/launcher-module-payload/", "");
      if (env.PAYLOAD_BUCKET) {
        const object = await env.PAYLOAD_BUCKET.get(objectKey);
        if (object) {
          return new Response(object.body, {
            headers: { "Content-Type": "application/vnd.android.package-archive" },
          });
        }
      }
      return new Response("ZIP Payload Stream", { status: 200 });
    }

    // ------------------------------------------------------------------------
    // FEATURE 6: PLAY STORE COMPATIBILITY & CHANGELOGS
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher-play-store-versions") {
      return okJson({
        ok: true,
        schema: 1,
        results: [],
      });
    }

    if (url.pathname.startsWith("/api/launcher-play-store-version/")) {
      return okJson({
        ok: true,
        latestVersion: "5.4",
        latestVersionCode: 100,
        updateAvailable: false,
      });
    }

    if (url.pathname.startsWith("/api/launcher-module-changelog/")) {
      return okJson({
        ok: true,
        entries: [{ build: 1, version: "1.0.0", notes: "Initial Release" }],
      });
    }

    if (url.pathname === "/api/launcher-release" || url.pathname.startsWith("/api/launcher-test-release/")) {
      const releasePayload = {
        schema: 1,
        audience: url.pathname.startsWith("/api/launcher-test-release/")
          ? "moodtools-standalone-launcher-test"
          : "moodtools-standalone-launcher",
        build: 301,
        version: "3.0.1",
        notes: "Self-hosted Cloudflare backend",
        flavor: "nonroot",
        files: {
          root: {
            path: "/api/launcher-download/301/root.apk",
            sha256: "0".repeat(64),
            size: 15000000,
          },
          nonroot: {
            path: "/api/launcher-download/301/nonroot.apk",
            sha256: "0".repeat(64),
            size: 15000000,
          },
        },
        file: {
          path: "/api/launcher-test-download/301/nonroot.apk",
          sha256: "0".repeat(64),
          size: 15000000,
        },
      };
      return okJson(await signEnvelope(releasePayload, privateKey));
    }

    if (url.pathname === "/api/launcher-changelog") {
      const changelogPayload = {
        schema: 1,
        audience: "moodtools-standalone",
        entries: [
          {
            build: 301,
            version: "3.0.1",
            notes: "Self-hosted Cloudflare backend",
            publishedAt: 1700000000,
          },
        ],
      };
      return okJson(await signEnvelope(changelogPayload, privateKey));
    }

    // ------------------------------------------------------------------------
    // FEATURE 7: USER UNLOCK & ACTIVATION WEB UI (/launcher/unlock)
    // ------------------------------------------------------------------------
    if (url.pathname === "/launcher/unlock") {
      const challenge = url.searchParams.get("challenge") || "";
      const installationId = url.searchParams.get("installationId") || "";
      const deviceId = url.searchParams.get("deviceId") || "";

      const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Activate Jester Mods</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #0b0d14;
      --card: rgba(22, 27, 46, 0.85);
      --accent: #8b5cf6;
      --accent-glow: rgba(139, 92, 246, 0.4);
      --text: #f1f5f9;
      --muted: #94a3b8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }
    body {
      background: var(--bg);
      background-image: radial-gradient(circle at 50% 20%, #1e1b4b 0%, #0b0d14 70%);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .card {
      background: var(--card);
      backdrop-filter: blur(16px);
      border: 1px solid rgba(139, 92, 246, 0.25);
      border-radius: 1.5rem;
      padding: 2.5rem;
      width: 100%;
      max-width: 440px;
      box-shadow: 0 20px 40px -15px var(--accent-glow);
      text-align: center;
    }
    .logo {
      width: 64px;
      height: 64px;
      margin-bottom: 1.25rem;
      border-radius: 1rem;
      background: linear-gradient(135deg, #a855f7, #6366f1);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 2rem;
      box-shadow: 0 8px 24px var(--accent-glow);
    }
    h1 { font-size: 1.75rem; font-weight: 700; margin-bottom: 0.5rem; letter-spacing: -0.02em; }
    p { color: var(--muted); font-size: 0.95rem; margin-bottom: 1.75rem; line-height: 1.5; }
    .input-group { margin-bottom: 1.25rem; text-align: left; }
    label { display: block; font-size: 0.85rem; font-weight: 600; color: var(--muted); margin-bottom: 0.5rem; }
    input {
      width: 100%;
      padding: 0.85rem 1rem;
      background: rgba(11, 13, 20, 0.8);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 0.75rem;
      color: #fff;
      font-size: 1rem;
      outline: none;
      transition: all 0.2s;
    }
    input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
    .btn {
      width: 100%;
      padding: 0.95rem;
      border: none;
      border-radius: 0.75rem;
      background: linear-gradient(135deg, #8b5cf6, #6366f1);
      color: #fff;
      font-size: 1rem;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.15s, box-shadow 0.2s;
      box-shadow: 0 4px 16px var(--accent-glow);
    }
    .btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(139, 92, 246, 0.6); }
    .btn:active { transform: translateY(0); }
    .device-info {
      margin-top: 1.5rem;
      font-size: 0.75rem;
      color: #64748b;
      word-break: break-all;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">🎭</div>
    <h1>Activate Launcher</h1>
    <p>Bind this Android device to your custom server to unlock all game modules.</p>

    <div class="input-group">
      <label>Digital Key / Pass</label>
      <input type="text" id="accessKey" placeholder="Enter VIP key or leave default" value="VIP-MEMBER-ACCESS">
    </div>

    <button class="btn" onclick="activateDevice()">Activate Device Now</button>

    <div class="device-info">
      Challenge: ${challenge ? challenge.substring(0, 16) + '...' : 'None'}<br>
      Device ID: ${deviceId ? deviceId.substring(0, 16) + '...' : 'Unknown'}
    </div>
  </div>

  <script>
    function activateDevice() {
      const challenge = "${challenge}";
      const token = "token_" + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
      const deepLink = "moodtools-launcher://unlock?token=" + encodeURIComponent(token) + "&challenge=" + encodeURIComponent(challenge);
      window.location.href = deepLink;
    }
  </script>
</body>
</html>`;

      return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
    }

    // ------------------------------------------------------------------------
    // FEATURE 8: WEB ADMIN DASHBOARD (/ and /admin)
    // ------------------------------------------------------------------------
    if (url.pathname === "/" || url.pathname === "/admin") {
      const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Jester Mods Server Dashboard</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #090d16;
      --sidebar: #111827;
      --card: rgba(17, 24, 39, 0.85);
      --accent: #8b5cf6;
      --text: #f8fafc;
      --muted: #94a3b8;
      --border: rgba(255, 255, 255, 0.08);
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }
    body { background: var(--bg); color: var(--text); min-height: 100vh; padding: 2rem; }
    .container { max-width: 1000px; margin: 0 auto; }
    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; }
    .brand { display: flex; align-items: center; gap: 0.75rem; }
    .brand-icon { font-size: 2rem; }
    h1 { font-size: 1.75rem; font-weight: 700; }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.3);
      padding: 0.4rem 0.85rem;
      border-radius: 9999px;
      font-size: 0.85rem;
      font-weight: 600;
    }
    .status-dot { width: 8px; height: 8px; background: #34d399; border-radius: 50%; box-shadow: 0 0 8px #34d399; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.5rem; margin-bottom: 2rem; }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 1.25rem;
      padding: 1.75rem;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
    }
    .card h2 { font-size: 1.25rem; margin-bottom: 0.5rem; font-weight: 600; }
    .card p { color: var(--muted); font-size: 0.9rem; margin-bottom: 1.25rem; }
    .btn {
      display: inline-block;
      width: 100%;
      text-align: center;
      background: linear-gradient(135deg, #8b5cf6, #6366f1);
      color: white;
      padding: 0.75rem 1rem;
      border-radius: 0.75rem;
      font-weight: 600;
      border: none;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.2s;
    }
    .btn:hover { opacity: 0.95; transform: translateY(-1px); }
    .code-box {
      background: #000;
      border: 1px solid var(--border);
      border-radius: 0.75rem;
      padding: 0.85rem;
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.85rem;
      color: #a78bfa;
      word-break: break-all;
      margin-top: 1rem;
      display: none;
    }
    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
    th, td { padding: 0.75rem 0.5rem; text-align: left; font-size: 0.85rem; border-bottom: 1px solid var(--border); }
    th { color: var(--muted); font-weight: 600; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="brand">
        <span class="brand-icon">🎭</span>
        <div>
          <h1>Jester Mods Server</h1>
          <p style="font-size: 0.85rem; color: var(--muted);">Cloudflare Worker Backend</p>
        </div>
      </div>
      <div class="status-badge">
        <div class="status-dot"></div>
        Backend Active
      </div>
    </header>

    <div class="grid">
      <div class="card">
        <h2>Generate Digital Key</h2>
        <p>Create a fresh VIP digital access key for device activation.</p>
        <button class="btn" onclick="createKey()">Generate New Key</button>
        <div id="keyBox" class="code-box"></div>
      </div>

      <div class="card">
        <h2>Client Configuration</h2>
        <p>Update your Android project's host configuration to point to this worker.</p>
        <div style="font-family: 'JetBrains Mono', monospace; font-size: 0.8rem; color: #38bdf8; background: #000; padding: 0.75rem; border-radius: 0.5rem; border: 1px solid var(--border);">
          BASE_URL = "${url.origin}"
        </div>
        <a href="/launcher/unlock" style="margin-top: 1rem;" class="btn">Test User Unlock Screen</a>
      </div>
    </div>

    <div class="card">
      <h2>Active Module Catalog</h2>
      <p>Modules currently served by <code>/api/launcher-modules</code></p>
      <table>
        <thead>
          <tr>
            <th>Package</th>
            <th>Title</th>
            <th>Versions</th>
            <th>Method</th>
            <th>Build</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td style="font-family: monospace; color: #a78bfa;">com.example.module</td>
            <td>Example Game Mod</td>
            <td>1.0.0, 5.4</td>
            <td><span style="background: rgba(139,92,246,0.2); padding: 2px 8px; border-radius: 4px;">injection</span></td>
            <td>#1</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <script>
    function createKey() {
      const key = "jm_" + Math.random().toString(36).substring(2) + Math.random().toString(36).substring(2) + Math.random().toString(36).substring(2);
      const box = document.getElementById('keyBox');
      box.style.display = 'block';
      box.innerText = key;
    }
  </script>
</body>
</html>`;

      return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
    }

    return new Response("Not Found", { status: 404 });
  },
};
