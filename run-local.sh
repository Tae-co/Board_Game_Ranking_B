#!/bin/sh

set -eu

if [ -f ".env.local" ]; then
    # Export local development environment variables.
    set -a
    . ./.env.local
    set +a
fi

if [ "$(uname)" = "Darwin" ] && [ -x "/usr/libexec/java_home" ]; then
    JAVA_21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    if [ -n "${JAVA_21_HOME}" ]; then
        export JAVA_HOME="${JAVA_21_HOME}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
fi

exec ./gradlew --no-daemon bootRun "$@"
