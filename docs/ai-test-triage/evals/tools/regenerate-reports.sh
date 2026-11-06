#!/bin/bash
# Regenera os relatorios congelados (project/reports/TEST-*.xml) de UM caso do dataset.
# Nao e necessario para correr o triage: os relatorios ja estao no repositorio e sao o dataset.
# Serve para auditar de onde vieram. Os valores de "time" e os numeros de linha dos frames internos
# do JUnit mudam entre execucoes, por isso o resultado NAO e identico byte a byte.
#
# Uso:   OPENTEST4J_JAR=/caminho/opentest4j-1.3.0.jar tools/regenerate-reports.sh case-01-state-guard-off-by-one
# Requer JDK 21. O jar do opentest4j esta em ~/.m2 depois de um `mvn test` (org/opentest4j/opentest4j).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
case_dir="$here/../dataset-v1/${1:?indique o diretorio do caso}"
: "${OPENTEST4J_JAR:?defina OPENTEST4J_JAR}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# 1. mini JUnit (Test, Assertions, Executable) e o executor
mkdir -p "$work/shim"
javac -cp "$OPENTEST4J_JAR" -d "$work/shim" $(find "$here/src" -name '*.java')

# 2. codigo do caso
mkdir -p "$work/case"
javac -cp "$work/shim:$OPENTEST4J_JAR" -d "$work/case" $(find "$case_dir/project/src" -name '*.java')

# 3. classes de teste = ficheiros *Test.java
classes=$(cd "$case_dir/project/src/test/java" && find . -name '*Test.java' | sed 's|^\./||; s|\.java$||; s|/|.|g' | sort)
rm -rf "$case_dir/project/reports"
(cd "$case_dir/project" && java -cp "$work/case:$work/shim:$OPENTEST4J_JAR" EvalCaseRunner reports $classes)
echo "Relatorios escritos em $case_dir/project/reports"
