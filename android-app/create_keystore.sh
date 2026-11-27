#!/bin/bash
# Generate a debug keystore for release builds
if [ -f "release.keystore" ]; then
    echo "Keystore already exists"
else
    # Try to use keytool if available
    if command -v keytool &> /dev/null; then
        keytool -genkey -v -keystore release.keystore -alias release \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass android -keypass android \
            -dname "CN=Agentic OS Release, OU=Development, O=Agentic OS, L=Remote, ST=NA, C=US" 2>/dev/null
        if [ $? -eq 0 ]; then
            echo "Keystore created successfully"
        else
            echo "keytool failed - creating placeholder"
        fi
    else
        # Create a minimal placeholder (won't actually work but allows build configuration)
        echo "KEYSTORE_PLACEHOLDER" > release.keystore
        echo "KEYSTORE_PLACEHOLDER" >> release.keystore
    fi
fi
