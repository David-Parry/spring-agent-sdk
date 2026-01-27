#!/bin/bash

# Script to clear com.davidparry.agent:core artifacts from local Maven and Gradle caches
# Usage: ./clear-local-mvn.sh <version>
# Example: ./clear-local-mvn.sh 2.1.6

set -e

# Configuration
GROUP_PATH="com/davidparry/agent"
ARTIFACT_ID="core"

# Check if version argument is provided
if [ -z "$1" ]; then
    echo "Error: Version argument is required"
    echo "Usage: $0 <version>"
    echo "Example: $0 2.1.6"
    exit 1
fi

VERSION="$1"

echo "Clearing local caches for ${GROUP_PATH}:${ARTIFACT_ID}:${VERSION}"
echo "============================================================"

# Clear from Maven local repository
MAVEN_PATH="$HOME/.m2/repository/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}"
if [ -d "$MAVEN_PATH" ]; then
    echo "Removing Maven cache: $MAVEN_PATH"
    rm -rf "$MAVEN_PATH"
    echo "✓ Maven cache cleared"
else
    echo "Maven cache not found at: $MAVEN_PATH (skipping)"
fi

# Clear from Gradle cache (caches/modules-2/files-2.1)
GRADLE_CACHE_PATH="$HOME/.gradle/caches/modules-2/files-2.1/${GROUP_PATH/\//.}/${ARTIFACT_ID}/${VERSION}"
if [ -d "$GRADLE_CACHE_PATH" ]; then
    echo "Removing Gradle modules cache: $GRADLE_CACHE_PATH"
    rm -rf "$GRADLE_CACHE_PATH"
    echo "✓ Gradle modules cache cleared"
else
    echo "Gradle modules cache not found at: $GRADLE_CACHE_PATH (skipping)"
fi

# Clear from Gradle metadata cache
GRADLE_METADATA_PATH="$HOME/.gradle/caches/modules-2/metadata-2.*/descriptors/${GROUP_PATH/\//.}/${ARTIFACT_ID}/${VERSION}"
for dir in $GRADLE_METADATA_PATH; do
    if [ -d "$dir" ]; then
        echo "Removing Gradle metadata cache: $dir"
        rm -rf "$dir"
        echo "✓ Gradle metadata cache cleared"
    fi
done

echo ""
echo "Done! Cleared local caches for version ${VERSION}"
