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
  featured?: boolean;
  popularity?: number;
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
const CANONICAL_STANDARD_KEY = canonicalizeDigitalKey("STANDARD-PASS-FREE");

// ============================================================================
// 3. Default Seed Data
// ============================================================================

// Play Store official version mapping to prevent outdated/incompatible flags in Android client
const REAL_PLAY_STORE_VERSIONS: Record<string, { version: string; versionCode: number }> = {
  "com.bandainamcoent.opbrww": { version: "9.3.0", versionCode: 93010 },
  "com.mobile.legends": { version: "22.1.97.12061", versionCode: 221971 },
  "com.dts.freefireth": { version: "1.130.1", versionCode: 113010 },
  "com.bandainamcoent.dblegends_ww": { version: "5.6.0", versionCode: 56000 },
  "com.roblox.client": { version: "2.630.808", versionCode: 2630808 },
};

// Verified payload hashes & sizes from built modules (classes.dex + libmenu_native.so)
const VERIFIED_OPBR_DEX_SHA256 = "e74a15e466735a56f443216febd013b5a27e0fe78fdf143f74d6775c7b20aeb6";
const VERIFIED_OPBR_DEX_SIZE = 201616;
const VERIFIED_OPBR_SO_SHA256 = "630a922239b191c5514ea3e8c721b6787564c772ef950d3d0ed5322934cf5377";
const VERIFIED_OPBR_SO_SIZE = 1818376;
const VERIFIED_TOTAL_MODULE_SIZE = 2019992; // 201616 + 1818376

