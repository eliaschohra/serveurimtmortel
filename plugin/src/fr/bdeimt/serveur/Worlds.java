package fr.bdeimt.serveur;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Le registre des univers du serveur.
 *
 * <p>Cette classe sait quel monde correspond a quelle {@link Zone}, cree ou
 * charge les mondes au demarrage, envoie les joueurs d'un univers a l'autre en
 * changeant leur inventaire au passage, et applique les regles communes :
 * qui peut casser, qui peut frapper, qui prend des degats.
 *
 * <p>Les regles fines (sa propre ile, sa propre parcelle) sont deleguees aux
 * modules concernes via {@link BuildRule}.
 */
public final class Worlds implements Listener {
   /** Autorisation de construire, fournie par le module qui gere la zone. */
   public interface BuildRule {
      boolean canBuild(Player p, Location at);
   }

   /** Points d'arrivee releves dans les cartes fournies, faute de reglage. */
   private static final Map<Zone, String> DEFAULT_SPAWNS = Map.of(
      Zone.HUB, "bdeimt_hub;191.5;-44.0;142.5;0.0;0.0",
      Zone.PARKOUR, "bdeimt_parkour;0.5;43.0;-0.5;1.5;0.3",
      Zone.SKYHUB, "bdeimt_skyspawn;44.5;86.0;32.5;0.0;0.0"
   );
   private final BDEIMT pl;
   private final Map<Zone, BuildRule> buildRules = new EnumMap<>(Zone.class);
   private final Map<Zone, Function<Player, Location>> spawns = new EnumMap<>(Zone.class);

   public Worlds(BDEIMT pl) {
      this.pl = pl;
   }

   // ---------------------------------------------------------------- mondes

   // ------------------------------------------------- cartes telechargees
   //
   // Depuis la version 26 de Minecraft, un serveur ne range plus chaque monde
   // dans son propre dossier a la racine. Tous les mondes sont des
   // « dimensions » du monde principal :
   //
   //     world/dimensions/minecraft/overworld/     la survie
   //     world/dimensions/minecraft/bdeimt_hub/    le lobby
   //
   // Une carte deposee a l'ancienne (bdeimt_hub/ a la racine) est donc
   // IMPORTEE par Paper au premier chargement : il deplace ses fichiers
   // region/, entities/ et poi/ dans le nouveau dossier, puis supprime
   // l'ancien. Le detail est dans LegacyCraftBukkitWorldMigration, cote Paper.
   //
   // Le piege : si une importation echoue en cours de route, une partie des
   // fichiers est deja partie dans le nouveau dossier. Toutes les tentatives
   // suivantes s'arretent alors sur « Refusing to overwrite existing migrated
   // file », que l'on voit remonter en « Failed to migrate legacy world ».
   // C'est ce qui bloquait le lobby et le parkour. On repare donc ce cas
   // avant de relancer l'importation.

   /** Charge les cartes deposees par FileZilla : le lobby et le parkour. */
   public void init() {
      this.load(Zone.HUB);
      this.load(Zone.PARKOUR);
      this.load(Zone.SKYHUB);
   }

   /** Le dossier ou Paper range la dimension d'un monde : world/dimensions/minecraft/‹nom›. */
   public java.nio.file.Path dimensionPath(String name) {
      java.nio.file.Path dims = null;

      // Le plus sur : lire ou Paper a range un monde que nous avons nous-memes
      // cree, l'ile de connexion. Ses voisins sont forcement au meme endroit.
      try {
         World own = Bukkit.getWorld(Lobby.WORLD);

         if (own != null) {
            java.nio.file.Path ownPath = own.getWorldPath();

            if (ownPath.getParent() != null && ownPath.getFileName().toString().equals(Lobby.WORLD)) {
               return ownPath.getParent().resolve(name.toLowerCase(java.util.Locale.ENGLISH).replace(" ", "_"));
            }
         }
      } catch (Throwable t) {
      }

      try {
         // A defaut, le chemin reel du monde principal donne la racine des
         // dimensions, sans rien supposer sur le nom du dossier du serveur.
         java.nio.file.Path overworld = ((World)Bukkit.getWorlds().get(0)).getWorldPath();
         java.nio.file.Path candidate = overworld.getParent() == null ? null : overworld.getParent().getParent();

         if (candidate != null && candidate.getFileName() != null && candidate.getFileName().toString().equals("dimensions")) {
            dims = candidate;
         }
      } catch (Throwable t) {
      }

      if (dims == null) {
         dims = Bukkit.getWorldContainer().toPath().resolve(((World)Bukkit.getWorlds().get(0)).getName()).resolve("dimensions");
      }

      return dims.resolve("minecraft").resolve(name.toLowerCase(java.util.Locale.ENGLISH).replace(" ", "_"));
   }

