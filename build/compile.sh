#!/bin/bash
cd /home/user/serveurimtmortel
rm -rf build/out && mkdir -p build/out
CP=$(ls build/deps/jars/*.jar | tr '\n' ':')
javac --release 21 -nowarn -proc:none -implicit:none -encoding UTF-8 -Xmaxerrs 400 \
  -sourcepath "$(cat build/sourcepath.txt)" -cp "$CP" \
  -d build/out plugin/src/fr/bdeimt/serveur/*.java 2>&1 | grep -v JAVA_TOOL_OPTIONS
