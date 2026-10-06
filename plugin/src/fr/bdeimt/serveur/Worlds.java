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

   private final BDEIMT pl;
   private final Map<Zone, BuildRule> buildRules = new EnumMap<>(Zone.class);
   private final Map<Zone, Function<Player, Location>> spawns = new EnumMap<>(Zone.class);

   public Worlds(BDEIMT pl) {
      this.pl = pl;
   }

   // ---------------------------------------------------------------- mondes

   /** Charge les mondes deposes par FileZilla (hub et parkour). */
   public void init() {
      this.load(Zone.HUB);
      this.load(Zone.PARKOUR);
   }

   private void load(Zone zone) {
      if (Bukkit.getWorld(zone.world) != null) {
         this.tune(zone);
         return;
      }

      File folder = new File(Bukkit.getWorldContainer(), zone.world);
      if (!new File(folder, "level.dat").exists()) {
         this.pl.getLogger().warning(
            "Monde absent : " + zone.world + " — depose le dossier dans " + Bukkit.getWorldContainer().getPath()
               + " (ce mode de jeu s'affichera « bientot disponible »)."
         );
         return;
      }

      try {
         World w = Bukkit.createWorld(new WorldCreator(zone.world));
         if (w == null) {
            this.pl.getLogger().warning("Monde " + zone.world + " illisible.");
         } else {
            this.pl.getLogger().info("Monde charge : " + zone.world);
            this.tune(zone);
         }
      } catch (Throwable t) {
         this.pl.getLogger().warning("Monde " + zone.world + " : " + t.getMessage());
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
      rule(w, true, "keepInventory", "keep_inventory");
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

   /** Un mode de jeu est disponible quand son monde est bien la. */
   public boolean available(Zone zone) {
      return this.world(zone) != null;
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

      if (from.group.equals(zone.group)) {
         return true;
      }

      Msg.raw(p, "<dark_gray>» <gray>Tu es maintenant dans " + zone.color + zone.shortLabel() + "</gray>.");
      Util.sound(p, "entity.enderman.teleport", 0.6F, 1.4F);
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

      if (zone == Zone.SURVIE) {
         this.pl.fly().onLogin(p);
         this.pl.graves().onLogin(p);
         this.pl.votes().onLogin(p);
      }

      this.pl.tab().refresh(p);
   }

   // ------------------------------------------------------------ protection

   private boolean bypass(Player p) {
      return p.isOp() && p.getGameMode() == GameMode.CREATIVE && this.pl.ranks().isAdmin(p);
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
         this.denied(e.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent e) {
      if (!this.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
         e.setCancelled(true);
         this.denied(e.getPlayer());
      }
   }

   private void denied(Player p) {
      Zone zone = this.zoneOf(p);
      if (zone.build) {
         Msg.err(p, "Tu ne peux rien modifier ici.");
      } else {
         Msg.err(p, "On ne casse rien dans " + zone.color + zone.shortLabel() + "</gray>.");
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
               Msg.err(attacker, "On ne se bat pas dans " + zone.color + zone.shortLabel() + "</gray>.");
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
      "panel", "mute", "unmute", "tempban", "ban", "unban", "kick", "imt", "maintenance", "moderateur",
      "maudire", "help", "?"
   );
   /** Ce qui n'a de sens qu'en survie : les delires, les votes, les tombes. */
   private static final java.util.Set<String> SURVIE_SEULEMENT = java.util.Set.of(
      "vote", "probavote", "probavotes", "kit", "kits", "listes", "liste", "bande", "team", "tombes", "tombe",
      "aura", "fly", "ghoule", "traq", "oniris", "sugardaddimt", "wei", "tunnel",
      "sethome", "home", "delhome", "homes"
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

      if (ENTRE_JOUEURS.contains(name)) {
         // Pas d'echange dans les parcelles, et rien dans le lobby ni le parkour.
         return zone == Zone.SKYBLOCK || zone == Zone.SKYHUB || zone == Zone.PARCELLES && !name.equals("echange");
      }

      return !SURVIE_SEULEMENT.contains(name);
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
      Player p = e.getPlayer();
      if (!this.pl.auth().isLogged(p) || this.bypass(p)) {
         return;
      }

      String raw = e.getMessage().substring(1).split(" ", 2)[0];
      int colon = raw.indexOf(':');
      String name = colon >= 0 ? raw.substring(colon + 1) : raw;
      Zone zone = this.zoneOf(p);
      if (!this.allowed(zone, name)) {
         e.setCancelled(true);
         Msg.err(p, "<white>/" + name + "</white> ne marche pas dans " + zone.color + zone.shortLabel() + "</gray>.");
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
