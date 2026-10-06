# Serveur Minecraft du BDE IMT Atlantique — dossier de reprise

Ce document sert de point d'entrée à Claude Code (ou à n'importe quel développeur)
pour reprendre le projet sans avoir l'historique de la conversation.

Dernière mise à jour : 6 octobre 2026 — plugin BDEIMT **v2.0**.

---

## 1. Contexte et contraintes

| Élément | Valeur |
|---|---|
| Propriétaire | Elias, président du BDE, IMT Atlantique Nantes |
| Pseudo admin en jeu | `curlybrownhair` — affiché `[Mobutu]` |
| Hébergement | Oracle Cloud Free Tier, instance `imtmortel` |
| Machine | VM.Standard.A1.Flex, 2 OCPU ARM, 12 Go RAM, Oracle Linux |
| Utilisateur système | `opc` (pas `ubuntu` : l'image Oracle Linux n'a pas cet utilisateur) |
| IP publique | `129.151.234.47` |
| Nom de domaine | `imtmortel.com` → enregistrement DNS **A** vers l'IP, port 25565 par défaut |
| Serveur | Paper **26.2**, `online-mode=false` (serveur cracké), 40 joueurs max |
| Dossier du serveur | `/home/opc/minecraft` |
| Accès du propriétaire | **FileZilla en SFTP uniquement** — il ne veut pas de ligne de commande |

### Contraintes à respecter absolument

- **Pas de ligne de commande** pour Elias. Toute manipulation doit se faire en
  déposant un fichier par FileZilla, puis `/stop` en jeu (le service systemd
  redémarre le serveur tout seul). Si un script shell est indispensable, il faut
  le livrer prêt à déposer, avec des explications pas à pas.
- **Ne jamais demander ni accepter de clé SSH privée.** Elias en a envoyé une par
  erreur au début du projet ; il a été prévenu de ne plus jamais le faire.
- Le compte admin est protégé par un code secret écrit dans
  `plugins/BDEIMT/CODE-ADMIN.txt`, lisible seulement par FileZilla. Il est exigé
  au `/register` de `curlybrownhair` et effacé une fois le compte créé.
- Les réponses à Elias sont **en français**, et le contenu en jeu aussi.

---

## 2. Historique du projet

1. **Recherche d'un hébergement** gratuit ou à moins de 2 €/mois pour 40 joueurs
   avec plugins → choix d'Oracle Cloud Free Tier (l'offre « Always Free » ARM
   donne 4 OCPU et 24 Go à répartir ; l'offre x86 E2.1.Micro à 1 Go est
   insuffisante).
2. **Script d'installation automatique** (`installer-minecraft.sh`), déposé dans
   le champ « Initialization script » à la création de l'instance Oracle.
3. **Dépannages successifs** : utilisateur `opc` au lieu de `ubuntu`, SELinux qui
   empêchait systemd de lancer `start.sh`, drapeau JVM retiré par Java 25
   (`G1RSetUpdatingPauseIntervalMillis`), AuthMe en conflit avec l'authentification
   du plugin maison. Scripts `reparer.sh` et `reparer2.sh` livrés pour ça.
4. **Développement du plugin maison `BDEIMT.jar`**, qui remplace une dizaine de
   plugins classiques (login, kits, tombes, téléportations, classements,
   modération, lobby). Versions 1.0 → 1.7.

---

## 3. Chaîne de compilation

> **Important** : les sources du plugin avaient été perdues avec l'ancien
> environnement de travail (il ne restait que le `.jar`). Elles ont été
> reconstituées par décompilation, corrigées, et **versionnées dans ce dépôt
> git**. Ne plus jamais travailler en dehors du dépôt.

Toujours ni Maven ni Gradle pour le plugin lui-même : on compile à la main avec
`javac`, contre les **sources de l'API Paper 26.2** — la version qui tourne
réellement sur le serveur, et non plus 1.21.4 comme avant. Compiler contre la
version exacte du serveur supprime le besoin de l'ancien outil `BinCheck` : la
compatibilité binaire est acquise par construction.

