#!/usr/bin/env python3
import json
import base64
import urllib.request
import re

BASE_URL = "https://jester-mods-worker.uncledrew697.workers.dev"
HEADERS = {
    "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UD1A.230803.041)",
    "Content-Type": "application/json"
}

def request_json(url, data=None, method="GET", extra_headers=None):
    hdrs = dict(HEADERS)
    if extra_headers:
        hdrs.update(extra_headers)
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8") if data is not None else None,
        headers=hdrs,
        method=method
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def verify_live():
    print("=== 1. Testing /api/admin/seed (Restore Official Production Catalog) ===")
    seed_res = request_json(f"{BASE_URL}/api/admin/seed", {}, method="POST")
    print("Seed response:", seed_res)
    assert seed_res.get("ok") is True

    print("\n=== 2. Testing /api/launcher-modules ===")
    cat_res = request_json(f"{BASE_URL}/api/launcher-modules")
    assert "payload" in cat_res, "Signed envelope missing payload"
    payload_json = json.loads(base64.b64decode(cat_res["payload"]).decode("utf-8"))
    assert payload_json["audience"] == "moodtools-standalone"
    modules = payload_json["modules"]
    print(f"Received {len(modules)} production modules:")
    
    package_pattern = re.compile(r"^[a-zA-Z0-9._]+$")
    slug_pattern = re.compile(r"^[a-z0-9-]+$")
    valid_methods = {"injection", "direct_patch", "identity_shell"}
    valid_abis = {"arm64-v8a", "armeabi-v7a"}

    for idx, mod in enumerate(modules, 1):
        pkg = mod["packageName"]
        slug = mod["slug"]
        title = mod["title"]
        version = mod["version"]
        build = mod["build"]
        method = mod["nonrootMethod"]
        methods = mod["nonrootMethods"]
        abis = mod["supportedAbis"]
        versions = mod["supportedVersions"]
        codes = mod.get("supportedVersionCodes", [])

        print(f"  [{idx}] {title} ({pkg})")
        print(f"      Slug: {slug} | v{version} (Build #{build})")
        print(f"      NonRoot: {method} (choices: {methods})")
        print(f"      ABIs: {abis} | Versions: {versions} | Codes: {codes}")
        
        # Verify strict Android constraints
        assert "privateScope" not in mod, "Private modules must not appear in public catalog"
        assert package_pattern.match(pkg), f"Invalid package name: {pkg}"
        assert slug_pattern.match(slug), f"Invalid slug: {slug}"
        assert "example" not in pkg.lower(), f"Package contains test example string: {pkg}"
        assert "example" not in slug.lower(), f"Slug contains test example string: {slug}"
        assert method in valid_methods, f"Invalid nonroot method: {method}"
        assert len(methods) > 0 and methods[0] == method, f"First nonrootMethod must match recommendation: {methods}"
        assert all(m in valid_methods for m in methods), f"Invalid choices in methods: {methods}"
        assert len(versions) > 0, "Empty supported versions"
        if codes:
            assert len(codes) == len(versions), f"Version codes length ({len(codes)}) != versions length ({len(versions)})"
            assert all(c > 0 for c in codes), f"Version codes must be positive: {codes}"
        assert all(a in valid_abis for a in abis), f"Invalid ABIs: {abis}"
        
        # Verify features object format expected by Android ModuleCatalogClient
        if "features" in mod:
            feats = mod["features"]
            assert feats["path"] == f"/api/launcher-module-features/{slug}/{build}"
            assert feats["count"] > 0

    print("\n=== 3. Testing /api/launcher-module for OPBR ===")
    mod_auth = request_json(
        f"{BASE_URL}/api/launcher-module",
        {"slug": "opbr-bounty-rush-mod", "keyId": "test_key_pixel8_prod"},
        method="POST"
    )
    assert mod_auth["ok"] is True
    assert "manifest" in mod_auth
    manifest_payload = json.loads(base64.b64decode(mod_auth["manifest"]["payload"]).decode("utf-8"))
    print("Manifest package:", manifest_payload["packageName"])
    print("Manifest build:", manifest_payload["build"])
    files = manifest_payload["files"]
    dex = files["dex"]
    print("DEX path:", dex["path"])
    print("DEX size:", dex["size"], "SHA256:", dex["sha256"][:16] + "...")
    assert dex["path"] == "/api/launcher-module-payload/opbr-bounty-rush-mod/93010/dex/classes.dex"
    assert dex["size"] == 201616, f"Unexpected DEX size: {dex['size']}"
    assert dex["sha256"] != "0" * 64, "DEX sha256 is placeholder!"

    for abi, native in files["native"].items():
        print(f"Native [{abi}] path:", native["path"])
        print(f"Native [{abi}] size:", native["size"], "SHA256:", native["sha256"][:16] + "...")
        assert native["path"] == f"/api/launcher-module-payload/opbr-bounty-rush-mod/93010/native/{abi}"
        assert native["size"] == 1818376, f"Unexpected SO size: {native['size']}"
        assert native["sha256"] != "0" * 64, "Native sha256 is placeholder!"

    print("\n=== 4. Testing payload downloads ===")
    # Test DEX download
    req_dex = urllib.request.Request(f"{BASE_URL}{dex['path']}", headers=HEADERS)
    with urllib.request.urlopen(req_dex) as resp:
        dex_bytes = resp.read()
        print(f"Downloaded classes.dex: {len(dex_bytes)} bytes, HTTP {resp.status}")
        assert len(dex_bytes) == 201616

    # Test Native SO download
    so_path = files["native"]["arm64-v8a"]["path"]
    req_so = urllib.request.Request(f"{BASE_URL}{so_path}", headers=HEADERS)
    with urllib.request.urlopen(req_so) as resp:
        so_bytes = resp.read()
        print(f"Downloaded libmenu_native.so: {len(so_bytes)} bytes, HTTP {resp.status}")
        assert len(so_bytes) == 1818376

    print("\n=== 5. Testing /api/launcher-module-changelog & features ===")
    cl = request_json(f"{BASE_URL}/api/launcher-module-changelog/opbr-bounty-rush-mod/93010")
    cl_payload = json.loads(base64.b64decode(cl["payload"]).decode("utf-8"))
    print("Changelog entries count:", len(cl_payload["entries"]))
    assert cl_payload["slug"] == "opbr-bounty-rush-mod"

    feat = request_json(f"{BASE_URL}/api/launcher-module-features/opbr-bounty-rush-mod/93010")
    feat_payload = json.loads(base64.b64decode(feat["payload"]).decode("utf-8"))
    print("Feature groups count:", len(feat_payload["groups"]))
    assert feat_payload["slug"] == "opbr-bounty-rush-mod"

    print("\n=== 6. Testing /api/launcher-play-store-versions ===")
    ps = request_json(f"{BASE_URL}/api/launcher-play-store-versions?packages=com.bandainamcoent.opbrww,com.mobile.legends")
    print("Play store versions response:", ps)
    assert ps["ok"] is True and ps["schema"] == 1
    res_map = {r["packageName"]: r for r in ps["results"]}
    assert res_map["com.bandainamcoent.opbrww"]["version"] == "9.3.0"
    assert res_map["com.bandainamcoent.opbrww"]["versionCode"] == 93010

    print("\n=== 7. Testing Admin Module Editing (Mission Control Save) ===")
    # Update OPBR popularity or notes via POST /api/admin/modules
    update_res = request_json(
        f"{BASE_URL}/api/admin/modules",
        {
            "slug": "opbr-bounty-rush-mod",
            "packageName": "com.bandainamcoent.opbrww",
            "title": "ONE PIECE Bounty Rush Mod",
            "version": "9.3.0",
            "build": 93010,
            "supportedVersions": ["9.3.0"],
            "supportedVersionCodes": [93010],
            "supportedAbis": ["arm64-v8a"],
            "nonrootMethod": "injection",
            "entryPoint": "com.android.support.Main",
            "features": [
                "Damage Multiplier (1x-10x)",
                "Defense Boost Toggle",
                "Skill Cooldown Reduction",
                "Radar Map Expansion (full map reveal)",
                "Camera FOV Unlock",
                "Speed Modifier",
                "Custom Battle HUD",
                "No Knock-Back Mode"
            ],
            "tags": ["action", "anime", "pvp", "bandai", "injection", "featured"],
            "notes": "Official verified release for ONE PIECE Bounty Rush 9.3.0.",
            "popularity": 995000,
            "featured": True
        },
        method="POST"
    )
    assert update_res.get("ok") is True
    saved_mod = update_res["module"]
    print("Successfully updated module via admin API!")
    print("Saved module files.dex.path:", saved_mod["files"]["dex"]["path"])
    print("Saved module files.dex.size:", saved_mod["files"]["dex"]["size"])
    print("Saved module files.dex.sha256:", saved_mod["files"]["dex"]["sha256"][:16] + "...")
    assert saved_mod["files"]["dex"]["size"] == 201616
    assert saved_mod["files"]["dex"]["sha256"] != "0" * 64
    assert saved_mod["popularity"] == 995000

    print("\n🎉 ALL LIVE PRODUCTION BACKEND AND MODULE INJECTION TESTS PASSED PERFECTLY! 🎉")

if __name__ == "__main__":
    verify_live()
