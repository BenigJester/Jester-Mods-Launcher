#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/../keys"
mkdir -p "${OUTPUT_DIR}"

echo "==> Generating RSA-2048 keypair for Jester Mods Cloudflare Worker..."
openssl genrsa -out "${OUTPUT_DIR}/private_key.pem" 2048

echo "==> Converting private key to PKCS#8 format for Cloudflare Workers Web Crypto..."
openssl pkcs8 -topk8 -inform PEM -outform PEM -in "${OUTPUT_DIR}/private_key.pem" -out "${OUTPUT_DIR}/private_key.pkcs8.pem" -nocrypt

echo "==> Exporting public key as DER Base64 (for app/build.gradle)..."
openssl rsa -in "${OUTPUT_DIR}/private_key.pem" -pubout -outform DER | base64 | tr -d '\n' > "${OUTPUT_DIR}/public_key_der_base64.txt"

echo ""
echo "========================================================================"
echo "Keys successfully generated in: ${OUTPUT_DIR}"
echo ""
echo "1. Set worker secret:"
echo "   npx wrangler secret put RSA_PRIVATE_KEY < ${OUTPUT_DIR}/private_key.pkcs8.pem"
echo ""
echo "2. Copy this string into app/build.gradle for:"
echo "   UPDATE_PUBLIC_KEY_DER_BASE64 and ACCESS_LEASE_PUBLIC_KEY_DER_BASE64:"
echo ""
cat "${OUTPUT_DIR}/public_key_der_base64.txt"
echo ""
echo "========================================================================"
