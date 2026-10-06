#!/usr/bin/env python3
"""
Fabrique le pack de sons du BDE a partir de fichiers .mp3.

    python3 build/sons/fabriquer-pack.py <dossier des mp3> <ffmpeg> <sortie.zip>

Chaque fichier doit porter le nom d'une liste : imtmortel.mp3, zimtzimt.mp3,
ascension.mp3, bartbart.mp3, wizart.mp3, passion.mp3 (les variantes
« liste-imtmortel.mp3 », « Liste_IMTmortel.mp3 »... sont reconnues).

Minecraft ne lit que l'Ogg Vorbis : chaque extrait est converti, ramene en
mono, coupe a 20 secondes et un peu adouci au debut et a la fin. Le plugin
joue ensuite le son « bdeimt:liste.<nom> » quand quelqu'un prend le kit de
la liste en survie.

A la fin, le script affiche l'empreinte SHA-1 du pack : c'est la valeur a
mettre dans config.yml (sons.pack-sha1), avec l'adresse ou le pack a ete
depose (sons.pack-url).
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile

LISTES = ["imtmortel", "zimtzimt", "ascension", "bartbart", "wizart", "passion"]
DUREE_MAX = 20


def liste_de(nom_fichier):
    base = os.path.splitext(os.path.basename(nom_fichier))[0].lower()
    base = re.sub(r"[^a-z0-9]", "", base)
    for liste in LISTES:
        if base == liste or base == "liste" + liste or base.endswith(liste):
            return liste
    return None


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    dossier, ffmpeg, sortie = sys.argv[1:]
    trouves = {}
    for nom in sorted(os.listdir(dossier)):
        if nom.lower().endswith((".mp3", ".wav", ".ogg", ".m4a", ".flac")):
            liste = liste_de(nom)
            if liste is None:
                print("  ignore (aucune liste reconnue) :", nom)
            elif liste in trouves:
                print("  ignore (deja une piste pour", liste, ") :", nom)
            else:
                trouves[liste] = os.path.join(dossier, nom)
    if not trouves:
        print("Aucun fichier exploitable.")
        sys.exit(1)

    sons = {}
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(sortie, "w", zipfile.ZIP_DEFLATED) as pack:
        for liste, chemin in trouves.items():
            ogg = os.path.join(tmp, liste + ".ogg")
            subprocess.run(
                [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", chemin,
                 "-t", str(DUREE_MAX), "-ac", "1", "-ar", "44100",
                 "-af", "afade=t=in:d=0.15,areverse,afade=t=in:d=0.6,areverse",
                 "-c:a", "libvorbis", "-q:a", "5", ogg],
                check=True,
            )
            pack.write(ogg, "assets/bdeimt/sounds/liste/%s.ogg" % liste)
            sons["liste." + liste] = {"sounds": [{"name": "bdeimt:liste/" + liste, "stream": False}]}
            print("  ok  %-10s <- %s (%d ko)" % (liste, os.path.basename(chemin), os.path.getsize(ogg) // 1024))

        pack.writestr("assets/bdeimt/sounds.json", json.dumps(sons, indent=2))
        # Format declare large : le jeu accepte tout pack dont la plage couvre sa version.
        pack.writestr("pack.mcmeta", json.dumps({"pack": {
            "description": "Les sons du BDE IMT Atlantique",
            "pack_format": 46,
            "supported_formats": [46, 9999],
            "min_format": 46,
            "max_format": 9999,
        }}, indent=2))

    sha1 = hashlib.sha1(open(sortie, "rb").read()).hexdigest()
    print()
    print("Pack :", sortie, "(%d ko)" % (os.path.getsize(sortie) // 1024))
    print("SHA-1 :", sha1)
    manquantes = [l for l in LISTES if l not in trouves]
    if manquantes:
        print("Pas de son pour :", ", ".join(manquantes))


if __name__ == "__main__":
    main()
