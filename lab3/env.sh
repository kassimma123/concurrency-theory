#!/usr/bin/env bash
# env.sh - sets up JDK 25 environment for Lab 3

if [ -d "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    : # Keep current JAVA_HOME
elif /usr/libexec/java_home -v 25 &>/dev/null; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
fi

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
