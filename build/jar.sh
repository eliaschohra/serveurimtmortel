#!/bin/bash
# Construit plugins/BDEIMT.jar
set -e
cd /home/user/serveurimtmortel
./build/compile.sh
if [ ! -f build/out/fr/bdeimt/serveur/BDEIMT.class ]; then echo "ECHEC COMPILATION"; exit 1; fi
cp plugin/resources/*.yml build/out/
rm -f livraison/BDEIMT.jar
mkdir -p livraison
(cd build/out && jar cf ../../livraison/BDEIMT.jar .)
echo "OK -> livraison/BDEIMT.jar"
ls -la livraison/BDEIMT.jar
