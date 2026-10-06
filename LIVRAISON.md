# BDEIMT v2.0 — ce qu'il faut faire pour installer

Tout se fait par **FileZilla** et **en jeu**. Aucune ligne de commande.

---

## 1. Remplacer le plugin

Dépose `BDEIMT.jar` dans `/home/opc/minecraft/plugins/`, en remplaçant
l'ancien. **Ne tape pas encore `/stop`**, fais d'abord l'étape 2.

---

## 2. Déposer les deux cartes

Les deux mondes que tu m'as envoyés doivent être posés dans
`/home/opc/minecraft/`, **à côté** du dossier `world` (pas dedans).

### Le hub

1. Décompresse `UnpackThisFolder.zip` sur ton ordinateur.
2. Tu obtiens un dossier `Empty`. **Renomme-le `bdeimt_hub`.**
3. Dedans, supprime le fichier `session.lock` s'il y est.
4. Dépose le dossier `bdeimt_hub` dans `/home/opc/minecraft/`.

### Le parkour

1. Décompresse `Warden-Parkour-Map.zip`.
2. Tu obtiens `Warden Parkour - v1.2`. **Renomme-le `bdeimt_parkour`**
   (sans espace, sans tiret, sans version).
3. Supprime `session.lock` s'il y est.
4. Dépose `bdeimt_parkour` dans `/home/opc/minecraft/`.

> Si tu sautes cette étape, le serveur démarre quand même : les deux modes
> apparaîtront simplement « bientôt disponible » dans la boussole, et tout le
> reste fonctionnera. Tu pourras les ajouter plus tard.

Les trois autres mondes — skyblock, place centrale du skyblock, parcelles —
sont **construits tout seuls** par le plugin au premier démarrage. Tu n'as rien
à déposer pour eux.

---

## 3. Redémarrer

Tape `/stop` en jeu. Le serveur revient tout seul en une trentaine de secondes.

Le premier démarrage est **plus long que d'habitude** : les deux cartes sont en
1.21.5 et 1.21.11, Paper doit les convertir, et la place centrale du skyblock
est construite bloc par bloc. Compte quelques minutes.

---

## 4. Les réglages à faire en jeu, une seule fois

Connecte-toi avec `curlybrownhair`, puis :

### Le point d'arrivée du hub

J'ai mis par défaut l'endroit où se tenait l'auteur de la carte
(191, -44, 142). **Si ce n'est pas le bon endroit** : place-toi où tu veux que
les joueurs arrivent, regarde dans la bonne direction, puis tape :

```
/imt monde spawn hub
```

Ça règle aussi le point de réapparition après une mort — c'est ce qui te
téléportait sous la carte.

Même chose pour les autres si besoin : `/imt monde spawn parkour`,
`/imt monde spawn parcelles`, `/imt monde spawn skyhub`.
`/imt monde` tout court te montre la liste des mondes et qui s'y trouve.

### Les deux PNJ du hub

Place-toi à l'endroit voulu, puis :

```
/imt pnj mobutu
/imt pnj zaza
```

Pour leur donner un skin, le plus simple est de **copier celui d'un compte
Minecraft existant** :

```
/imt pnj skin zaza <pseudo du compte>
/imt pnj skin mobutu <pseudo du compte>
```

Le skin arrive au bout de quelques secondes. Si tu préfères un skin que tu as
en fichier, il faut d'abord le mettre en ligne quelque part (MineSkin par
exemple) et coller la valeur `textures` dans `config.yml` — dis-le-moi et je te
guiderai.

- **Zaza** a 20 points de vie, se frappe, meurt, et revient au même endroit au
  bout de 3 secondes.
- **Le Mobutu** a 500 points de vie, comme un Warden, et **te rend 5 fois les
  dégâts que tu lui mets**. Si quelqu'un y arrive, tout le serveur, dans tous
  les mondes, reçoit l'annonce de sa colère.

### Les points de contrôle du parkour

