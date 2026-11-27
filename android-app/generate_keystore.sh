#!/bin/bash
# Script to generate release keystore for Agentic OS

# Navigate to the android-app directory
cd "$(dirname "$0")"

# Generate a release keystore for signing
keytool -genkey -v \
    -keystore release.keystore \
    -alias release \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=Agentic OS Release, OU=Development, O=Agentic OS, L=Remote, ST=NA, C=US"

echo "Keystore generated at $(pwd)/release.keystore"
echo "Key Alias: release"
echo "Store Password: android"
echo "Key Password: android"
echo ""
echo "IMPORTANT: Update gradle.properties with:"
echo "RELEASE_STORE_PASSWORD=android"
echo "RELEASE_KEY_ALIAS=release"
echo "RELEASE_KEY_PASSWORD=android"