   /** L'ancien dossier, a la racine du serveur, tel qu'il a ete depose. */
   private File legacyFolder(Zone zone) {
      return new File(Bukkit.getWorldContainer(), zone.world);
   }

   /** Une carte deposee a l'ancienne, pas encore importee par Paper. */
   private boolean hasLegacyMap(Zone zone) {
      return new File(this.legacyFolder(zone), "level.dat").exists();
   }

   /** Un chemin lisible pour les messages : relatif au dossier du serveur. */
   private static String shown(java.nio.file.Path path) {
      try {
         return Bukkit.getWorldContainer().toPath().toAbsolutePath().relativize(path.toAbsolutePath()).toString();
      } catch (Throwable t) {
         return path.toString();
      }
   }

   /**
    * Dit, en francais, ce que le plugin trouve sur le disque pour cette zone.
    * C'est la reponse a « j'ai depose le dossier et ca ne marche pas ».
    */
   public String diagnose(Zone zone) {
      if (zone.world == null) {
         return "Monde principal du serveur.";
      }

      if (!this.enabled(zone)) {
         return "Ferme dans config.yml (modes." + zone.group + ": false).";
      }

      if (Bukkit.getWorld(zone.world) != null) {
         return "Charge.";
      }

      java.nio.file.Path migrated = this.dimensionPath(zone.world);

      if (this.hasLegacyMap(zone)) {
         return "Carte presente dans " + zone.world + "/, mais Paper n'a pas reussi a l'importer. La cause exacte est dans la console, juste apres « BDEIMT » au demarrage.";
      }

      if (java.nio.file.Files.isDirectory(migrated)) {
         return "Carte deja importee dans " + shown(migrated) + " mais pas chargee : regarde la console au demarrage.";
      }

      File folder = this.legacyFolder(zone);

      if (folder.isDirectory()) {
         File nested = nestedWorld(folder);

         if (nested != null) {
            return "Le dossier contient un autre dossier (« " + nested.getName() + " ») qui est le vrai monde : redemarre, le plugin le remet a plat.";
         }

         return "Le dossier " + zone.world + "/ n'a pas de level.dat : ce n'est pas un monde Minecraft.";
      }

      StringBuilder near = new StringBuilder();
      File[] siblings = Bukkit.getWorldContainer().listFiles(File::isDirectory);

      if (siblings != null) {
         String wanted = zone.world.replace("bdeimt_", "");

         for (File sibling : siblings) {
            String flat = sibling.getName().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");

            if (flat.contains(wanted) && !sibling.getName().equals(zone.world) && !sibling.getName().contains("mises-de-cote") && !sibling.getName().contains("import-rate")) {
               near.append(" J'y vois « ").append(sibling.getName()).append(" » : renomme-le exactement « ").append(zone.world).append(" ».");
            }
         }
      }

      return "Introuvable : ni " + zone.world + "/ a la racine du serveur, ni " + shown(migrated) + "." + near;
   }

   /** Un dossier de monde pose par erreur a l'interieur d'un autre. */
   private static File nestedWorld(File folder) {
      File[] children = folder.listFiles(File::isDirectory);

      if (children == null) {
         return null;
      }

      File found = null;

      for (File child : children) {
         if (new File(child, "level.dat").exists()) {
            if (found != null) {
               return null;
            }

            found = child;
         }
      }

      return found;
   }