```
serveurimtmortel/
├── plugin/
│   ├── src/fr/bdeimt/serveur/*.java    ← le code (41 fichiers, ~16 200 lignes)
│   └── resources/                       ← plugin.yml, config.yml, histoires.yml
├── build/
│   ├── compile.sh       ← compile dans build/out
│   ├── jar.sh           ← compile puis fabrique livraison/BDEIMT.jar
│   ├── sourcepath.txt   ← chemins des sources de l'API Paper + des stubs
│   ├── deps/pom.xml     ← les dépendances de l'API, tirées de Maven Central
│   └── stubs/           ← Brigadier, introuvable sur Maven Central
└── livraison/BDEIMT.jar
```

### Préparer l'environnement sur une machine neuve

```bash
# 1. les sources de l'API Paper, à la version du serveur
git clone --depth 1 https://github.com/PaperMC/paper /home/user/papermc/paper
git -C /home/user/papermc/paper fetch --depth 1 origin tag 26.2
git -C /home/user/papermc/paper checkout 26.2

# 2. les dépendances de l'API (Maven Central)
cd build/deps && mvn -q -B dependency:copy-dependencies -DoutputDirectory=jars
```

`build/sourcepath.txt` pointe vers `paper-api/src/main/java`,
`paper-api/src/generated/java` et `build/stubs`.

