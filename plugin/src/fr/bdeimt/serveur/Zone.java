package fr.bdeimt.serveur;

import org.bukkit.GameMode;

/**
 * Les differents univers du serveur.
 *
 * <p>Chaque zone a son monde, son groupe d'inventaire et de chat, et ses
 * regles. Deux zones du meme groupe partagent l'inventaire et le chat : le
 * lobby de connexion et le hub, ou le hub du skyblock et les iles.
 */
public enum Zone {
   /** L'ile flottante ou l'on arrive, le temps de /login. */
   LOGIN("bdeimt_lobby", "lobby", "la Connexion", "<#8899AA>", GameMode.ADVENTURE, false, false, false),
   /** Le hub : on s'y promene, on choisit son mode de jeu. */
   HUB("bdeimt_hub", "lobby", "le Lobby", "<#4FC3FF>", GameMode.ADVENTURE, false, false, false),
   /** La survie historique du serveur, avec le Nether et l'End. */
   SURVIE(null, "survie", "la Survie", "<#55FF88>", GameMode.SURVIVAL, true, true, true),
   /** Le parkour du mois, commun a tout le monde. */
   PARKOUR("bdeimt_parkour", "parkour", "le Parkour", "<#FFD25E>", GameMode.ADVENTURE, false, false, false),
   /** Le lobby du skyblock : la carte SkySpawn, ses marchands et son marche. */
   SKYHUB("bdeimt_skyspawn", "skyblock", "le Skyblock", "<#7FE3FF>", GameMode.ADVENTURE, false, false, false),
   /** Les iles du skyblock, toutes dans le meme monde. */
   SKYBLOCK("bdeimt_skyblock", "skyblock", "le Skyblock", "<#7FE3FF>", GameMode.SURVIVAL, false, true, true),
   /** Les parcelles creatives. */
   PARCELLES("bdeimt_parcelles", "parcelles", "les Parcelles", "<#C48BFF>", GameMode.CREATIVE, false, true, false);

   /** Nom du monde, ou null pour la survie qui regroupe plusieurs mondes. */
   public final String world;
   /** Groupe d'inventaire et de chat. */
   public final String group;
   /** Nom affiche aux joueurs, precede d'un article. */
   public final String label;
   /** Couleur MiniMessage de la zone. */
   public final String color;
   /** Mode de jeu applique en arrivant. */
   public final GameMode mode;
   /** Les joueurs peuvent-ils se frapper entre eux ? */
   public final boolean pvp;
   /** Peut-on casser et poser des blocs (sous reserve des regles de la zone) ? */
   public final boolean build;
   /** Subit-on les degats, la faim et la mort ? */
   public final boolean damage;

   Zone(String world, String group, String label, String color, GameMode mode, boolean pvp, boolean build, boolean damage) {
      this.world = world;
      this.group = group;
      this.label = label;
      this.color = color;
      this.mode = mode;
      this.pvp = pvp;
      this.build = build;
      this.damage = damage;
   }

   /** Le nom de la zone sans article, pour les titres. */
   public String shortLabel() {
      int cut = this.label.indexOf(' ');
      return cut < 0 ? this.label : this.label.substring(cut + 1);
   }

   /** Le nom colore de la zone, en MiniMessage. */
   public String colored() {
      return this.color + this.shortLabel() + "</" + this.color.substring(1, this.color.length() - 1) + ">";
   }

   /** Les zones ou l'on peut etre envoye par la boussole. */
   public boolean isGameMode() {
      return this == SURVIE || this == PARKOUR || this == SKYHUB || this == PARCELLES;
   }
}
