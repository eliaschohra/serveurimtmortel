package fr.bdeimt.serveur;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class BDEIMT extends JavaPlugin {
   private static BDEIMT instance;
   private State state;
   private DataStore data;
   private Ranks ranks;
   private Auth auth;
   private Lobby lobby;
   private Fly fly;
   private Votes votes;
   private Kits kits;
   private Graves graves;
   private Teleports teleports;
   private Social social;
   private Trade trade;
   private Moderation moderation;
   private Announcer announcer;
   private Dragon dragon;
   private Motd motd;
   private Aura aura;
   private HealthBars healthBars;
   private Panel panel;
   private Fun fun;
   private Maintenance maintenance;
   private Worlds worlds;
   private Inventories inventories;
   private Hub hub;
   private Tab tab;
   private Lists lists;
   private Npcs npcs;
   private Parkour parkour;
   private Plots plots;
   private Skyblock skyblock;
   private Skins skins;
   private Sounds sounds;
   /** Les autres jars BDEIMT trouves dans plugins/, s'il y en a. */
   private final java.util.List<String> doubles = new java.util.ArrayList<>();
   private SkyHub skyhub;

   public static BDEIMT get() {
      return instance;
   }

   public State state() {
      return this.state;
   }

   public DataStore data() {
      return this.data;
   }

   public Ranks ranks() {
      return this.ranks;
   }

   public Auth auth() {
      return this.auth;
   }

   public Lobby lobby() {
      return this.lobby;
   }

   public Fly fly() {
      return this.fly;
   }

   public Votes votes() {
      return this.votes;
   }

   public Graves graves() {
      return this.graves;
   }

   public Announcer announcer() {
      return this.announcer;
   }

   public Dragon dragon() {
      return this.dragon;
   }

   public Motd motd() {
      return this.motd;
   }

   public Fun fun() {
      return this.fun;
   }

   public Kits kits() {
      return this.kits;
   }

   public Panel panel() {
      return this.panel;
   }

   public Maintenance maintenance() {
      return this.maintenance;
   }

   public Worlds worlds() {
      return this.worlds;
   }

   public Inventories inventories() {
      return this.inventories;
   }

   public Hub hub() {
      return this.hub;
   }

   public Tab tab() {
      return this.tab;
   }

   public Lists lists() {
      return this.lists;
   }

   public Npcs npcs() {
      return this.npcs;
   }

   public Parkour parkour() {
      return this.parkour;
   }

   public Plots plots() {
      return this.plots;
   }

   public Skyblock skyblock() {
      return this.skyblock;
   }

   public Skins skins() {
      return this.skins;
   }

   public Sounds sounds() {
      return this.sounds;
   }

   /** Le nom du fichier .jar reellement charge par le serveur. */
   public String getFileName() {
      try {
         return this.getFile().getName();
      } catch (Throwable t) {
         return "?";
      }
   }

   /** Les jars en trop, a signaler au staff tant qu'ils sont la. */
   public java.util.List<String> doublesJar() {
      return this.doubles;
   }

   public SkyHub skyhub() {
      return this.skyhub;
   }

   public String adminName() {
      return this.getConfig().getString("admin", "curlybrownhair");
   }

   public void onEnable() {
      instance = this;
      this.checkJars();
      this.saveDefaultConfig();
      // Avant tout le reste : les modules lisent ces reglages des leur creation.
      this.ensureDefaults();
      File var1 = new File(this.getDataFolder(), "images");
      if (!var1.exists() && !var1.mkdirs()) {
         this.getLogger().warning("Dossier images impossible a creer");
      }

      if (Bukkit.getPluginManager().getPlugin("AuthMe") != null) {
         this.getLogger().severe("AuthMe est encore installe : supprime AuthMe*.jar du dossier plugins (BDEIMT gere deja /register et /login).");
      }

      this.state = new State(this.getDataFolder());
      this.state.load();
      this.data = new DataStore(this.getDataFolder());
      this.data.loadAll();
      this.ranks = new Ranks(this);
      this.ranks.setupTeams();
      this.auth = new Auth(this);
      this.auth.load();
      this.auth.prepareAdminCode();
      this.lobby = new Lobby(this);
      this.lobby.init();
      this.inventories = new Inventories(this);
      this.worlds = new Worlds(this);
      this.worlds.init();
      this.hub = new Hub(this);
      this.hub.loadPortals();
      this.tab = new Tab(this);
      this.lists = new Lists(this);
      this.lists.load();
      this.npcs = new Npcs(this);
      this.skins = new Skins(this);
      this.sounds = new Sounds(this);
      this.parkour = new Parkour(this);
      this.parkour.load();
      this.plots = new Plots(this);
      this.skyblock = new Skyblock(this);
      this.skyhub = new SkyHub(this);
      this.fly = new Fly(this);
      this.votes = new Votes(this);
      this.kits = new Kits(this);
      this.graves = new Graves(this);
      this.graves.load();
      this.teleports = new Teleports(this);
      this.social = new Social(this);
      this.trade = new Trade(this);
      this.moderation = new Moderation(this);
      this.announcer = new Announcer(this);
      this.dragon = new Dragon(this);
      this.dragon.init();
      this.motd = new Motd(this);
      this.motd.loadIcon();
      this.aura = new Aura(this);
      this.healthBars = new HealthBars(this);
      this.healthBars.setupPlayers();
      this.panel = new Panel(this);
      this.panel.init();
      this.fun = new Fun(this);
      this.fun.init();
      this.maintenance = new Maintenance(this);
      this.worlds.registerSpawn(Zone.SURVIE, player -> {
         PlayerData data = this.data.get(player);
         Location back = Util.loc(data.returnLoc);
         return back != null && this.worlds.zoneOf(back.getWorld()) == Zone.SURVIE ? back : this.survivalSpawn();
      });
      this.hub.decorate();
      this.lobby.decorateHub();
      this.npcs.spawnAll();
      this.skins.syncAll();
      this.parkour.refreshHologram();
      this.plots.init();
      this.skyblock.init();
      this.skyhub.init();

      for (Listener var5 : new Listener[]{
         this.auth,
         this.ranks,
         this.lobby,
         this.kits,
         this.graves,
         this.teleports,
         this.social,
         this.trade,
         this.moderation,
         this.fly,
         this.motd,
         this.aura,
         this.healthBars,
         this.panel,
         this.fun,
         this.maintenance,
         this.worlds,
         this.hub,
         this.lists,
         this.npcs,
         this.parkour,
         this.plots,
         this.skyblock,
         this.skyhub,
         new FunItems(),
         new Menu.Listen()
      }) {
         this.getServer().getPluginManager().registerEvents(var5, this);
      }

      this.cmd("register", this.auth, null);
      this.cmd("login", this.auth, null);
      this.cmd("changemdp", this.auth, null);
      this.cmd("vote", this.votes, null);
      this.cmd("probavote", this.votes, null);
      this.cmd("kit", this.kits, this.kits);
      this.cmd("kits", this.kits, this.kits);
      this.cmd("guide", this.announcer, null);
      this.cmd("tombes", this.graves, null);
      this.cmd("aura", this.aura, null);
      this.cmd("panel", this.panel, this.panel);
      this.cmd("maudire", this.panel, this.panel);

      for (String var25 : new String[]{"ghoule", "traq", "oniris", "sugardaddimt", "wei", "tunnel"}) {
         this.cmd(var25, this.fun, null);
      }

      this.cmd("listes", this.kits, null);
      this.cmd("liste", this.lists, this.lists);
      this.cmd("hub", this.hub, null);
      this.cmd("musique", this.hub, null);
      this.cmd("parkour", this.parkour, this.parkour);
      this.cmd("parcelle", this.plots, this.plots);
      this.cmd("ile", this.skyblock, this.skyblock);
      this.cmd("marche", this.skyhub, null);

      for (String var26 : new String[]{"tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle", "sethome", "home", "delhome", "homes", "spawn"}) {
         this.cmd(var26, this.teleports, this.teleports);
      }

      for (String var27 : new String[]{"msg", "r", "ignore"}) {
         this.cmd(var27, this.social, this.social);
      }

      this.cmd("echange", this.trade, this.trade);

      for (String var28 : new String[]{"mute", "unmute", "tempban", "ban", "unban", "kick", "fly"}) {
         this.cmd(var28, this.moderation, this.moderation);
      }

      AdminCommand var14 = new AdminCommand(this);
      this.cmd("imt", var14, var14);
      this.cmd("maintenance", this.maintenance, this.maintenance);
      OpCommands var19 = new OpCommands(this, var14);
      this.cmd("moderateur", var19, var19);
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         this.auth.tick();
         this.fly.tick();
         this.graves.tick();
         this.tab.tick();
         this.hub.musicTick();
         this.parkour.tick();
         this.plots.tick();
      }, 20L, 20L);
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         this.dragon.check();
         this.data.saveDirty();
         this.votes.updateSidebar();
      }, 1200L, 1200L);
      // Le ble repousse toutes les 20 minutes, les betes toutes les 30,
      // et les filons de la mine se reforment chaque heure.
      Bukkit.getScheduler().runTaskTimer(this, this.npcs::watch, 200L, 200L);
      Bukkit.getScheduler().runTaskTimer(this, this.hub::particles, 40L, 3L);
      Bukkit.getScheduler().runTaskTimer(this, this.hub::refreshTitles, 100L, 100L);
      Bukkit.getScheduler().runTaskTimer(this, this.skyhub::regrowFarm, 24000L, 24000L);
      Bukkit.getScheduler().runTaskTimer(this, this.skyhub::restock, 36000L, 36000L);
      Bukkit.getScheduler().runTaskTimer(this, this.skyhub::regrowVeins, 72000L, 72000L);
      long var24 = Math.max(1L, this.getConfig().getLong("astuces-toutes-les-minutes", 20L)) * 1200L;
      long var6 = Math.max(1L, this.getConfig().getLong("rappel-guide-toutes-les-minutes", 120L)) * 1200L;
      Bukkit.getScheduler().runTaskTimer(this, this.announcer::tip, var24, var24);
      Bukkit.getScheduler().runTaskTimer(this, this.announcer::guideReminder, var6, var6);
      this.votes.updateSidebar();
      this.fixServerSettings();
      new ThirdParty(this).run();

      for (Player var9 : Bukkit.getOnlinePlayers()) {
         var9.kick(Msg.mm("<#4FC3FF>Le serveur vient d'être mis à jour.</#4FC3FF>\n<gray>Reconnecte-toi !"));
      }

      this.getLogger().info("==============================================================");
      this.getLogger().info(" BDEIMT version " + this.getPluginMeta().getVersion() + " charge depuis " + this.getFile().getName());
      this.getLogger().info(" Mondes ouverts :");

      for (Zone var30 : Zone.values()) {
         if (var30.isGameMode()) {
            this.getLogger()
               .info(
                  "   " + (this.worlds.available(var30) ? "OUI" : "non")
                     + "  " + var30.shortLabel()
                     + (this.worlds.enabled(var30) ? "" : " (ferme dans config.yml)")
               );
         }
      }

      if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
         this.getLogger().info(" Multiverse-Core detecte : il charge ses mondes avant nous.");
      }

      this.getLogger().info("==============================================================");
   }

   public void onDisable() {
      try {
         if (this.npcs != null) {
            this.npcs.clearAll();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.inventories != null) {
            this.inventories.saveAll();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.lists != null) {
            this.lists.save();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.parkour != null) {
            this.parkour.save();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.plots != null) {
            this.plots.save();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.skyblock != null) {
            this.skyblock.save();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.skyhub != null) {
            this.skyhub.clearAll();
         }
      } catch (Throwable t) {
      }

      try {
         if (this.trade != null) {
            this.trade.cancelAll();
         }
      } catch (Throwable var6) {
      }

      try {
         if (this.healthBars != null) {
            this.healthBars.clearAll();
         }
      } catch (Throwable var5) {
      }

      try {
         if (this.graves != null) {
            this.graves.save();
            this.graves.hideAll();
         }
      } catch (Throwable var4) {
      }

      try {
         if (this.data != null) {
            this.data.saveAll();
         }
      } catch (Throwable var3) {
      }

      try {
         if (this.state != null) {
            this.state.save();
         }
      } catch (Throwable var2) {
      }
   }

   private void cmd(String var1, CommandExecutor var2, TabCompleter var3) {
      PluginCommand var4 = this.getCommand(var1);
      if (var4 == null) {
         this.getLogger().warning("Commande absente de plugin.yml : " + var1);
      } else {
         var4.setExecutor(var2);
         if (var3 != null) {
            var4.setTabCompleter(var3);
         }
      }
   }

   public Location survivalSpawn() {
      World var1 = (World)Bukkit.getWorlds().get(0);
      Location var2 = var1.getSpawnLocation();
      Block var3 = var2.getBlock();
      if (var3.getType().isSolid() || var3.getRelative(BlockFace.UP).getType().isSolid() || !var3.getRelative(BlockFace.DOWN).getType().isSolid()) {
         var3 = var1.getHighestBlockAt(var2).getRelative(BlockFace.UP);
      }

      return new Location(var1, var3.getX() + 0.5, var3.getY(), var3.getZ() + 0.5, var2.getYaw(), 0.0F);
   }

   /**
    * Verifie qu'il n'y a qu'un seul jar du plugin dans {@code plugins/}.
    *
    * <p>C'est le piege le plus couteux de tous : deposer la nouvelle version
    * sous un autre nom — BDEIMT-2.jar, BDEIMTv4.jar, BDEIMT (1).jar — sans
    * retirer l'ancienne. Les deux portent le meme nom de plugin, le serveur
    * n'en charge qu'un, souvent le plus ancien, et refuse l'autre. Tout ce
    * qu'on vient de livrer semble alors n'avoir aucun effet.
    */
   private void checkJars() {
      try {
         File var1 = this.getFile();
         File[] var2 = var1.getParentFile().listFiles();

         if (var2 == null) {
            return;
         }

         for (File var6 : var2) {
            String var7 = var6.getName();

            if (var6.isFile()
               && var7.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")
               && var7.toLowerCase(java.util.Locale.ROOT).contains("bdeimt")
               && !var6.getAbsolutePath().equals(var1.getAbsolutePath())) {
               this.doubles.add(var7);
            }
         }

         if (!this.doubles.isEmpty()) {
            this.getLogger().severe("==============================================================");
            this.getLogger().severe(" ATTENTION : plusieurs jars du plugin dans le dossier plugins.");
            this.getLogger().severe(" Celui qui tourne : " + var1.getName() + " (version " + this.getPluginMeta().getVersion() + ")");

            for (String var9 : this.doubles) {
               this.getLogger().severe(" A SUPPRIMER      : " + var9);
            }

            this.getLogger().severe(" Garde un seul fichier, puis /stop. Sinon le serveur peut");
            this.getLogger().severe(" continuer a charger l'ancienne version sans le dire.");
            this.getLogger().severe("==============================================================");
         }
      } catch (Throwable var10) {
      }
   }

   /**
    * Ajoute a config.yml les reglages apparus depuis sa creation.
    *
    * <p>Le fichier du serveur n'est jamais remplace — le pseudo de l'admin et
    * les reglages d'Elias y sont. On se contente donc d'y ecrire les cles qui
    * manquent, sans jamais toucher a celles qui existent deja.
    */
   private void ensureDefaults() {
      Object[][] var1 = new Object[][]{
         {"modes.survie", true},
         {"modes.parkour", true},
         {"modes.skyblock", false},
         {"modes.parcelles", true},
         {"connexion-dans-le-hub", true},
         {"mondes.hub.spawn", "bdeimt_hub;191.5;-44.0;142.5;0.0;0.0"},
         {"mondes.parkour.spawn", "bdeimt_parkour;0.5;43.0;-0.5;1.5;0.3"},
         {"pnj.mobutu.aussi-pour-l-admin", true},
         {"pnj.zaza.modele", "classic"},
         {"pnj.mobutu.modele", "classic"},
         {"mineskin-cle", ""}
      };
      boolean var2 = false;

      for (Object[] var6 : var1) {
         if (!this.getConfig().contains((String)var6[0])) {
            this.getConfig().set((String)var6[0], var6[1]);
            var2 = true;
         }
      }

      if (var2) {
         this.saveConfig();
         this.getLogger().info("Nouveaux reglages ajoutes a config.yml.");
      }
   }

   private void fixServerSettings() {
      boolean var1 = false;

      try {
         File var2 = new File(this.getServer().getWorldContainer(), "spigot.yml");
         if (var2.exists()) {
            YamlConfiguration var3 = YamlConfiguration.loadConfiguration(var2);
            if (var3.getBoolean("commands.log", true)) {
               var3.set("commands.log", false);
               var3.save(var2);
               var1 = true;
            }
         }
      } catch (Exception var12) {
         this.getLogger().warning("spigot.yml non modifie : " + var12.getMessage());
      }

      try {
         File var13 = new File(this.getServer().getWorldContainer(), "server.properties");
         if (var13.exists()) {
            Properties var14 = new Properties();
            FileReader var4 = new FileReader(var13, StandardCharsets.UTF_8);

            try {
               var14.load(var4);
            } catch (Throwable var10) {
               try {
                  var4.close();
               } catch (Throwable var8) {
                  var10.addSuppressed(var8);
               }

               throw var10;
            }

            var4.close();
            if (!"true".equalsIgnoreCase(var14.getProperty("allow-flight"))) {
               var14.setProperty("allow-flight", "true");

               try (FileOutputStream var15 = new FileOutputStream(var13)) {
                  var14.store(var15, "Minecraft server properties");
               }

               var1 = true;
            }
         }
      } catch (Exception var11) {
         this.getLogger().warning("server.properties non modifie : " + var11.getMessage());
      }

      if (var1) {
         this.getLogger().warning("==============================================================");
         this.getLogger().warning(" Reglages ajustes (logs sans mots de passe, allow-flight).");
         this.getLogger().warning(" Redemarre le serveur une fois de plus (/stop) pour les appliquer.");
         this.getLogger().warning("==============================================================");
      }
   }
}