**Attention au réseau** : depuis l'environnement de travail en nuage, seul
`repo1.maven.org` est joignable. `repo.papermc.io`, `hub.spigotmc.org`,
`jitpack.io` et `api.papermc.io` sont bloqués. GitHub n'est accessible qu'en
clonage via le proxy git de la session. C'est pour cette raison que l'API Paper
est prise sous forme de **sources clonées**, et que **Brigadier** — absent de
Maven Central — est remplacé par des stubs d'API dans `build/stubs`, suffisants
pour que le typage passe (le plugin n'utilise pas Brigadier lui-même).

### Compiler et livrer

```bash
./build/compile.sh     # doit afficher zéro erreur
./build/jar.sh         # produit livraison/BDEIMT.jar
```

`jar.sh` recopie les `.yml` de `plugin/resources` dans `build/out` avant de
fabriquer l'archive : sans ça, `plugin.yml` manque et le plugin ne se charge
pas.

### Avant chaque livraison

1. `./build/compile.sh` sans erreur.
2. Numéro de version incrémenté dans `plugin/resources/plugin.yml`.
3. Toute nouvelle commande déclarée dans `plugin.yml` **et** enregistrée dans
   `BDEIMT.onEnable()` via `cmd(...)`.
4. Tout nouveau `Listener` ajouté au tableau de `registerEvents`.
5. Commit dans ce dépôt.

## 4. Architecture du plugin

`BDEIMT.java` est la classe principale. Elle instancie tous les modules dans un
ordre précis (`State` → `DataStore` → `Ranks` → `Auth` → `Lobby` → le reste),
les enregistre comme écouteurs d'événements, branche les commandes, puis lance
trois tâches répétées : une par seconde (`auth.tick`, `fly.tick`, `graves.tick`),
une par minute (dragon, sauvegarde, classement), et les annonces de Poulpy.

| Fichier | Rôle |
|---|---|
| `BDEIMT.java` | Classe principale : instanciation, écouteurs, commandes, tâches |
| `State.java` | État global persisté dans `etat.yml` : modérateurs, cartes-image, prochain dragon, votes par liste, mode maintenance |
| `DataStore.java` | Fiches joueurs en mémoire + `joueurs/<uuid>.yml`, classements votes et aura |
| `PlayerData.java` | Une fiche joueur : votes, aura, homes, mute, délais de kits, lots en attente |
| `Auth.java` | `/register`, `/login`, `/changemdp`. PBKDF2-HmacSHA256, 120 000 itérations, sel par compte. Tant qu'un joueur n'est pas identifié, il est figé dans le lobby et tous ses événements sont annulés |
| `Lobby.java` | Île flottante dans le vide, hologrammes, images sur cartes (`BDEIMT.png`, `serveur.png`) |
| `Ranks.java` | `[Mobutu]` / `[Modérateur]` / `[Joueur]` : préfixes chat, tab, au-dessus de la tête, et droits accordés aux modos sur les autres plugins |
| `Msg.java` | Tous les messages. Deux « IA » : **Poulpy** (bleu, infos) et **LaPanthèreRose** (rose, votes et événements) |
| `Votes.java` | `/vote` horaire, `/probavote`, panneau de classement latéral |
| `Loots.java` | Les 200 lots du `/vote` et leurs probabilités, répartis en 6 raretés |
| `Kits.java` | `/kit`, `/kit topvote`, `/kit top1`, les 6 kits vote, les kits cachés du Mobutu, `/listes` |
| `Graves.java` | Tombes : le stuff est gardé 24 h, 2 tombes max, barre de distance en haut de l'écran |
| `Teleports.java` | `/tpa`, `/tpahere`, `/tpaccept`, `/sethome` (1 seul home), `/spawn` |
| `Social.java` | `/msg`, `/r`, `/ignore` |
| `Trade.java` | `/echange` : coffre partagé, double validation, compte à rebours |
| `Moderation.java` | `/mute`, `/ban`, `/tempban`, `/kick`, `/fly` |
| `Panel.java` | `/panel` : tableau de bord du staff en jeu. `/maudire` : le châtiment du Mobutu |
| `Aura.java` | Points gagnés en enchaînant les coups critiques, perdus à la mort |
| `HealthBars.java` | Vie sous le pseudo des joueurs et au-dessus des mobs frappés |
| `Fly.java` | Fly gagné au `/vote`, décompté à la minute, et fly libre du staff |
| `Dragon.java` | L'Ender Dragon renaît toutes les 48 h |
| `Motd.java` | Message et icône dans la liste des serveurs |
| `Announcer.java` | `/guide`, message de bienvenue, astuces régulières |
| `Fun.java` | `/ghoule`, `/traq`, `/oniris`, `/sugardaddimt`, `/wei`, `/tunnel`, enchantements jusqu'à X, succès masqués |
| `FunItems.java` | Les objets drôles des lots et leurs effets |
| `Maintenance.java` | `/maintenance` : compte à rebours, fermeture, mode staff uniquement |
| `OpCommands.java` | `/moderateur` : réservé aux OP |
| `AdminCommand.java` | `/imt` : outils du Mobutu |
| `Menu.java` | Base des interfaces en coffre, en lecture seule |
| `Util.java` | Objets, durées, positions, sons |
| `Zone.java` | **La table des univers** : monde, groupe d'inventaire et de chat, couleur, mode de jeu, PvP, construction, dégâts |
| `Worlds.java` | Chargement des mondes, protections communes, filtrage des commandes selon le monde, voyage d'un univers à l'autre |
| `Inventories.java` | Un inventaire, une expérience et une vie **par groupe de mondes** |
| `Hub.java` | La boussole, son menu des modes de jeu, les portails physiques |
| `Tab.java` | La liste des joueurs : connectés par monde, rang et rôle de liste |
| `Lists.java` | Les listes de la survie (20 joueurs, pas de coups entre membres) |
| `Npcs.java` | Zaza et le Mobutu, deux mannequins au vrai modèle de joueur |
| `Parkour.java` | Le parkour du mois, ses points de contrôle et son classement |
| `Plots.java` | Les parcelles en créatif, leur générateur de monde et les votes |
| `Skyblock.java` | Les îles, toutes dans un seul monde, sur une grille de 256 blocs |
| `SkyHub.java` | La place centrale du skyblock : champ, enclos, mine, marchands |
| `ThirdParty.java` | Retouches sur les autres plugins (message de SkinsRestorer) |

### Fichiers créés sur le serveur

Dans `plugins/BDEIMT/` :

```
config.yml          réglages (admin, WhatsApp, fréquences, dragon)
etat.yml            modérateurs, dragon, votes par liste, maintenance
comptes.yml         mots de passe (hachés)
tombes.yml          tombes en cours
histoires.yml       les 13 histoires du /tunnel — ÉDITABLE, puis /imt reload
CODE-ADMIN.txt      code secret du compte admin (effacé une fois utilisé)
images/             BDEIMT.png et serveur.png à déposer soi-même
joueurs/<uuid>.yml  une fiche par joueur
inventaires/<uuid>.yml  un inventaire par groupe de mondes
listes.yml          les listes de la survie
parkour.yml         points de contrôle et classement du parkour
parcelles.yml       les parcelles, leurs invités et leurs voix
skyblock.yml        les îles et leurs invités
```

---

## 5. Pièges rencontrés — à ne pas refaire

Ces problèmes ont tous coûté une livraison ratée. Ils sont listés pour éviter de
les reproduire.

### MiniMessage (la bibliothèque de formatage)

- Les `TagResolver` **ne sont pas interprétés à l'intérieur des arguments** d'une
  balise `click` ou `hover`. Écrire `<click:run_command:'/tpaccept <n>'>` envoie
  littéralement `<n>`. Il faut concaténer le nom en Java avant la désérialisation.
- Une **apostrophe à l'intérieur d'un argument entre apostrophes** casse
  l'analyse. `hover:show_text:'Ouvrir l'échange'` plante : utiliser l'apostrophe
  typographique `’`.
- `Msg.p(clé, valeur)` utilise `Placeholder.unparsed` : le texte d'un joueur n'est
  jamais réinterprété comme du MiniMessage. C'est volontaire (sécurité).

### Panneau de classement latéral (scoreboard)

- Le client Minecraft **masque les lignes dont le nom commence par `#`**. Les
  entrées sont donc nommées `~v1`, `~a2`, `~l3`…
- **15 lignes maximum** sont affichées. Au-delà, elles sont coupées sans erreur.
  La mise en page actuelle : 1 titre + 5 votes, 1 ligne vide, 1 titre + 3 aura,
  1 titre + 3 listes = 15.
- Les vrais scores sont des entiers 32 bits, trop petits pour l'aura (10^12). On
  affiche donc un `Score.customName` et un `NumberFormat.fixed(...)`.

### Interfaces en coffre

- Il est interdit d'ouvrir ou de fermer un inventaire **pendant** le traitement
  d'un clic. Il faut passer par `Bukkit.getScheduler().runTask(...)` pour agir au
  tick suivant, sinon le client se désynchronise.

### Compatibilité entre versions

- Le plugin compile contre l'API **1.21.4** mais tourne sur **26.2**. Certaines
  signatures changent : `Adventure 5` a cassé la compatibilité binaire de
  `TextComponent.Builder.build()`, remplacé par une chaîne de `Component.append`.
- Les noms de certaines règles de jeu ont changé en 1.21.11 : on parcourt
  `GameRule.values()` et on compare les noms plutôt que d'utiliser les constantes.
- **D'où `BinCheck`** : aucune livraison sans 0 problème sur les deux versions.

### Paper 26 range les mondes autrement — le piège le plus coûteux

Depuis Minecraft 26, un serveur ne range plus chaque monde dans son propre
dossier à la racine. Tous les mondes sont des **dimensions** du monde
principal :

```
world/dimensions/minecraft/overworld/     la survie
world/dimensions/minecraft/bdeimt_hub/    le lobby
```

Une carte déposée à l'ancienne (`bdeimt_hub/` à la racine, avec son
`level.dat`) est **importée** par Paper au premier `createWorld` : il déplace
ses dossiers `region/`, `entities/`, `poi/` **fichier par fichier** vers la
nouvelle dimension, puis supprime l'ancien dossier. Code côté Paper :
`io.papermc.paper.world.migration.LegacyCraftBukkitWorldMigration`.

