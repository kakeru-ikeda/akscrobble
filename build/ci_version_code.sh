#!/usr/bin/env bash
set -euo pipefail

fail() {
    echo "$1" >&2
    exit 2
}

if [ "$#" -ne 1 ]; then
    fail "usage: ci_version_code.sh <MAJOR.MINOR.PATCH>"
fi
version=$1
if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    fail "invalid version: expected MAJOR.MINOR.PATCH"
fi

decimal() {
    local value=$1
    value="${value#"${value%%[!0]*}"}"
    printf '%s' "${value:-0}"
}

major_text=$(decimal "${BASH_REMATCH[1]}")
minor_text=$(decimal "${BASH_REMATCH[2]}")
patch_text=$(decimal "${BASH_REMATCH[3]}")
if (( ${#major_text} > 4 )) || { (( ${#major_text} == 4 )) && [[ "$major_text" > 2099 ]]; } || [[ "$major_text" == 0 ]]; then
    fail "major version must be between 1 and 2099"
fi
if (( ${#minor_text} > 3 )); then
    fail "minor version must be between 0 and 999"
fi
if (( ${#patch_text} > 3 )); then
    fail "patch version must be between 0 and 999"
fi

major=$((10#$major_text))
minor=$((10#$minor_text))
patch=$((10#$patch_text))

printf '%d\n' "$((major * 1000000 + minor * 1000 + patch))"