   /**
    * Remonte d'un cran le contenu d'un monde depose dans un sous-dossier.
    * C'est l'erreur classique : decompresser une archive cree un niveau de
    * plus, et Minecraft ne trouve plus le monde.
    */
   public boolean unnest(Zone zone) {
      File folder = this.legacyFolder(zone);
      File nested = nestedWorld(folder);

      if (nested == null || new File(folder, "level.dat").exists()) {
         return false;
      }

      File[] content = nested.listFiles();

      if (content == null) {
         return false;
      }

      for (File file : content) {
         if (!file.renameTo(new File(folder, file.getName()))) {
            this.pl.getLogger().warning("Impossible de deplacer " + file.getName() + " : fais-le a la main par FileZilla.");
            return false;
         }
      }

      nested.delete();
      this.pl.getLogger().info("Contenu de " + zone.world + "/" + nested.getName() + " remonte d'un cran.");
      return true;
   }

   /**
    * Met de cote les dossiers DIM-1 et DIM1 d'une carte faite en solo. Ils ne
    * contiennent aucun terrain dans nos cartes, et l'importation n'en a pas
    * besoin pour un monde de surface.
    */
   private void prepare(Zone zone) {
      File folder = this.legacyFolder(zone);
      File aside = new File(Bukkit.getWorldContainer(), zone.world + "_dimensions-mises-de-cote");

      for (String name : new String[]{"DIM-1", "DIM1"}) {
         File dim = new File(folder, name);

         if (dim.isDirectory()) {
            aside.mkdirs();

            if (!dim.renameTo(new File(aside, name + "-" + System.currentTimeMillis()))) {
               this.pl.getLogger().warning("Impossible de deplacer " + zone.world + "/" + name + " : supprime-le par FileZilla, il est vide.");
            }
         }
      }
   }

   /**
    * Remet en etat une importation ratee, pour que la suivante reparte de zero.
    *
    * <p>Les fichiers que la tentative ratee avait deja deplaces sont ramenes
    * dans la carte d'origine. Ceux qui existent des deux cotes — par exemple
    * un monde vide cree par erreur sous le meme nom — sont mis de cote : on
    * garde toujours la version de la carte deposee. Rien n'est supprime.
    *
    * @return le nombre de fichiers ramenes dans la carte
    */
   private int repairFailedImport(Zone zone) throws java.io.IOException {
      java.nio.file.Path aside = Bukkit.getWorldContainer().toPath().resolve(zone.world + "_import-rate-" + System.currentTimeMillis());
      MapRepair.Result result = MapRepair.repair(this.legacyFolder(zone).toPath(), this.dimensionPath(zone.world), aside);

      if (result == null) {
         return 0;
      }

      this.pl.getLogger().info(
         "Importation ratee de " + zone.world + " reparee : " + result.restored() + " fichier(s) ramene(s) dans la carte, "
            + result.duplicates() + " doublon(s) mis de cote dans " + result.aside().getFileName() + "."
      );
      return result.restored();
   }

   /** Charge un monde en cours de partie, sans redemarrer. */
   public boolean loadNow(Zone zone) {
      this.load(zone);
      return this.available(zone);
   }