C'est l'étape la plus longue, mais elle se fait une fois pour toutes. Va dans
le parkour (boussole → Parkour), puis parcours la map **dans l'ordre** en
posant un point à chaque palier :

```
/parkour point depart
/parkour point 1
/parkour point 2
...
/parkour point arrivee
```

- Le **premier** point posé est le départ : c'est là qu'on apparaît et qu'on
  revient quand on tombe.
- Le **dernier** est l'arrivée : la franchir enregistre le temps.
- `/parkour annuler` retire le dernier point si tu t'es trompé.
- Place-toi au niveau le plus bas de la map et tape `/parkour plancher` : en
  dessous de cette hauteur, on repart du dernier point de contrôle au lieu de
  mourir.
- `/parkour nom Warden Parkour` change le nom affiché.

L'hologramme du classement se pose tout seul au-dessus du premier point.

### Les portails (facultatif)

Sur la carte du hub il y a des endroits prévus pour des portails. Place-toi
dedans et tape :

```
/imt portail parkour 2
/imt portail parcelles 2
/imt portail survie 2
/imt portail skyhub 2
```

Le `2` est le rayon en blocs. Marcher dedans change de monde.
`/imt portail effacer` retire tous les portails du monde où tu es.

---

## 5. Ce que les joueurs voient maintenant

1. Ils arrivent dans le **lobby de connexion**, figés, avec l'image du serveur
   — nettement plus grande qu'avant — et le message `/register` / `/login`.
2. Une fois identifiés, ils sont posés dans le **hub**, où ils se déplacent
   librement. Inventaire vide, sauf **une boussole au milieu de la barre
   d'objets**. Clic droit dessus : le menu des modes de jeu, avec le nombre de
   joueurs dans chacun, et le guide du serveur.
3. Ils choisissent leur monde.

**Chaque monde est étanche** : son propre inventaire, sa propre expérience, sa
propre vie, son propre chat. Le stuff de la survie ne sort pas de la survie. Un
message écrit dans le skyblock n'est pas lu en survie. Seule la mort du Mobutu
traverse tout.

Les commandes aussi sont filtrées : `/ghoule`, `/traq`, `/tunnel`, `/vote`,
`/kit`, `/home`, `/aura`, les tombes… restent la survie. Le skyblock récupère
`/vote`, `/kit`, `/marche`, `/echange` et les `/tpa`. Les parcelles gardent les
`/tpa` mais pas l'échange. Le lobby et le parkour n'ont rien.

Dans la touche **TAB**, l'en-tête donne le nombre de connectés et le pied de
page le détail monde par monde.

---

## 6. Les nouveautés, mode par mode

### Survie — les listes

```
/liste create <nom>              fonder sa bande
/liste invite <joueur> <role>    inviter quelqu'un avec un rôle
/liste accept                    accepter une invitation
/liste role <joueur> <role>      changer un rôle
/liste kick <joueur>             exclure
/liste quitter                   partir
/liste supprimer                 dissoudre (propriétaire seulement)
```

20 personnes maximum. Les membres d'une même liste **ne peuvent pas se
frapper** et se **téléportent entre eux sans les 3 secondes d'attente ni le
délai de 30 secondes**. Le rôle s'affiche entre parenthèses à côté du pseudo,
dans le chat et dans le TAB, **sans gras**, dans la couleur de la liste. Chaque
liste créée reçoit une couleur franche différente (12 couleurs en rotation).

### Parkour

Carte commune, pas de stuff, pas de casse, pas de coups. On avance de point en
point ; tomber ramène au dernier atteint. Le classement « qui est allé le plus
loin » s'affiche **sur le côté de l'écran** pendant qu'on joue et sur
l'**hologramme** au départ. Pour ceux qui terminent, le meilleur temps s'ajoute.

### Parcelles en créatif

```
/parcelle creer                  prendre sa parcelle
/parcelle tp [joueur]            aller chez soi ou chez quelqu'un
/parcelle invite <joueur>        l'autoriser à construire
/parcelle retirer <joueur>       lui retirer le droit
/parcelle ban <joueur>           le bannir de chez soi
/parcelle vote <joueur>          une voix par jour
/parcelle top                    le classement
/parcelle supprimer              rendre sa parcelle
```

