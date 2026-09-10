export interface Env {
  LAUNCHER_KV?: KVNamespace;
  PAYLOAD_BUCKET?: R2Bucket;
  RSA_PRIVATE_KEY: string;
  ADMIN_TOKEN?: string;
}

// ============================================================================
// 1. Data Models & Type Definitions
// ============================================================================

export interface ModuleFile {
  path: string;
  size: number;
  sha256: string;
}

export interface ModuleConfig {
  packageName: string;
  dexFile: string;
  nativeFile: string;
  title: string;
  entryPoint: string;
  supportedVersions: string[];
  supportedAbis: string[];
  nonrootMethod: string;
}

export interface ChangelogEntry {
  build: number;
  version: string;
  notes: string;
  publishedAt: number;
  updateType: string;
}

export interface FeatureGroup {
  title: string;
  features: string[];
}

export interface ModuleItem {
  packageName: string;
  slug: string;
  title: string;
  version: string;
  notes: string;
  category: string;
  tags: string[];
  publishedAt: number;
  updatedAt: number;
  build: number;
  supportedVersions: string[];
  supportedVersionCodes: number[];
  supportedAbis: string[];
  downloadSizeByAbi: Record<string, number>;
  nonrootMethod: string;
  nonrootMethods: string[];
  features: string[];
  source: {
    path: string;
    sizeBytes: number;
    sha256: string;
  };
  moduleConfig?: ModuleConfig;
  files?: {
    dex: ModuleFile;
    native?: Record<string, ModuleFile>;
  };
  changelogEntries?: ChangelogEntry[];
  featureGroups?: FeatureGroup[];
}

export interface DigitalKeyRecord {
  key: string;
  tier: "vip" | "standard" | "trial" | "lifetime";
  note: string;
  active: boolean;
  createdAt: number;
  maxDevices: number;
  boundDevices: string[];
}

export interface DeviceRecord {
  deviceId: string;
  proofKeyId: string;
  digitalKey?: string;
  flavor?: string;
  lastSeen: number;
  registeredAt: number;
}

// ============================================================================
// 2. Canonical Digital Key Helper
// Android client strictly requires: require(digitalKey.length in 80..4096)
// ============================================================================

export function canonicalizeDigitalKey(rawKey?: string): string {
  const base = (rawKey || "VIP-MEMBER-ACCESS").trim();
  if (base.length >= 80 && base.length <= 4096) {
    return base;
  }
  const suffix = "-JESTER-MODS-OFFICIAL-VALIDATED-VIP-LICENSE-KEY-2026-X";
  return (base + suffix).padEnd(88, "0").substring(0, 88);
}

const CANONICAL_VIP_KEY = canonicalizeDigitalKey("VIP-MEMBER-ACCESS");
const CANONICAL_LIFETIME_KEY = canonicalizeDigitalKey("JM-PREMIUM-2026");

// ============================================================================
// 3. Default Seed Data
// ============================================================================