Si une importation échoue à mi-chemin, la carte reste coupée en deux, et
**toutes** les tentatives suivantes s'arrêtent sur « Refusing to overwrite
existing migrated file », qui remonte en « Failed to migrate legacy world ».
C'est ce qui a bloqué le lobby et le parkour pendant des heures.

`MapRepair` recolle les morceaux avant chaque importation : ce qui avait été
déplacé revient dans la carte, les doublons partent de côté, rien n'est
supprimé. Il est éprouvé sur une copie de la vraie carte du lobby par
`build/tests/run-repair-test.sh` — à relancer après toute modification.

Conséquences pratiques :

- après une importation réussie, le dossier `bdeimt_hub/` **disparaît** de la
  racine : c'est normal, la carte vit désormais dans `world/dimensions/` ;
- pour savoir où Paper range une dimension, lire `World#getWorldPath()` d'un
  monde déjà chargé plutôt que deviner (`Worlds.dimensionPath`) ;
- Multiverse doit se charger **après** nous (`loadbefore`), pour ne pas lancer
  sa propre importation avant notre réparation.

### Les univers séparés

- **L'inventaire à la reconnexion.** Minecraft rend au joueur l'inventaire du
  monde où il s'est déconnecté. Il faut donc relever son groupe **à la
  connexion** (`Auth.onJoin`) avant de le faire passer dans le hub, sinon on
  range l'inventaire du hub dans le groupe « survie » et on écrase le stuff.