Monde plat, **terre sur 127 couches de profondeur**, parcelles de 32 × 32
séparées par des chemins en planches bordés de dalles de pierre, avec un
lampadaire à chaque croisement. La nuit est sautée dès qu'elle tombe. On
circule partout, on ne construit que chez soi. 5 personnes maximum par
parcelle. Le classement des parcelles les plus aimées s'affiche sur le côté de
l'écran.

### Skyblock

```
/ile creer                       fabriquer son île
/ile                             rentrer chez soi
/ile tp <joueur>                 aller chez quelqu'un
/ile invite <joueur>             l'autoriser à construire
/ile hub                         la place centrale
/ile supprimer confirmer         repartir de zéro
/marche                          les marchands
```

**Toutes les îles sont dans un seul monde**, sur une grille de 256 blocs. C'est
le choix le plus économique pour ta machine : un monde par joueur, ce serait un
dossier, une sauvegarde et des chunks chargés à chaque connexion — ça ne tient
pas à 40 joueurs. Une seule île par personne, qu'on peut rendre pour en
reprendre une neuve. On réapparaît toujours chez soi, et tomber dans le vide y
ramène au lieu de tuer.

**La place centrale** est construite par le plugin, assez grande pour une
trentaine de personnes :

- un **champ de blé** qui remûrit d'un coup toutes les **20 minutes** ;
- un **enclos** où vaches et moutons reviennent toutes les **30 minutes**
  (12 de chaque, jamais plus) ;
- une **salle de mine** avec de gros blocs de charbon, cuivre, fer, redstone,
  lapis, or, diamant et émeraude, qui se reforment **chaque heure** ;
- un **marché** de trois PNJ qui échangent des denrées contre du minerai :
  le Fermier (blé, pain, graines, citrouilles), l'Éleveur (cuir, laine, bœuf
  cuit) et le Mineur (pavé, charbon, fer, or).

On ne casse rien dans la place centrale **sauf** le blé du champ et le minerai
de la mine. Pas de PvP.

---

## 7. Le message de SkinsRestorer

Il n'existe aucun moyen propre d'empêcher un plugin d'écrire dans le chat d'un
joueur. Le plugin va donc chercher le message **dans les fichiers de traduction
de SkinsRestorer** et vide la ligne, à chaque démarrage. Si SkinsRestorer est
mis à jour et réécrit ses fichiers, le message disparaîtra à nouveau tout seul.

Au démarrage, la console écrit combien de fichiers ont été nettoyés. **Si le
message est toujours là** après le redémarrage, c'est qu'il vient d'ailleurs :
dis-moi le texte exact et je le trouverai.

---

## 8. Ce que je n'ai pas pu vérifier

Je n'ai **pas de serveur Minecraft** dans mon environnement de travail : le
plugin compile sans erreur contre la version exacte de ton serveur (Paper
26.2), mais **rien n'a été essayé en jeu**. À surveiller au premier démarrage,
dans la console :

- la conversion des deux cartes ;
- les deux mannequins Zaza et Mobutu : c'est une entité récente de Minecraft,
  si le serveur refuse de les poser le plugin l'écrit et le reste continue ;
- la construction de la place centrale du skyblock.

Dis-moi ce qui coince et je corrige.

---

## 9. Détail à savoir

Ton `config.yml` existant **n'est pas remplacé** (c'est voulu : ton pseudo
d'admin et tes réglages y sont). Les nouvelles options — points d'arrivée,
portails, PNJ — ont des valeurs par défaut dans le plugin, donc tout marche
sans rien toucher, et les commandes `/imt monde`, `/imt portail` et `/imt pnj`
écrivent dans le fichier toutes seules.

Si tu veux le `config.yml` neuf avec tous les commentaires, dis-le-moi : je te
l'envoie, tu recopieras juste ton `admin:` dedans.