   private void load(Zone zone) {
      if (Bukkit.getWorld(zone.world) != null) {
         this.tune(zone);
         return;
      }

      if (!this.enabled(zone)) {
         this.pl.getLogger().info(zone.shortLabel() + " ferme dans config.yml : son monde n'est pas charge.");
         return;
      }

      this.unnest(zone);
      boolean legacy = this.hasLegacyMap(zone);
      boolean migrated = java.nio.file.Files.isDirectory(this.dimensionPath(zone.world));

      if (!legacy && !migrated) {
         this.pl.getLogger().warning("Monde absent : " + zone.world + " — " + this.diagnose(zone));
         return;
      }

      if (legacy) {
         this.prepare(zone);

         // Paper ne supprime l'ancien dossier qu'une fois l'importation
         // reussie : s'il est encore la ET que le nouveau existe deja, c'est
         // qu'une tentative a echoue a mi-chemin.
         if (migrated) {
            try {
               this.repairFailedImport(zone);
            } catch (Throwable t) {
               this.pl.getLogger().warning("Reparation de " + zone.world + " impossible : " + t);
            }
         }

         this.pl.getLogger().info("Importation de la carte " + zone.world + " au format de Paper 26 (une seule fois, patiente)...");
      }

      try {
         // Un generateur vide : autour de la carte, le vide plutot qu'un
         // terrain genere au hasard.
         World w = Bukkit.createWorld(new WorldCreator(zone.world).generator(new Skyblock.VoidGenerator()).generateStructures(false));

         if (w == null) {
            this.pl.getLogger().warning("Monde " + zone.world + " : le serveur n'a rien renvoye.");
         } else {
            this.pl.getLogger().info("Monde charge : " + zone.world + " (" + shown(w.getWorldPath()) + ")");
            this.tune(zone);
         }
      } catch (Throwable t) {
         this.pl.getLogger().warning("Monde " + zone.world + " refuse par le serveur.");

         // La cause utile est souvent deux ou trois crans plus bas.
         for (Throwable cause = t; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            this.pl.getLogger().warning("   cause : " + cause);
         }
      }
   }

   /** Applique les regles de jeu d'une zone a son monde. */
   public void tune(Zone zone) {
      World w = this.world(zone);
      if (w == null) {
         return;
      }

      rule(w, false, "doFireTick", "fire_damage");
      rule(w, false, "mobGriefing", "mob_griefing");
      // Partout on garde son stuff en mourant, sauf sur les iles du skyblock :
      // tomber dans le vide y est une vraie mort, comme dans le skyblock classique.
      rule(w, zone != Zone.SKYBLOCK, "keepInventory", "keep_inventory");
      rule(w, false, "announceAdvancements", "announce_advancements");
      rule(w, false, "showDeathMessages", "show_death_messages");
      rule(w, zone.pvp, "pvp");

      if (zone == Zone.HUB || zone == Zone.PARKOUR || zone == Zone.SKYHUB || zone == Zone.PARCELLES) {
         rule(w, false, "doMobSpawning", "spawn_mobs");
         rule(w, false, "doPatrolSpawning", "spawn_patrols");
         rule(w, false, "doTraderSpawning", "spawn_wandering_traders");
         rule(w, false, "doInsomnia", "spawn_phantoms");
         rule(w, false, "doWeatherCycle", "advance_weather");
         rule(w, 0, "randomTickSpeed", "random_tick_speed");
         safe(() -> w.setDifficulty(Difficulty.PEACEFUL));
         safe(() -> w.setStorm(false));
         safe(() -> w.setThundering(false));
      }

      if (zone == Zone.HUB || zone == Zone.PARKOUR || zone == Zone.SKYHUB) {
         rule(w, false, "doDaylightCycle", "advance_time");
         rule(w, false, "fallDamage", "fall_damage");
         safe(() -> w.setTime(6000L));
      }

      if (zone == Zone.PARCELLES) {
         // Nuit tres courte : le cycle tourne, mais on le repousse au matin des
         // qu'il fait nuit (voir Plots#tick).
         rule(w, true, "doDaylightCycle", "advance_time");
         rule(w, false, "fallDamage", "fall_damage");
      }

      Location spawn = this.configSpawn(zone);
      if (spawn != null && spawn.getWorld() == w) {
         safe(() -> w.setSpawnLocation(spawn));
      }
   }