- **Les panneaux latéraux.** Le classement des votes vit sur le tableau
  *principal*, partagé par tout le monde. Le parkour et les parcelles ont
  besoin d'un panneau différent par joueur : ils reçoivent donc un tableau
  neuf. `Worlds.arrive` rend le tableau principal partout ailleurs — ne pas
  oublier d'exclure une zone qui aurait son propre panneau.
- **Les points de réapparition des cartes téléchargées sont faux.** Celui de la
  carte du hub pointe sous la carte. Les coordonnées relevées dans les cartes
  livrées servent de valeur par défaut dans `Worlds.DEFAULT_SPAWNS`, et
  `/imt monde spawn ‹zone›` permet de corriger en jeu.
- **Générer un monde bloc par bloc coûte cher.** Le monde des parcelles a 127
  couches de terre : `ChunkData.setRegion` remplit le volume d'un coup, là où
  `setBlock` ferait trente-deux mille appels par chunk.
- **Effacer une île rendue** touche plus de quatre millions de blocs. Le travail
  est étalé sur plusieurs ticks et saute les colonnes vides (le monde est vide
  par défaut), sinon le serveur se fige plusieurs secondes.
- **Aucun plugin ne peut intercepter un message envoyé par un autre plugin.**
  Pour faire taire SkinsRestorer, on va vider la ligne dans ses propres
  fichiers de traduction au démarrage (`ThirdParty`).

### Système

- **SELinux** (Oracle Linux) empêche systemd de lancer un script du dossier
  personnel : erreur `203/EXEC`. Correctif : `chcon -t bin_t start.sh`.
- **Java 25** a supprimé `-XX:G1RSetUpdatingPauseIntervalMillis` : la JVM refuse
  de démarrer si le drapeau est présent dans `start.sh`.
- **AuthMe** doit être supprimé du dossier `plugins` : il entre en conflit avec
  l'authentification intégrée. Le plugin log une erreur explicite s'il le détecte.

---

## 6. Les univers (v2.0)

Le serveur n'est plus une seule survie : c'est un ensemble de mondes étanches,
reliés par un hub. Deux mondes du même **groupe** partagent l'inventaire et le
chat ; deux groupes différents ne partagent rien.

| Zone | Monde | Groupe | Mode | PvP | Casser | Dégâts |
|---|---|---|---|---|---|---|
| `LOGIN` | `bdeimt_lobby` | lobby | aventure | non | non | non |
| `HUB` | `bdeimt_hub` | lobby | aventure | non | non | non |
| `SURVIE` | `world` (+ nether, end) | survie | survie | oui | oui | oui |
| `PARKOUR` | `bdeimt_parkour` | parkour | aventure | non | non | non |
| `SKYHUB` | `bdeimt_skyhub` | skyblock | survie | non | blé et minerai | oui |
| `SKYBLOCK` | `bdeimt_skyblock` | skyblock | survie | non | sur son île | oui |
| `PARCELLES` | `bdeimt_parcelles` | parcelles | créatif | non | sur sa parcelle | non |

`bdeimt_hub` et `bdeimt_parkour` sont des cartes **déposées à la main** dans le
dossier du serveur. Si le dossier manque, le mode apparaît « bientôt
disponible » dans la boussole et le reste continue de tourner. Les trois autres
mondes sont créés par le plugin au premier démarrage.

Le parcours d'un joueur : il arrive dans `bdeimt_lobby`, figé, le temps de
`/register` ou `/login` ; une fois identifié il est posé dans `bdeimt_hub`, où
il se déplace librement, l'inventaire vide sauf une boussole au milieu de la
barre d'objets. La boussole ouvre le menu des modes de jeu. Des portails
physiques peuvent faire la même chose (`/imt portail ‹zone›`).

### Règles communes

- Le chat est séparé par groupe. Seule la mort du Mobutu traverse tous les
  mondes.
- Les commandes sont filtrées par monde (`Worlds.allowed`) : les délires, les
  votes, les tombes, les homes et l'aura restent en survie ; le skyblock
  récupère les votes, les kits, le marché et les téléportations ; les parcelles
  gardent les téléportations mais pas l'échange.
- `/spawn` ramène au départ du monde où l'on est. `/hub` ramène au hub.

## 7. Ce qui existe aujourd'hui

### Commandes joueur

