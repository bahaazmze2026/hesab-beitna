#!/usr/bin/env sh
# Portable Gradle launcher. The official wrapper JAR could not be downloaded in this workspace.
# Downloads the official pinned distribution and verifies its published SHA-256 before execution.
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_version=8.9
task_cache="$task_root/.gradle-bootstrap"
task_gradle="$task_cache/gradle-$task_version/bin/gradle"
if [ ! -x "$task_gradle" ]; then
    if command -v gradle >/dev/null 2>&1; then
        task_installed=$(gradle --version | awk '$1 == "Gradle" { print $2; exit }')
        [ "$task_installed" = "$task_version" ] || { echo 'Use Gradle 8.9 for this project.' >&2; exit 1; }
        exec gradle "$@"
    fi
    command -v curl >/dev/null 2>&1 || { echo 'Install curl, unzip and JDK 17, or Gradle 8.9.' >&2; exit 1; }
    command -v unzip >/dev/null 2>&1 || { echo 'Install unzip.' >&2; exit 1; }
    mkdir -p "$task_cache"
    task_url="https://downloads.gradle.org/distributions/gradle-$task_version-bin.zip"
    curl --fail --location --retry 2 --connect-timeout 15 --max-time 300 "$task_url" -o "$task_cache/gradle.zip"
    curl --fail --location --retry 2 --connect-timeout 15 --max-time 30 "$task_url.sha256" -o "$task_cache/gradle.sha256"
    task_expected=$(tr -d '\r\n ' < "$task_cache/gradle.sha256")
    [ "${#task_expected}" = 64 ] || { echo 'Invalid Gradle checksum.' >&2; exit 1; }
    task_actual=$(sha256sum "$task_cache/gradle.zip" | cut -d ' ' -f 1)
    [ "$task_actual" = "$task_expected" ] || { echo 'Gradle checksum mismatch.' >&2; exit 1; }
    unzip -q -o "$task_cache/gradle.zip" -d "$task_cache"
    rm "$task_cache/gradle.zip"
fi
exec "$task_gradle" "$@"
