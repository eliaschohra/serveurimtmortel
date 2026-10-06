package fr.bdeimt.serveur;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Panel implements CommandExecutor, TabCompleter, Listener {
   private final BDEIMT pl;
   private final Map<UUID, Long> sessionStart = new HashMap<>();
   private final Set<Menu> open = new HashSet<>();
   private volatile String publicIp = "?";

   public Panel(BDEIMT var1) {
      this.pl = var1;
   }

   public void init() {
      for (Player var2 : Bukkit.getOnlinePlayers()) {
         this.sessionStart.put(var2.getUniqueId(), System.currentTimeMillis());
      }

      Bukkit.getScheduler()
         .runTaskAsynchronously(
            this.pl,
            () -> {
               try {
                  HttpClient var1 = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
                  String var2x = var1.send(
                        HttpRequest.newBuilder(URI.create("https://api.ipify.org")).timeout(Duration.ofSeconds(5L)).build(), BodyHandlers.ofString()
                     )
                     .body()
                     .trim();
                  if (var2x.matches("[0-9a-fA-F.:]+")) {
                     this.publicIp = var2x;
                  }
               } catch (Exception var3) {
               }
            }
         );
      Bukkit.getScheduler().runTaskTimer(this.pl, () -> {
         for (Menu var2x : new ArrayList<>(this.open)) {
            if (var2x.getInventory().getViewers().isEmpty()) {
               this.open.remove(var2x);
            } else if (var2x instanceof Panel.Main var3) {
               var3.refresh();
            } else if (var2x instanceof Panel.One var4) {
               var4.refresh();
            }
         }
      }, 40L, 40L);
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      this.sessionStart.put(var1.getPlayer().getUniqueId(), System.currentTimeMillis());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.sessionStart.remove(var1.getPlayer().getUniqueId());
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var2.getName().equals("maudire")) {
         if (var1 instanceof Player var6 && !this.pl.ranks().isAdmin(var6)) {
            Msg.err(var6, "Seul Mobutu peut maudire.");
            return true;
         } else if (var4.length < 1) {
            Msg.err(var1, "Utilisation : <white>/maudire ‹joueur›</white>");
            return true;
         } else {
            Player var7 = Bukkit.getPlayerExact(var4[0]);
            if (var7 != null && this.pl.auth().isLogged(var7)) {
               this.curse(var7);
               return true;
            } else {
               Msg.err(var1, "Joueur introuvable (il doit être connecté).");
               return true;
            }
         }
      } else if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         if (!this.pl.ranks().isStaff(var5)) {
            Msg.err(var5, "Réservé au staff.");
            return true;
         } else {
            this.openMain(var5, 0);
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var2.getName().equals("maudire") && var4.length == 1) {
         for (Player var7 : Bukkit.getOnlinePlayers()) {
            if (this.pl.auth().isLogged(var7)) {
               var5.add(var7.getName());
            }
         }

         String var8 = var4[0].toLowerCase(Locale.ROOT);
         var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var8));
      }

      return var5;
   }

   private void openMain(Player var1, int var2) {
      Panel.Main var3 = new Panel.Main(var1, var2);
      this.open.add(var3);
      var3.open(var1);
   }

   private void openOne(Player var1, UUID var2) {
      Panel.One var3 = new Panel.One(var1, var2);
      this.open.add(var3);
      var3.open(var1);
   }

   private static ItemStack info(Material var0, String var1, List<String> var2) {
      ItemStack var3 = Util.item(var0, 1, var1);
      return Util.lore(var3, var2);
   }

   private ItemStack head(Player var1) {
      ItemStack var2 = new ItemStack(Material.PLAYER_HEAD);
      if (var2.getItemMeta() instanceof SkullMeta var3) {
         try {
            var3.setPlayerProfile(var1.getPlayerProfile());
         } catch (Throwable var5) {
            var3.setOwningPlayer(var1);
         }

         var2.setItemMeta(var3);
      }

      return var2;
   }

   private static String mb(long var0) {
      return var0 / 1048576L + " Mo";
   }

   private static String gb(long var0) {
      return String.format(Locale.FRANCE, "%.1f Go", var0 / 1.0737418E9F);
   }

   private static String tpsColor(double var0) {
      return var0 >= 19.0 ? "<#55FF88>" : (var0 >= 15.0 ? "<#FFD25E>" : "<#FF5555>");
   }

   private List<String> playerLore(Player var1, boolean var2) {
      PlayerData var3 = this.pl.data().get(var1);
      ArrayList var4 = new ArrayList();
      long var5 = this.sessionStart.getOrDefault(var1.getUniqueId(), System.currentTimeMillis());
      int var7 = this.pl.data().rankOf(var1.getUniqueId());
      int var8 = this.pl.data().auraRankOf(var1.getUniqueId());
      long var9 = 0L;
      int var11 = 0;

      try {
         var9 = var1.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L;
      } catch (Throwable var14) {
      }

      try {
         var11 = var1.getStatistic(Statistic.DEATHS);
      } catch (Throwable var13) {
      }

      var4.add("<gray>Rang : " + this.pl.ranks().prefix(var1));
      var4.add("<gray>Statut : " + (this.pl.auth().isLogged(var1) ? "<#55FF88>connecté" : "<#FFB020>au lobby (pas encore identifié)"));
      var4.add(" ");
      var4.add("<gray>Session : <white>" + Util.duration(System.currentTimeMillis() - var5));
      var4.add("<gray>Temps de jeu total : <white>" + Util.duration(var9));
      var4.add("<gray>Votes : <#FFC93C>" + var3.votes + (var7 > 0 ? " <dark_gray>(n°" + var7 + ")" : ""));
      var4.add("<gray>Aura : <#C77DFF>" + Aura.fmt(var3.aura) + (var8 > 0 ? " <dark_gray>(n°" + var8 + ")" : ""));
      var4.add("<gray>Morts depuis le début : <#FF5555>" + var11);
      var4.add(" ");
      var4.add("<gray>Vie : <white>" + (int)Math.ceil(var1.getHealth()) + "</white> ❤  <gray>Faim : <white>" + var1.getFoodLevel());
      var4.add("<gray>Monde : <white>" + Util.worldName(var1.getWorld()) + " <dark_gray>(" + Util.coords(var1.getLocation()) + ")");
      var4.add("<gray>Mode : <white>" + var1.getGameMode().name().toLowerCase(Locale.ROOT) + "  <gray>Ping : <white>" + var1.getPing() + " ms");
      if (var3.isMuted()) {
         var4.add("<#FF5555>Muet");
      }

      if (var2 && var1.getAddress() != null) {
         var4.add("<dark_gray>IP : " + var1.getAddress().getAddress().getHostAddress());
      }

      return var4;
   }

   private int countAccounts() {
      int var1 = 0;

      for (PlayerData var3 : this.pl.data().all()) {
         if (this.pl.auth().hasAccount(var3.name)) {
            var1++;
         }
      }

      return var1;
   }

   public void curse(Player var1) {
      Location var2 = var1.getLocation();
      var1.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 300, 0, false, false, false));
      var1.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 0, false, false, false));
      var1.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 2, false, false, false));

      try {
         var1.showElderGuardian(false);
      } catch (Throwable var6) {
      }

      Util.sound(var1, "ambient.cave", 1.0F, 0.5F);
      Util.sound(var1, "entity.wither.spawn", 1.0F, 0.6F);
      var1.showTitle(
         Title.title(
            Msg.mm("<#8B0000><bold>☠ MAUDIT PAR MOBUTU ☠</bold></#8B0000>"),
            Msg.mm("<#FF5555><bold>Ton âme lui appartient désormais.</bold></#FF5555>"),
            Times.times(Duration.ofMillis(200L), Duration.ofSeconds(5L), Duration.ofSeconds(1L))
         )
      );
      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (var1.isOnline()) {
            var1.getWorld().strikeLightningEffect(var1.getLocation());
            Util.sound(var1, "entity.ender_dragon.growl", 1.0F, 0.5F);
            var1.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 2, false, false, false));
            var1.sendActionBar(Msg.mm("<#8B0000><bold>Mobutu te soulève comme une plume...</bold>"));
         }
      }, 40L);
      int[] var3 = new int[]{0};
      Bukkit.getScheduler().runTaskTimer(this.pl, var2x -> {
         if (var1.isOnline() && var3[0]++ <= 30) {
            try {
               var1.getWorld().spawnParticle(Particle.SOUL, var1.getLocation().add(0.0, 1.0, 0.0), 12, 0.5, 0.8, 0.5, 0.02);
               var1.getWorld().spawnParticle(Particle.LARGE_SMOKE, var1.getLocation().add(0.0, 1.0, 0.0), 8, 0.4, 0.6, 0.4, 0.01);
            } catch (Throwable var4) {
            }
         } else {
            var2x.cancel();
         }
      }, 40L, 4L);
      Bukkit.getScheduler()
         .runTaskLater(
            this.pl,
            () -> {
               if (var1.isOnline()) {
                  var1.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0, false, false, false));
                  var1.setFallDistance(0.0F);
                  if (Util.give(var1, List.of(this.cursedSkull(var1)))) {
                     Msg.info(var1, "Le crâne est tombé à tes pieds.");
                  }

                  Util.sound(var1, "entity.warden.sonic_boom", 1.0F, 0.7F);
                  var1.showTitle(
                     Title.title(
                        Msg.mm("<#8B0000><bold>TU AS ÉTÉ MAUDIT</bold></#8B0000>"),
                        Msg.mm("<gray>Porte ce crâne et souviens-toi de qui règne ici.</gray>"),
                        Times.times(Duration.ZERO, Duration.ofSeconds(4L), Duration.ofSeconds(1L))
                     )
                  );
               }
            },
            120L
         );
      Msg.broadcast(
         Msg.mm("<#8B0000><bold>☠</bold></#8B0000> ")
            .append(this.pl.ranks().display(var1))
            .append(
               Msg.mm(" <#FF5555>a été <bold>maudit par Mobutu</bold>. Que son nom soit murmuré avec crainte.</#FF5555> <#8B0000><bold>☠</bold></#8B0000>")
            )
      );

      for (Player var5 : Bukkit.getOnlinePlayers()) {
         if (!var5.equals(var1) && this.pl.auth().isLogged(var5)) {
            Util.sound(var5, "entity.lightning_bolt.thunder", 0.6F, 0.7F);
         }
      }

      this.pl.getLogger().info(var1.getName() + " a ete maudit par Mobutu.");
      if (var2.getWorld() != null) {
         var2.getWorld().strikeLightningEffect(var2.clone().add(2.0, 0.0, 2.0));
      }
   }

   private ItemStack cursedSkull(Player var1) {
      ItemStack var2 = new ItemStack(Material.WITHER_SKELETON_SKULL);

      try {
         for (Enchantment var4 : Registry.ENCHANTMENT) {
            var2.addUnsafeEnchantment(var4, var4.getMaxLevel());
         }
      } catch (Throwable var7) {
      }

      ItemMeta var8 = var2.getItemMeta();
      if (var8 != null) {
         var8.displayName(Util.noItalic(Msg.mm("<#8B0000><bold>Crâne maudit de Mobutu</bold></#8B0000>")));
         ArrayList var9 = new ArrayList();

         for (String var6 : List.of(
            "<gray>Arraché à l'âme de <white>" + var1.getName() + "</white><gray>.",
            "<gray>Il pèse plus lourd que tous tes péchés.",
            " ",
            "<#FF5555>« Nul n'échappe au regard du Mobutu. »"
         )) {
            var9.add(Util.noItalic(Msg.mm(var6)));
         }

         var8.lore(var9);
         var2.setItemMeta(var8);
      }

      return var2;
   }

   private final class Main extends Menu {
      private static final int FIRST = 18;
      private static final int PER_PAGE = 27;
      private final Player viewer;
      private final int page;
      private final Map<Integer, UUID> slots = new HashMap<>();

      Main(Player nullx, int nullxx) {
         super(6, Msg.mm("<#FF3B3B><bold>Panneau du Mobutu</bold></#FF3B3B>"));
         this.viewer = nullx;
         this.page = nullxx;
         this.refresh();
      }

      void refresh() {
         this.inv.clear();
         this.slots.clear();
         Runtime var1 = Runtime.getRuntime();
         long var2 = var1.totalMemory() - var1.freeMemory();
         double[] var4 = new double[]{20.0, 20.0, 20.0};

         try {
            var4 = Bukkit.getTPS();
         } catch (Throwable var28) {
         }

         double var5 = 0.0;

         try {
            var5 = Bukkit.getAverageTickTime();
         } catch (Throwable var27) {
         }

         long var7 = ManagementFactory.getRuntimeMXBean().getUptime();
         double var9 = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
         int var11 = var1.availableProcessors();
         int var12 = 0;
         int var13 = 0;

         for (Player var15 : Bukkit.getOnlinePlayers()) {
            if (Panel.this.pl.auth().isLogged(var15)) {
               var12++;
            } else {
               var13++;
            }
         }

         int var29 = 0;
         int var30 = 0;

         for (World var17 : Bukkit.getWorlds()) {
            var29 += var17.getLoadedChunks().length;
            var30 += var17.getEntities().size();
         }

         File var31 = Bukkit.getWorldContainer();
         boolean var32 = var4[0] >= 18.0 && var2 < var1.maxMemory() * 0.9;
         this.inv
            .setItem(
               0,
               Panel.info(
                  var32 ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE,
                  var32 ? "<#55FF88><bold>Serveur en forme</bold>" : "<#FFB020><bold>Serveur sous pression</bold>",
                  List.of(
                     "<gray>Version : <white>" + Bukkit.getMinecraftVersion(),
                     "<gray>Paper : <white>" + Bukkit.getVersion(),
                     "<gray>Plugins : <white>" + Bukkit.getPluginManager().getPlugins().length
                  )
               )
            );
         this.inv
            .setItem(
               1,
               Panel.info(
                  Material.PLAYER_HEAD,
                  "<#4FC3FF><bold>Joueurs : " + var12 + " / " + Bukkit.getMaxPlayers() + "</bold>",
                  List.of(
                     "<gray>Identifiés : <white>" + var12,
                     "<gray>Au lobby : <white>" + var13,
                     "<gray>Comptes créés : <white>" + Panel.this.countAccounts(),
                     "<gray>Joueurs déjà venus : <white>" + Panel.this.pl.data().all().size()
                  )
               )
            );
         this.inv
            .setItem(
               2,
               Panel.info(
                  Material.CLOCK,
                  "<#FFC93C><bold>En ligne depuis " + Util.duration(var7) + "</bold>",
                  List.of("<gray>Prochain dragon dans : <white>" + Util.duration(Panel.this.pl.dragon().next() - System.currentTimeMillis()))
               )
            );
         this.inv
            .setItem(
               3,
               Panel.info(
                  Material.REDSTONE,
                  "<#FF5555><bold>RAM : " + Panel.mb(var2) + " / " + Panel.mb(var1.maxMemory()) + "</bold>",
                  List.of(
                     "<gray>Utilisée : <white>" + (int)(100.0 * var2 / var1.maxMemory()) + " %",
                     "<gray>Réservée par Java : <white>" + Panel.mb(var1.totalMemory())
                  )
               )
            );
         this.inv
            .setItem(
               4,
               Panel.info(
                  Material.COMPARATOR,
                  Panel.tpsColor(var4[0]) + "<bold>TPS : " + String.format(Locale.FRANCE, "%.1f", Math.min(20.0, var4[0])) + "</bold>",
                  List.of(
                     "<gray>1 min / 5 min / 15 min : <white>"
                        + String.format(Locale.FRANCE, "%.1f / %.1f / %.1f", Math.min(20.0, var4[0]), Math.min(20.0, var4[1]), Math.min(20.0, var4[2])),
                     "<gray>Temps par tick : <white>" + String.format(Locale.FRANCE, "%.1f ms", var5) + " <dark_gray>(max conseillé 50)",
                     "<gray>Charge CPU : <white>" + (var9 < 0.0 ? "?" : String.format(Locale.FRANCE, "%.2f", var9)) + " <dark_gray>sur " + var11 + " cœurs"
                  )
               )
            );
         this.inv
            .setItem(
               5,
               Panel.info(
                  Material.COMPASS,
                  "<#4FC3FF><bold>Adresse : " + Panel.this.publicIp + "</bold>",
                  List.of("<gray>Port : <white>" + Bukkit.getPort(), "<gray>À donner aux joueurs : <white>" + Panel.this.publicIp)
               )
            );
         this.inv
            .setItem(
               6,
               Panel.info(
                  Material.GRASS_BLOCK,
                  "<#55FF88><bold>Mondes</bold>",
                  List.of(
                     "<gray>Mondes chargés : <white>" + Bukkit.getWorlds().size(), "<gray>Chunks chargés : <white>" + var29, "<gray>Entités : <white>" + var30
                  )
               )
            );
         this.inv
            .setItem(
               7,
               Panel.info(
                  Material.CHEST,
                  "<#C77DFF><bold>Disque</bold>",
                  List.of("<gray>Libre : <white>" + Panel.gb(var31.getUsableSpace()) + " <gray>sur <white>" + Panel.gb(var31.getTotalSpace()))
               )
            );
         this.inv
            .setItem(8, Panel.info(Material.BARRIER, "<#FF5555>Fermer", List.of("<dark_gray>Le panneau se met à jour", "<dark_gray>toutes les 2 secondes.")));
         ItemStack var18 = Util.item(Material.BLACK_STAINED_GLASS_PANE, 1, " ");

         for (int var19 = 9; var19 < 18; var19++) {
            this.inv.setItem(var19, var18);
         }

         this.inv.setItem(13, Panel.info(Material.NAME_TAG, "<white><bold>Joueurs connectés</bold>", List.of("<gray>Clique sur un joueur pour les actions.")));
         ArrayList<Player> var33 = new ArrayList<>(Bukkit.getOnlinePlayers());
         var33.sort((var0, var1x) -> var0.getName().compareToIgnoreCase(var1x.getName()));
         int var20 = Math.max(1, (var33.size() + 27 - 1) / 27);
         int var21 = Math.min(this.page, var20 - 1);
         boolean var22 = Panel.this.pl.ranks().isAdmin(this.viewer);

         for (int var23 = 0; var23 < 27 && var21 * 27 + var23 < var33.size(); var23++) {
            Player var24 = (Player)var33.get(var21 * 27 + var23);
            ItemStack var25 = Panel.this.head(var24);
            Util.named(var25, "<white><bold>" + var24.getName() + "</bold>");
            List var26 = Panel.this.playerLore(var24, var22);
            var26.add(" ");
            var26.add("<#FFC93C>▶ Clique pour les actions");
            Util.lore(var25, var26);
            this.inv.setItem(18 + var23, var25);
            this.slots.put(18 + var23, var24.getUniqueId());
         }

         for (int var34 = 45; var34 < 54; var34++) {
            this.inv.setItem(var34, var18);
         }

         if (var21 > 0) {
            this.inv.setItem(45, Util.item(Material.ARROW, 1, "<white>← Page précédente"));
         }

         if (var21 < var20 - 1) {
            this.inv.setItem(53, Util.item(Material.ARROW, 1, "<white>Page suivante →"));
         }

         this.inv.setItem(49, Util.item(Material.PAPER, 1, "<gray>Page " + (var21 + 1) + " / " + var20));
      }

      @Override
      public void click(Player var1, int var2) {
         if (var2 == 8) {
            var1.closeInventory();
         } else if (var2 == 45 && this.page > 0) {
            Panel.this.openMain(var1, this.page - 1);
         } else if (var2 == 53) {
            Panel.this.openMain(var1, this.page + 1);
         } else {
            UUID var3 = this.slots.get(var2);
            if (var3 != null && Bukkit.getPlayer(var3) != null) {
               Panel.this.openOne(var1, var3);
            }
         }
      }
   }

   private final class One extends Menu {
      private final Player viewer;
      private final UUID target;

      One(Player nullx, UUID nullxx) {
         super(4, Msg.mm("<#FF3B3B><bold>Joueur</bold></#FF3B3B>"));
         this.viewer = nullx;
         this.target = nullxx;
         this.refresh();
      }

      void refresh() {
         Player var1 = Bukkit.getPlayer(this.target);
         this.inv.clear();
         ItemStack var2 = Util.item(Material.GRAY_STAINED_GLASS_PANE, 1, " ");

         for (int var3 = 0; var3 < 36; var3++) {
            this.inv.setItem(var3, var2);
         }

         if (var1 == null) {
            this.inv.setItem(13, Util.item(Material.BARRIER, 1, "<#FF5555>Ce joueur s'est déconnecté"));
            this.inv.setItem(31, Util.item(Material.ARROW, 1, "<white>← Retour"));
         } else {
            boolean var6 = Panel.this.pl.ranks().isAdmin(this.viewer);
            ItemStack var4 = Panel.this.head(var1);
            Util.named(var4, "<white><bold>" + var1.getName() + "</bold>");
            Util.lore(var4, Panel.this.playerLore(var1, var6));
            this.inv.setItem(4, var4);
            if (var6) {
               this.inv
                  .setItem(
                     10,
                     Util.item(
                        Material.WITHER_SKELETON_SKULL,
                        1,
                        "<#8B0000><bold>☠ Faire subir le châtiment de Mobutu</bold>",
                        "<gray>Nausée, cécité, lévitation, foudre,",
                        "<gray>un crâne maudit et l'annonce à tout le serveur.",
                        " ",
                        "<#FF5555>Clique pour maudire " + var1.getName()
                     )
                  );
            }

            this.inv.setItem(12, Util.item(Material.ENDER_PEARL, 1, "<#4FC3FF><bold>Aller vers lui</bold>"));
            this.inv.setItem(13, Util.item(Material.LEAD, 1, "<#4FC3FF><bold>L'amener à moi</bold>"));
            this.inv.setItem(14, Util.item(Material.CHEST, 1, "<#FFC93C><bold>Voir son inventaire</bold>", "<dark_gray>(modifiable)"));
            this.inv.setItem(15, Util.item(Material.ENDER_CHEST, 1, "<#C77DFF><bold>Voir son coffre de l'Ender</bold>"));
            PlayerData var5 = Panel.this.pl.data().get(var1);
            this.inv
               .setItem(
                  16,
                  var5.isMuted()
                     ? Util.item(Material.LIME_DYE, 1, "<#55FF88><bold>Lui rendre la parole</bold>")
                     : Util.item(Material.GRAY_DYE, 1, "<#FFB020><bold>Le rendre muet 10 min</bold>")
               );
            this.inv.setItem(22, Util.item(Material.IRON_DOOR, 1, "<#FF5555><bold>L'expulser</bold>", "<gray>(il peut revenir)"));
            this.inv.setItem(31, Util.item(Material.ARROW, 1, "<white>← Retour"));
         }
      }

      @Override
      public void click(Player var1, int var2) {
         if (var2 == 31) {
            Panel.this.openMain(var1, 0);
         } else {
            Player var3 = Bukkit.getPlayer(this.target);
            if (var3 != null) {
               boolean var4 = Panel.this.pl.ranks().isAdmin(var1);
               boolean var5 = Panel.this.pl.ranks().rankOf(var3) != Ranks.Rank.JOUEUR && !var4;
               switch (var2) {
                  case 10:
                     if (var4) {
                        var1.closeInventory();
                        Panel.this.curse(var3);
                     }
                  case 11:
                  case 17:
                  case 18:
                  case 19:
                  case 20:
                  case 21:
                  default:
                     break;
                  case 12:
                     var1.closeInventory();
                     var1.teleport(var3.getLocation());
                     break;
                  case 13:
                     if (!var5) {
                        var1.closeInventory();
                        var3.teleport(var1.getLocation());
                        Msg.ok(var1, var3.getName() + " amené.");
                     }
                     break;
                  case 14:
                     var1.openInventory(var3.getInventory());
                     break;
                  case 15:
                     var1.openInventory(var3.getEnderChest());
                     break;
                  case 16:
                     if (var5) {
                        return;
                     }

                     PlayerData var6 = Panel.this.pl.data().get(var3);
                     if (var6.isMuted()) {
                        var6.mutedUntil = 0L;
                        Msg.ok(var3, "Tu peux de nouveau parler.");
                     } else {
                        var6.mutedUntil = System.currentTimeMillis() + 600000L;
                        var6.muteReason = "Panneau du staff";
                        Msg.err(var3, "Tu es muet pour 10 min.");
                     }

                     var6.touch();
                     Panel.this.pl.data().save(var6);
                     Msg.staff("<white>" + var1.getName() + "</white> a " + (var6.isMuted() ? "rendu muet" : "rendu la parole à") + " <white>" + var3.getName());
                     this.refresh();
                     break;
                  case 22:
                     if (var5) {
                        return;
                     }

                     var1.closeInventory();
                     var3.kick(Msg.mm("<#FFB020><bold>Tu as été expulsé du serveur.</bold></#FFB020>\n<gray>Tu peux revenir."));
                     Msg.staff("<white>" + var1.getName() + "</white> a expulsé <white>" + var3.getName());
               }
            }
         }
      }
   }
}
