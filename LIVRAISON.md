# BDEIMT v3.3 — mise à jour

1. Dans `plugins/`, remplace `BDEIMT.jar`, puis `/stop`.
2. Rien d'autre : le nouveau catalogue des marchands remplace l'ancien tout seul (l'ancien est gardé dans `plugins/BDEIMT/marchands-ancien.yml`).

Nouveautés : panneau du lobby skyblock déplacé (plus petit, plus haut, lisible), arrivée tournée vers lui ; 50 pièces au départ ; tout s'achète et se revend chez les marchands, nourriture comprise, sans combine pour s'enrichir à l'infini ; tuer Zaza débloque le Griddidi (une fois).

---

# BDEIMT v3.2 — mise à jour

1. Dans `plugins/`, remplace `BDEIMT.jar` (un seul fichier BDEIMT), puis `/stop`.
2. Sons : envoie le nouveau `sons-bde.zip` sur mc-packs.net, puis colle le lien dans `sons.pack-url` et le SHA-1 dans `sons.pack-sha1` (`plugins/BDEIMT/config.yml`). Ensuite `/stop`.
3. Les îles déjà créées gardent leur ancienne forme. Pour avoir la nouvelle : `/ile supprimer confirmer`, puis `/ile creer`.

Nouveautés : nouvelle île Standard Skyblock, mort classique dans le vide (réapparition sur l'île), bordure autour de chaque île, panneau du lobby skyblock qui ne disparaît plus (avec des astuces qui changent), astuces par mode dans le chat, `/parcelle` qui marche de partout, créatif forcé et protégé dans les parcelles, bâton de retour au parkour, Traq annoncé seulement en survie, lots rares moins fréquents, son « ascension ».

---

# BDEIMT v3.0 — installation

Tout se fait par **FileZilla** et **en jeu**.

---

## 1. Déposer le plugin et la carte du skyblock

1. Dans `/home/opc/minecraft/plugins/`, remplace `BDEIMT.jar`. **Un seul** fichier BDEIMT dans ce dossier.
2. Décompresse `SkySpawn_1.8.zip`. Tu obtiens un dossier `SkySpawn` : **renomme-le `bdeimt_skyspawn`** et dépose-le dans `/home/opc/minecraft/`, à côté de `world`.
3. Le skyblock est fermé depuis la dernière fois. En jeu : `/imt mode skyblock on`
4. `/stop`. Le premier démarrage est long : la carte SkySpawn date de Minecraft 1.12, Paper doit la convertir. Comme pour le lobby, le dossier `bdeimt_skyspawn` **disparaît ensuite de la racine** : c'est normal, la carte vit désormais dans `world/dimensions/`.

---

## 2. Les réglages en jeu, une seule fois

**Lobby**
- `/imt photo` — vise un mur ou un bloc : la photo du BDE s'y accroche. Sinon, elle flotte dans le ciel face au point d'arrivée.
- Les portails déjà posés reçoivent tout seuls leur titre et leur colonne de particules.

**Survie**
- `/imt pnj zaza-survie` — à l'endroit où Zaza doit vivre en survie.

**Skyblock** — dans le lobby du skyblock :
- `/imt marchand liste` — les 11 marchands.
- Puis, en te plaçant à chaque endroit voulu : `/imt marchand fermier`, `/imt marchand eleveur`, `/imt marchand mineur`, `/imt marchand bucheron`, `/imt marchand terrassier`, `/imt marchand pecheur`, `/imt marchand chasseur`, `/imt marchand alchimiste`, `/imt marchand enchanteur`, `/imt marchand explorateur`, `/imt marchand decorateur`.
- `/imt hologramme` — si le grand panneau des règles n'est pas bien placé, mets-toi où il doit flotter.
- Les prix se changent dans `plugins/BDEIMT/marchands.yml` (le fichier explique tout), puis `/imt reload`.

**Ton skin** : dépose `mobutu.png` dans `plugins/BDEIMT/skins/` et redémarre. Il est confié une fois pour toutes à SkinsRestorer.

---

## 3. Ce qui change

**Corrigé**
- Plus d'écran « Loading… » quelques secondes après chaque arrivée : c'était le changement de skin, refait à chaque monde. SkinsRestorer s'en charge maintenant, à la connexion, sans rechargement.
- Zaza et Mobutu ne disparaissent plus : leur coin de carte reste chargé, et un garde-fou les repose s'ils manquent. Plus de phrase sous le nom de Zaza.
- La boussole téléportait : c'était la baguette de **WorldEdit** (la boussole est son outil de navigation). C'est maintenant une boussole de récupération, qui n'ouvre que le menu.
- « Impossible de casser dans Lobby</gray> » : message corrigé, et il parle du monde du bloc visé. En créatif, l'admin peut retoucher toutes les cartes.
- La barre de tombe ne s'affiche plus qu'en survie.

**Lobby**
- Connexion dans le vrai lobby : la photo du BDE et un énorme `/register` · `/login` dans le ciel, visible seulement de ceux qui ne sont pas encore connectés.
- Panneau de présentation à l'entrée, titre et nombre de joueurs au-dessus de chaque portail, colonne de particules arc-en-ciel au centre.
- Musique douce (disques de Minecraft, l'un après l'autre). `/musique` pour la couper.

**Survie**
- `/vote ‹liste›` remplace `/kit vote ‹liste›` (l'ancienne marche encore).
- Kit Ascension : + les **Bottes de Silas**, fer, Agilité givrée I, à moitié usées.
- `/imt aura ‹joueur› set|add|reset ‹n›`.
- `/guide` ouvre un livre de 11 pages (la première annonce qu'il y en a plusieurs).

**Skyblock**
- L'île de départ est l'île classique, relevée bloc par bloc dans BSkyBlock : herbe, terre, **pierre et sable dessous**, bedrock, chêne, vache, et **le coffre d'origine** — lave, 2 glaces, canne à sucre, champignons, pastèque, citrouille, cactus, os, pousse, pierre, graines de betterave, ragoût.
- Lobby = ta carte SkySpawn, avec un grand panneau des règles.
- `/vote ‹pseudo›` : une voix par jour pour l'île de quelqu'un.
- Argent : `/solde`, `/payer`, 100 pièces à la première île. Affiché sur le côté avec les îles les plus aimées.
- `/marche` : les joueurs vendent ce qu'ils veulent (`/marche vendre ‹prix›` objet en main). Rappel dans le chat toutes les 15 minutes.
- 11 marchands, 204 offres, des graines aux élytres.
- Pas de tombe dans le skyblock.

---

## 4. Les sons de `/vote ‹liste›`

C'est possible, mais Minecraft ne lit pas les mp3 envoyés par un serveur : ils doivent voyager dans un **pack de ressources**. Le plugin est prêt.

1. Prépare tes extraits (20 secondes max) et nomme-les d'après les listes : `imtmortel.mp3`, `zimtzimt.mp3`, `ascension.mp3`, `bartbart.mp3`, `wizart.mp3`, `passion.mp3`.
2. **Envoie-les-moi ici** : je les convertis et je te renvoie le pack `.zip` avec son empreinte.
3. Dépose le `.zip` sur **mc-packs.net** (gratuit, fait pour ça) : il te donne une adresse.
4. Colle l'adresse et l'empreinte dans `config.yml`, section `sons:`, puis `/stop`.

Les joueurs se verront proposer le pack à la connexion. Les sons ne jouent qu'en survie.

---

## 5. Ce que je n'ai pas pu vérifier

Pas de serveur Minecraft ici : tout compile contre Paper 26.2, la réparation des cartes et le pack de sons ont été testés à part, mais **rien n'a tourné en jeu**. À surveiller : la conversion de SkySpawn (1.12), et l'apparition des marchands. En cas de souci, envoie-moi `logs/latest.log`.

À savoir : les fermes et la mine qui repoussaient toutes seules étaient construites sur l'ancienne plateforme générée. Avec ta carte SkySpawn, je les ai retirées pour ne pas écrire de blocs au hasard dedans. Si tu veux les retrouver, dis-moi où dans ta carte.
