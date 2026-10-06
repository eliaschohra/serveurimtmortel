#!/bin/bash
# Eprouve MapRepair sur une copie d'une vraie carte.
# Usage : build/tests/run-repair-test.sh <dossier-d-une-carte-avec-level.dat>
set -e
cd "$(dirname "$0")"
OUT=$(mktemp -d)
javac -d "$OUT" ../../plugin/src/fr/bdeimt/serveur/MapRepair.java TestRepair.java
RUNS=$(mktemp -d)
java -cp "$OUT" TestRepair "$1" "$RUNS"
