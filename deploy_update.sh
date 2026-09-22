#!/bin/bash
set -e

echo "Cleaning old APK outputs..."
rm -rf app/build/outputs/apk/debug/*

echo "Building fresh APK..."
./gradlew assembleDebug

# Check if GitHub CLI is installed
if command -v gh &> /dev/null; then
    echo "Uploading to GitHub Releases..."
    gh release create "$1" app/build/outputs/apk/debug/app-debug.apk --title "Release $1" --notes "$2" --latest
else
    echo "GitHub CLI not found. Please install it or manually upload app/build/outputs/apk/debug/app-debug.apk"
    exit 1
fi