| Commande | Effet |
|---|---|
| `/register`, `/login`, `/changemdp` | Compte et mot de passe |
| `/vote` | Un lot au hasard parmi 200, une fois par heure |
| `/probavote` | Les 200 lots et leurs probabilités, en interface |
| `/kit` | Kit de départ, toutes les 2 h |
| `/kit topvote`, `/kit top1` | Réservés au top 10 et au n°1 des votes |
| `/kit vote <liste>` | `imtmortel`, `zimtzimt`, `ascension`, `bartbart`, `wizart`, `passion` — un seul toutes les 2 h, et chaque prise donne **1 voix à la liste** |
| `/listes` | Classement des listes les plus votées |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpcancel`, `/tpatoggle` | Téléportations avec accord |
| `/sethome`, `/home`, `/delhome`, `/homes`, `/spawn` | Un seul home par joueur |
| `/msg`, `/r`, `/ignore` | Messages privés |
| `/echange <joueur>` | Échange sécurisé |
| `/tombes` | Où sont ses tombes |
| `/aura` | Son aura et le classement |
| `/guide` | Le guide de Poulpy |
| `/fly` | Le fly gagné au vote |
| `/hub` | Revenir au hub choisir un monde |
| `/liste …` | Sa bande en survie : `create`, `invite`, `accept`, `role`, `kick`, `quitter`, `supprimer` |
| `/parkour` | Le classement du parkour du mois ; `/parkour recommencer` |
| `/parcelle …` | `creer`, `tp`, `invite`, `retirer`, `ban`, `unban`, `vote`, `top`, `supprimer` |
| `/ile …` | Skyblock : `creer`, `tp`, `invite`, `retirer`, `hub`, `supprimer confirmer` |
| `/marche` | Les trois marchands du hub skyblock |

### Commandes de délire

| Commande | Délai | Effet |
|---|---|---|
| `/ghoule` | 5 h | Bruit de gorgée, nausée + cécité + vitesse à fond 10 s, puis lenteur 30 s et barre de faim à 3. Tous les joueurs à moins de 50 blocs reçoivent une alerte à l'écran, de la cécité, de la lenteur, un écran qui tremble et un dégât très léger |
| `/traq` | 1 min | **Ouvert** : vitesse et saut à fond 5 s, « On remet le facteur sur le vélo ? Rejoins les ghoules ! », puis chute lente pour ne pas mourir. **Fermé** : « Le Pôle Log se bave dessus… Envoie-leur un message sur IMTA1 ». Le Traq ouvre au hasard ~10 fois par jour pour 1 h, annoncé par LaPanthère |
| `/oniris` | 2 h | 10 chats autour du joueur, miaulements et musique à fond pendant 10 s |
| `/sugardaddimt` | 2 h | Deux gâteaux et un cookie |
| `/wei` | 30 min | Un seul feu d'artifice |
| `/tunnel` | 1 h | Le joueur « écrit » une des 13 histoires du campus dans le chat commun, ligne par ligne, comme s'il tapait lui-même. Histoires éditables dans `histoires.yml` |
| `/kit vote brest` | — | **Commande cachée** : suicide, avec le message « a pris le TER pour Brest. Il n'en est jamais revenu. » |

L'admin (`curlybrownhair`) **n'a aucun délai** sur `/vote` ni sur ces commandes,
et `/traq` lui répond même quand le Traq est fermé. Les kits vote gardent leurs
2 h pour tout le monde, afin de ne pas fausser le classement des listes.

### Commandes du staff

| Commande | Qui | Effet |
|---|---|---|
| `/panel` | Modo | Tableau de bord en jeu |
| `/mute`, `/unmute`, `/kick`, `/ban`, `/tempban`, `/unban` | Modo | Modération |
| `/moderateur ajouter\|retirer\|liste <pseudo>` | OP | Donne ou retire le rôle Modérateur |
| `/maintenance [durée] [raison]` | OP | Compte à rebours de 3 min par défaut, barre en haut de l'écran, titres, puis fermeture propre. Ensuite le serveur n'accepte plus que le staff |
| `/maintenance redemarrer` | OP | Pareil, mais tout le monde peut revenir |
| `/maintenance annuler` / `activer` / `fin` / `etat` | OP | Gestion du mode maintenance |
| `/maudire <joueur>` | Mobutu | Le châtiment |
| `/imt …` | Mobutu | `modo`, `resetmdp`, `info`, `votes`, `lot`, `traq ouvrir\|fermer`, `listes reset`, `dragon`, `lobby`, `reload` |
| `/imt monde` | Mobutu | La liste des univers ; `/imt monde spawn ‹zone›` fixe le point d'arrivée |
| `/imt portail ‹zone› [rayon]` | Mobutu | Pose un portail physique ; `/imt portail effacer` nettoie le monde |
| `/imt pnj ‹zaza\|mobutu›` | Mobutu | Pose le PNJ ici ; `/imt pnj skin ‹id› ‹pseudo›` lui donne un skin |
| `/parkour point ‹nom›` | Mobutu | Pose un point de contrôle ; `annuler`, `nom`, `plancher`, `reset` |

### Autres mécaniques

- **Enchantements jusqu'à X** à l'enclume : deux Tranchant V donnent Tranchant VI,
  et ainsi de suite jusqu'au niveau 10, pour tous les enchantements à plusieurs
  niveaux. Le coût est plafonné à 39 pour éviter le « Trop cher ! » du client.
- **Tombes ouvertes à tous** : n'importe qui peut piller la tombe d'un autre, et
  le propriétaire est prévenu.
- **Succès masqués** dans le chat.
- **Aura** : les coups critiques en combo font monter un score (×10 par palier),
  remis à zéro à la mort.

---

## 8. Travaux en attente

### Console web du staff — écrite mais **exclue du jar**

Le fichier `/home/claude/build/hors-build/WebConsole.java` contient un serveur
HTTP intégré (port 8123) qui sert une page de gestion : liste des joueurs, chat
en direct, boutons de modération, console du serveur et champ de commande pour
l'admin. L'authentification est un défi-réponse : le navigateur calcule
`HMAC-SHA256(PBKDF2(mot de passe), défi)` et le mot de passe ne circule jamais.

**Ce code n'est pas dans le jar**, et son branchement a été retiré de
`BDEIMT.java`, `Msg.java`, `Moderation.java` et `Auth.java`. Il manque aussi la
page `console.html`, jamais écrite. Le fichier est conservé uniquement comme
point de départ si le sujet revient.

Si Elias redemande une console externe, lui proposer d'abord **Crafty Controller**
ou **MCSManager**, qui sont faits pour ça, plutôt que de rouvrir un port depuis le
plugin.

### Rien n'a été testé en jeu

Le plugin compile contre l'API exacte du serveur, mais **aucune de ces
fonctionnalités n'a été essayée sur un vrai serveur** : pas de Minecraft dans
l'environnement de travail. Les points les plus à surveiller au premier
démarrage :

- les deux cartes déposées (`bdeimt_hub`, `bdeimt_parkour`) sont en 1.21.5 et
  1.21.11 : Paper 26.2 va les convertir au chargement, ce qui peut prendre un
  moment la première fois ;
- les mannequins (Zaza, le Mobutu) sont une entité récente ; si le serveur
  refuse de les poser, le plugin l'écrit dans la console et le reste tourne ;
- la construction du hub skyblock pose une vingtaine de milliers de blocs au
  tout premier démarrage.

### Autres pistes évoquées

- Les kits vote n'ont pas de délai séparé par liste : prendre `passion` bloque
  aussi `imtmortel` pendant 2 h. C'est voulu, mais à revoir si le vote des listes
  devient l'enjeu principal.
- La lenteur à la connexion n'est toujours pas diagnostiquée. La piste
  SkinsRestorer reste la plus probable (il interroge Mojang à chaque arrivée en
  mode cracké). Le message publicitaire est maintenant supprimé, mais pas la
  requête réseau elle-même.
- `Msg.broadcast` reste global : les arrivées, les départs et les astuces de
  Poulpy traversent encore tous les mondes. Seul le chat des joueurs est
  cloisonné. À revoir si ça gêne.
- Les parcelles sont attribuées en spirale depuis l'origine, sans limite de
  monde. Si le quartier devient immense, poser une bordure de monde.

---

## 9. Procédure de livraison à Elias

1. Compiler, vérifier avec BinCheck, construire le jar.
2. Envoyer `BDEIMT.jar` (et les sources si utile).
3. Lui dire, en français et sans jargon :
   - remplacer l'ancien `BDEIMT.jar` dans `plugins/` par FileZilla ;
   - taper `/stop` en jeu, le serveur redémarre tout seul en ~30 s ;
   - ce qui a changé, du point de vue d'un joueur ;
   - préciser que le plugin n'a pas été testé en jeu de notre côté.
4. Ne jamais lui demander d'ouvrir un terminal.