   private static void rule(World w, Object value, String... names) {
      try {
         for (GameRule<?> gr : GameRule.values()) {
            String name = gr.getName();

            for (String wanted : names) {
               if (name.equalsIgnoreCase(wanted) || name.endsWith(":" + wanted)) {
                  set(w, gr, value);
                  return;
               }
            }
         }
      } catch (Throwable t) {
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> void set(World w, GameRule<T> rule, Object value) {
      if (rule.getType().isInstance(value)) {
         w.setGameRule(rule, (T)value);
      }
   }

   private static void safe(Runnable r) {
      try {
         r.run();
      } catch (Throwable t) {
      }
   }

   // ------------------------------------------------------------- recherche

   public World world(Zone zone) {
      return zone.world == null ? (World)Bukkit.getWorlds().get(0) : Bukkit.getWorld(zone.world);
   }

   /**
    * Un mode est-il ouvert ? On peut en fermer un temporairement avec
    * {@code modes.‹zone›: false} dans config.yml, sans toucher a ses mondes :
    * il reapparait « bientot disponible » dans la boussole, ses commandes
    * cessent de repondre, et son monde n'est meme pas charge.
    */
   public boolean enabled(Zone zone) {
      return this.pl.getConfig().getBoolean("modes." + zone.group, true);
   }

   /** Un mode de jeu est disponible quand il est ouvert et que son monde est la. */
   public boolean available(Zone zone) {
      return this.enabled(zone) && this.world(zone) != null;
   }

   public Zone zoneOf(World w) {
      if (w != null) {
         String name = w.getName();

         for (Zone zone : Zone.values()) {
            if (zone.world != null && zone.world.equals(name)) {
               return zone;
            }
         }
      }

      return Zone.SURVIE;
   }

   public Zone zoneOf(Player p) {
      return this.zoneOf(p.getWorld());
   }

   /** Toutes les zones ouvertes, dans l'ordre du menu de la boussole. */
   public List<Zone> openZones() {
      List<Zone> out = new ArrayList<>();

      for (Zone zone : Zone.values()) {
         if (zone.isGameMode() && this.available(zone)) {
            out.add(zone);
         }
      }

      return out;
   }

   /** Combien de joueurs identifies se trouvent dans ce groupe de zones. */
   public int count(Zone zone) {
      int n = 0;

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p) && this.zoneOf(p).group.equals(zone.group)) {
            n++;
         }
      }