const DEFAULT_MODULES: ModuleItem[] = [
  {
    packageName: "com.example.module",
    slug: "com-example-module",
    title: "Example Game Mod",
    version: "1.0.0",
    notes: "Core injection enhancement module with high-performance overlay.",
    category: "Action",
    tags: ["mod", "custom", "rootless", "v3"],
    publishedAt: 1700000000,
    updatedAt: 1700000000,
    build: 1,
    supportedVersions: ["1.0.0", "5.4"],
    supportedVersionCodes: [100, 101],
    supportedAbis: ["arm64-v8a"],
    downloadSizeByAbi: { "arm64-v8a": 10240 },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: ["Custom UI Overlay", "Memory Optimization", "Asset Override"],
    source: {
      path: "/api/launcher-module-payload/com.example.module/1/module.zip",
      sizeBytes: 10240,
      sha256: "0".repeat(64),
    },
    moduleConfig: {
      packageName: "com.example.module",
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
    changelogEntries: [
      {
        build: 1,
        version: "1.0.0",
        notes: "Initial public release with universal support.",
        publishedAt: 1700000000,
        updateType: "feature",
      },
    ],
    featureGroups: [
      {
        title: "Visuals & HUD",
        features: ["Ultra HD Texture Scaling", "Dynamic Frame Rate Unlocker", "Custom Crosshair"],
      },
      {
        title: "Performance & Stability",
        features: ["Memory Cache Cleanup", "Input Latency Reduction", "Anti-Crash Hook"],
      },
    ],
  },
  {
    packageName: "com.jester.speedbooster",
    slug: "jester-speed-booster",
    title: "Jester Velocity Engine",
    version: "2.1.0",
    notes: "Direct native physics modifier and responsiveness accelerator.",
    category: "Performance",
    tags: ["booster", "physics", "speed"],
    publishedAt: 1705000000,
    updatedAt: 1706000000,
    build: 21,
    supportedVersions: ["2.0.0", "2.1.0"],
    supportedVersionCodes: [201, 202],
    supportedAbis: ["arm64-v8a", "armeabi-v7a"],
    downloadSizeByAbi: { "arm64-v8a": 15360, "armeabi-v7a": 14200 },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: ["Clock Speed Sync", "Render Pipeline Turbo", "Battery Saver Mode"],
    source: {
      path: "/api/launcher-module-payload/com.jester.speedbooster/21/module.zip",
      sizeBytes: 15360,
      sha256: "0".repeat(64),
    },
    moduleConfig: {
      packageName: "com.jester.speedbooster",
      dexFile: "classes.dex",
      nativeFile: "libvelocity.so",
      title: "Jester Velocity Engine",
      entryPoint: "com.jester.speed.Bootstrap",
      supportedVersions: ["2.0.0", "2.1.0"],
      supportedAbis: ["arm64-v8a", "armeabi-v7a"],
      nonrootMethod: "injection",
    },
    files: {
      dex: { path: "classes.dex", size: 5120, sha256: "0".repeat(64) },
      native: {
        "arm64-v8a": { path: "libvelocity.so", size: 10240, sha256: "0".repeat(64) },
      },
    },
    changelogEntries: [
      {
        build: 21,
        version: "2.1.0",
        notes: "Support added for 120Hz refresh rates.",
        publishedAt: 1706000000,
        updateType: "feature",
      },
      {
        build: 20,
        version: "2.0.0",
        notes: "Major architecture overhaul.",
        publishedAt: 1705000000,
        updateType: "major",
      },
    ],
    featureGroups: [
      {
        title: "Frame Smoothing",
        features: ["Adaptive Buffer Throttle", "Jitter Removal"],
      },
    ],
  },
];

const DEFAULT_KEYS: DigitalKeyRecord[] = [
  {
    key: CANONICAL_VIP_KEY,
    tier: "vip",
    note: "Default Developer & Test VIP Pass (Canonical 88-char)",
    active: true,
    createdAt: 1700000000,
    maxDevices: 100,
    boundDevices: [],
  },
  {
    key: CANONICAL_LIFETIME_KEY,
    tier: "lifetime",
    note: "Lifetime Master Access Pass (Canonical 88-char)",
    active: true,
    createdAt: 1704000000,
    maxDevices: 100,
    boundDevices: [],
  },
  {
    key: "VIP-MEMBER-ACCESS",
    tier: "vip",
    note: "Default Developer & Test VIP Pass (Short Alias)",
    active: true,
    createdAt: 1700000000,
    maxDevices: 100,
    boundDevices: [],
  },
  {
    key: "JM-PREMIUM-2026",
    tier: "lifetime",
    note: "Lifetime Master Access Pass (Short Alias)",
    active: true,
    createdAt: 1704000000,
    maxDevices: 100,
    boundDevices: [],
  },
];

// In-memory fallback stores when KV is not attached in local development
const memoryModules = new Map<string, ModuleItem>();
for (const m of DEFAULT_MODULES) {
  memoryModules.set(m.slug, m);
}

const memoryKeys = new Map<string, DigitalKeyRecord>();
for (const k of DEFAULT_KEYS) {
  memoryKeys.set(k.key, k);
}

const memoryDevices = new Map<string, DeviceRecord>();
const deviceProofKeys = new Map<string, string>();

// ============================================================================
// 4. Cryptography Engine: RSA SHA-256 Web Crypto
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
  return toCanonicalBase64(bytes)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

async function computeSha256UrlSafe(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return toCanonicalBase64(new Uint8Array(digest))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// ============================================================================
// 5. Persistence Service (KV + Fallback Memory Cache)
// ============================================================================

function sanitizeModules(modules: ModuleItem[]): { list: ModuleItem[]; changed: boolean } {
  let changed = false;
  const list = modules.map((m) => {
    const copy = { ...m };
    if (copy.supportedVersions && copy.supportedVersionCodes && copy.supportedVersionCodes.length > 0) {
      if (copy.supportedVersions.length !== copy.supportedVersionCodes.length) {
        changed = true;
        if (copy.supportedVersions.length > copy.supportedVersionCodes.length) {
          copy.supportedVersions = copy.supportedVersions.slice(0, copy.supportedVersionCodes.length);
        } else {
          copy.supportedVersionCodes = copy.supportedVersionCodes.slice(0, copy.supportedVersions.length);
        }
        if (copy.moduleConfig) {
          copy.moduleConfig = {
            ...copy.moduleConfig,
            supportedVersions: [...copy.supportedVersions],
          };
        }
      }
    }
    return copy;
  });
  return { list, changed };
}

async function getStoredModules(env: Env): Promise<ModuleItem[]> {
  if (env.LAUNCHER_KV) {
    const raw = await env.LAUNCHER_KV.get("modules:catalog", "json");
    if (raw && Array.isArray(raw) && raw.length > 0) {
      const { list, changed } = sanitizeModules(raw as ModuleItem[]);
      if (changed) {
        await env.LAUNCHER_KV.put("modules:catalog", JSON.stringify(list));
      }
      return list;
    }
    // Seed KV initially if empty
    await env.LAUNCHER_KV.put("modules:catalog", JSON.stringify(DEFAULT_MODULES));
  }
  const { list } = sanitizeModules(Array.from(memoryModules.values()));
  return list;
}

async function saveStoredModules(modules: ModuleItem[], env: Env): Promise<void> {
  const { list } = sanitizeModules(modules);
  memoryModules.clear();
  for (const m of list) {
    memoryModules.set(m.slug, m);
  }
  if (env.LAUNCHER_KV) {
    await env.LAUNCHER_KV.put("modules:catalog", JSON.stringify(list));
  }
}

async function getStoredKeys(env: Env): Promise<DigitalKeyRecord[]> {
  if (env.LAUNCHER_KV) {
    const raw = await env.LAUNCHER_KV.get("keys:list", "json");
    if (raw && Array.isArray(raw) && raw.length > 0) {
      const stored = raw as DigitalKeyRecord[];
      // Self-healing: Ensure at least the canonical VIP keys exist and are active
      const hasActive = stored.some((k) => k.active);
      if (!hasActive) {
        for (const k of stored) {
          k.active = true;
        }
        await env.LAUNCHER_KV.put("keys:list", JSON.stringify(stored));
      }
      return stored;
    }
    await env.LAUNCHER_KV.put("keys:list", JSON.stringify(DEFAULT_KEYS));
  }
  return Array.from(memoryKeys.values());
}

async function saveStoredKeys(keys: DigitalKeyRecord[], env: Env): Promise<void> {
  memoryKeys.clear();
  for (const k of keys) {
    memoryKeys.set(k.key, k);
  }
  if (env.LAUNCHER_KV) {
    await env.LAUNCHER_KV.put("keys:list", JSON.stringify(keys));
  }
}

async function getStoredDevices(env: Env): Promise<DeviceRecord[]> {
  if (env.LAUNCHER_KV) {
    const raw = await env.LAUNCHER_KV.get("devices:list", "json");
    if (raw && Array.isArray(raw)) {
      return raw as DeviceRecord[];
    }
  }
  return Array.from(memoryDevices.values());
}

async function recordDeviceActivity(
  deviceId: string,
  proofKeyId: string,
  digitalKey: string | undefined,
  flavor: string | undefined,
  env: Env
): Promise<void> {
  const now = Math.floor(Date.now() / 1000);
  const devices = await getStoredDevices(env);
  const existingIdx = devices.findIndex((d) => d.deviceId === deviceId);

  const updated: DeviceRecord = {
    deviceId,
    proofKeyId,
    digitalKey: digitalKey || devices[existingIdx]?.digitalKey,
    flavor: flavor || devices[existingIdx]?.flavor || "nonroot",
    lastSeen: now,
    registeredAt: existingIdx >= 0 ? devices[existingIdx].registeredAt : now,
  };

  if (existingIdx >= 0) {
    devices[existingIdx] = updated;
  } else {
    devices.unshift(updated);
    if (devices.length > 300) devices.pop(); // Keep bounded
  }

  memoryDevices.set(deviceId, updated);
  if (env.LAUNCHER_KV) {
    await env.LAUNCHER_KV.put("devices:list", JSON.stringify(devices));
  }
}

function checkAdminAuth(request: Request, env: Env): boolean {
  if (!env.ADMIN_TOKEN) return true; // Open when no secret configured yet
  const authHeader = request.headers.get("Authorization") || "";
  const tokenFromHeader = authHeader.startsWith("Bearer ")
    ? authHeader.substring(7).trim()
    : request.headers.get("X-Admin-Token") || "";
  const url = new URL(request.url);
  const tokenFromQuery = url.searchParams.get("admin_token") || "";

  return tokenFromHeader === env.ADMIN_TOKEN || tokenFromQuery === env.ADMIN_TOKEN;
}

// ============================================================================
// 6. Main Worker Router
// ============================================================================

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method;

    // CORS Headers for dashboard & direct API testing
    const corsHeaders: Record<string, string> = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Admin-Token",
    };

    if (method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    if (!env.RSA_PRIVATE_KEY) {
      return new Response(
        JSON.stringify({
          error: "RSA_PRIVATE_KEY is not configured in worker secrets. Run 'npx wrangler secret put RSA_PRIVATE_KEY'",
        }),
        {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    const privateKey = await getPrivateKey(env.RSA_PRIVATE_KEY);
    const jsonHeaders = {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
    };
    const okJson = (data: any, status = 200) =>
      new Response(JSON.stringify(data), { status, headers: jsonHeaders });
    const errJson = (message: string, status = 400) =>
      new Response(JSON.stringify({ ok: false, error: message }), {
        status,
        headers: jsonHeaders,
      });

    // ------------------------------------------------------------------------
    // API: PROOF NONCES & CHALLENGES
    // ------------------------------------------------------------------------
    if (
      url.pathname === "/api/launcher/proof/challenge" ||
      url.pathname === "/api/launcher/proof/attestation/challenge"
    ) {
      const body: any = await request.clone().json().catch(() => ({}));
      const nonce = generateRandomId(32); // 32 bytes Base64 URL-safe without padding = 43 chars (matches ID_PATTERN)
      const keyId =
        (body.keyId as string) ||
        (body.proof?.keyId as string) ||
        generateRandomId(24);
      const deviceId = (body.deviceId as string) || "dev-01";

      if (deviceId && keyId) {
        deviceProofKeys.set(deviceId, keyId);
        if (env.LAUNCHER_KV) {
          await env.LAUNCHER_KV.put(`device_key:${deviceId}`, keyId);
        }
      }

      if (env.LAUNCHER_KV) {
        await env.LAUNCHER_KV.put(`nonce:${nonce}`, keyId, {
          expirationTtl: 120,
        });
      }

      return okJson({
        ok: true,
        success: true,
        nonce: nonce,
        proofVersion: 1,
        keyId: keyId,
        registered: false,
        accepted: true,
        expiresAt: Math.floor(Date.now() / 1000) + 120,
      });
    }

    // ------------------------------------------------------------------------
    // API: PROOF REGISTRATION & ATTESTATION ACCEPTANCE
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher/proof/register") {
      const body: any = await request.json().catch(() => ({}));
      const keyId =
        (body.proof?.keyId as string) ||
        (body.keyId as string) ||
        generateRandomId(24);
      const deviceId = (body.deviceId as string) || "dev-01";

      if (deviceId && keyId) {
        deviceProofKeys.set(deviceId, keyId);
        if (env.LAUNCHER_KV) {
          await env.LAUNCHER_KV.put(`device_key:${deviceId}`, keyId);
        }
      }

      if (env.LAUNCHER_KV && body.publicKey) {
        await env.LAUNCHER_KV.put(`proof_key:${keyId}`, JSON.stringify(body));
      }

      await recordDeviceActivity(deviceId, keyId, undefined, undefined, env);

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
      const proofKeyId =
        (body.proof?.keyId as string) ||
        (body.proofKeyId as string) ||
        generateRandomId(24);
      return okJson({
        ok: true,
        recoveryBound: true,
        proofKeyId: proofKeyId,
      });
    }

    // ------------------------------------------------------------------------
    // API: ACCESS LEASES & KEY RECOVERY
    // Android client strictly requires: require(digitalKey.length in 80..4096)
    // ------------------------------------------------------------------------
    if (
      url.pathname === "/api/launcher/access" ||
      url.pathname === "/api/launcher/recover"
    ) {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + 7 * 86400; // 7 days offline lease
      const isRecover = url.pathname === "/api/launcher/recover";

      // Ensure key is canonical length in 80..4096 to satisfy Android client requirement
      const rawDigitalKey = (body.digitalKey as string | undefined)?.trim();
      const digitalKey = canonicalizeDigitalKey(rawDigitalKey);
      const digitalKeySha256 = await computeSha256UrlSafe(digitalKey);

      // Validate digital key against KV / storage (skip rejection for recover flow so first launch succeeds)
      const keys = await getStoredKeys(env);
      const matchingKey = keys.find(
        (k) => k.key === digitalKey || (rawDigitalKey && k.key === rawDigitalKey)
      );

      // Only reject if explicit digitalKey was sent in /access and marked inactive
      if (!isRecover && rawDigitalKey && matchingKey && !matchingKey.active) {
        return errJson("Digital key has been deactivated or revoked", 403);
      }

      let proofKeyId = body.proofKeyId;
      if (!proofKeyId && body.deviceId) {
        if (env.LAUNCHER_KV) {
          proofKeyId = await env.LAUNCHER_KV.get(`device_key:${body.deviceId}`);
        }
        if (!proofKeyId) {
          proofKeyId = deviceProofKeys.get(body.deviceId);
        }
      }
      if (!proofKeyId) {
        proofKeyId = body.keyId || "key_default";
      }

      // Record device binding to key
      if (body.deviceId) {
        await recordDeviceActivity(
          body.deviceId,
          proofKeyId,
          digitalKey,
          body.flavor,
          env
        );

        if (matchingKey && !matchingKey.boundDevices.includes(body.deviceId)) {
          matchingKey.boundDevices.push(body.deviceId);
          await saveStoredKeys(keys, env);
        }
      }

      const leasePayload = {
        schema: 1,
        audience: "moodtools-launcher-offline-lease",
        leaseVersion: 1,
        accessVersion: 4,
        proofVersion: body.proofVersion || 1,
        grantId: "grant_" + generateRandomId(20),
        deviceId: body.deviceId || "dev-01",
        flavor: body.flavor || "nonroot",
        proofKeyId: proofKeyId,
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
        proofKeyId: proofKeyId,
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
        proofKeyId: body.proofKeyId || body.proof?.keyId || "proof-key-id",
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

      const capability = generateRandomId(64)
        .replace(/[^A-Za-z0-9_.]/g, "a")
        .padEnd(80, "b");
      return okJson({
        ok: true,
        approved: true,
        recoveryBound: true,
        capability: capability,
        expiresAt: expiresAt,
        offlineLease: offlineLease,
      });
    }

    if (url.pathname === "/api/launcher/redeem") {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + 7 * 86400;

      const digitalKey = canonicalizeDigitalKey(body.digitalKey);
      const digitalKeySha256 = await computeSha256UrlSafe(digitalKey);
      const proofKeyId = body.proofKeyId || body.proof?.keyId || "proof-key-id";

      if (body.deviceId) {
        await recordDeviceActivity(
          body.deviceId,
          proofKeyId,
          digitalKey,
          body.flavor,
          env
        );
      }

      const leasePayload = {
        schema: 1,
        audience: "moodtools-launcher-offline-lease",
        leaseVersion: 1,
        accessVersion: 4,
        proofVersion: body.proofVersion || 1,
        grantId: "grant_" + generateRandomId(20),
        deviceId: body.deviceId || "dev-01",
        flavor: body.flavor || "nonroot",
        proofKeyId: proofKeyId,
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
        proofKeyId: proofKeyId,
        digitalKey: digitalKey,
        issuedAt: now,
        expiresAt: expiresAt,
        offlineLease: offlineLease,
      });
    }

    // ------------------------------------------------------------------------
    // API: PRIVATE-ACCESS — ensure proofKeyId echoed at top level
    // (already handled above, keeping for docs)
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // API: DYNAMIC MODULE CATALOGS (KV-Backed)
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher-modules") {
      const modules = await getStoredModules(env);
      const catalogData = {
        schema: 1,
        audience: "moodtools-standalone",
        modules: modules.map((m) => ({
          packageName: m.packageName,
          slug: m.slug,
          title: m.title,
          version: m.version,
          notes: m.notes,
          category: m.category,
          tags: m.tags,
          publishedAt: m.publishedAt,
          updatedAt: m.updatedAt,
          build: m.build,
          supportedVersions: m.supportedVersions,
          supportedVersionCodes: m.supportedVersionCodes,
          supportedAbis: m.supportedAbis,
          downloadSizeByAbi: m.downloadSizeByAbi,
          nonrootMethod: m.nonrootMethod,
          nonrootMethods: m.nonrootMethods,
          features: m.features,
          source: m.source,
        })),
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
      const expiresAt = Math.floor(Date.now() / 1000) + 600;
      const capability = generateRandomId(64)
        .replace(/[^A-Za-z0-9_.]/g, "a")
        .padEnd(80, "b");
      return okJson({
        ok: true,
        capability: capability,
        expiresAt: expiresAt,
        catalogs: [signedPrivate],
      });
    }

    // ------------------------------------------------------------------------
    // API: MODULE AUTHORIZATION & PAYLOAD DOWNLOADING
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher-module") {
      const body: any = await request.json().catch(() => ({}));
      const now = Math.floor(Date.now() / 1000);
      const capability = generateRandomId(64)
        .replace(/[^A-Za-z0-9_.]/g, "a")
        .padEnd(80, "b");
      const proofKeyId = body.proof?.keyId || body.keyId || generateRandomId(24);

      const modules = await getStoredModules(env);
      const targetSlug = body.slug || "";
      const targetPkg = body.packageName || "";
      const matched = modules.find(
        (m) => m.slug === targetSlug || m.packageName === targetPkg
      ) || modules[0];

      const manifestPayload = {
        schema: 1,
        audience: "moodtools-standalone",
        packageName: matched.packageName,
        slug: matched.slug,
        build: matched.build,
        version: matched.version,
        minimumBootstrap: 1,
        moduleConfig: matched.moduleConfig || {
          packageName: matched.packageName,
          dexFile: "classes.dex",
          nativeFile: "libmenu_native.so",
          title: matched.title,
          entryPoint: "com.android.support.Main",
          supportedVersions: matched.supportedVersions,
          supportedAbis: matched.supportedAbis,
          nonrootMethod: matched.nonrootMethod,
        },
        files: matched.files || {
          dex: { path: "classes.dex", size: 4096, sha256: "0".repeat(64) },
          native: {
            "arm64-v8a": { path: "libmenu_native.so", size: 8192, sha256: "0".repeat(64) },
          },
        },
      };

      return okJson({
        ok: true,
        capability: capability,
        expiresAt: now + 600,
        proofRequired: true,
        proofVersion: 1,
        attestationRequired: false,
        proofKeyId: proofKeyId,
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
    // API: APK DOWNLOAD PROXY (Stable + Test channels)
    // Android LauncherUpdateClient.kt downloads from /api/launcher-download/:build/:flavor.apk
    // Trusted path validated by: isTrustedStableLauncherDownloadPath()
    // ------------------------------------------------------------------------
    if (
      url.pathname.startsWith("/api/launcher-download/") ||
      url.pathname.startsWith("/api/launcher-test-download/")
    ) {
      const isTest = url.pathname.startsWith("/api/launcher-test-download/");
      const objectKey = isTest
        ? url.pathname.replace("/api/launcher-test-download/", "launcher-test/")
        : url.pathname.replace("/api/launcher-download/", "launcher-stable/");

      if (env.PAYLOAD_BUCKET) {
        const object = await env.PAYLOAD_BUCKET.get(objectKey);
        if (object) {
          return new Response(object.body, {
            headers: {
              "Content-Type": "application/vnd.android.package-archive",
              "Content-Disposition": `attachment; filename="${objectKey.split("/").pop() || "launcher.apk"}"`,
            },
          });
        }
      }

      // No R2 bucket or object not found — return informative 404
      return new Response(
        JSON.stringify({
          ok: false,
          error: "APK not available. Upload the APK to R2 bucket at: " + objectKey,
          hint: "Run: npx wrangler r2 object put launcher-modules/" + objectKey + " --file=<path-to-apk>",
        }),
        { status: 404, headers: jsonHeaders }
      );
    }

    // ------------------------------------------------------------------------
    // API: PLAY STORE VERSIONS, CHANGELOGS & FEATURES
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher-play-store-versions") {
      const now = Math.floor(Date.now() / 1000);
      let packageNames: string[] = [];
      if (method === "POST") {
        try {
          const body: any = await request.json();
          if (Array.isArray(body.packageNames)) {
            packageNames = body.packageNames;
          }
        } catch {}
      }
      const results = packageNames.map((pkg) => ({
        ok: true,
        packageName: pkg,
        version: "5.4.0",
        versionCode: 5400,
        checkedAt: now,
        listingUpdatedAt: now - 86400,
        stale: false,
      }));
      return okJson({
        ok: true,
        schema: 1,
        results,
      });
    }

    if (url.pathname.startsWith("/api/launcher-play-store-version/")) {
      const pkg = url.pathname.replace("/api/launcher-play-store-version/", "");
      const now = Math.floor(Date.now() / 1000);
      return okJson({
        ok: true,
        schema: 1,
        packageName: pkg,
        version: "5.4.0",
        versionCode: 5400,
        checkedAt: now,
        listingUpdatedAt: now - 86400,
      });
    }

    if (url.pathname.startsWith("/api/launcher-module-changelog/")) {
      const parts = url.pathname.split("/").filter(Boolean);
      const slug = parts[2] || "com-example-module";
      const build = parseInt(parts[3] || "1", 10);

      const modules = await getStoredModules(env);
      const mod = modules.find((m) => m.slug === slug) || modules[0];

      const entries = mod.changelogEntries && mod.changelogEntries.length > 0
        ? mod.changelogEntries
        : [
            {
              build: build,
              version: mod.version,
              notes: mod.notes || "Continuous performance update.",
              publishedAt: mod.publishedAt || 1700000000,
              updateType: "feature",
            },
          ];

      const changelogPayload = {
        schema: 1,
        audience: "moodtools-standalone-module-changelog",
        slug: mod.slug,
        packageName: mod.packageName,
        currentBuild: build,
        supportedVersions: mod.supportedVersions,
        entries: entries,
      };
      return okJson(await signEnvelope(changelogPayload, privateKey));
    }

    if (url.pathname.startsWith("/api/launcher-module-features/")) {
      const parts = url.pathname.split("/").filter(Boolean);
      const slug = parts[2] || "com-example-module";
      const build = parseInt(parts[3] || "1", 10);

      const modules = await getStoredModules(env);
      const mod = modules.find((m) => m.slug === slug) || modules[0];

      const groups = mod.featureGroups && mod.featureGroups.length > 0
        ? mod.featureGroups
        : [
            {
              title: "Core Features",
              features: mod.features.length > 0 ? mod.features : ["Enhanced gameplay", "Custom UI overlays"],
            },
          ];

      const featuresPayload = {
        schema: 1,
        audience: "moodtools-standalone-module-features",
        slug: mod.slug,
        packageName: mod.packageName,
        build: build,
        groups: groups,
      };
      return okJson(await signEnvelope(featuresPayload, privateKey));
    }

    if (
      url.pathname === "/api/launcher-release" ||
      url.pathname.startsWith("/api/launcher-test-release/")
    ) {
      const isTest = url.pathname.startsWith("/api/launcher-test-release/");
      const testFlavor = isTest
        ? url.pathname.split("/").pop() || "nonroot"
        : "nonroot";
      const BUILD = 301;
      const VERSION = "3.0.1";
      const releasePayload: any = {
        schema: 1,
        audience: isTest
          ? "moodtools-standalone-launcher-test"
          : "moodtools-standalone-launcher",
        build: BUILD,
        version: VERSION,
        notes: "Self-hosted Cloudflare backend with full dynamic KV management",
      };
      if (isTest) {
        releasePayload.flavor = testFlavor;
        releasePayload.file = {
          path: `/api/launcher-test-download/${BUILD}/${testFlavor}.apk`,
          sha256: "0".repeat(64),
          size: 15000000,
        };
      } else {
        releasePayload.files = {
          root: {
            path: `/api/launcher-download/${BUILD}/root.apk`,
            sha256: "0".repeat(64),
            size: 15000000,
          },
          nonroot: {
            path: `/api/launcher-download/${BUILD}/nonroot.apk`,
            sha256: "0".repeat(64),
            size: 15000000,
          },
        };
      }
      return okJson(await signEnvelope(releasePayload, privateKey));
    }

    if (url.pathname === "/api/launcher-changelog") {
      const BUILD = 301;
      const changelogPayload = {
        schema: 1,
        audience: "moodtools-standalone-launcher-changelog",
        currentBuild: BUILD,
        entries: [
          {
            build: BUILD,
            version: "3.0.1",
            notes: "Cloudflare Worker full-stack backend with KV storage and Admin Dashboard.",
            publishedAt: 1700000000,
          },
        ],
      };
      return okJson(await signEnvelope(changelogPayload, privateKey));
    }

    // ------------------------------------------------------------------------
    // API: ADMIN REST ENDPOINTS (Protected by ADMIN_TOKEN)
    // ------------------------------------------------------------------------
    if (url.pathname.startsWith("/api/admin/")) {
      if (!checkAdminAuth(request, env)) {
        return errJson("Unauthorized: Valid ADMIN_TOKEN required.", 401);
      }

      // GET /api/admin/stats
      if (url.pathname === "/api/admin/stats" && method === "GET") {
        const modules = await getStoredModules(env);
        const keys = await getStoredKeys(env);
        const devices = await getStoredDevices(env);
        return okJson({
          ok: true,
          stats: {
            totalModules: modules.length,
            totalKeys: keys.length,
            activeKeys: keys.filter((k) => k.active).length,
            totalDevices: devices.length,
            kvConfigured: !!env.LAUNCHER_KV,
            r2Configured: !!env.PAYLOAD_BUCKET,
            adminTokenConfigured: !!env.ADMIN_TOKEN,
            serverTime: Math.floor(Date.now() / 1000),
          },
        });
      }

      // GET /api/admin/modules
      if (url.pathname === "/api/admin/modules" && method === "GET") {
        const modules = await getStoredModules(env);
        return okJson({ ok: true, modules });
      }

      // POST /api/admin/modules (Create or Update)
      if (url.pathname === "/api/admin/modules" && method === "POST") {
        const body: any = await request.json().catch(() => ({}));
        if (!body.slug || !body.packageName || !body.title) {
          return errJson("Missing required module fields: slug, packageName, title");
        }

        const modules = await getStoredModules(env);
        const now = Math.floor(Date.now() / 1000);
        const existingIdx = modules.findIndex((m) => m.slug === body.slug);

        const newModule: ModuleItem = {
          packageName: body.packageName,
          slug: body.slug,
          title: body.title,
          version: body.version || "1.0.0",
          notes: body.notes || "",
          category: body.category || "General",
          tags: Array.isArray(body.tags) ? body.tags : ["custom"],
          publishedAt: existingIdx >= 0 ? modules[existingIdx].publishedAt : now,
          updatedAt: now,
          build: Number(body.build) || 1,
          supportedVersions: Array.isArray(body.supportedVersions)
            ? body.supportedVersions
            : [body.version || "1.0.0"],
          supportedVersionCodes: Array.isArray(body.supportedVersionCodes)
            ? body.supportedVersionCodes
            : [100],
          supportedAbis: Array.isArray(body.supportedAbis)
            ? body.supportedAbis
            : ["arm64-v8a"],
          downloadSizeByAbi: body.downloadSizeByAbi || { "arm64-v8a": 10240 },
          nonrootMethod: body.nonrootMethod || "injection",
          nonrootMethods: body.nonrootMethods || ["injection"],
          features: Array.isArray(body.features) ? body.features : [],
          source: body.source || {
            path: `/api/launcher-module-payload/${body.packageName}/${body.build || 1}/module.zip`,
            sizeBytes: 10240,
            sha256: "0".repeat(64),
          },
          moduleConfig: body.moduleConfig || {
            packageName: body.packageName,
            dexFile: "classes.dex",
            nativeFile: "libmenu_native.so",
            title: body.title,
            entryPoint: "com.android.support.Main",
            supportedVersions: body.supportedVersions || [body.version || "1.0.0"],
            supportedAbis: body.supportedAbis || ["arm64-v8a"],
            nonrootMethod: body.nonrootMethod || "injection",
          },
          files: body.files || {
            dex: { path: "classes.dex", size: 4096, sha256: "0".repeat(64) },
            native: {
              "arm64-v8a": { path: "libmenu_native.so", size: 8192, sha256: "0".repeat(64) },
            },
          },
          changelogEntries: body.changelogEntries || [
            {
              build: Number(body.build) || 1,
              version: body.version || "1.0.0",
              notes: body.notes || "Initial release.",
              publishedAt: now,
              updateType: "feature",
            },
          ],
          featureGroups: body.featureGroups || [
            {
              title: "General",
              features: Array.isArray(body.features) ? body.features : ["Standard Features"],
            },
          ],
        };

        if (existingIdx >= 0) {
          modules[existingIdx] = newModule;
        } else {
          modules.push(newModule);
        }

        await saveStoredModules(modules, env);
        return okJson({ ok: true, module: newModule });
      }

      // DELETE /api/admin/modules/:slug
      if (url.pathname.startsWith("/api/admin/modules/") && method === "DELETE") {
        const slug = url.pathname.replace("/api/admin/modules/", "");
        let modules = await getStoredModules(env);
        modules = modules.filter((m) => m.slug !== slug);
        await saveStoredModules(modules, env);
        return okJson({ ok: true, deletedSlug: slug });
      }

      // GET /api/admin/keys
      if (url.pathname === "/api/admin/keys" && method === "GET") {
        const keys = await getStoredKeys(env);
        return okJson({ ok: true, keys });
      }

      // POST /api/admin/keys (Generate New Canonical 88-char Key)
      if (url.pathname === "/api/admin/keys" && method === "POST") {
        const body: any = await request.json().catch(() => ({}));
        const tier = body.tier || "vip";
        const note = body.note || "Generated via Admin Dashboard";
        const maxDevices = Number(body.maxDevices) || 3;
        const keyPrefix = tier === "lifetime" ? "JM-LIFE-" : tier === "vip" ? "JM-VIP-" : "JM-KEY-";
        const randomPart = generateRandomId(48).replace(/[^A-Za-z0-9]/g, "A").substring(0, 60);
        const generatedKey = canonicalizeDigitalKey(body.customKey || `${keyPrefix}${randomPart}`);

        const newKeyRecord: DigitalKeyRecord = {
          key: generatedKey,
          tier: tier,
          note: note,
          active: true,
          createdAt: Math.floor(Date.now() / 1000),
          maxDevices: maxDevices,
          boundDevices: [],
        };

        const keys = await getStoredKeys(env);
        keys.unshift(newKeyRecord);
        await saveStoredKeys(keys, env);
        return okJson({ ok: true, key: newKeyRecord });
      }

      // POST /api/admin/keys/toggle
      if (url.pathname === "/api/admin/keys/toggle" && method === "POST") {
        const body: any = await request.json().catch(() => ({}));
        const targetKey = body.key;
        const keys = await getStoredKeys(env);
        const record = keys.find((k) => k.key === targetKey);
        if (!record) return errJson("Key not found", 404);
        record.active = body.active !== undefined ? !!body.active : !record.active;
        await saveStoredKeys(keys, env);
        return okJson({ ok: true, key: record });
      }

      // DELETE /api/admin/keys/:key
      if (url.pathname.startsWith("/api/admin/keys/") && method === "DELETE") {
        const targetKey = decodeURIComponent(url.pathname.replace("/api/admin/keys/", ""));
        let keys = await getStoredKeys(env);
        keys = keys.filter((k) => k.key !== targetKey);
        await saveStoredKeys(keys, env);
        return okJson({ ok: true, deletedKey: targetKey });
      }

      // GET /api/admin/devices
      if (url.pathname === "/api/admin/devices" && method === "GET") {
        const devices = await getStoredDevices(env);
        return okJson({ ok: true, devices });
      }

      // DELETE /api/admin/devices/:deviceId
      if (url.pathname.startsWith("/api/admin/devices/") && method === "DELETE") {
        const deviceId = decodeURIComponent(url.pathname.replace("/api/admin/devices/", ""));
        let devices = await getStoredDevices(env);
        devices = devices.filter((d) => d.deviceId !== deviceId);
        if (env.LAUNCHER_KV) {
          await env.LAUNCHER_KV.put("devices:list", JSON.stringify(devices));
          await env.LAUNCHER_KV.delete(`device_key:${deviceId}`);
        }
        memoryDevices.delete(deviceId);
        deviceProofKeys.delete(deviceId);
        return okJson({ ok: true, unbindDeviceId: deviceId });
      }

      // POST /api/admin/seed (Reset Catalog to default)
      if (url.pathname === "/api/admin/seed" && method === "POST") {
        await saveStoredModules(DEFAULT_MODULES, env);
        await saveStoredKeys(DEFAULT_KEYS, env);
        return okJson({ ok: true, message: "Catalog and Keys seeded with defaults" });
      }

      // GET /api/admin/releases — current OTA release manifest info
      if (url.pathname === "/api/admin/releases" && method === "GET") {
        const BUILD = 301;
        const VERSION = "3.0.1";
        const r2Configured = !!env.PAYLOAD_BUCKET;
        return okJson({
          ok: true,
          release: {
            build: BUILD,
            version: VERSION,
            stableEndpoint: "/api/launcher-release",
            changelogEndpoint: "/api/launcher-changelog",
            downloads: {
              root: {
                endpoint: `/api/launcher-download/${BUILD}/root.apk`,
                r2Key: `launcher-stable/${BUILD}/root.apk`,
                available: r2Configured,
              },
              nonroot: {
                endpoint: `/api/launcher-download/${BUILD}/nonroot.apk`,
                r2Key: `launcher-stable/${BUILD}/nonroot.apk`,
                available: r2Configured,
              },
            },
            r2Configured,
            uploadHint: r2Configured
              ? null
              : "Enable R2 by uncommenting [[r2_buckets]] in wrangler.toml, then upload APKs with: npx wrangler r2 object put launcher-modules/launcher-stable/<build>/<flavor>.apk --file=<path>",
          },
        });
      }

      // POST /api/admin/release/publish — update the build number & version in KV
      if (url.pathname === "/api/admin/release/publish" && method === "POST") {
        const body: any = await request.json().catch(() => ({}));
        const build = Number(body.build) || 301;
        const version = String(body.version || "3.0.1");
        const notes = String(body.notes || "");
        if (env.LAUNCHER_KV) {
          await env.LAUNCHER_KV.put(
            "release:current",
            JSON.stringify({ build, version, notes, publishedAt: Math.floor(Date.now() / 1000) })
          );
        }
        return okJson({ ok: true, published: { build, version, notes } });
      }

      return errJson("Admin route not found", 404);
    }

    // ------------------------------------------------------------------------
    // USER UNLOCK & ACTIVATION WEB UI (/launcher/unlock)
    // Android requirement: tokens and challenges MUST match [A-Za-z0-9_-]{43}
    // ------------------------------------------------------------------------
    if (url.pathname === "/launcher/unlock") {
      const challenge = url.searchParams.get("challenge") || "";
      const deviceId = url.searchParams.get("deviceId") || "";

      const html = `<!DOCTYPE html>
<html lang="en" style="color-scheme: dark;">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Activate Jester Mods</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
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
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid rgba(139, 92, 246, 0.25);
      border-radius: 1.5rem;
      padding: 2.5rem;
      width: 100%;
      max-width: 450px;
      box-shadow: 0 20px 40px -15px var(--accent-glow);
      text-align: center;
    }
    .logo {
      width: 68px;
      height: 68px;
      margin: 0 auto 1.25rem;
      border-radius: 1.25rem;
      background: linear-gradient(135deg, #a855f7, #6366f1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2.2rem;
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
      margin-bottom: 0.75rem;
    }
    .btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(139, 92, 246, 0.6); }
    .btn-secondary {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      box-shadow: none;
    }
    .btn-secondary:hover { background: rgba(255, 255, 255, 0.1); box-shadow: none; }
    .device-info {
      margin-top: 1.5rem;
      font-size: 0.75rem;
      color: #64748b;
      word-break: break-all;
      line-height: 1.6;
      background: rgba(0, 0, 0, 0.25);
      padding: 0.75rem;
      border-radius: 0.5rem;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }
    .status-msg {
      margin-top: 1rem;
      padding: 0.75rem;
      border-radius: 0.5rem;
      font-size: 0.85rem;
      display: none;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">🎭</div>
    <h1>Activate Launcher</h1>
    <p>Bind this Android device to your custom server to unlock full mod catalog capabilities.</p>

    <div class="input-group">
      <label for="accessKey">Digital VIP Key</label>
      <input type="text" id="accessKey" placeholder="e.g. VIP-MEMBER-ACCESS" value="VIP-MEMBER-ACCESS">
    </div>

    <button class="btn" onclick="activateDevice()">Launch In Jester App</button>
    <button class="btn btn-secondary" onclick="copyDeepLink()">Copy Activation Link</button>

    <div id="statusMsg" class="status-msg"></div>

    <div class="device-info">
      <div><strong>Challenge:</strong> ${challenge ? challenge.substring(0, 20) + "..." : "Auto-Generated"}</div>
      <div><strong>Device ID:</strong> ${deviceId ? deviceId.substring(0, 20) + "..." : "Auto-Detected"}</div>
    </div>
  </div>

  <script>
    function genId43() {
      const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
      const bytes = new Uint8Array(43);
      crypto.getRandomValues(bytes);
      let res = "";
      for (let i = 0; i < 43; i++) {
        res += chars[bytes[i] % chars.length];
      }
      return res;
    }

    function getDeepLink() {
      const challengeParam = "${challenge}";
      // Android ID_PATTERN is Regex("[A-Za-z0-9_-]{43}")
      const challenge = (challengeParam && challengeParam.length === 43 && /^[A-Za-z0-9_-]{43}$/.test(challengeParam))
        ? challengeParam
        : genId43();
      const token = genId43();
      return "moodtools-launcher://unlock?token=" + encodeURIComponent(token) + "&challenge=" + encodeURIComponent(challenge);
    }

    function activateDevice() {
      const deepLink = getDeepLink();
      const status = document.getElementById("statusMsg");
      status.style.display = "block";
      status.style.background = "rgba(139, 92, 246, 0.15)";
      status.style.color = "#c4b5fd";
      status.innerText = "Redirecting to Jester Launcher app...";
      window.location.href = deepLink;
    }

    async function copyDeepLink() {
      const deepLink = getDeepLink();
      await navigator.clipboard.writeText(deepLink);
      const status = document.getElementById("statusMsg");
      status.style.display = "block";
      status.style.background = "rgba(16, 185, 129, 0.15)";
      status.style.color = "#6ee7b7";
      status.innerText = "Activation deep-link copied to clipboard!";
      setTimeout(() => { status.style.display = "none"; }, 3000);
    }
  </script>
</body>
</html>`;

      return new Response(html, {
        headers: { "Content-Type": "text/html; charset=utf-8" },
      });
    }

    // ------------------------------------------------------------------------
    // WEB ADMIN DASHBOARD SPA (/ and /admin)
    // ------------------------------------------------------------------------
    if (url.pathname === "/" || url.pathname === "/admin") {
      const origin = url.origin;
      const html = `<!DOCTYPE html>
<html lang="en" style="color-scheme: dark;">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Jester Mods Launcher — Cloudflare Mission Control</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #07090e;
      --surface: #0e121b;
      --card: rgba(17, 24, 39, 0.75);
      --card-border: rgba(255, 255, 255, 0.08);
      --card-hover: rgba(255, 255, 255, 0.12);
      --accent: #8b5cf6;
      --accent-strong: #7c3aed;
      --accent-glow: rgba(139, 92, 246, 0.35);
      --indigo: #6366f1;
      --success: #10b981;
      --danger: #ef4444;
      --warning: #f59e0b;
      --text: #f8fafc;
      --muted: #94a3b8;
      --mono: 'JetBrains Mono', monospace;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }
    body {
      background: var(--bg);
      color: var(--text);
      min-height: 100vh;
      background-image: 
        radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.12) 0px, transparent 50%),
        radial-gradient(at 100% 0%, rgba(139, 92, 246, 0.15) 0px, transparent 50%),
        radial-gradient(at 50% 100%, rgba(16, 185, 129, 0.05) 0px, transparent 50%);
      display: flex;
      flex-direction: column;
    }
    .wrapper { max-width: 1200px; margin: 0 auto; width: 100%; padding: 1.5rem; flex: 1; }
    
    /* Top Header */
    header {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid var(--card-border);
      margin-bottom: 1.75rem;
    }
    .brand { display: flex; align-items: center; gap: 0.85rem; }
    .brand-icon {
      width: 44px;
      height: 44px;
      background: linear-gradient(135deg, #a855f7, #6366f1);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.5rem;
      box-shadow: 0 4px 16px var(--accent-glow);
    }
    .brand h1 { font-size: 1.4rem; font-weight: 700; letter-spacing: -0.02em; }
    .brand p { font-size: 0.8rem; color: var(--muted); }

    .header-actions { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(16, 185, 129, 0.12);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.25);
      padding: 0.4rem 0.8rem;
      border-radius: 9999px;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .status-dot { width: 7px; height: 7px; background: #34d399; border-radius: 50%; box-shadow: 0 0 8px #34d399; }

    /* Navigation Tabs */
    .tabs {
      display: flex;
      gap: 0.5rem;
      overflow-x: auto;
      margin-bottom: 1.75rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid var(--card-border);
    }
    .tab-btn {
      background: transparent;
      border: none;
      color: var(--muted);
      padding: 0.65rem 1rem;
      font-size: 0.9rem;
      font-weight: 600;
      border-radius: 0.6rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      transition: all 0.2s ease;
      white-space: nowrap;
    }
    .tab-btn:hover { color: var(--text); background: rgba(255, 255, 255, 0.05); }
    .tab-btn.active {
      color: #fff;
      background: rgba(139, 92, 246, 0.2);
      border: 1px solid rgba(139, 92, 246, 0.4);
      box-shadow: 0 2px 10px rgba(139, 92, 246, 0.2);
    }

    /* Tab Sections */
    .tab-pane { display: none; }
    .tab-pane.active { display: block; animation: fadeIn 0.25s ease forwards; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

    /* Cards & Grids */
    .grid-4 { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .grid-2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 1.25rem; margin-bottom: 1.5rem; }
    
    .card {
      background: var(--card);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid var(--card-border);
      border-radius: 1rem;
      padding: 1.25rem 1.5rem;
      box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.5);
    }
    .stat-title { font-size: 0.8rem; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem; }
    .stat-val { font-size: 2rem; font-weight: 800; color: #fff; line-height: 1; }
    .stat-sub { font-size: 0.75rem; color: #64748b; margin-top: 0.5rem; }

    /* Tables */
    .table-container { overflow-x: auto; margin-top: 1rem; }
    table { width: 100%; border-collapse: collapse; text-align: left; }
    th {
      background: rgba(0, 0, 0, 0.3);
      padding: 0.75rem 0.85rem;
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--muted);
      border-bottom: 1px solid var(--card-border);
      white-space: nowrap;
    }
    td {
      padding: 0.85rem;
      font-size: 0.85rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      vertical-align: middle;
    }
    tr:hover td { background: rgba(255, 255, 255, 0.02); }

    /* Badges */
    .badge {
      display: inline-flex;
      align-items: center;
      padding: 0.2rem 0.55rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .badge-purple { background: rgba(139, 92, 246, 0.2); color: #c4b5fd; border: 1px solid rgba(139, 92, 246, 0.3); }
    .badge-green { background: rgba(16, 185, 129, 0.15); color: #6ee7b7; border: 1px solid rgba(16, 185, 129, 0.25); }
    .badge-red { background: rgba(239, 68, 68, 0.15); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.25); }
    .badge-indigo { background: rgba(99, 102, 241, 0.15); color: #a5b4fc; border: 1px solid rgba(99, 102, 241, 0.25); }

    /* Buttons & Inputs */
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.4rem;
      background: linear-gradient(135deg, var(--accent), var(--indigo));
      color: #fff;
      border: none;
      padding: 0.55rem 1rem;
      border-radius: 0.6rem;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      box-shadow: 0 4px 12px var(--accent-glow);
      text-decoration: none;
    }
    .btn:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(139, 92, 246, 0.5); }
    .btn:active { transform: translateY(0); }
    .btn-sm { padding: 0.35rem 0.65rem; font-size: 0.75rem; border-radius: 0.45rem; }
    .btn-outline {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--card-border);
      box-shadow: none;
      color: var(--text);
    }
    .btn-outline:hover { background: rgba(255, 255, 255, 0.1); border-color: rgba(255, 255, 255, 0.2); box-shadow: none; }
    .btn-danger { background: rgba(239, 68, 68, 0.2); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.3); box-shadow: none; }
    .btn-danger:hover { background: rgba(239, 68, 68, 0.4); }

    input, select, textarea {
      width: 100%;
      background: rgba(0, 0, 0, 0.4);
      border: 1px solid var(--card-border);
      border-radius: 0.55rem;
      padding: 0.65rem 0.85rem;
      color: #fff;
      font-size: 0.85rem;
      outline: none;
      transition: all 0.2s;
    }
    input:focus, select:focus, textarea:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-glow); }
    label { display: block; font-size: 0.8rem; font-weight: 600; color: var(--muted); margin-bottom: 0.35rem; }
    .form-group { margin-bottom: 1rem; }

    /* Code & Pre */
    .code-pill {
      font-family: var(--mono);
      font-size: 0.8rem;
      background: rgba(0, 0, 0, 0.4);
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      color: #c4b5fd;
      border: 1px solid rgba(255, 255, 255, 0.06);
    }
    pre {
      background: #05070a;
      border: 1px solid var(--card-border);
      padding: 1rem;
      border-radius: 0.6rem;
      font-family: var(--mono);
      font-size: 0.8rem;
      color: #38bdf8;
      overflow-x: auto;
      white-space: pre-wrap;
    }

    /* Modal Dialog */
    dialog {
      margin: auto;
      background: #0f131f;
      color: var(--text);
      border: 1px solid rgba(139, 92, 246, 0.3);
      border-radius: 1.25rem;
      padding: 2rem;
      max-width: 580px;
      width: 90%;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.85);
      backdrop-filter: blur(24px);
    }
    dialog::backdrop { background: rgba(0, 0, 0, 0.7); backdrop-filter: blur(6px); }

    /* Toast */
    #toast {
      position: fixed;
      bottom: 2rem;
      right: 2rem;
      background: #1e1b4b;
      border: 1px solid #8b5cf6;
      color: #fff;
      padding: 0.75rem 1.25rem;
      border-radius: 0.75rem;
      font-size: 0.85rem;
      font-weight: 500;
      box-shadow: 0 10px 25px rgba(0, 0, 0, 0.6);
      display: none;
      z-index: 9999;
      animation: slideUp 0.2s ease forwards;
    }
    @keyframes slideUp { from { transform: translateY(12px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
  </style>
</head>
<body>
  <div class="wrapper">
    <header>
      <div class="brand">
        <div class="brand-icon">🎭</div>
        <div>
          <h1>Jester Mods Control Panel</h1>
          <p>Cloudflare Serverless Engine • Dynamic KV Backend</p>
        </div>
      </div>
      <div class="header-actions">
        <div class="status-badge">
          <div class="status-dot"></div>
          <span id="backendStatus">Live & Verified</span>
        </div>
        <button class="btn btn-outline btn-sm" onclick="openTokenDialog()">
          🔑 <span id="tokenLabel">Admin Auth</span>
        </button>
        <button class="btn btn-sm" onclick="seedDefaultData()">
          ⚡ Quick Seed
        </button>
      </div>
    </header>

    <!-- Navigation Tabs -->
    <div class="tabs">
      <button class="tab-btn active" onclick="switchTab('overview')">📊 Overview</button>
      <button class="tab-btn" onclick="switchTab('modules')">📦 Module Catalog</button>
      <button class="tab-btn" onclick="switchTab('keys')">🔑 Digital Passes</button>
      <button class="tab-btn" onclick="switchTab('devices')">📱 Device Registry</button>
      <button class="tab-btn" onclick="switchTab('releases')">🚀 OTA Releases</button>
      <button class="tab-btn" onclick="switchTab('api')">🧪 API Inspector</button>
    </div>

    <!-- TAB 1: OVERVIEW -->
    <div id="tab-overview" class="tab-pane active">
      <div class="grid-4">
        <div class="card">
          <div class="stat-title">Active Modules</div>
          <div class="stat-val" id="statModules">-</div>
          <div class="stat-sub">Served by /api/launcher-modules</div>
        </div>
        <div class="card">
          <div class="stat-title">Digital Passes</div>
          <div class="stat-val" id="statKeys">-</div>
          <div class="stat-sub">Active VIP & Standard licenses</div>
        </div>
        <div class="card">
          <div class="stat-title">Connected Devices</div>
          <div class="stat-val" id="statDevices">-</div>
          <div class="stat-sub">Bound proof keys & active leases</div>
        </div>
        <div class="card">
          <div class="stat-title">Cryptographic Engine</div>
          <div class="stat-val" style="color: #a78bfa; font-size: 1.6rem;">RSA-2048</div>
          <div class="stat-sub">RSASSA-PKCS1-v1_5 SHA-256</div>
        </div>
      </div>

      <div class="grid-2">
        <div class="card">
          <h2 style="font-size: 1.1rem; margin-bottom: 0.75rem;">Android Client Configuration</h2>
          <p style="font-size: 0.85rem; color: var(--muted); margin-bottom: 1rem;">
            All 10 Android networking clients connect directly to this Cloudflare Worker instance.
          </p>
          <div style="margin-bottom: 0.75rem;">
            <label>Current Base URL</label>
            <div style="display: flex; gap: 0.5rem;">
              <input type="text" readonly value="${origin}" id="baseUrlInput">
              <button class="btn btn-outline btn-sm" onclick="copyText(document.getElementById('baseUrlInput').value)">Copy</button>
            </div>
          </div>
          <div style="display: flex; gap: 0.5rem; margin-top: 1.25rem;">
            <a href="/launcher/unlock" class="btn btn-outline btn-sm" target="_blank">Open Mobile Unlock UI ↗</a>
            <button class="btn btn-outline btn-sm" onclick="testCatalogEndpoint()">Verify Signed Modules ↗</button>
          </div>
        </div>

        <div class="card">
          <h2 style="font-size: 1.1rem; margin-bottom: 0.75rem;">System Diagnostics</h2>
          <div id="diagInfo" style="font-size: 0.85rem; line-height: 1.8; color: var(--muted);">
            <div>KV Storage Binding: <span class="badge badge-green" id="diagKv">Checking...</span></div>
            <div>R2 Payload Storage: <span class="badge badge-indigo" id="diagR2">Checking...</span></div>
            <div>Admin Protection: <span class="badge badge-purple" id="diagAuth">Checking...</span></div>
            <div>Offline Lease Duration: <span style="color: #fff;">7 Days (Self-Validating)</span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- TAB 2: MODULE CATALOG -->
    <div id="tab-modules" class="tab-pane">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
        <div>
          <h2 style="font-size: 1.2rem; font-weight: 700;">Installed Modules</h2>
          <p style="font-size: 0.8rem; color: var(--muted);">These modules are signed and delivered to the Jester Android app.</p>
        </div>
        <button class="btn btn-sm" onclick="openModuleModal()">+ Add New Module</button>
      </div>

      <div class="card" style="padding: 0.5rem;">
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Package Name</th>
                <th>Title</th>
                <th>Version</th>
                <th>Build</th>
                <th>Non-Root Method</th>
                <th>ABI Support</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="modulesTableBody">
              <tr><td colspan="7" style="text-align: center; color: var(--muted);">Loading catalog...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- TAB 3: DIGITAL KEYS -->
    <div id="tab-keys" class="tab-pane">
      <div class="grid-2">
        <div class="card">
          <h2 style="font-size: 1.1rem; margin-bottom: 0.5rem;">Generate Digital VIP Pass</h2>
          <p style="font-size: 0.8rem; color: var(--muted); margin-bottom: 1rem;">Create activation keys for users to bind in the Jester Launcher.</p>
          
          <div class="form-group">
            <label>License Tier</label>
            <select id="keyTier">
              <option value="vip">VIP Member (Full Catalog)</option>
              <option value="lifetime">Lifetime Master (Unrestricted)</option>
              <option value="standard">Standard Pass</option>
              <option value="trial">7-Day Trial Pass</option>
            </select>
          </div>
          <div class="form-group">
            <label>Internal Note / User Tag</label>
            <input type="text" id="keyNote" placeholder="e.g. Beta Tester @jester_user">
          </div>
          <div class="form-group">
            <label>Max Bound Devices</label>
            <input type="number" id="keyMaxDevices" value="3" min="1" max="100">
          </div>
          <button class="btn" onclick="generateKey()">Generate Pass Key</button>
        </div>

        <div class="card">
          <h2 style="font-size: 1.1rem; margin-bottom: 0.5rem;">Active Digital Passes</h2>
          <p style="font-size: 0.8rem; color: var(--muted); margin-bottom: 1rem;">Keys validated during device activation and offline lease creation.</p>
          <div class="table-container" style="max-height: 380px; overflow-y: auto;">
            <table>
              <thead>
                <tr>
                  <th>Pass Key</th>
                  <th>Tier</th>
                  <th>Status</th>
                  <th>Devices</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody id="keysTableBody">
                <tr><td colspan="5" style="text-align: center; color: var(--muted);">Loading passes...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- TAB 4: DEVICE REGISTRY -->
    <div id="tab-devices" class="tab-pane">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
        <div>
          <h2 style="font-size: 1.2rem; font-weight: 700;">Registered Android Devices</h2>
          <p style="font-size: 0.8rem; color: var(--muted);">Devices registered through hardware attestation, ECDSA proof keys, and recovery leases.</p>
        </div>
        <button class="btn btn-outline btn-sm" onclick="loadDevices()">Refresh Registry</button>
      </div>

      <div class="card" style="padding: 0.5rem;">
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Device ID</th>
                <th>Proof Key ID</th>
                <th>Digital Pass</th>
                <th>Flavor</th>
                <th>Last Active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="devicesTableBody">
              <tr><td colspan="6" style="text-align: center; color: var(--muted);">Loading devices...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- TAB 5: OTA RELEASES -->
    <div id="tab-releases" class="tab-pane">
      <div class="grid-2" style="margin-bottom: 1.5rem;">
        <div class="card">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
            <div>
              <h2 style="font-size: 1.1rem; font-weight: 700;">Live Release Channel</h2>
              <p style="font-size: 0.8rem; color: var(--muted);">
                Polled by <code>LauncherUpdateClient.kt</code> on every app launch.
              </p>
            </div>
            <button class="btn btn-outline btn-sm" onclick="loadRelease()">↻ Refresh</button>
          </div>
          <div id="releaseInfo" style="font-size: 0.85rem; line-height: 2; color: var(--muted);">
            <div>Build: <strong id="rBuild" style="color:#fff">—</strong></div>
            <div>Version: <strong id="rVersion" style="color:#fff">—</strong></div>
            <div>Root APK: <span id="rRootAvail" class="badge badge-indigo">Checking…</span></div>
            <div>Non-Root APK: <span id="rNonrootAvail" class="badge badge-indigo">Checking…</span></div>
            <div>R2 Storage: <span id="rR2" class="badge badge-indigo">Checking…</span></div>
          </div>
          <hr style="border: none; border-top: 1px solid var(--card-border); margin: 1rem 0;">
          <div>
            <label>Stable Manifest Endpoint</label>
            <pre style="cursor: pointer;" onclick="runApiTest('/api/launcher-release')">/api/launcher-release</pre>
            <label style="margin-top: 0.5rem;">Signed Changelog Endpoint</label>
            <pre style="cursor: pointer;" onclick="runApiTest('/api/launcher-changelog')">/api/launcher-changelog</pre>
          </div>
        </div>

        <div class="card">
          <h2 style="font-size: 1.1rem; font-weight: 700; margin-bottom: 0.5rem;">Publish New Release</h2>
          <p style="font-size: 0.8rem; color: var(--muted); margin-bottom: 1rem;">Update the build manifest served to all Android devices.</p>
          <div class="form-group">
            <label>Build Code (Integer)</label>
            <input type="number" id="rNewBuild" value="302" min="1">
          </div>
          <div class="form-group">
            <label>Version String</label>
            <input type="text" id="rNewVersion" value="3.0.2">
          </div>
          <div class="form-group">
            <label>Release Notes</label>
            <textarea id="rNewNotes" rows="2" placeholder="What changed in this release?"></textarea>
          </div>
          <button class="btn" onclick="publishRelease()">🚀 Publish Manifest</button>

          <hr style="border: none; border-top: 1px solid var(--card-border); margin: 1.25rem 0;">
          <h3 style="font-size: 0.9rem; margin-bottom: 0.5rem; color: var(--muted);">Upload APK to R2 Storage</h3>
          <p style="font-size: 0.78rem; color: #64748b; line-height: 1.6; margin-bottom: 0.75rem;">
            Run these commands to upload your signed APKs to Cloudflare R2 so the download endpoints serve them:
          </p>
          <pre style="font-size: 0.72rem; line-height: 1.7;">npx wrangler r2 object put \\
  launcher-modules/launcher-stable/302/nonroot.apk \\
  --file=./release/nonroot.apk

npx wrangler r2 object put \\
  launcher-modules/launcher-stable/302/root.apk \\
  --file=./release/root.apk</pre>
        </div>
      </div>
    </div>

    <!-- TAB 6: API INSPECTOR -->
    <div id="tab-api" class="tab-pane">
      <div class="grid-2">
        <div class="card">
          <h2 style="font-size: 1.1rem; margin-bottom: 0.75rem;">Live Endpoint Tester</h2>
          <div style="display: flex; flex-direction: column; gap: 0.5rem;">
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-modules')">GET /api/launcher-modules</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-release')">GET /api/launcher-release</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-changelog')">GET /api/launcher-changelog</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-module-changelog/com-example-module/1')">GET /api/launcher-module-changelog</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-module-features/com-example-module/1')">GET /api/launcher-module-features</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-play-store-version/com.example.module')">GET /api/launcher-play-store-version</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/admin/releases', true)">GET /api/admin/releases</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/admin/stats', true)">GET /api/admin/stats</button>
          </div>
        </div>
        <div class="card">
          <h2 style="font-size: 1.1rem; margin-bottom: 0.75rem;">Response Payload</h2>
          <pre id="apiResponse" style="max-height: 420px; overflow-y: auto;">Click an endpoint to test live response from worker...</pre>
        </div>
      </div>
    </div>
  </div>

  <!-- MODAL: ADD / EDIT MODULE -->
  <dialog id="moduleModal">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
      <h2 style="font-size: 1.25rem; font-weight: 700;" id="modalModuleTitle">Add New Module</h2>
      <button class="btn btn-outline btn-sm" onclick="closeModuleModal()">✕</button>
    </div>
    <form id="moduleForm" onsubmit="saveModule(event)">
      <div class="grid-2">
        <div class="form-group">
          <label>Slug (Unique ID)</label>
          <input type="text" id="mSlug" placeholder="e.g. game-speed-mod" required>
        </div>
        <div class="form-group">
          <label>Package Name</label>
          <input type="text" id="mPkg" placeholder="e.g. com.game.mod" required>
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Display Title</label>
          <input type="text" id="mTitle" placeholder="e.g. Turbo Speed Mod" required>
        </div>
        <div class="form-group">
          <label>Category</label>
          <input type="text" id="mCategory" placeholder="e.g. Performance">
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Version String</label>
          <input type="text" id="mVersion" placeholder="1.0.0" value="1.0.0" required>
        </div>
        <div class="form-group">
          <label>Build Code (Integer)</label>
          <input type="number" id="mBuild" placeholder="1" value="1" required>
        </div>
      </div>
      <div class="form-group">
        <label>Non-Root Injection Method</label>
        <select id="mMethod">
          <option value="injection">injection (classes.dex / libmenu_native.so)</option>
          <option value="overlay">overlay (Direct Floating View)</option>
          <option value="virtual_runtime">virtual_runtime (Sandbox Execution)</option>
        </select>
      </div>
      <div class="form-group">
        <label>Release Notes</label>
        <textarea id="mNotes" rows="2" placeholder="Describe module changes..."></textarea>
      </div>
      <div style="display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1.5rem;">
        <button type="button" class="btn btn-outline" onclick="closeModuleModal()">Cancel</button>
        <button type="submit" class="btn">Save & Publish Module</button>
      </div>
    </form>
  </dialog>

  <!-- MODAL: ADMIN TOKEN -->
  <dialog id="tokenModal">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
      <h2 style="font-size: 1.25rem; font-weight: 700;">Admin Secret Token</h2>
      <button class="btn btn-outline btn-sm" onclick="document.getElementById('tokenModal').close()">✕</button>
    </div>
    <p style="font-size: 0.85rem; color: var(--muted); margin-bottom: 1rem;">
      Enter the ADMIN_TOKEN configured in your Cloudflare Worker secrets. Stored in your local browser only.
    </p>
    <div class="form-group">
      <label>Admin Secret Token</label>
      <input type="password" id="adminTokenInput" placeholder="Enter ADMIN_TOKEN">
    </div>
    <div style="display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1.25rem;">
      <button class="btn btn-outline" onclick="clearAdminToken()">Clear</button>
      <button class="btn" onclick="saveAdminToken()">Save Token</button>
    </div>
  </dialog>

  <!-- TOAST NOTIFICATION -->
  <div id="toast"></div>

  <script>
    // State
    let adminToken = localStorage.getItem("jester_admin_token") || "";

    function showToast(msg) {
      const t = document.getElementById("toast");
      t.innerText = msg;
      t.style.display = "block";
      setTimeout(() => { t.style.display = "none"; }, 3500);
    }

    function switchTab(name) {
      document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
      document.querySelectorAll(".tab-pane").forEach(p => p.classList.remove("active"));
      const btn = Array.from(document.querySelectorAll(".tab-btn")).find(b => b.innerText.toLowerCase().includes(name));
      if (btn) btn.classList.add("active");
      const pane = document.getElementById("tab-" + name);
      if (pane) pane.classList.add("active");
      if (name === "releases") loadRelease();
    }

    function getAuthHeaders() {
      const headers = { "Content-Type": "application/json" };
      if (adminToken) {
        headers["Authorization"] = "Bearer " + adminToken;
        headers["X-Admin-Token"] = adminToken;
      }
      return headers;
    }

    async function loadStats() {
      try {
        const res = await fetch("/api/admin/stats", { headers: getAuthHeaders() });
        const data = await res.json();
        if (data.ok) {
          document.getElementById("statModules").innerText = data.stats.totalModules;
          document.getElementById("statKeys").innerText = data.stats.activeKeys + " / " + data.stats.totalKeys;
          document.getElementById("statDevices").innerText = data.stats.totalDevices;
          document.getElementById("diagKv").innerText = data.stats.kvConfigured ? "Connected (LAUNCHER_KV)" : "In-Memory Fallback";
          document.getElementById("diagKv").className = data.stats.kvConfigured ? "badge badge-green" : "badge badge-purple";
          document.getElementById("diagR2").innerText = data.stats.r2Configured ? "Connected" : "Optional / Unset";
          document.getElementById("diagAuth").innerText = data.stats.adminTokenConfigured ? "Enforced" : "Open Access";
          document.getElementById("diagAuth").className = data.stats.adminTokenConfigured ? "badge badge-green" : "badge badge-purple";
        }
      } catch (e) {
        console.error("Stats load failed", e);
      }
    }

    async function loadModules() {
      try {
        const res = await fetch("/api/admin/modules", { headers: getAuthHeaders() });
        const data = await res.json();
        const tbody = document.getElementById("modulesTableBody");
        if (data.ok && data.modules.length > 0) {
          tbody.innerHTML = data.modules.map(m => \`
            <tr>
              <td><span class="code-pill">\${m.packageName}</span></td>
              <td><strong>\${m.title}</strong></td>
              <td>v\${m.version}</td>
              <td>#\${m.build}</td>
              <td><span class="badge badge-purple">\${m.nonrootMethod}</span></td>
              <td>\${(m.supportedAbis || []).join(", ") || "arm64-v8a"}</td>
              <td>
                <button class="btn btn-outline btn-sm" onclick="editModule('\${m.slug}')">Edit</button>
                <button class="btn btn-danger btn-sm" onclick="deleteModule('\${m.slug}')">Delete</button>
              </td>
            </tr>
          \`).join("");
        } else {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--muted);">No modules found. Click "+ Add New Module" or "Quick Seed".</td></tr>';
        }
      } catch (e) {
        console.error("Modules load failed", e);
      }
    }

    async function loadKeys() {
      try {
        const res = await fetch("/api/admin/keys", { headers: getAuthHeaders() });
        const data = await res.json();
        const tbody = document.getElementById("keysTableBody");
        if (data.ok && data.keys.length > 0) {
          tbody.innerHTML = data.keys.map(k => \`
            <tr>
              <td><span class="code-pill" style="color: #6ee7b7; font-size: 0.75rem;">\${k.key.substring(0, 24)}...</span></td>
              <td><span class="badge badge-indigo">\${k.tier.toUpperCase()}</span></td>
              <td>
                <span class="badge \${k.active ? 'badge-green' : 'badge-red'}">\${k.active ? 'Active' : 'Revoked'}</span>
              </td>
              <td>\${(k.boundDevices || []).length} / \${k.maxDevices}</td>
              <td>
                <button class="btn btn-outline btn-sm" onclick="copyText('\${k.key}')">Copy</button>
                <button class="btn btn-sm \${k.active ? 'btn-danger' : 'btn-outline'}" onclick="toggleKey('\${k.key}', \${!k.active})">\${k.active ? 'Revoke' : 'Restore'}</button>
              </td>
            </tr>
          \`).join("");
        } else {
          tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--muted);">No keys generated yet.</td></tr>';
        }
      } catch (e) {
        console.error("Keys load failed", e);
      }
    }

    async function loadDevices() {
      try {
        const res = await fetch("/api/admin/devices", { headers: getAuthHeaders() });
        const data = await res.json();
        const tbody = document.getElementById("devicesTableBody");
        if (data.ok && data.devices.length > 0) {
          tbody.innerHTML = data.devices.map(d => \`
            <tr>
              <td><span class="code-pill">\${d.deviceId}</span></td>
              <td><span class="code-pill" style="color: #a78bfa;">\${(d.proofKeyId || '').substring(0, 16)}...</span></td>
              <td>\${(d.digitalKey || 'VIP Pass').substring(0, 16)}...</td>
              <td><span class="badge badge-purple">\${d.flavor || 'nonroot'}</span></td>
              <td>\${new Date(d.lastSeen * 1000).toLocaleString()}</td>
              <td>
                <button class="btn btn-danger btn-sm" onclick="unbindDevice('\${d.deviceId}')">Unbind</button>
              </td>
            </tr>
          \`).join("");
        } else {
          tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--muted);">No active device registrations recorded yet.</td></tr>';
        }
      } catch (e) {
        console.error("Devices load failed", e);
      }
    }

    async function generateKey() {
      const tier = document.getElementById("keyTier").value;
      const note = document.getElementById("keyNote").value.trim();
      const maxDevices = parseInt(document.getElementById("keyMaxDevices").value, 10) || 3;
      try {
        const res = await fetch("/api/admin/keys", {
          method: "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify({ tier, note, maxDevices })
        });
        const data = await res.json();
        if (data.ok) {
          showToast("Generated key: " + data.key.key.substring(0, 20) + "...");
          await loadKeys();
          await loadStats();
        } else {
          alert("Error: " + (data.error || "Failed to create key"));
        }
      } catch (e) {
        alert("Request error: " + e.message);
      }
    }

    async function toggleKey(key, active) {
      try {
        const res = await fetch("/api/admin/keys/toggle", {
          method: "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify({ key, active })
        });
        const data = await res.json();
        if (data.ok) {
          showToast("Pass " + (active ? "restored" : "revoked"));
          await loadKeys();
          await loadStats();
        }
      } catch (e) {
        alert("Toggle error: " + e.message);
      }
    }

    async function unbindDevice(deviceId) {
      if (!confirm("Unbind device " + deviceId + "? This will require the user to re-register proof.")) return;
      try {
        const res = await fetch("/api/admin/devices/" + encodeURIComponent(deviceId), {
          method: "DELETE",
          headers: getAuthHeaders()
        });
        const data = await res.json();
        if (data.ok) {
          showToast("Device unbound successfully");
          await loadDevices();
          await loadStats();
        }
      } catch (e) {
        alert("Unbind error: " + e.message);
      }
    }

    function openModuleModal(mod) {
      const modal = document.getElementById("moduleModal");
      document.getElementById("modalModuleTitle").innerText = mod ? "Edit Module" : "Add New Module";
      document.getElementById("mSlug").value = mod ? mod.slug : "";
      document.getElementById("mSlug").readOnly = !!mod;
      document.getElementById("mPkg").value = mod ? mod.packageName : "";
      document.getElementById("mTitle").value = mod ? mod.title : "";
      document.getElementById("mCategory").value = mod ? mod.category : "Action";
      document.getElementById("mVersion").value = mod ? mod.version : "1.0.0";
      document.getElementById("mBuild").value = mod ? mod.build : 1;
      document.getElementById("mMethod").value = mod ? mod.nonrootMethod : "injection";
      document.getElementById("mNotes").value = mod ? mod.notes : "";
      modal.showModal();
    }

    function closeModuleModal() {
      document.getElementById("moduleModal").close();
    }

    async function saveModule(e) {
      e.preventDefault();
      const slug = document.getElementById("mSlug").value.trim();
      const packageName = document.getElementById("mPkg").value.trim();
      const title = document.getElementById("mTitle").value.trim();
      const category = document.getElementById("mCategory").value.trim();
      const version = document.getElementById("mVersion").value.trim();
      const build = parseInt(document.getElementById("mBuild").value, 10) || 1;
      const nonrootMethod = document.getElementById("mMethod").value;
      const notes = document.getElementById("mNotes").value.trim();

      try {
        const res = await fetch("/api/admin/modules", {
          method: "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify({
            slug, packageName, title, category, version, build, nonrootMethod, notes,
            supportedVersions: [version],
            supportedAbis: ["arm64-v8a"],
            features: [title + " features"]
          })
        });
        const data = await res.json();
        if (data.ok) {
          closeModuleModal();
          showToast("Module published to live catalog!");
          await loadModules();
          await loadStats();
        } else {
          alert("Error: " + (data.error || "Save failed"));
        }
      } catch (err) {
        alert("Save error: " + err.message);
      }
    }

    async function editModule(slug) {
      try {
        const res = await fetch("/api/admin/modules", { headers: getAuthHeaders() });
        const data = await res.json();
        const mod = (data.modules || []).find(m => m.slug === slug);
        if (mod) openModuleModal(mod);
      } catch (e) {
        console.error(e);
      }
    }

    async function deleteModule(slug) {
      if (!confirm("Are you sure you want to delete module: " + slug + "?")) return;
      try {
        const res = await fetch("/api/admin/modules/" + encodeURIComponent(slug), {
          method: "DELETE",
          headers: getAuthHeaders()
        });
        const data = await res.json();
        if (data.ok) {
          showToast("Module removed");
          await loadModules();
          await loadStats();
        }
      } catch (e) {
        alert("Delete error: " + e.message);
      }
    }

    async function seedDefaultData() {
      if (!confirm("Seed default demo catalog and VIP passes into KV database?")) return;
      try {
        const res = await fetch("/api/admin/seed", {
          method: "POST",
          headers: getAuthHeaders()
        });
        const data = await res.json();
        if (data.ok) {
          showToast("Database seeded successfully!");
          await loadStats();
          await loadModules();
          await loadKeys();
        }
      } catch (e) {
        alert("Seed error: " + e.message);
      }
    }

    async function runApiTest(endpoint, useAuth = false) {
      const box = document.getElementById("apiResponse");
      box.innerText = "⏳ Querying " + endpoint + "...";
      try {
        const headers = useAuth ? getAuthHeaders() : { "Content-Type": "application/json" };
        const res = await fetch(endpoint, { headers });
        const json = await res.json();
        box.innerText = JSON.stringify(json, null, 2);
      } catch (e) {
        box.innerText = "❌ Error fetching endpoint: " + e.message;
      }
    }

    function testCatalogEndpoint() {
      switchTab('api');
      runApiTest('/api/launcher-modules');
    }

    async function loadRelease() {
      try {
        const res = await fetch("/api/admin/releases", { headers: getAuthHeaders() });
        const data = await res.json();
        if (data.ok && data.release) {
          const r = data.release;
          const el = (id) => document.getElementById(id);
          el("rBuild").innerText = r.build || "—";
          el("rVersion").innerText = r.version || "—";
          const rootOk = r.downloads?.root?.available;
          const nonrootOk = r.downloads?.nonroot?.available;
          const r2Ok = r.r2Configured;
          el("rRootAvail").innerText = rootOk ? "Available" : "Not Uploaded";
          el("rRootAvail").className = rootOk ? "badge badge-green" : "badge badge-red";
          el("rNonrootAvail").innerText = nonrootOk ? "Available" : "Not Uploaded";
          el("rNonrootAvail").className = nonrootOk ? "badge badge-green" : "badge badge-red";
          el("rR2").innerText = r2Ok ? "Connected" : "Not Configured";
          el("rR2").className = r2Ok ? "badge badge-green" : "badge badge-purple";
        }
      } catch (e) {
        console.error("loadRelease failed", e);
      }
    }

    async function publishRelease() {
      const build = parseInt(document.getElementById("rNewBuild").value, 10) || 302;
      const version = document.getElementById("rNewVersion").value.trim() || "3.0.2";
      const notes = document.getElementById("rNewNotes").value.trim();
      if (!confirm("Publish Build " + build + " (v" + version + ") to all Android devices?")) return;
      try {
        const res = await fetch("/api/admin/release/publish", {
          method: "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify({ build, version, notes })
        });
        const data = await res.json();
        if (data.ok) {
          showToast("\u2705 Released Build " + build + " (v" + version + ")");
          await loadRelease();
        } else {
          alert("Publish failed: " + (data.error || "Unknown error"));
        }
      } catch (e) {
        alert("Publish error: " + e.message);
      }
    }

    function openTokenDialog() {
      document.getElementById("adminTokenInput").value = adminToken;
      document.getElementById("tokenModal").showModal();
    }

    function saveAdminToken() {
      adminToken = document.getElementById("adminTokenInput").value.trim();
      localStorage.setItem("jester_admin_token", adminToken);
      document.getElementById("tokenModal").close();
      updateTokenLabel();
      showToast("Admin token saved");
      loadStats();
    }

    function clearAdminToken() {
      adminToken = "";
      localStorage.removeItem("jester_admin_token");
      document.getElementById("adminTokenInput").value = "";
      document.getElementById("tokenModal").close();
      updateTokenLabel();
      showToast("Admin token cleared");
      loadStats();
    }

    function updateTokenLabel() {
      document.getElementById("tokenLabel").innerText = adminToken ? "Admin: Configured" : "Admin Auth";
    }

    async function copyText(txt) {
      await navigator.clipboard.writeText(txt);
      showToast("Copied to clipboard!");
    }

    // Init
    updateTokenLabel();
    loadStats();
    loadModules();
    loadKeys();
    loadDevices();
    loadRelease();
  </script>
</body>
</html>`;

      return new Response(html, {
        headers: { "Content-Type": "text/html; charset=utf-8" },
      });
    }

    return new Response("Not Found", { status: 404, headers: corsHeaders });
  },
};