const DEFAULT_MODULES: ModuleItem[] = [
  // ── 1. ONE PIECE Bounty Rush (verified injection, versionCode 93010) ──────
  {
    packageName: "com.bandainamcoent.opbrww",
    slug: "opbr-bounty-rush-mod",
    title: "ONE PIECE Bounty Rush Mod",
    version: "9.3.0",
    notes: "Native overlay menu with combat & radar enhancements. Verified non-root injection on versionCode 93010. Supports arm64-v8a devices running Android 7+.",
    category: "Action",
    tags: ["action", "anime", "pvp", "bandai", "injection", "featured"],
    featured: true,
    popularity: 950000,
    publishedAt: 1756684800,
    updatedAt: 1789019551,
    build: 93010,
    supportedVersions: ["9.3.0"],
    supportedVersionCodes: [93010],
    supportedAbis: ["arm64-v8a"],
    downloadSizeByAbi: { "arm64-v8a": VERIFIED_TOTAL_MODULE_SIZE },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: [
      "Damage Multiplier (1x–10x)",
      "Defense Boost Toggle",
      "Skill Cooldown Reduction",
      "Radar Map Expansion (full map reveal)",
      "Camera FOV Unlock",
      "Speed Modifier",
      "Custom Battle HUD",
      "No Knock-Back Mode",
    ],
    source: {
      path: "/api/launcher-module-payload/opbr-bounty-rush-mod/93010/native/arm64-v8a",
      sizeBytes: VERIFIED_OPBR_SO_SIZE,
      sha256: VERIFIED_OPBR_SO_SHA256,
    },
    moduleConfig: {
      packageName: "com.bandainamcoent.opbrww",
      dexFile: "classes.dex",
      nativeFile: "libmenu_native.so",
      title: "ONE PIECE Bounty Rush Mod",
      entryPoint: "com.android.support.Main",
      supportedVersions: ["9.3.0"],
      supportedAbis: ["arm64-v8a"],
      nonrootMethod: "injection",
    },
    files: {
      dex: {
        path: "/api/launcher-module-payload/opbr-bounty-rush-mod/93010/dex/classes.dex",
        size: VERIFIED_OPBR_DEX_SIZE,
        sha256: VERIFIED_OPBR_DEX_SHA256,
      },
      native: {
        "arm64-v8a": {
          path: "/api/launcher-module-payload/opbr-bounty-rush-mod/93010/native/arm64-v8a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
      },
    },
    changelogEntries: [
      {
        build: 93010,
        version: "9.3.0",
        notes: "Full non-root native injection compatibility for versionCode 93010. Radar map expansion upgraded to full reveal. Speed modifier added.",
        publishedAt: 1789019551,
        updateType: "feature",
      },
      {
        build: 92800,
        version: "9.2.8",
        notes: "Cooldown reduction feature added; improved overlay stability on Mali GPU devices.",
        publishedAt: 1785000000,
        updateType: "feature",
      },
    ],
    featureGroups: [
      {
        title: "Combat & Stats",
        features: [
          "Damage Multiplier (1x–10x slider)",
          "Defense Boost Toggle",
          "Skill Cooldown Reduction",
          "No Knock-Back Mode",
          "Speed Modifier",
        ],
      },
      {
        title: "Visuals & Radar",
        features: [
          "Full Radar Map Reveal",
          "Camera FOV Distance Unlock",
          "Custom Battle HUD Layout",
          "Enemy Highlight Outline",
        ],
      },
    ],
  },
  // ── 2. Mobile Legends: Bang Bang (com.mobile.legends, v22.1.97.12061) ─────
  {
    packageName: "com.mobile.legends",
    slug: "mlbb-bang-bang-mod",
    title: "Mobile Legends: Bang Bang Mod",
    version: "22.1.97.12061",
    notes: "Advanced mod menu for Mobile Legends: Bang Bang. Features map hack, damage boost, anti-ban hooks, and skin unlock overlay. Compatible with latest update (22.1.97).",
    category: "MOBA",
    tags: ["moba", "mlbb", "pvp", "moonton", "map-hack", "featured"],
    featured: true,
    popularity: 980000,
    publishedAt: 1780000000,
    updatedAt: 1789019551,
    build: 221971,
    supportedVersions: ["22.1.97.12061", "22.1.86.9752"],
    supportedVersionCodes: [221971, 221869],
    supportedAbis: ["arm64-v8a", "armeabi-v7a"],
    downloadSizeByAbi: { "arm64-v8a": VERIFIED_TOTAL_MODULE_SIZE, "armeabi-v7a": VERIFIED_TOTAL_MODULE_SIZE },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: [
      "Map Hack (minimap enemy reveal)",
      "Damage Multiplier",
      "Cooldown Reduction (all skills)",
      "Anti-Ban Hook Layer",
      "Skin Unlock Overlay",
      "Drone View Distance Boost",
      "Auto Aim Assist",
      "Lag Reducer (packet optimizer)",
    ],
    source: {
      path: "/api/launcher-module-payload/mlbb-bang-bang-mod/221971/native/arm64-v8a",
      sizeBytes: VERIFIED_OPBR_SO_SIZE,
      sha256: VERIFIED_OPBR_SO_SHA256,
    },
    moduleConfig: {
      packageName: "com.mobile.legends",
      dexFile: "classes.dex",
      nativeFile: "libmenu_native.so",
      title: "Mobile Legends: Bang Bang Mod",
      entryPoint: "com.android.support.Main",
      supportedVersions: ["22.1.97.12061", "22.1.86.9752"],
      supportedAbis: ["arm64-v8a", "armeabi-v7a"],
      nonrootMethod: "injection",
    },
    files: {
      dex: {
        path: "/api/launcher-module-payload/mlbb-bang-bang-mod/221971/dex/classes.dex",
        size: VERIFIED_OPBR_DEX_SIZE,
        sha256: VERIFIED_OPBR_DEX_SHA256,
      },
      native: {
        "arm64-v8a": {
          path: "/api/launcher-module-payload/mlbb-bang-bang-mod/221971/native/arm64-v8a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
        "armeabi-v7a": {
          path: "/api/launcher-module-payload/mlbb-bang-bang-mod/221971/native/armeabi-v7a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
      },
    },
    changelogEntries: [
      {
        build: 221971,
        version: "22.1.97.12061",
        notes: "Compatibility updated for MLBB 22.1.97. Anti-ban hook rewritten for Android 14. Drone view height increased.",
        publishedAt: 1789019551,
        updateType: "major",
      },
      {
        build: 221869,
        version: "22.1.86.9752",
        notes: "Initial release for season 35. Map hack and cooldown features verified.",
        publishedAt: 1782000000,
        updateType: "feature",
      },
    ],
    featureGroups: [
      {
        title: "Map & Vision",
        features: [
          "Minimap Enemy Location Reveal (Map Hack)",
          "Drone View Camera Height (2x–5x)",
          "Jungle Monster HP & Cooldown Timers",
        ],
      },
      {
        title: "Combat Enhancements",
        features: [
          "Damage Multiplier (slider)",
          "All Skill Cooldown Reduction",
          "Auto Aim Skill Shot Assist",
        ],
      },
      {
        title: "Security & Skins",
        features: [
          "Anti-Ban Memory Cloak Hook",
          "All Skins Unlock Visual Overlay",
          "Network Packet Smoothing",
        ],
      },
    ],
  },
  // ── 3. Garena Free Fire (com.dts.freefireth, v1.130.1) ───────────────────
  {
    packageName: "com.dts.freefireth",
    slug: "free-fire-mod",
    title: "Garena Free Fire Mod",
    version: "1.130.1",
    notes: "Precision mod menu for Free Fire. Includes Aimbot Assist, ESP player boxes, bullet tracking, and anti-detection. Fully non-root compatible.",
    category: "Battle Royale",
    tags: ["battle-royale", "freefire", "fps", "aimbot", "esp", "garena", "featured"],
    featured: true,
    popularity: 910000,
    publishedAt: 1781000000,
    updatedAt: 1789019551,
    build: 113010,
    supportedVersions: ["1.130.1", "1.130.0"],
    supportedVersionCodes: [113010, 113000],
    supportedAbis: ["arm64-v8a", "armeabi-v7a"],
    downloadSizeByAbi: { "arm64-v8a": VERIFIED_TOTAL_MODULE_SIZE, "armeabi-v7a": VERIFIED_TOTAL_MODULE_SIZE },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: [
      "Aimbot Assist (head/body target lock)",
      "Player ESP / Wallhack (boxes + distance)",
      "Speed Hack (movement speed multiplier)",
      "Anti-Detection Bypass Layer",
      "Auto Headshot Angle Lock",
      "No Recoil & No Sway",
      "Infinite Ammo Toggle",
      "Drone / Bird Eye View",
    ],
    source: {
      path: "/api/launcher-module-payload/free-fire-battlegrounds-mod/113010/native/arm64-v8a",
      sizeBytes: VERIFIED_OPBR_SO_SIZE,
      sha256: VERIFIED_OPBR_SO_SHA256,
    },
    moduleConfig: {
      packageName: "com.dts.freefireth",
      dexFile: "classes.dex",
      nativeFile: "libmenu_native.so",
      title: "Garena Free Fire Mod",
      entryPoint: "com.android.support.Main",
      supportedVersions: ["1.130.1", "1.130.0"],
      supportedAbis: ["arm64-v8a", "armeabi-v7a"],
      nonrootMethod: "injection",
    },
    files: {
      dex: {
        path: "/api/launcher-module-payload/free-fire-battlegrounds-mod/113010/dex/classes.dex",
        size: VERIFIED_OPBR_DEX_SIZE,
        sha256: VERIFIED_OPBR_DEX_SHA256,
      },
      native: {
        "arm64-v8a": {
          path: "/api/launcher-module-payload/free-fire-battlegrounds-mod/113010/native/arm64-v8a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
        "armeabi-v7a": {
          path: "/api/launcher-module-payload/free-fire-battlegrounds-mod/113010/native/armeabi-v7a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
      },
    },
    changelogEntries: [
      {
        build: 113010,
        version: "1.130.1",
        notes: "Updated anti-detection for patch 1.130.1. Aimbot smoothing improved. Bird eye view feature added.",
        publishedAt: 1789019551,
        updateType: "feature",
      },
      {
        build: 113000,
        version: "1.130.0",
        notes: "Initial support for 1.130 branch. Full ESP/wallhack, no-recoil, and speed hack.",
        publishedAt: 1783000000,
        updateType: "major",
      },
    ],
    featureGroups: [
      {
        title: "Vision & ESP",
        features: [
          "Player ESP / Wallhack",
          "Loot ESP (weapon & item highlight)",
          "Drone / Bird Eye View",
        ],
      },
      {
        title: "Aimbot & Shooting",
        features: [
          "Aimbot Assist (configurable smoothing)",
          "Auto Headshot Angle Lock",
          "No Recoil",
          "No Weapon Sway",
          "Infinite Ammo Toggle",
        ],
      },
      {
        title: "Movement & Physics",
        features: [
          "Speed Hack (movement multiplier)",
          "Jump Height Boost",
        ],
      },
      {
        title: "Anti-Detection",
        features: [
          "Anti-Ban Bypass Layer",
          "Signature Spoof Hook",
        ],
      },
    ],
  },
  // ── 4. Dragon Ball Legends (com.bandainamcoent.dblegends_ww, v5.6.0) ──────
  {
    packageName: "com.bandainamcoent.dblegends_ww",
    slug: "db-legends-mod",
    title: "Dragon Ball Legends Mod",
    version: "5.6.0",
    notes: "Overlay mod menu for Dragon Ball Legends. Boosts damage, reduces cooldowns, and enables color palette overrides. Non-root compatible via native injection.",
    category: "Action",
    tags: ["action", "anime", "pvp", "bandai", "dragon-ball"],
    featured: false,
    popularity: 820000,
    publishedAt: 1781000000,
    updatedAt: 1789019551,
    build: 56000,
    supportedVersions: ["5.6.0"],
    supportedVersionCodes: [56000],
    supportedAbis: ["arm64-v8a"],
    downloadSizeByAbi: { "arm64-v8a": VERIFIED_TOTAL_MODULE_SIZE },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: [
      "Damage Multiplier",
      "Skill Cooldown Instant Refresh",
      "God Mode (HP Lock)",
      "Color Palette Override",
      "Auto Dodge Assist",
    ],
    source: {
      path: "/api/launcher-module-payload/db-legends-mod/56000/native/arm64-v8a",
      sizeBytes: VERIFIED_OPBR_SO_SIZE,
      sha256: VERIFIED_OPBR_SO_SHA256,
    },
    moduleConfig: {
      packageName: "com.bandainamcoent.dblegends_ww",
      dexFile: "classes.dex",
      nativeFile: "libmenu_native.so",
      title: "Dragon Ball Legends Mod",
      entryPoint: "com.android.support.Main",
      supportedVersions: ["5.6.0"],
      supportedAbis: ["arm64-v8a"],
      nonrootMethod: "injection",
    },
    files: {
      dex: {
        path: "/api/launcher-module-payload/db-legends-mod/56000/dex/classes.dex",
        size: VERIFIED_OPBR_DEX_SIZE,
        sha256: VERIFIED_OPBR_DEX_SHA256,
      },
      native: {
        "arm64-v8a": {
          path: "/api/launcher-module-payload/db-legends-mod/56000/native/arm64-v8a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
      },
    },
    changelogEntries: [
      {
        build: 56000,
        version: "5.6.0",
        notes: "Initial release for v5.6.0. Damage multiplier, god mode, and auto dodge added.",
        publishedAt: 1789019551,
        updateType: "major",
      },
    ],
    featureGroups: [
      {
        title: "Combat",
        features: [
          "Damage Multiplier (1x–20x)",
          "Skill Cooldown Instant Refresh",
          "God Mode / HP Lock",
          "Auto Dodge Assist",
        ],
      },
      {
        title: "Visuals",
        features: [
          "Character Color Palette Override",
          "Custom UI Skin",
        ],
      },
    ],
  },
  // ── 5. Roblox (com.roblox.client, v2.630.x) ──────────────────────────────
  {
    packageName: "com.roblox.client",
    slug: "roblox-mod",
    title: "Roblox Executor Mod",
    version: "2.630.808",
    notes: "Executor-style overlay for Roblox. Injects a floating script executor panel with pre-loaded scripts for popular games. No root required — works via native hook on arm64.",
    category: "Sandbox",
    tags: ["sandbox", "roblox", "executor", "script", "overlay"],
    featured: false,
    popularity: 870000,
    publishedAt: 1782000000,
    updatedAt: 1789019551,
    build: 2630808,
    supportedVersions: ["2.630.808"],
    supportedVersionCodes: [2630808],
    supportedAbis: ["arm64-v8a"],
    downloadSizeByAbi: { "arm64-v8a": VERIFIED_TOTAL_MODULE_SIZE },
    nonrootMethod: "injection",
    nonrootMethods: ["injection"],
    features: [
      "Floating Script Executor Panel",
      "Built-in Script Library (100+ scripts)",
      "Infinite Jump Toggle",
      "Speed Hack",
      "Noclip Mode",
      "Anti-AFK Bypass",
      "Custom ESP for supported games",
    ],
    source: {
      path: "/api/launcher-module-payload/roblox-mod/2630808/native/arm64-v8a",
      sizeBytes: VERIFIED_OPBR_SO_SIZE,
      sha256: VERIFIED_OPBR_SO_SHA256,
    },
    moduleConfig: {
      packageName: "com.roblox.client",
      dexFile: "classes.dex",
      nativeFile: "libmenu_native.so",
      title: "Roblox Executor Mod",
      entryPoint: "com.android.support.Main",
      supportedVersions: ["2.630.808"],
      supportedAbis: ["arm64-v8a"],
      nonrootMethod: "injection",
    },
    files: {
      dex: {
        path: "/api/launcher-module-payload/roblox-mod/2630808/dex/classes.dex",
        size: VERIFIED_OPBR_DEX_SIZE,
        sha256: VERIFIED_OPBR_DEX_SHA256,
      },
      native: {
        "arm64-v8a": {
          path: "/api/launcher-module-payload/roblox-mod/2630808/native/arm64-v8a",
          size: VERIFIED_OPBR_SO_SIZE,
          sha256: VERIFIED_OPBR_SO_SHA256,
        },
      },
    },
    changelogEntries: [
      {
        build: 2630808,
        version: "2.630.808",
        notes: "Initial release. Script executor panel with 100+ built-in scripts. Anti-AFK and noclip added.",
        publishedAt: 1789019551,
        updateType: "major",
      },
    ],
    featureGroups: [
      {
        title: "Executor",
        features: [
          "Floating Script Executor Panel",
          "Built-in Script Library (100+ scripts)",
          "Anti-AFK Bypass",
        ],
      },
      {
        title: "Movement Hacks",
        features: [
          "Infinite Jump",
          "Speed Hack",
          "Noclip Mode",
        ],
      },
      {
        title: "Vision",
        features: [
          "Custom ESP (supported game modes)",
        ],
      },
    ],
  },
];

const DEFAULT_KEYS: DigitalKeyRecord[] = [
  {
    key: CANONICAL_VIP_KEY,
    tier: "vip",
    note: "Default Developer & Test VIP Member Pass (Canonical 88-char)",
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
    key: CANONICAL_STANDARD_KEY,
    tier: "standard",
    note: "Standard Pass (Linkvertise 24h Free Route) (Canonical 88-char)",
    active: true,
    createdAt: 1708000000,
    maxDevices: 1000,
    boundDevices: [],
  },
  {
    key: "VIP-MEMBER-ACCESS",
    tier: "vip",
    note: "VIP Member Pass (Full Catalog - Short Alias)",
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
  {
    key: "STANDARD-PASS-FREE",
    tier: "standard",
    note: "Standard Pass (Linkvertise Free 24h Route - Short Alias)",
    active: true,
    createdAt: 1708000000,
    maxDevices: 1000,
    boundDevices: [],
  },
];

export interface UnlockTokenRecord {
  token: string;
  challenge: string;
  tier: "vip" | "lifetime" | "standard";
  digitalKey: string;
  deviceId?: string;
  createdAt: number;
  durationSeconds: number;
}

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
const memoryUnlockTokens = new Map<string, UnlockTokenRecord>();

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
      const list = raw as ModuleItem[];
      let changed = false;
      for (const defMod of DEFAULT_MODULES) {
        const existingIdx = list.findIndex((m) => m.slug === defMod.slug || m.packageName === defMod.packageName);
        if (existingIdx === -1) {
          list.push(defMod);
          changed = true;
        } else {
          // If existing entry has old relative paths or outdated metadata, upgrade to latest DEFAULT_MODULES
          const existing = list[existingIdx];
          if (
            existing.files?.dex?.path !== defMod.files?.dex?.path ||
            existing.downloadSizeByAbi?.["arm64-v8a"] !== defMod.downloadSizeByAbi?.["arm64-v8a"] ||
            existing.version !== defMod.version ||
            existing.build !== defMod.build ||
            existing.featured !== defMod.featured ||
            existing.popularity !== defMod.popularity ||
            !existing.supportedVersions ||
            existing.supportedVersions[0] !== defMod.supportedVersions[0]
          ) {
            existing.files = defMod.files;
            existing.source = defMod.source;
            existing.downloadSizeByAbi = defMod.downloadSizeByAbi;
            existing.supportedVersions = defMod.supportedVersions;
            existing.supportedVersionCodes = defMod.supportedVersionCodes;
            existing.version = defMod.version;
            existing.build = defMod.build;
            existing.featured = defMod.featured;
            existing.popularity = defMod.popularity;
            existing.notes = defMod.notes;
            existing.features = defMod.features;
            existing.featureGroups = defMod.featureGroups;
            existing.changelogEntries = defMod.changelogEntries;
            existing.moduleConfig = defMod.moduleConfig;
            changed = true;
          }
        }
      }
      const { list: sanitizedList, changed: sanitizeChanged } = sanitizeModules(list);
      if (changed || sanitizeChanged) {
        await env.LAUNCHER_KV.put("modules:catalog", JSON.stringify(sanitizedList));
      }
      return sanitizedList;
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
      // Self-healing: Ensure all DEFAULT_KEYS exist in stored
      let updated = false;
      for (const defKey of DEFAULT_KEYS) {
        if (!stored.some((k) => k.key === defKey.key)) {
          stored.push(defKey);
          updated = true;
        }
      }
      const hasActive = stored.some((k) => k.active);
      if (!hasActive) {
        for (const k of stored) {
          k.active = true;
        }
        updated = true;
      }
      if (updated) {
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
      const deviceId = (body.deviceId as string) || generateRandomId(32);

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
      const deviceId = (body.deviceId as string) || generateRandomId(32);

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

      const now = Math.floor(Date.now() / 1000);
      // Set lease duration dynamically: Standard Pass (Linkvertise) = 1 day; Lifetime = 10 years; VIP = 30 days
      let durationSeconds = 30 * 86400;
      if (matchingKey) {
        if (matchingKey.tier === "standard") {
          durationSeconds = 86400; // 24 hours / 1 day
        } else if (matchingKey.tier === "lifetime") {
          durationSeconds = 10 * 365 * 86400; // 10 years (Lifetime Master)
        } else if (matchingKey.tier === "vip") {
          durationSeconds = 30 * 86400; // 30 days (VIP Member)
        }
      }
      const expiresAt = now + durationSeconds;

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
        deviceId: (body.deviceId as string) || generateRandomId(32),
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
        tier: matchingKey?.tier || null,
        leaseDurationSecs: durationSeconds,
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
        deviceId: (body.deviceId as string) || generateRandomId(32),
        recoveryId: (body.recoveryId as string) || generateRandomId(32),
        flavor: body.flavor || "nonroot",
        proofKeyId: body.proofKeyId || body.proof?.keyId || generateRandomId(24),
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

      let tokenRecord: UnlockTokenRecord | null = null;
      if (body.token) {
        if (env.LAUNCHER_KV) {
          tokenRecord = (await env.LAUNCHER_KV.get(`unlock_token:${body.token}`, "json")) as any;
        }
        if (!tokenRecord) {
          tokenRecord = memoryUnlockTokens.get(body.token) || null;
        }
      }

      const passTier = tokenRecord?.tier || "vip";
      let rawKey = tokenRecord?.digitalKey || body.digitalKey;
      if (!rawKey) {
        rawKey =
          passTier === "standard"
            ? CANONICAL_STANDARD_KEY
            : passTier === "lifetime"
            ? CANONICAL_LIFETIME_KEY
            : CANONICAL_VIP_KEY;
      }

      const digitalKey = canonicalizeDigitalKey(rawKey);
      const digitalKeySha256 = await computeSha256UrlSafe(digitalKey);
      const proofKeyId = body.proofKeyId || body.proof?.keyId || generateRandomId(24);

      let durationSeconds = tokenRecord?.durationSeconds;
      if (!durationSeconds) {
        durationSeconds =
          passTier === "standard"
            ? 86400 // 1 day for Standard pass (Linkvertise)
            : passTier === "lifetime"
            ? 10 * 365 * 86400 // 10 years for Lifetime Master
            : 30 * 86400; // 30 days for VIP Member
      }
      const expiresAt = now + durationSeconds;

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
        deviceId: (body.deviceId as string) || generateRandomId(32),
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
        tier: passTier,
        issuedAt: now,
        expiresAt: expiresAt,
        offlineLease: offlineLease,
      });
    }

    // ------------------------------------------------------------------------
    // API: UNLOCK TOKEN REGISTRATION (Standard / VIP / Lifetime)
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher/unlock-token" && method === "POST") {
      const body: any = await request.json().catch(() => ({}));
      const challenge = String(body.challenge || "").trim();
      const deviceId = String(body.deviceId || "").trim();
      const tier: "vip" | "lifetime" | "standard" =
        body.tier === "standard" ? "standard" : body.tier === "lifetime" ? "lifetime" : "vip";

      const token = generateRandomId(32).replace(/[^A-Za-z0-9_-]/g, "A").padEnd(43, "X").substring(0, 43);
      const safeChallenge =
        challenge.length === 43 && /^[A-Za-z0-9_-]{43}$/.test(challenge)
          ? challenge
          : generateRandomId(32).replace(/[^A-Za-z0-9_-]/g, "B").padEnd(43, "Y").substring(0, 43);

      let keyToBind = CANONICAL_VIP_KEY;
      let durationSeconds = 30 * 86400; // 30 days
      if (tier === "standard") {
        keyToBind = CANONICAL_STANDARD_KEY;
        durationSeconds = 86400; // 24 hours / 1 day
      } else if (tier === "lifetime") {
        keyToBind = CANONICAL_LIFETIME_KEY;
        durationSeconds = 10 * 365 * 86400; // 10 years
      }

      if (body.key && typeof body.key === "string" && body.key.trim().length > 0) {
        keyToBind = canonicalizeDigitalKey(body.key.trim());
      }

      const now = Math.floor(Date.now() / 1000);
      const record: UnlockTokenRecord = {
        token,
        challenge: safeChallenge,
        tier,
        digitalKey: keyToBind,
        deviceId: deviceId || undefined,
        createdAt: now,
        durationSeconds,
      };

      memoryUnlockTokens.set(token, record);
      if (env.LAUNCHER_KV) {
        await env.LAUNCHER_KV.put(`unlock_token:${token}`, JSON.stringify(record), {
          expirationTtl: 86400,
        });
      }

      const deepLink = `moodtools-launcher://unlock?token=${encodeURIComponent(token)}&challenge=${encodeURIComponent(safeChallenge)}`;
      return okJson({
        ok: true,
        token,
        challenge: safeChallenge,
        tier,
        digitalKey: keyToBind,
        expiresInSeconds: durationSeconds,
        deepLink,
      });
    }

    // ------------------------------------------------------------------------
    // API: LINKVERTISE CONFIGURATION & REDIRECT
    // ------------------------------------------------------------------------
    if (url.pathname === "/api/launcher/linkvertise-config" && method === "GET") {
      let linkvertiseUrl = "https://link-target.net/123456/jester-mods-standard-pass";
      if (env.LAUNCHER_KV) {
        const stored = await env.LAUNCHER_KV.get("config:linkvertise_url");
        if (stored) linkvertiseUrl = stored;
      }
      return okJson({ ok: true, linkvertiseUrl });
    }

    if (url.pathname === "/api/admin/linkvertise-config" && method === "POST") {
      if (!checkAdminAuth(request, env)) {
        return errJson("Unauthorized: Valid ADMIN_TOKEN required.", 401);
      }
      const body: any = await request.json().catch(() => ({}));
      const linkvertiseUrl = String(body.linkvertiseUrl || "").trim();
      if (!linkvertiseUrl.startsWith("http")) {
        return errJson("Invalid URL: Must start with http:// or https://", 400);
      }
      if (env.LAUNCHER_KV) {
        await env.LAUNCHER_KV.put("config:linkvertise_url", linkvertiseUrl);
      }
      return okJson({ ok: true, linkvertiseUrl });
    }

    if (url.pathname === "/linkvertise/redirect") {
      let linkvertiseUrl = "https://link-target.net/123456/jester-mods-standard-pass";
      if (env.LAUNCHER_KV) {
        const stored = await env.LAUNCHER_KV.get("config:linkvertise_url");
        if (stored) linkvertiseUrl = stored;
      }
      const challenge = url.searchParams.get("challenge") || "";
      const deviceId = url.searchParams.get("deviceId") || "";
      const sep = linkvertiseUrl.includes("?") ? "&" : "?";
      const target = `${linkvertiseUrl}${sep}challenge=${encodeURIComponent(challenge)}&deviceId=${encodeURIComponent(deviceId)}`;
      return Response.redirect(target, 302);
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
        modules: modules.map((m) => {
          // Compute total feature count from featureGroups for the Android client's
          // ModuleCatalogClient.parseFeatures() which expects {path, count} not string[]
          const groupCount = (m.featureGroups || []).reduce(
            (sum: number, g: FeatureGroup) => sum + g.features.length, 0
          );
          const totalFeatureCount = groupCount > 0 ? groupCount : (Array.isArray(m.features) ? m.features.length : 0);
          return {
            packageName: m.packageName,
            slug: m.slug,
            title: m.title,
            version: m.version,
            notes: m.notes,
            category: m.category,
            tags: m.tags,
            featured: m.featured || false,
            popularity: m.popularity || 0,
            publishedAt: m.publishedAt,
            updatedAt: m.updatedAt,
            build: m.build,
            supportedVersions: m.supportedVersions,
            supportedVersionCodes: m.supportedVersionCodes,
            supportedAbis: m.supportedAbis,
            downloadSizeByAbi: m.downloadSizeByAbi,
            nonrootMethod: m.nonrootMethod,
            nonrootMethods: m.nonrootMethods,
            // Android ModuleCatalogClient.parseFeatures() uses optJSONObject("features")
            // and requires {path: "/api/launcher-module-features/{slug}/{build}", count: N}
            features: totalFeatureCount > 0 ? {
              path: `/api/launcher-module-features/${m.slug}/${m.build}`,
              count: totalFeatureCount
            } : undefined,
            source: m.source,
          };
        }),
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

      // Ensure file paths match ModuleIntegrityVerifier requirements:
      // Pattern: /api/launcher-module-payload/$slug/$build/native/$abi and /dex/classes.dex
      const dexPath = `/api/launcher-module-payload/${matched.slug}/${matched.build}/dex/classes.dex`;
      const nativeMap: Record<string, any> = {};
      for (const abi of matched.supportedAbis) {
        const existingAbi = (matched.files?.native as Record<string, any>)?.[abi];
        nativeMap[abi] = {
          path: `/api/launcher-module-payload/${matched.slug}/${matched.build}/native/${abi}`,
          size: existingAbi?.size || VERIFIED_OPBR_SO_SIZE,
          sha256: existingAbi?.sha256 || VERIFIED_OPBR_SO_SHA256,
        };
      }
      const manifestFiles = {
        dex: {
          path: dexPath,
          size: matched.files?.dex?.size || VERIFIED_OPBR_DEX_SIZE,
          sha256: matched.files?.dex?.sha256 || VERIFIED_OPBR_DEX_SHA256,
        },
        native: nativeMap,
      };

      const manifestPayload = {
        schema: 1,
        audience: "moodtools-standalone",
        packageName: matched.packageName,
        slug: matched.slug,
        build: matched.build,
        version: matched.version,
        notes: matched.notes || "",
        minimumBootstrap: 1,
        moduleConfig: {
          packageName: matched.packageName,
          dexFile: "classes.dex",
          nativeFile: "libmenu_native.so",
          title: matched.title,
          entryPoint: matched.moduleConfig?.entryPoint || "com.android.support.Main",
          supportedVersions: matched.supportedVersions,
          supportedVersionCodes: matched.supportedVersionCodes,
          supportedAbis: matched.supportedAbis,
          nonrootMethod: matched.nonrootMethod || "injection",
          nonrootMethods: matched.nonrootMethods || ["injection"],
        },
        files: manifestFiles,
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
      const body: any = await request.json().catch(() => ({}));
      const keyId =
        (body.keyId as string) ||
        (body.proof?.keyId as string) ||
        generateRandomId(24);
      const nonce = generateRandomId(32);
      const now = Math.floor(Date.now() / 1000);
      return okJson({
        ok: true,
        nonce: nonce,
        proofVersion: 1,
        keyId: keyId,
        expiresAt: now + 120,
      });
    }

    if (url.pathname.startsWith("/api/launcher-module-payload/")) {
      const objectKey = url.pathname.replace("/api/launcher-module-payload/", "");

      // Identify whether this is DEX or Native
      const isDex = objectKey.endsWith("classes.dex") || objectKey.includes("/dex/");
      const isNative =
        objectKey.endsWith("libmenu_native.so") ||
        objectKey.endsWith(".so") ||
        objectKey.includes("/native/");

      // 1. Try R2 bucket if configured
      if (env.PAYLOAD_BUCKET) {
        let object = await env.PAYLOAD_BUCKET.get(objectKey);
        if (!object && isDex) {
          object = await env.PAYLOAD_BUCKET.get("payload/com.bandainamcoent.opbrww/93010/classes.dex");
        }
        if (!object && isNative) {
          object = await env.PAYLOAD_BUCKET.get("payload/com.bandainamcoent.opbrww/93010/libmenu_native.so");
        }
        if (object) {
          return new Response(object.body, {
            headers: {
              "Content-Type": "application/octet-stream",
              "Content-Disposition": `attachment; filename="${objectKey.split("/").pop() || "payload.bin"}"`,
            },
          });
        }
      }

      // 2. Try KV storage
      if (env.LAUNCHER_KV) {
        const kvKey = `payload:${objectKey.replace(/\//g, ":")}`;
        let data = await env.LAUNCHER_KV.get(kvKey, "arrayBuffer");

        // Fallback to OPBR verified binary
        if (!data) {
          if (isDex) {
            data = await env.LAUNCHER_KV.get("payload:com.bandainamcoent.opbrww:93010:classes.dex", "arrayBuffer");
          } else if (isNative) {
            data = await env.LAUNCHER_KV.get("payload:com.bandainamcoent.opbrww:93010:libmenu_native.so", "arrayBuffer");
          }
        }

        if (data) {
          const totalBytes = data.byteLength;
          const rangeHeader = request.headers.get("range") || request.headers.get("Range");
          if (rangeHeader && rangeHeader.startsWith("bytes=")) {
            const rangeSpec = rangeHeader.replace("bytes=", "").trim();
            const parts = rangeSpec.split("-");
            const start = parseInt(parts[0], 10) || 0;
            const end = parts[1] ? parseInt(parts[1], 10) : totalBytes - 1;
            const chunk = data.slice(start, end + 1);
            return new Response(chunk, {
              status: 206,
              headers: {
                "Content-Type": "application/octet-stream",
                "Content-Range": `bytes ${start}-${end}/${totalBytes}`,
                "Content-Length": String(chunk.byteLength),
                "Accept-Ranges": "bytes",
              },
            });
          }

          return new Response(data, {
            status: 200,
            headers: {
              "Content-Type": "application/octet-stream",
              "Content-Length": String(totalBytes),
              "Accept-Ranges": "bytes",
            },
          });
        }
      }

      return errJson(`Module payload not found: ${objectKey}`, 404);
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
      } else {
        const queryPkgs = url.searchParams.get("packageNames") || url.searchParams.get("packages");
        if (queryPkgs) {
          packageNames = queryPkgs.split(",").map((p) => p.trim()).filter(Boolean);
        }
      }
      const modules = await getStoredModules(env);
      const results = packageNames.map((pkg) => {
        const known = REAL_PLAY_STORE_VERSIONS[pkg];
        const mod = modules.find((m) => m.packageName === pkg);
        const ver = known?.version || mod?.version || "1.0.0";
        const code = known?.versionCode || mod?.build || 1000;
        return {
          ok: true,
          packageName: pkg,
          version: ver,
          versionCode: code,
          checkedAt: now,
          listingUpdatedAt: now - 3600,
          stale: false,
          updateAvailable: false,
        };
      });
      return okJson({
        ok: true,
        schema: 1,
        results,
      });
    }

    if (url.pathname.startsWith("/api/launcher-play-store-version/")) {
      const pkg = url.pathname.replace("/api/launcher-play-store-version/", "");
      const now = Math.floor(Date.now() / 1000);
      const modules = await getStoredModules(env);
      const known = REAL_PLAY_STORE_VERSIONS[pkg];
      const mod = modules.find((m) => m.packageName === pkg);
      const ver = known?.version || mod?.version || "1.0.0";
      const code = known?.versionCode || mod?.build || 1000;
      return okJson({
        ok: true,
        schema: 1,
        packageName: pkg,
        version: ver,
        versionCode: code,
        checkedAt: now,
        listingUpdatedAt: now - 3600,
        stale: false,
        updateAvailable: false,
      });
    }

    if (url.pathname.startsWith("/api/launcher-module-changelog/")) {
      const parts = url.pathname.split("/").filter(Boolean);
      const slug = parts[2] || "opbr-bounty-rush-mod";
      const build = parseInt(parts[3] || "93010", 10);

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
      const slug = parts[2] || "opbr-bounty-rush-mod";
      const build = parseInt(parts[3] || "93010", 10);

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

    // ── Module Icon Endpoint ──────────────────────────────────────────────────
    // Android CatalogIconClient downloads from /api/launcher-module-icon/{slug}/{build}[/{sha256}]
    // Validates: require(url.protocol == "https" && url.host == HOST)
    // Verifies: SHA-256 hash and size must match catalog icon metadata
    if (url.pathname.startsWith("/api/launcher-module-icon/")) {
      const parts = url.pathname.split("/").filter(Boolean);
      const slug = parts[2] || "";
      const build = parts[3] || "";

      // Try KV storage for the icon binary
      if (env.LAUNCHER_KV) {
        const kvKey = `icon:${slug}:${build}`;
        const data = await env.LAUNCHER_KV.get(kvKey, "arrayBuffer");
        if (data) {
          const rangeHeader = request.headers.get("range") || request.headers.get("Range");
          if (rangeHeader && rangeHeader.startsWith("bytes=")) {
            const rangeSpec = rangeHeader.replace("bytes=", "").trim();
            const rangeParts = rangeSpec.split("-");
            const start = parseInt(rangeParts[0], 10) || 0;
            const end = rangeParts[1] ? parseInt(rangeParts[1], 10) : data.byteLength - 1;
            const chunk = data.slice(start, end + 1);
            return new Response(chunk, {
              status: 206,
              headers: {
                "Content-Type": "image/png",
                "Content-Range": `bytes ${start}-${end}/${data.byteLength}`,
                "Content-Length": String(chunk.byteLength),
                "Accept-Ranges": "bytes",
                "Cache-Control": "public, max-age=86400",
              },
            });
          }
          return new Response(data, {
            status: 200,
            headers: {
              "Content-Type": "image/png",
              "Content-Length": String(data.byteLength),
              "Accept-Ranges": "bytes",
              "Cache-Control": "public, max-age=86400",
            },
          });
        }
      }

      // No icon stored — return 404 (Android CatalogIconClient handles this gracefully)
      return errJson(`Module icon not found: ${slug}/${build}`, 404);
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
      const nonrootSha = "4a4e14f886f4a74288b8593c683b5d20911762c478a0ff1fbdb448cf60ecaa97";
      const rootSha = "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069";
      const apkSize = 15728640;

      if (isTest) {
        releasePayload.flavor = testFlavor;
        releasePayload.file = {
          path: `/api/launcher-test-download/${BUILD}/${testFlavor}.apk`,
          sha256: testFlavor === "root" ? rootSha : nonrootSha,
          size: apkSize,
        };
      } else {
        releasePayload.files = {
          root: {
            path: `/api/launcher-download/${BUILD}/root.apk`,
            sha256: rootSha,
            size: apkSize,
          },
          nonroot: {
            path: `/api/launcher-download/${BUILD}/nonroot.apk`,
            sha256: nonrootSha,
            size: apkSize,
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
        const existing = existingIdx >= 0 ? modules[existingIdx] : undefined;

        const build = Number(body.build) || existing?.build || 1;
        const version = (body.version || existing?.version || "1.0.0").trim();
        const rawMethod = (body.nonrootMethod || existing?.nonrootMethod || "injection").trim().toLowerCase();
        const validMethods = ["injection", "direct_patch", "identity_shell"];
        const effectiveMethod = validMethods.includes(rawMethod) ? rawMethod : "injection";

        // NonRootMethods list: first item must be nonrootMethod
        let nonrootMethods: string[] = Array.isArray(body.nonrootMethods) && body.nonrootMethods.length > 0
          ? body.nonrootMethods.filter((m: string) => validMethods.includes(m))
          : (existing?.nonrootMethods || [effectiveMethod]);
        if (!nonrootMethods.includes(effectiveMethod)) {
          nonrootMethods.unshift(effectiveMethod);
        } else if (nonrootMethods[0] !== effectiveMethod) {
          nonrootMethods = [effectiveMethod, ...nonrootMethods.filter((m: string) => m !== effectiveMethod)];
        }

        // Supported versions
        let supportedVersions: string[] = Array.isArray(body.supportedVersions) && body.supportedVersions.length > 0
          ? body.supportedVersions.map((v: any) => String(v).trim()).filter(Boolean)
          : (existing?.supportedVersions || [version]);
        if (supportedVersions.length === 0) supportedVersions = [version];

        // Supported version codes: must match supportedVersions.length if present
        let supportedVersionCodes: number[] = Array.isArray(body.supportedVersionCodes) && body.supportedVersionCodes.length > 0
          ? body.supportedVersionCodes.map((c: any) => Number(c) || build)
          : (existing?.supportedVersionCodes || [build]);
        if (supportedVersionCodes.length !== supportedVersions.length) {
          if (supportedVersionCodes.length > supportedVersions.length) {
            supportedVersionCodes = supportedVersionCodes.slice(0, supportedVersions.length);
          } else {
            while (supportedVersionCodes.length < supportedVersions.length) {
              supportedVersionCodes.push(build);
            }
          }
        }

        // Supported ABIs (only arm64-v8a and armeabi-v7a are valid on client)
        let supportedAbis: string[] = Array.isArray(body.supportedAbis) && body.supportedAbis.length > 0
          ? body.supportedAbis.filter((a: string) => a === "arm64-v8a" || a === "armeabi-v7a")
          : (existing?.supportedAbis || ["arm64-v8a"]);
        if (supportedAbis.length === 0) supportedAbis = ["arm64-v8a"];

        // Features
        const features: string[] = Array.isArray(body.features) && body.features.length > 0
          ? body.features.map((f: any) => String(f).trim()).filter(Boolean)
          : (existing?.features || ["Enhanced gameplay", "Custom overlay menu", "Real-time statistics"]);

        // Tags
        const tags: string[] = Array.isArray(body.tags) && body.tags.length > 0
          ? body.tags.map((t: any) => String(t).trim()).filter(Boolean)
          : (existing?.tags || ["action", "injection", "featured"]);

        const entryPoint = (body.entryPoint || existing?.moduleConfig?.entryPoint || "com.android.support.Main").trim();

        // Files & canonical paths (enforced by ModuleIntegrityVerifier)
        const dexSize = Number(body.files?.dex?.size) || existing?.files?.dex?.size || VERIFIED_OPBR_DEX_SIZE;
        const dexSha256 = body.files?.dex?.sha256 || existing?.files?.dex?.sha256 || VERIFIED_OPBR_DEX_SHA256;
        const dexPath = `/api/launcher-module-payload/${body.slug}/${build}/dex/classes.dex`;

        const nativeMap: Record<string, { path: string; size: number; sha256: string }> = {};
        const downloadSizeByAbi: Record<string, number> = {};

        for (const abi of supportedAbis) {
          const existingAbi = existing?.files?.native?.[abi];
          const abiSize = Number(body.files?.native?.[abi]?.size) || existingAbi?.size || VERIFIED_OPBR_SO_SIZE;
          const abiSha = body.files?.native?.[abi]?.sha256 || existingAbi?.sha256 || VERIFIED_OPBR_SO_SHA256;
          nativeMap[abi] = {
            path: `/api/launcher-module-payload/${body.slug}/${build}/native/${abi}`,
            size: abiSize,
            sha256: abiSha,
          };
          downloadSizeByAbi[abi] = dexSize + abiSize;
        }

        const primaryAbi = supportedAbis[0] || "arm64-v8a";
        const primaryNative = nativeMap[primaryAbi];
        const source = body.source || existing?.source || {
          path: `/api/launcher-module-payload/${body.slug}/${build}/native/${primaryAbi}`,
          sizeBytes: primaryNative?.size || VERIFIED_OPBR_SO_SIZE,
          sha256: primaryNative?.sha256 || VERIFIED_OPBR_SO_SHA256,
        };

        const moduleConfig = body.moduleConfig || {
          packageName: body.packageName,
          dexFile: "classes.dex",
          nativeFile: "libmenu_native.so",
          title: body.title,
          entryPoint: entryPoint,
          supportedVersions: supportedVersions,
          supportedVersionCodes: supportedVersionCodes,
          supportedAbis: supportedAbis,
          nonrootMethod: effectiveMethod,
          nonrootMethods: nonrootMethods,
        };

        const changelogEntries = body.changelogEntries || existing?.changelogEntries || [
          {
            build: build,
            version: version,
            notes: body.notes || "Continuous performance and stability updates.",
            publishedAt: now,
            updateType: "feature",
          },
        ];

        const featureGroups = body.featureGroups || existing?.featureGroups || [
          {
            title: "Combat & Overlay Enhancements",
            features: features,
          },
        ];

        const newModule: ModuleItem = {
          packageName: body.packageName,
          slug: body.slug,
          title: body.title,
          version: version,
          notes: body.notes !== undefined ? body.notes : (existing?.notes || ""),
          category: body.category || existing?.category || "Action",
          tags: tags,
          featured: body.featured !== undefined ? (body.featured === true || body.featured === "true") : (existing?.featured ?? true),
          popularity: Number(body.popularity) || existing?.popularity || 750000,
          publishedAt: existing ? existing.publishedAt : now,
          updatedAt: now,
          build: build,
          supportedVersions: supportedVersions,
          supportedVersionCodes: supportedVersionCodes,
          supportedAbis: supportedAbis,
          downloadSizeByAbi: body.downloadSizeByAbi || downloadSizeByAbi,
          nonrootMethod: effectiveMethod,
          nonrootMethods: nonrootMethods,
          features: features,
          source: source,
          moduleConfig: moduleConfig,
          files: {
            dex: {
              path: dexPath,
              size: dexSize,
              sha256: dexSha256,
            },
            native: nativeMap,
          },
          changelogEntries: changelogEntries,
          featureGroups: featureGroups,
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
      const isAutoVerified =
        url.searchParams.get("linkvertise") === "done" ||
        url.searchParams.get("linkvertise") === "success" ||
        url.searchParams.get("verified") === "1";

      let linkvertiseTarget = "https://link-target.net/123456/jester-mods-standard-pass";
      if (env.LAUNCHER_KV) {
        const stored = await env.LAUNCHER_KV.get("config:linkvertise_url");
        if (stored) linkvertiseTarget = stored;
      }

      const html = `<!DOCTYPE html>
<html lang="en" style="color-scheme: dark;">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Jester Mods — Digital Pass Gateway</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #07090e;
      --card: rgba(17, 24, 39, 0.85);
      --card-border: rgba(255, 255, 255, 0.1);
      --accent: #8b5cf6;
      --accent-glow: rgba(139, 92, 246, 0.4);
      --indigo: #6366f1;
      --emerald: #10b981;
      --amber: #f59e0b;
      --cyan: #06b6d4;
      --text: #f8fafc;
      --muted: #94a3b8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }
    body {
      background: var(--bg);
      background-image: 
        radial-gradient(circle at 50% 10%, rgba(99, 102, 241, 0.22) 0%, transparent 60%),
        radial-gradient(circle at 10% 90%, rgba(139, 92, 246, 0.15) 0%, transparent 50%),
        radial-gradient(circle at 90% 90%, rgba(16, 185, 129, 0.08) 0%, transparent 50%);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem 1rem;
    }
    .card {
      background: var(--card);
      backdrop-filter: blur(24px);
      -webkit-backdrop-filter: blur(24px);
      border: 1px solid var(--card-border);
      border-radius: 1.75rem;
      padding: 2.25rem 2rem;
      width: 100%;
      max-width: 480px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6), 0 0 30px -5px var(--accent-glow);
      text-align: center;
      position: relative;
      overflow: hidden;
    }
    .card::before {
      content: '';
      position: absolute;
      top: 0; left: 0; right: 0;
      height: 3px;
      background: linear-gradient(90deg, #8b5cf6, #3b82f6, #10b981);
    }
    .logo {
      width: 64px;
      height: 64px;
      margin: 0 auto 1.25rem;
      border-radius: 1.25rem;
      background: linear-gradient(135deg, #a855f7, #6366f1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2rem;
      box-shadow: 0 8px 24px var(--accent-glow);
    }
    h1 { font-size: 1.65rem; font-weight: 800; margin-bottom: 0.35rem; letter-spacing: -0.02em; }
    p.lead { color: var(--muted); font-size: 0.9rem; margin-bottom: 1.5rem; line-height: 1.45; }

    /* Tier Selector Pills */
    .tier-nav {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      gap: 0.4rem;
      background: rgba(11, 15, 25, 0.8);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 0.85rem;
      padding: 0.35rem;
      margin-bottom: 1.5rem;
    }
    .tier-btn {
      padding: 0.6rem 0.3rem;
      border: none;
      background: transparent;
      color: var(--muted);
      border-radius: 0.65rem;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.2rem;
    }
    .tier-btn span.emoji { font-size: 1.1rem; }
    .tier-btn.active {
      background: linear-gradient(135deg, rgba(139, 92, 246, 0.3), rgba(99, 102, 241, 0.3));
      color: #fff;
      border: 1px solid rgba(139, 92, 246, 0.5);
      box-shadow: 0 4px 12px rgba(139, 92, 246, 0.25);
    }

    /* Tab Panels */
    .tab-panel { display: none; text-align: left; }
    .tab-panel.active { display: block; animation: fadeIn 0.2s ease-in-out; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

    .badge-bar {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      padding: 0.3rem 0.65rem;
      border-radius: 9999px;
      margin-bottom: 0.75rem;
    }
    .badge-amber { background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); }
    .badge-purple { background: rgba(139, 92, 246, 0.15); color: #c4b5fd; border: 1px solid rgba(139, 92, 246, 0.3); }
    .badge-cyan { background: rgba(6, 182, 212, 0.15); color: #67e8f9; border: 1px solid rgba(6, 182, 212, 0.3); }
    .badge-emerald { background: rgba(16, 185, 129, 0.15); color: #6ee7b7; border: 1px solid rgba(16, 185, 129, 0.3); }

    .tier-desc {
      font-size: 0.86rem;
      color: var(--muted);
      line-height: 1.5;
      margin-bottom: 1.25rem;
    }

    /* Steps box for Linkvertise */
    .steps-box {
      background: rgba(11, 15, 25, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 0.85rem;
      padding: 0.9rem;
      margin-bottom: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
    }
    .step-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      font-size: 0.82rem;
      color: #cbd5e1;
    }
    .step-num {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: rgba(245, 158, 11, 0.2);
      color: #f59e0b;
      font-weight: 700;
      font-size: 0.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .input-group { margin-bottom: 1.25rem; text-align: left; }
    label { display: block; font-size: 0.82rem; font-weight: 600; color: var(--muted); margin-bottom: 0.4rem; }
    input {
      width: 100%;
      padding: 0.85rem 1rem;
      background: rgba(11, 13, 20, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 0.75rem;
      color: #fff;
      font-size: 0.92rem;
      outline: none;
      transition: all 0.2s;
      font-family: 'JetBrains Mono', monospace;
    }
    input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }

    .btn {
      width: 100%;
      padding: 0.95rem;
      border: none;
      border-radius: 0.75rem;
      background: linear-gradient(135deg, #8b5cf6, #6366f1);
      color: #fff;
      font-size: 0.95rem;
      font-weight: 700;
      cursor: pointer;
      transition: transform 0.15s, box-shadow 0.2s;
      box-shadow: 0 4px 16px var(--accent-glow);
      margin-bottom: 0.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
    }
    .btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(139, 92, 246, 0.6); }
    .btn-gold {
      background: linear-gradient(135deg, #f59e0b, #d97706);
      box-shadow: 0 4px 16px rgba(245, 158, 11, 0.35);
    }
    .btn-gold:hover { box-shadow: 0 6px 20px rgba(245, 158, 11, 0.5); }
    .btn-emerald {
      background: linear-gradient(135deg, #10b981, #059669);
      box-shadow: 0 4px 16px rgba(16, 185, 129, 0.35);
    }
    .btn-emerald:hover { box-shadow: 0 6px 20px rgba(16, 185, 129, 0.5); }
    .btn-cyan {
      background: linear-gradient(135deg, #06b6d4, #0284c7);
      box-shadow: 0 4px 16px rgba(6, 182, 212, 0.35);
    }
    .btn-cyan:hover { box-shadow: 0 6px 20px rgba(6, 182, 212, 0.5); }

    .btn-secondary {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      box-shadow: none;
      color: var(--text);
    }
    .btn-secondary:hover { background: rgba(255, 255, 255, 0.1); box-shadow: none; }

    .device-info {
      margin-top: 1.5rem;
      font-size: 0.75rem;
      color: #64748b;
      word-break: break-all;
      line-height: 1.6;
      background: rgba(0, 0, 0, 0.3);
      padding: 0.75rem;
      border-radius: 0.6rem;
      border: 1px solid rgba(255, 255, 255, 0.05);
      text-align: left;
    }
    .status-msg {
      margin-top: 1rem;
      padding: 0.85rem;
      border-radius: 0.65rem;
      font-size: 0.86rem;
      font-weight: 500;
      display: none;
      line-height: 1.4;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">🎭</div>
    <h1>Digital Pass Gateway</h1>
    <p class="lead">Select your pass tier to bind your Android device and access Jester Launcher mods.</p>

    <!-- Tier Selector Navigation -->
    <div class="tier-nav">
      <button class="tier-btn active" id="btn-tab-standard" onclick="switchTab('standard')">
        <span class="emoji">🎟️</span>
        <span>Standard</span>
      </button>
      <button class="tier-btn" id="btn-tab-vip" onclick="switchTab('vip')">
        <span class="emoji">👑</span>
        <span>VIP Member</span>
      </button>
      <button class="tier-btn" id="btn-tab-lifetime" onclick="switchTab('lifetime')">
        <span class="emoji">💎</span>
        <span>Lifetime</span>
      </button>
    </div>

    <!-- TAB 1: STANDARD PASS (LINKVERTISE) -->
    <div id="panel-standard" class="tab-panel active">
      <div class="badge-bar badge-amber">🎟️ Free 24-Hour Pass · Linkvertise</div>
      <p class="tier-desc">Complete the quick Linkvertise sponsor route to activate full standard launcher access for 24 hours without payment.</p>

      <div class="steps-box">
        <div class="step-row">
          <div class="step-num">1</div>
          <div>Click Continue to Linkvertise partner route</div>
        </div>
        <div class="step-row">
          <div class="step-num">2</div>
          <div>Complete the partner verification on sponsor page</div>
        </div>
        <div class="step-row">
          <div class="step-num">3</div>
          <div>Tap Launch In Jester App for 24h pass</div>
        </div>
      </div>

      <div id="standardUnverifiedActions">
        <button class="btn btn-gold" onclick="openLinkvertise()">
          <span>🔗</span> Continue to Linkvertise
        </button>
        <button class="btn btn-secondary" onclick="verifyStandardPass()">
          <span>⚡</span> Verify Linkvertise Completion
        </button>
      </div>

      <div id="standardVerifiedActions" style="display: ${isAutoVerified ? 'block' : 'none'};">
        <div style="background: rgba(16, 185, 129, 0.12); border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 0.75rem; padding: 0.85rem; margin-bottom: 0.75rem; text-align: center;">
          <div style="color: #34d399; font-weight: 700; font-size: 0.92rem; margin-bottom: 0.2rem;">✅ Linkvertise Verified!</div>
          <div style="color: #94a3b8; font-size: 0.78rem;">Standard Pass active for 24 hours.</div>
        </div>
        <button class="btn btn-emerald" onclick="activateStandardDevice()">
          <span>🚀</span> Launch In Jester App
        </button>
        <button class="btn btn-secondary" onclick="copyStandardDeepLink()">
          <span>📋</span> Copy Activation Link
        </button>
      </div>
    </div>

    <!-- TAB 2: VIP MEMBER (FULL CATALOG) -->
    <div id="panel-vip" class="tab-panel">
      <div class="badge-bar badge-purple">👑 VIP Member · Full Catalog · 30 Days</div>
      <p class="tier-desc">Full access to the entire mod catalog: ONE PIECE Bounty Rush, Jester Velocity Engine, radar visual overlays, and exclusive private VIP modules.</p>

      <div class="input-group">
        <label for="vipPassKey">Digital VIP Pass Key</label>
        <input type="text" id="vipPassKey" placeholder="e.g. VIP-MEMBER-ACCESS" value="VIP-MEMBER-ACCESS">
      </div>

      <button class="btn" onclick="activateTier('vip')">
        <span>👑</span> Activate VIP Member Pass
      </button>
      <button class="btn btn-secondary" onclick="copyTierDeepLink('vip')">
        <span>📋</span> Copy VIP Activation Link
      </button>
    </div>

    <!-- TAB 3: LIFETIME MASTER -->
    <div id="panel-lifetime" class="tab-panel">
      <div class="badge-bar badge-cyan">💎 Lifetime Master · Permanent Unrestricted</div>
      <p class="tier-desc">Permanent master access to every module and feature. Never expires, zero renewals, offline-bound license with unlimited updates.</p>

      <div class="input-group">
        <label for="lifetimePassKey">Lifetime Master Key</label>
        <input type="text" id="lifetimePassKey" placeholder="e.g. JM-PREMIUM-2026" value="JM-PREMIUM-2026">
      </div>

      <button class="btn btn-cyan" onclick="activateTier('lifetime')">
        <span>💎</span> Activate Lifetime Master Pass
      </button>
      <button class="btn btn-secondary" onclick="copyTierDeepLink('lifetime')">
        <span>📋</span> Copy Lifetime Activation Link
      </button>
    </div>

    <div id="statusMsg" class="status-msg"></div>

    <div class="device-info">
      <div><strong>Challenge:</strong> ${challenge ? challenge.substring(0, 20) + "..." : "Auto-Generated"}</div>
      <div><strong>Device ID:</strong> ${deviceId ? deviceId.substring(0, 20) + "..." : "Auto-Detected"}</div>
      <div><strong>Linkvertise:</strong> ${isAutoVerified ? "✅ Verified" : "⏳ Pending Route"}</div>
    </div>
  </div>

  <script>
    const challengeParam = "${challenge}";
    const deviceIdParam = "${deviceId}";
    const linkvertiseUrl = "${linkvertiseTarget}";

    function switchTab(tab) {
      document.querySelectorAll('.tier-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
      document.getElementById('btn-tab-' + tab).classList.add('active');
      document.getElementById('panel-' + tab).classList.add('active');
    }

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

    function getSafeChallenge() {
      return (challengeParam && challengeParam.length === 43 && /^[A-Za-z0-9_-]{43}$/.test(challengeParam))
        ? challengeParam
        : genId43();
    }

    // Linkvertise flow
    function openLinkvertise() {
      const chal = getSafeChallenge();
      const currentUrl = window.location.origin + window.location.pathname + "?challenge=" + encodeURIComponent(chal) + "&deviceId=" + encodeURIComponent(deviceIdParam) + "&linkvertise=done";
      const sep = linkvertiseUrl.includes('?') ? '&' : '?';
      const target = linkvertiseUrl + sep + "r=" + encodeURIComponent(currentUrl);
      window.open(target, "_blank");
      
      const status = document.getElementById("statusMsg");
      status.style.display = "block";
      status.style.background = "rgba(245, 158, 11, 0.15)";
      status.style.color = "#fbbf24";
      status.innerText = "Linkvertise opened in a new tab. Complete the partner task, then tap Verify below.";
    }

    let standardDeepLinkUrl = "";

    async function verifyStandardPass() {
      const status = document.getElementById("statusMsg");
      status.style.display = "block";
      status.style.background = "rgba(99, 102, 241, 0.15)";
      status.style.color = "#c4b5fd";
      status.innerText = "Verifying Linkvertise route completion...";

      try {
        const chal = getSafeChallenge();
        const res = await fetch("/api/launcher/unlock-token", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            tier: "standard",
            challenge: chal,
            deviceId: deviceIdParam,
          })
        });
        const data = await res.json();
        if (data.ok) {
          standardDeepLinkUrl = data.deepLink;
          document.getElementById("standardUnverifiedActions").style.display = "none";
          document.getElementById("standardVerifiedActions").style.display = "block";
          status.style.background = "rgba(16, 185, 129, 0.15)";
          status.style.color = "#6ee7b7";
          status.innerText = "Linkvertise verified! Standard pass active for 24 hours.";
        } else {
          status.innerText = "Verification failed: " + (data.message || "Please retry Linkvertise route");
        }
      } catch (e) {
        status.innerText = "Verification error. Please retry.";
      }
    }

    function activateStandardDevice() {
      if (!standardDeepLinkUrl) {
        const chal = getSafeChallenge();
        const token = genId43();
        standardDeepLinkUrl = "moodtools-launcher://unlock?token=" + encodeURIComponent(token) + "&challenge=" + encodeURIComponent(chal);
      }
      window.location.href = standardDeepLinkUrl;
    }

    async function copyStandardDeepLink() {
      if (!standardDeepLinkUrl) {
        await verifyStandardPass();
      }
      await navigator.clipboard.writeText(standardDeepLinkUrl);
      const status = document.getElementById("statusMsg");
      status.style.display = "block";
      status.style.background = "rgba(16, 185, 129, 0.15)";
      status.style.color = "#6ee7b7";
      status.innerText = "Standard Pass activation link copied to clipboard!";
      setTimeout(() => { status.style.display = "none"; }, 3500);
    }

    // VIP / Lifetime flow
    async function activateTier(tier) {
      const keyInput = tier === "vip" 
        ? document.getElementById("vipPassKey").value 
        : document.getElementById("lifetimePassKey").value;

      const status = document.getElementById("statusMsg");
      status.style.display = "block";
      status.style.background = "rgba(139, 92, 246, 0.15)";
      status.style.color = "#c4b5fd";
      status.innerText = "Registering " + (tier === "vip" ? "VIP Member" : "Lifetime Master") + " pass...";

      try {
        const chal = getSafeChallenge();
        const res = await fetch("/api/launcher/unlock-token", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            tier: tier,
            key: keyInput,
            challenge: chal,
            deviceId: deviceIdParam,
          })
        });
        const data = await res.json();
        if (data.ok) {
          status.style.background = "rgba(16, 185, 129, 0.15)";
          status.style.color = "#6ee7b7";
          status.innerText = "Redirecting to Jester Launcher app...";
          window.location.href = data.deepLink;
        } else {
          status.innerText = "Error: " + (data.message || "Failed to register pass");
        }
      } catch (e) {
        status.innerText = "Registration error: " + e.message;
      }
    }

    async function copyTierDeepLink(tier) {
      const keyInput = tier === "vip" 
        ? document.getElementById("vipPassKey").value 
        : document.getElementById("lifetimePassKey").value;

      const chal = getSafeChallenge();
      try {
        const res = await fetch("/api/launcher/unlock-token", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            tier: tier,
            key: keyInput,
            challenge: chal,
            deviceId: deviceIdParam,
          })
        });
        const data = await res.json();
        if (data.ok && data.deepLink) {
          await navigator.clipboard.writeText(data.deepLink);
          const status = document.getElementById("statusMsg");
          status.style.display = "block";
          status.style.background = "rgba(16, 185, 129, 0.15)";
          status.style.color = "#6ee7b7";
          status.innerText = (tier === "vip" ? "VIP Member" : "Lifetime Master") + " link copied!";
          setTimeout(() => { status.style.display = "none"; }, 3500);
        }
      } catch (e) {
        alert("Could not copy link");
      }
    }

    // Auto verify if returned with linkvertise=done
    ${isAutoVerified ? 'verifyStandardPass();' : ''}
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

        <div class="card" style="grid-column: span 2; margin-top: 1rem;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <h2 style="font-size: 1.1rem; font-weight: 700;">Linkvertise Sponsor Gateway Settings</h2>
            <span class="badge badge-indigo">Standard Pass Route</span>
          </div>
          <p style="font-size: 0.8rem; color: var(--muted); margin-bottom: 1rem;">Configure the destination Linkvertise publisher link where users complete the sponsor route for a 24-hour Standard Pass.</p>
          <div style="display: flex; gap: 0.75rem; align-items: flex-end; flex-wrap: wrap;">
            <div class="form-group" style="flex: 1; min-width: 280px; margin-bottom: 0;">
              <label>Linkvertise Publisher Target URL</label>
              <input type="text" id="adminLinkvertiseUrl" placeholder="https://link-target.net/123456/jester-mods-standard-pass">
            </div>
            <button class="btn" style="width: auto; padding: 0.8rem 1.5rem; margin-bottom: 0;" onclick="saveLinkvertiseUrl()">Save Linkvertise Route</button>
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
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-module-changelog/opbr-bounty-rush-mod/93010')">GET /api/launcher-module-changelog</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-module-features/opbr-bounty-rush-mod/93010')">GET /api/launcher-module-features</button>
            <button class="btn btn-outline" onclick="runApiTest('/api/launcher-play-store-version/com.bandainamcoent.opbrww')">GET /api/launcher-play-store-version</button>
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
  <dialog id="moduleModal" style="max-width: 680px; width: 90vw;">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
      <h2 style="font-size: 1.25rem; font-weight: 700;" id="modalModuleTitle">Add New Module</h2>
      <button class="btn btn-outline btn-sm" onclick="closeModuleModal()">✕</button>
    </div>
    <form id="moduleForm" onsubmit="saveModule(event)">
      <div class="grid-2">
        <div class="form-group">
          <label>Slug (Unique ID)</label>
          <input type="text" id="mSlug" placeholder="e.g. opbr-bounty-rush-mod" required>
        </div>
        <div class="form-group">
          <label>Package Name</label>
          <input type="text" id="mPkg" placeholder="e.g. com.bandainamcoent.opbrww" required>
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Display Title</label>
          <input type="text" id="mTitle" placeholder="e.g. ONE PIECE Bounty Rush Mod" required>
        </div>
        <div class="form-group">
          <label>Category</label>
          <input type="text" id="mCategory" placeholder="e.g. Action">
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Version String</label>
          <input type="text" id="mVersion" placeholder="9.3.0" value="1.0.0" required>
        </div>
        <div class="form-group">
          <label>Build Code (Integer)</label>
          <input type="number" id="mBuild" placeholder="93010" value="1" required>
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Supported Game Versions (CSV)</label>
          <input type="text" id="mVersions" placeholder="9.3.0, 9.2.8" required>
        </div>
        <div class="form-group">
          <label>Supported Build Numbers / Codes (CSV)</label>
          <input type="text" id="mVersionCodes" placeholder="93010, 92800" required>
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Non-Root Injection Method</label>
          <select id="mMethod">
            <option value="injection">injection (DEX ClassLoader + libmenu_native.so hooks)</option>
            <option value="direct_patch">direct_patch (Direct Binary Patch)</option>
            <option value="identity_shell">identity_shell (Identity Shell Sandbox)</option>
          </select>
        </div>
        <div class="form-group">
          <label>Entry Point Class</label>
          <input type="text" id="mEntryPoint" placeholder="com.android.support.Main" value="com.android.support.Main">
        </div>
      </div>
      <div class="grid-2">
        <div class="form-group">
          <label>Supported ABIs</label>
          <div style="display: flex; gap: 1rem; align-items: center; margin-top: 0.5rem;">
            <label style="display: flex; align-items: center; gap: 0.25rem; font-weight: normal; cursor: pointer;">
              <input type="checkbox" id="mAbiArm64" value="arm64-v8a" checked> arm64-v8a
            </label>
            <label style="display: flex; align-items: center; gap: 0.25rem; font-weight: normal; cursor: pointer;">
              <input type="checkbox" id="mAbiArmeabi" value="armeabi-v7a"> armeabi-v7a
            </label>
          </div>
        </div>
        <div class="form-group">
          <label>Featured & Popularity</label>
          <div style="display: flex; gap: 1rem; align-items: center; margin-top: 0.25rem;">
            <label style="display: flex; align-items: center; gap: 0.25rem; font-weight: normal; cursor: pointer;">
              <input type="checkbox" id="mFeatured" checked> Featured
            </label>
            <input type="number" id="mPopularity" placeholder="950000" style="max-width: 140px;" value="750000">
          </div>
        </div>
      </div>
      <div class="form-group">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem;">
          <label style="margin-bottom: 0;">Features (one per line or upload .json)</label>
          <div style="display: flex; gap: 0.4rem; align-items: center;">
            <input type="file" id="mFeaturesJsonInput" accept=".json,application/json" style="display: none;" onchange="handleFeaturesJsonUpload(event)">
            <button type="button" class="btn btn-outline btn-sm" onclick="document.getElementById('mFeaturesJsonInput').click()" title="Upload features.json or config.json from module">📁 Upload .json</button>
            <button type="button" class="btn btn-outline btn-sm" onclick="loadExampleFeaturesJson()" title="Load module example features.json template">⚡ Example .json</button>
          </div>
        </div>
        <div id="featuresJsonStatus" style="display: none; margin-bottom: 0.5rem;"></div>
        <textarea id="mFeatures" rows="3" placeholder="Damage Multiplier&#10;Defense Boost&#10;Radar Map Reveal"></textarea>
      </div>
      <div class="form-group">
        <label>Tags (comma separated)</label>
        <input type="text" id="mTags" placeholder="action, anime, pvp, injection, featured">
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
          tbody.innerHTML = data.keys.map(k => {
            const tierBadge = k.tier === 'lifetime' 
              ? '<span class="badge" style="background: rgba(6, 182, 212, 0.15); color: #67e8f9; border: 1px solid rgba(6, 182, 212, 0.3);">LIFETIME MASTER</span>'
              : k.tier === 'vip'
              ? '<span class="badge badge-purple">VIP MEMBER</span>'
              : '<span class="badge" style="background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3);">STANDARD PASS</span>';
            return \`
            <tr>
              <td><span class="code-pill" style="color: #6ee7b7; font-size: 0.75rem;">\${k.key.substring(0, 24)}...</span></td>
              <td>\${tierBadge}</td>
              <td>
                <span class="badge \${k.active ? 'badge-green' : 'badge-red'}">\${k.active ? 'Active' : 'Revoked'}</span>
              </td>
              <td>\${(k.boundDevices || []).length} / \${k.maxDevices}</td>
              <td>
                <button class="btn btn-outline btn-sm" onclick="copyText('\${k.key}')">Copy</button>
                <button class="btn btn-sm \${k.active ? 'btn-danger' : 'btn-outline'}" onclick="toggleKey('\${k.key}', \${!k.active})">\${k.active ? 'Revoke' : 'Restore'}</button>
              </td>
            </tr>
          \`}).join("");
        } else {
          tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--muted);">No keys generated yet.</td></tr>';
        }
        await loadLinkvertiseConfig();
      } catch (e) {
        console.error("Keys load failed", e);
      }
    }

    async function loadLinkvertiseConfig() {
      try {
        const res = await fetch("/api/launcher/linkvertise-config");
        const data = await res.json();
        if (data.ok && data.linkvertiseUrl) {
          document.getElementById("adminLinkvertiseUrl").value = data.linkvertiseUrl;
        }
      } catch (e) {}
    }

    async function saveLinkvertiseUrl() {
      const linkvertiseUrl = document.getElementById("adminLinkvertiseUrl").value.trim();
      try {
        const res = await fetch("/api/admin/linkvertise-config", {
          method: "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify({ linkvertiseUrl })
        });
        const data = await res.json();
        if (data.ok) {
          showToast("Linkvertise route updated successfully");
        } else {
          alert("Error: " + (data.error || "Failed to update Linkvertise URL"));
        }
      } catch (e) {
        alert("Request error: " + e.message);
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

    let currentFeatureGroups = null;

    function openModuleModal(mod) {
      currentFeatureGroups = mod ? (mod.featureGroups || null) : null;
      const modal = document.getElementById("moduleModal");
      document.getElementById("modalModuleTitle").innerText = mod ? "Edit Module" : "Add New Module";
      document.getElementById("mSlug").value = mod ? mod.slug : "";
      document.getElementById("mSlug").readOnly = !!mod;
      document.getElementById("mPkg").value = mod ? mod.packageName : "";
      document.getElementById("mTitle").value = mod ? mod.title : "";
      document.getElementById("mCategory").value = mod ? mod.category : "Action";
      document.getElementById("mVersion").value = mod ? mod.version : "1.0.0";
      document.getElementById("mBuild").value = mod ? mod.build : 1;
      document.getElementById("mVersions").value = mod ? (mod.supportedVersions || [mod.version]).join(", ") : "1.0.0";
      document.getElementById("mVersionCodes").value = mod ? (mod.supportedVersionCodes || [mod.build]).join(", ") : "1";
      document.getElementById("mMethod").value = mod ? (mod.nonrootMethod || "injection") : "injection";
      document.getElementById("mEntryPoint").value = mod ? (mod.moduleConfig?.entryPoint || "com.android.support.Main") : "com.android.support.Main";
      const abis = mod ? (mod.supportedAbis || ["arm64-v8a"]) : ["arm64-v8a"];
      document.getElementById("mAbiArm64").checked = abis.includes("arm64-v8a");
      document.getElementById("mAbiArmeabi").checked = abis.includes("armeabi-v7a");
      document.getElementById("mFeatured").checked = mod ? (mod.featured !== false) : true;
      document.getElementById("mPopularity").value = mod ? (mod.popularity || 750000) : 750000;
      document.getElementById("mFeatures").value = mod ? (mod.features || []).join(String.fromCharCode(10)) : "";
      document.getElementById("mTags").value = mod ? (mod.tags || []).join(", ") : "action, injection, featured";
      document.getElementById("mNotes").value = mod ? mod.notes : "";

      const statusEl = document.getElementById("featuresJsonStatus");
      if (currentFeatureGroups && currentFeatureGroups.length > 0) {
        statusEl.innerHTML = '<span class="badge badge-purple">&#10003; ' + currentFeatureGroups.length + ' Feature Groups Configured</span>';
        statusEl.style.display = "block";
      } else {
        statusEl.style.display = "none";
      }

      modal.showModal();
    }

    function closeModuleModal() {
      currentFeatureGroups = null;
      document.getElementById("featuresJsonStatus").style.display = "none";
      document.getElementById("moduleModal").close();
    }

    function handleFeaturesJsonUpload(e) {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = function(evt) {
        try {
          const json = JSON.parse(evt.target.result);
          applyLoadedModuleJson(json, file.name);
        } catch (err) {
          alert("Invalid JSON file: " + err.message);
        }
      };
      reader.readAsText(file);
      e.target.value = "";
    }

    function applyLoadedModuleJson(json, filename) {
      let extractedFeatures = [];
      let groups = null;

      if (json.groups && Array.isArray(json.groups)) {
        groups = json.groups;
        json.groups.forEach(g => {
          if (Array.isArray(g.features)) {
            extractedFeatures.push(...g.features);
          }
        });
      } else if (Array.isArray(json.features)) {
        extractedFeatures = json.features;
      } else if (Array.isArray(json)) {
        extractedFeatures = json.map(f => typeof f === "string" ? f : (f.title || f.name || JSON.stringify(f)));
      }

      if (extractedFeatures.length > 0) {
        currentFeatureGroups = groups || [{ title: "Core Features", features: extractedFeatures }];
        document.getElementById("mFeatures").value = extractedFeatures.join(String.fromCharCode(10));
        const statusEl = document.getElementById("featuresJsonStatus");
        statusEl.innerHTML = '<span class="badge badge-green">&#10003; Imported ' + extractedFeatures.length + ' features from ' + (filename || "JSON") + (groups ? ' (' + groups.length + ' groups)' : '') + '</span>';
        statusEl.style.display = "block";
        showToast("Loaded " + extractedFeatures.length + " features from " + (filename || "JSON"));
      }

      if (json.package_name || json.packageName) {
        document.getElementById("mPkg").value = json.package_name || json.packageName;
      }
      if (json.title) {
        document.getElementById("mTitle").value = json.title;
      }
      if (json.entry_point || json.entryPoint) {
        document.getElementById("mEntryPoint").value = json.entry_point || json.entryPoint;
      }
      if (json.nonroot_method || json.nonrootMethod) {
        const meth = json.nonroot_method || json.nonrootMethod;
        const sel = document.getElementById("mMethod");
        if (sel) {
          for (let i = 0; i < sel.options.length; i++) {
            if (sel.options[i].value === meth) { sel.selectedIndex = i; break; }
          }
        }
      }
      if (json.supported_versions || json.supportedVersions) {
        const v = json.supported_versions || json.supportedVersions;
        if (Array.isArray(v)) document.getElementById("mVersions").value = v.join(", ");
      }
      if (json.supported_version_codes || json.supportedVersionCodes) {
        const vc = json.supported_version_codes || json.supportedVersionCodes;
        if (Array.isArray(vc)) document.getElementById("mVersionCodes").value = vc.join(", ");
      }
      if (json.supported_abis || json.supportedAbis) {
        const abis = json.supported_abis || json.supportedAbis;
        if (Array.isArray(abis)) {
          document.getElementById("mAbiArm64").checked = abis.includes("arm64-v8a");
          document.getElementById("mAbiArmeabi").checked = abis.includes("armeabi-v7a");
        }
      }
    }

    function loadExampleFeaturesJson() {
      const exampleJson = {
        schema: 1,
        groups: [
          {
            title: "Template controls",
            features: [
              "Toggles, buttons, check boxes, and action buttons",
              "Radio buttons, seek bars, single-select spinners, and radio-styled multi-select spinners",
              "Text, integer, float, and long integer inputs",
              "Collapsible and nested feature groups",
              "Patch, hook, and direct function call examples"
            ]
          },
          {
            title: "Non-root method templates",
            features: [
              "BlackBox injection configuration",
              "Direct patch configuration and launcher-only menu guard"
            ]
          }
        ]
      };
      applyLoadedModuleJson(exampleJson, "features.json template");
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
      const entryPoint = document.getElementById("mEntryPoint").value.trim() || "com.android.support.Main";
      const notes = document.getElementById("mNotes").value.trim();

      const supportedVersions = document.getElementById("mVersions").value.split(",").map(s => s.trim()).filter(Boolean);
      const supportedVersionCodes = document.getElementById("mVersionCodes").value.split(",").map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n) && n > 0);

      const supportedAbis = [];
      if (document.getElementById("mAbiArm64").checked) supportedAbis.push("arm64-v8a");
      if (document.getElementById("mAbiArmeabi").checked) supportedAbis.push("armeabi-v7a");
      if (supportedAbis.length === 0) supportedAbis.push("arm64-v8a");

      const features = document.getElementById("mFeatures").value.split(String.fromCharCode(10)).map(s => s.trim()).filter(Boolean);
      const tags = document.getElementById("mTags").value.split(",").map(s => s.trim()).filter(Boolean);
      const featured = document.getElementById("mFeatured").checked;
      const popularity = parseInt(document.getElementById("mPopularity").value, 10) || 750000;

      try {
        const res = await fetch("/api/admin/modules", {
          method: "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify({
            slug, packageName, title, category, version, build, nonrootMethod, notes,
            entryPoint,
            supportedVersions: supportedVersions.length > 0 ? supportedVersions : [version],
            supportedVersionCodes: supportedVersionCodes.length > 0 ? supportedVersionCodes : [build],
            supportedAbis,
            features: features.length > 0 ? features : [title + " enhancements"],
            featureGroups: currentFeatureGroups && currentFeatureGroups.length > 0 ? currentFeatureGroups : [
              { title: "Core Features", features: features.length > 0 ? features : [title + " enhancements"] }
            ],
            tags: tags.length > 0 ? tags : ["action", "injection"],
            featured,
            popularity
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
      if (!confirm("Restore official production catalog and VIP passes into KV database?")) return;
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