      return n;
   }

   // --------------------------------------------------------------- arrivee

   public void registerBuildRule(Zone zone, BuildRule rule) {
      this.buildRules.put(zone, rule);
   }

   /** Un module peut decider lui-meme ou atterrit le joueur (son ile, sa parcelle). */
   public void registerSpawn(Zone zone, Function<Player, Location> spawn) {
      this.spawns.put(zone, spawn);
   }

   private Location configSpawn(Zone zone) {
      if (zone.world == null) {
         return this.pl.survivalSpawn();
      }

      Location fromConfig = Util.loc(this.pl.getConfig().getString("mondes." + zone.name().toLowerCase(java.util.Locale.ROOT) + ".spawn"));
      if (fromConfig != null) {
         return fromConfig;
      }

      // Rien dans config.yml : on retombe sur les coordonnees relevees dans les
      // cartes livrees, car leur point de reapparition enregistre est faux
      // (celui du hub envoie sous la carte).
      Location known = Util.loc(DEFAULT_SPAWNS.get(zone));
      if (known != null) {
         return known;
      }

      World w = this.world(zone);
      return w == null ? null : w.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
   }

   /** Ou arrive ce joueur-la dans cette zone. */
   public Location spawnOf(Zone zone, Player p) {
      Function<Player, Location> custom = this.spawns.get(zone);
      if (custom != null && p != null) {
         Location l = custom.apply(p);
         if (l != null) {
            return l;
         }
      }

      return this.configSpawn(zone);
   }

   /** Enregistre le point d'arrivee d'une zone dans config.yml. */
   public void setSpawn(Zone zone, Location l) {
      this.pl.getConfig().set("mondes." + zone.name().toLowerCase(java.util.Locale.ROOT) + ".spawn", Util.loc(l));
      this.pl.saveConfig();
      this.tune(zone);
   }

   /**
    * Envoie un joueur dans un autre univers : range son inventaire, sort celui
    * de la destination, le teleporte et lui remet le bon mode de jeu.
    */
   public boolean send(Player p, Zone zone) {
      if (!this.available(zone)) {
         Msg.err(p, "Ce mode de jeu n'est pas encore ouvert.");
         return false;
      }

      Location target = this.spawnOf(zone, p);
      if (target == null || target.getWorld() == null) {
         Msg.err(p, "Impossible de trouver le point d'arrivee de ce monde.");
         return false;
      }

      Zone from = this.zoneOf(p);
      if (from == Zone.SURVIE && !p.isDead()) {
         PlayerData data = this.pl.data().get(p);
         data.returnLoc = Util.loc(p.getLocation());
         data.savedGameMode = p.getGameMode().name();
         data.touch();
      }

      if (p.isInsideVehicle()) {
         p.leaveVehicle();
      }

      p.closeInventory();
      p.setFallDistance(0.0F);
      p.setFireTicks(0);
      this.pl.inventories().switchTo(p, zone.group);
      p.setGameMode(zone.mode);
      p.setAllowFlight(zone.mode == GameMode.CREATIVE);
      p.setFlying(false);
      p.teleport(target);
      this.arrive(p, zone);
      this.enforceModeLater(p);

      if (from.group.equals(zone.group)) {
         return true;
      }

      Msg.raw(p, "<dark_gray>» <gray>Tu es maintenant dans " + zone.colored() + "<gray>.</gray>");
      Util.sound(p, "entity.enderman.teleport", 0.6F, 1.4F);
      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (p.isOnline() && this.zoneOf(p) == zone) {
            this.pl.announcer().welcome(p, zone);
         }
      }, 30L);
      return true;
   }

   /** Ce qu'on prepare juste apres l'atterrissage : stuff de zone, boussole. */
   public void arrive(Player p, Zone zone) {
      if (zone.group.equals("lobby") || zone == Zone.PARKOUR) {
         Inventories.clear(p);
      }

      if (zone == Zone.HUB) {
         this.pl.hub().giveCompass(p);
      }

      if (zone == Zone.PARKOUR) {
         this.pl.parkour().start(p);
      } else if (zone != Zone.PARCELLES) {
         // Le panneau des classements — votes, aura, listes — appartient a la
         // survie : il vit sur le tableau principal, que tout le monde partage.
         // Ailleurs on donne au joueur un tableau vierge, sinon il verrait le
         // classement de la survie depuis le skyblock ou le hub.
         try {
            p.setScoreboard(
               zone == Zone.SURVIE
                  ? Bukkit.getScoreboardManager().getMainScoreboard()
                  : Bukkit.getScoreboardManager().getNewScoreboard()
            );
         } catch (Throwable t) {
         }
      }

      if (zone == Zone.SURVIE) {
         this.pl.fly().onLogin(p);
         this.pl.graves().onLogin(p);
         this.pl.votes().onLogin(p);
      }

      this.pl.tab().refresh(p);
   }

   // ----------------------------------------------------------- mode de jeu

   /**
    * Remet le mode de jeu de la zone, un peu apres l'arrivee.
    *
    * <p>Multiverse impose lui aussi un mode de jeu a chaque changement de
    * monde (survie par defaut), juste apres nous : sans ce second passage, on
    * arrivait en survie dans les parcelles. On repasse donc deux fois.
    */
   public void enforceModeLater(Player p) {
      for (long delay : new long[]{3L, 20L}) {
         Bukkit.getScheduler().runTaskLater(this.pl, () -> this.enforceMode(p, null), delay);
      }
   }

   private void enforceMode(Player p, Zone from) {
      if (!p.isOnline() || p.isDead() || !this.pl.auth().isLogged(p)) {
         return;
      }

      Zone zone = this.zoneOf(p);
      GameMode wanted = zone.mode;

      if (zone == Zone.SURVIE) {
         // En survie, l'admin choisit son mode ; mais personne n'y garde le
         // creatif des parcelles.
         if (p.getGameMode() != GameMode.CREATIVE || this.pl.ranks().isAdmin(p) && from != Zone.PARCELLES) {
            return;
         }

         wanted = GameMode.SURVIVAL;
      }

      if (p.getGameMode() != wanted && p.getGameMode() != GameMode.SPECTATOR) {
         p.setGameMode(wanted);
      }

      if (wanted == GameMode.CREATIVE) {
         p.setAllowFlight(true);
      } else if (zone != Zone.SURVIE && !this.pl.ranks().isStaff(p)) {
         p.setFlying(false);
         p.setAllowFlight(false);
      }
   }

   /** Quel que soit le moyen (portail, /mvtp, mort...), changer de monde remet le bon mode. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
      Player p = e.getPlayer();
      Zone from = this.zoneOf(e.getFrom());

      for (long delay : new long[]{3L, 20L}) {
         Bukkit.getScheduler().runTaskLater(this.pl, () -> this.enforceMode(p, from), delay);
      }
   }

   // ------------------------------------------------------------ protection

   /** L'admin en creatif peut retoucher n'importe quelle carte. */
   private boolean bypass(Player p) {
      // Dans les parcelles tout le monde est en creatif : l'admin y suit les
      // memes regles que les autres (chemins et parcelles d'autrui proteges).
      return p.getGameMode() == GameMode.CREATIVE && this.pl.ranks().isAdmin(p) && this.zoneOf(p) != Zone.PARCELLES;
   }

   private boolean canBuild(Player p, Location at) {
      Zone zone = this.zoneOf(at.getWorld());
      if (zone == Zone.SURVIE || this.bypass(p)) {
         return true;
      }

      if (!zone.build) {
         return false;
      }

      BuildRule rule = this.buildRules.get(zone);
      return rule == null || rule.canBuild(p, at);
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent e) {
      if (!this.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
         e.setCancelled(true);
         this.denied(e.getPlayer(), e.getBlock().getLocation());
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent e) {
      if (!this.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
         e.setCancelled(true);
         this.denied(e.getPlayer(), e.getBlock().getLocation());
      }
   }

   /** Dire pourquoi, en parlant du monde du BLOC vise, pas de celui du joueur. */
   private void denied(Player p, Location at) {
      Zone zone = this.zoneOf(at.getWorld());

      if (zone.build) {
         Msg.err(p, "Ce n'est pas chez toi : tu ne peux rien modifier ici.");
      } else if (this.pl.ranks().isAdmin(p)) {
         Msg.err(p, "On ne casse rien dans <white>" + zone.shortLabel() + "</white>. <gray>Pour modifier la carte : /gamemode creative.</gray>");
      } else {
         Msg.err(p, "On ne casse rien dans <white>" + zone.shortLabel() + "</white>.");
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onHit(EntityDamageByEntityEvent e) {
      if (e.getEntity() instanceof Player victim) {
         Zone zone = this.zoneOf(victim.getWorld());
         Player attacker = attacker(e);
         if (attacker != null && !attacker.equals(victim)) {
            if (!zone.pvp) {
               e.setCancelled(true);
               Msg.err(attacker, "On ne se bat pas dans <white>" + zone.shortLabel() + "</white>.");
               return;
            }

            if (this.pl.lists().sameList(attacker, victim)) {
               e.setCancelled(true);
               Msg.err(attacker, "<white><n></white> est dans ta liste.", Msg.p("n", victim.getName()));
               return;
            }
         }

         if (!zone.damage) {
            e.setCancelled(true);
         }
      }
   }

   private static Player attacker(EntityDamageByEntityEvent e) {
      if (e.getDamager() instanceof Player p) {
         return p;
      }

      if (e.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
         return p;
      }

      return null;
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent e) {
      if (e.getEntity() instanceof Player p && !this.zoneOf(p.getWorld()).damage) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onFood(FoodLevelChangeEvent e) {
      if (e.getEntity() instanceof Player p && !this.zoneOf(p.getWorld()).damage) {
         e.setCancelled(true);
         p.setFoodLevel(20);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDrop(PlayerDropItemEvent e) {
      Zone zone = this.zoneOf(e.getPlayer());
      if ((zone.group.equals("lobby") || zone == Zone.PARKOUR) && !this.bypass(e.getPlayer())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onClick(InventoryClickEvent e) {
      if (e.getWhoClicked() instanceof Player p) {
         Zone zone = this.zoneOf(p);
         if ((zone.group.equals("lobby") || zone == Zone.PARKOUR) && !this.bypass(p)) {
            e.setCancelled(true);
         }
      }
   }

   // ------------------------------------------------------- commandes

   /** Ce qui marche partout : le compte, les messages prives, le staff. */
   private static final java.util.Set<String> PARTOUT = java.util.Set.of(
      "register", "reg", "inscription", "login", "l", "log", "connexion", "changemdp", "changepassword",
      "msg", "m", "w", "tell", "r", "repondre", "ignore", "guide", "aide", "spawn", "hub", "lobby",
      "panel", "mute", "unmute", "tempban", "ban", "unban", "kick", "imt", "maintenance", "moderateur", "musique", "music",
      "maudire", "help", "?"
   );
   /** Ce qui n'a de sens qu'en survie : les delires, les votes, les tombes. */
   private static final java.util.Set<String> SURVIE_SEULEMENT = java.util.Set.of(
      "vote", "probavote", "probavotes", "kit", "kits", "listes", "liste", "bande", "team", "tombes", "tombe",
      "aura", "fly", "ghoule", "traq", "oniris", "sugardaddimt", "wei", "tunnel",
      "sethome", "home", "delhome", "homes"
   );
   /** Ce que le skyblock reprend de la survie : les votes, les kits, le marche. */
   private static final java.util.Set<String> SKYBLOCK_AUSSI = java.util.Set.of(
      "vote", "probavote", "probavotes", "kit", "kits", "marche", "marché", "marchand", "ile", "île", "is", "island", "skyblock",
      "solde", "money", "balance", "bal", "payer", "pay"
   );
   /** Les teleportations et l'echange, autorises aussi dans le skyblock. */
   private static final java.util.Set<String> ENTRE_JOUEURS = java.util.Set.of(
      "tpa", "tpahere", "tpaccept", "tpyes", "tpdeny", "tpno", "tpcancel", "tpatoggle", "echange"
   );

   /** Cette commande a-t-elle un sens dans cette zone ? */
   public boolean allowed(Zone zone, String cmd) {
      String name = cmd.toLowerCase(java.util.Locale.ROOT);
      if (PARTOUT.contains(name)) {
         return true;
      }

      if (zone == Zone.SURVIE) {
         return true;
      }

      boolean sky = zone == Zone.SKYBLOCK || zone == Zone.SKYHUB;
      if (sky && SKYBLOCK_AUSSI.contains(name)) {
         return true;
      }

      if (ENTRE_JOUEURS.contains(name)) {
         // Pas d'echange dans les parcelles, et rien dans le lobby ni le parkour.
         return zone == Zone.SKYBLOCK || zone == Zone.SKYHUB || zone == Zone.PARCELLES && !name.equals("echange");
      }

      return !SURVIE_SEULEMENT.contains(name);
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
      Player p = e.getPlayer();
      if (!this.pl.auth().isLogged(p)) {
         return;
      }

      String raw = e.getMessage().substring(1).split(" ", 2)[0];
      int colon = raw.indexOf(':');
      String name = colon >= 0 ? raw.substring(colon + 1) : raw;
      Zone zone = this.zoneOf(p);
      if (!this.allowed(zone, name)) {
         e.setCancelled(true);
         Msg.err(p, "<white>/" + name + "</white> ne marche pas dans <white>" + zone.shortLabel() + "</white>.");
         Msg.info(p, "Reprends la boussole pour revenir choisir un monde.");
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onWeather(WeatherChangeEvent e) {
      Zone zone = this.zoneOf(e.getWorld());
      if (zone != Zone.SURVIE && e.toWeatherState()) {
         e.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      Player p = e.getPlayer();
      if (this.pl.auth().isLogged(p)) {
         try {
            this.pl.inventories().save(p);
         } catch (Throwable t) {
         }
      }

      this.pl.inventories().forget(p);
   }
}
