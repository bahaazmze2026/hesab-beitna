#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-check
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d build/core-check app/src/main/java/com/hesabbeitna/app/Finance.java app/src/main/java/com/hesabbeitna/app/BackupCrypto.java tools/FinanceCheck.java tools/CryptoCheck.java tools/PropertyCheck.java
java -cp build/core-check com.hesabbeitna.app.FinanceCheck
java -cp build/core-check com.hesabbeitna.app.CryptoCheck
java -cp build/core-check com.hesabbeitna.app.PropertyCheck
