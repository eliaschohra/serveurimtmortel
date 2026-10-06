package fr.bdeimt.serveur;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class Auth implements Listener, CommandExecutor {
   private static final int ITERATIONS = 120000;
   private static final int MAX_FAILS = 5;
   private static final long LOGIN_TIMEOUT_MS = 180000L;
   private static final long LOCKOUT_MS = 120000L;
   private static final Set<String> ALLOWED = Set.of("login", "l", "log", "register", "reg", "connexion", "inscription");
   private final BDEIMT pl;
   private final File file;
   private final Map<String, Auth.Account> accounts = new ConcurrentHashMap<>();
   private final Set<UUID> logged = ConcurrentHashMap.newKeySet();
   private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> fails = new HashMap<>();
   private final Map<String, Long> lockout = new ConcurrentHashMap<>();
   private final Set<String> onlineNames = ConcurrentHashMap.newKeySet();
   private final SecureRandom random = new SecureRandom();
   private volatile String adminCode;

   public Auth(BDEIMT var1) {
      this.pl = var1;
      this.file = new File(var1.getDataFolder(), "comptes.yml");
   }

   public void load() {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection var2 = var1.getConfigurationSection("comptes");
      if (var2 != null) {
         for (String var4 : var2.getKeys(false)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               this.accounts
                  .put(
                     var4,
                     new Auth.Account(
                        var5.getString("nom", var4),
                        var5.getString("sel"),
                        var5.getString("hash"),
                        var5.getInt("iterations", 120000),
                        var5.getLong("cree"),
                        var5.getLong("derniere-connexion"),
                        var5.getString("derniere-ip")
                     )
                  );
            }
         }
      }
   }

   private File adminCodeFile() {
      return new File(this.pl.getDataFolder(), "CODE-ADMIN.txt");
   }

   public void prepareAdminCode() {
      if (this.hasAccount(this.pl.adminName())) {
         this.adminCode = null;
         if (this.adminCodeFile().exists() && !this.adminCodeFile().delete()) {
            this.pl.getLogger().warning("CODE-ADMIN.txt non supprime");
         }
      } else {
         String var1 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
         StringBuilder var2 = new StringBuilder();

         for (int var3 = 0; var3 < 6; var3++) {
            var2.append(var1.charAt(this.random.nextInt(var1.length())));
         }

         this.adminCode = var2.toString();

         try {
            Files.writeString(
               this.adminCodeFile().toPath(),
               "Code secret pour creer le compte admin ("
                  + this.pl.adminName()
                  + ") :\n\n    "
                  + this.adminCode
                  + "\n\nEn jeu, avec le pseudo "
                  + this.pl.adminName()
                  + ", tape :\n    /register ton-mot-de-passe ton-mot-de-passe "
                  + this.adminCode
                  + "\n\nCe fichier s'efface tout seul une fois le compte cree.\n"
            );
         } catch (IOException var4) {
            this.pl.getLogger().warning("CODE-ADMIN.txt impossible a ecrire : " + var4.getMessage());
         }

         this.pl.getLogger().warning("Compte admin pas encore cree : code secret dans plugins/BDEIMT/CODE-ADMIN.txt");
      }
   }

   private boolean isAdminName(String var1) {
      return var1.equals(this.pl.adminName());
   }

   private void saveAccounts() {
      YamlConfiguration var1 = new YamlConfiguration();
      var1.options()
         .setHeader(
            List.of(
               "Comptes du serveur. Les mots de passe sont chiffres (PBKDF2), personne ne peut les relire.",
               "Pour qu'un joueur recree son mot de passe : /imt resetmdp <pseudo>"
            )
         );

      for (Entry var3 : this.accounts.entrySet()) {
         Auth.Account var4 = (Auth.Account)var3.getValue();
         String var5 = "comptes." + (String)var3.getKey() + ".";
         var1.set(var5 + "nom", var4.name());
         var1.set(var5 + "sel", var4.salt());
         var1.set(var5 + "hash", var4.hash());
         var1.set(var5 + "iterations", var4.iterations());
         var1.set(var5 + "cree", var4.created());
         var1.set(var5 + "derniere-connexion", var4.lastLogin());
         var1.set(var5 + "derniere-ip", var4.lastIp());
      }

      try {
         var1.save(this.file);
      } catch (IOException var6) {
         this.pl.getLogger().severe("comptes.yml impossible a sauvegarder : " + var6.getMessage());
      }
   }

   public boolean hasAccount(String var1) {
      return this.accounts.containsKey(var1.toLowerCase(Locale.ROOT));
   }

   public boolean resetAccount(String var1) {
      boolean var2 = this.accounts.remove(var1.toLowerCase(Locale.ROOT)) != null;
      if (var2) {
         this.saveAccounts();
         if (var1.equalsIgnoreCase(this.pl.adminName())) {
            this.prepareAdminCode();
         }
      }

      return var2;
   }

   public boolean isLogged(Player var1) {
      return var1 != null && this.logged.contains(var1.getUniqueId());
   }

   private static String hash(String var0, byte[] var1, int var2) {
      try {
         PBEKeySpec var3 = new PBEKeySpec(var0.toCharArray(), var1, var2, 256);
         byte[] var4 = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(var3).getEncoded();
         var3.clearPassword();
         return Base64.getEncoder().encodeToString(var4);
      } catch (Exception var5) {
         throw new IllegalStateException(var5);
      }
   }

   private static boolean check(Auth.Account var0, String var1) {
      String var2 = hash(var1, Base64.getDecoder().decode(var0.salt()), var0.iterations());
      return MessageDigest.isEqual(var2.getBytes(), var0.hash().getBytes());
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         String var6 = var2.getName();
         switch (var6) {
            case "register":
               this.register(var5, var4);
               break;
            case "login":
               this.login(var5, var4);
               break;
            case "changemdp":
               this.change(var5, var4);
         }

         return true;
      }
   }

   private void register(Player var1, String[] var2) {
      if (this.isLogged(var1)) {
         Msg.err(var1, "Tu es déjà connecté.");
      } else if (this.hasAccount(var1.getName())) {
         Msg.err(var1, "Ce pseudo a déjà un mot de passe. Tape <white>/login ‹mot de passe›</white>.");
      } else {
         boolean var3 = this.isAdminName(var1.getName()) && this.adminCode != null;
         if (var3) {
            if (var2.length != 3 || !var2[2].equalsIgnoreCase(this.adminCode)) {
               Msg.err(
                  var1,
                  "Compte admin : ajoute le code secret. <white>/register ‹mdp› ‹mdp› ‹code›</white> <gray>(le code est dans plugins/BDEIMT/CODE-ADMIN.txt, via FileZilla)</gray>"
               );
               return;
            }
         } else if (var2.length != 2) {
            Msg.err(var1, "Utilisation : <white>/register ‹mot de passe› ‹mot de passe›</white> (le même deux fois).");
            return;
         }

         if (!var2[0].equals(var2[1])) {
            Msg.err(var1, "Les deux mots de passe ne sont pas identiques, recommence.");
         } else {
            String var4 = var2[0];
            if (var4.length() < 5) {
               Msg.err(var1, "Mot de passe trop court : 5 caractères minimum.");
            } else if (var4.length() > 64) {
               Msg.err(var1, "Mot de passe trop long (64 caractères max).");
            } else if (var4.equalsIgnoreCase(var1.getName())) {
               Msg.err(var1, "Ton mot de passe ne peut pas être ton pseudo.");
            } else if (this.busy.add(var1.getUniqueId())) {
               String var5 = var1.getAddress() != null ? var1.getAddress().getAddress().getHostAddress() : "?";
               String var6 = var1.getName();
               UUID var7 = var1.getUniqueId();
               Bukkit.getScheduler()
                  .runTaskAsynchronously(
                     this.pl,
                     () -> {
                        byte[] var5x = new byte[16];
                        this.random.nextBytes(var5x);
                        String var6x = hash(var4, var5x, 120000);
                        Bukkit.getScheduler()
                           .runTask(
                              this.pl,
                              () -> {
                                 this.busy.remove(var7);
                                 Player var6xx = Bukkit.getPlayer(var7);
                                 if (var6xx != null && !this.isLogged(var6xx)) {
                                    long var7x = System.currentTimeMillis();
                                    this.accounts
                                       .put(
                                          var6.toLowerCase(Locale.ROOT),
                                          new Auth.Account(var6, Base64.getEncoder().encodeToString(var5x), var6x, 120000, var7x, var7x, var5)
                                       );
                                    this.saveAccounts();
                                    if (this.isAdminName(var6)) {
                                       this.prepareAdminCode();
                                    }

                                    this.pl.getLogger().info(var6 + " a cree son compte.");
                                    this.completeLogin(var6xx, true);
                                 }
                              }
                           );
                     }
                  );
            }
         }
      }
   }

   private void login(Player var1, String[] var2) {
      if (this.isLogged(var1)) {
         Msg.err(var1, "Tu es déjà connecté.");
      } else {
         Auth.Account var3 = this.accounts.get(var1.getName().toLowerCase(Locale.ROOT));
         if (var3 == null) {
            Msg.err(var1, "Tu n'as pas encore de mot de passe. Tape <white>/register ‹mot de passe› ‹mot de passe›</white>.");
         } else if (var2.length != 1) {
            Msg.err(var1, "Utilisation : <white>/login ‹mot de passe›</white>");
         } else if (this.busy.add(var1.getUniqueId())) {
            String var4 = var2[0];
            UUID var5 = var1.getUniqueId();
            Bukkit.getScheduler()
               .runTaskAsynchronously(
                  this.pl,
                  () -> {
                     boolean var4x = check(var3, var4);
                     Bukkit.getScheduler()
                        .runTask(
                           this.pl,
                           () -> {
                              this.busy.remove(var5);
                              Player var4xx = Bukkit.getPlayer(var5);
                              if (var4xx != null && !this.isLogged(var4xx)) {
                                 if (var4x) {
                                    String var5x = var4xx.getAddress() != null ? var4xx.getAddress().getAddress().getHostAddress() : "?";
                                    this.accounts
                                       .put(
                                          var4xx.getName().toLowerCase(Locale.ROOT),
                                          new Auth.Account(
                                             var3.name(), var3.salt(), var3.hash(), var3.iterations(), var3.created(), System.currentTimeMillis(), var5x
                                          )
                                       );
                                    this.saveAccounts();
                                    this.completeLogin(var4xx, false);
                                 } else {
                                    int var6 = this.fails.merge(var5, 1, Integer::sum);
                                    if (var6 >= 5) {
                                       this.lockout.put(var4xx.getName().toLowerCase(Locale.ROOT), System.currentTimeMillis() + 120000L);
                                       var4xx.kick(
                                          Msg.mm(
                                             "<#FF5555><bold>Trop d'essais ratés.</bold></#FF5555>\n\n<gray>Réessaie dans 2 minutes.\nMot de passe oublié ? Demande à Elias de le réinitialiser.</gray>"
                                          )
                                       );
                                    } else {
                                       Util.sound(var4xx, "entity.villager.no", 1.0F, 1.0F);
                                       Msg.err(var4xx, "Mauvais mot de passe. <white><r></white> essai(s) restant(s).", Msg.p("r", 5 - var6));
                                    }
                                 }
                              }
                           }
                        );
                  }
               );
         }
      }
   }

   private void change(Player var1, String[] var2) {
      if (this.isLogged(var1)) {
         if (var2.length != 2) {
            Msg.err(var1, "Utilisation : <white>/changemdp ‹ancien› ‹nouveau›</white>");
         } else {
            Auth.Account var3 = this.accounts.get(var1.getName().toLowerCase(Locale.ROOT));
            if (var3 != null) {
               String var4 = var2[0];
               String var5 = var2[1];
               if (var5.length() < 5 || var5.length() > 64) {
                  Msg.err(var1, "Le nouveau mot de passe doit faire entre 5 et 64 caractères.");
               } else if (this.busy.add(var1.getUniqueId())) {
                  UUID var6 = var1.getUniqueId();
                  Bukkit.getScheduler()
                     .runTaskAsynchronously(
                        this.pl,
                        () -> {
                           boolean var5x = check(var3, var4);
                           byte[] var6x = new byte[16];
                           this.random.nextBytes(var6x);
                           String var7 = var5x ? hash(var5, var6x, 120000) : null;
                           Bukkit.getScheduler()
                              .runTask(
                                 this.pl,
                                 () -> {
                                    this.busy.remove(var6);
                                    Player var6xx = Bukkit.getPlayer(var6);
                                    if (var6xx != null) {
                                       if (!var5x) {
                                          Msg.err(var6xx, "L'ancien mot de passe est faux.");
                                       } else {
                                          this.accounts
                                             .put(
                                                var3.name().toLowerCase(Locale.ROOT),
                                                new Auth.Account(
                                                   var3.name(),
                                                   Base64.getEncoder().encodeToString(var6x),
                                                   var7,
                                                   120000,
                                                   var3.created(),
                                                   var3.lastLogin(),
                                                   var3.lastIp()
                                                )
                                             );
                                          this.saveAccounts();
                                          Msg.ok(var6xx, "Mot de passe changé.");
                                       }
                                    }
                                 }
                              );
                        }
                     );
               }
            }
         }
      }
   }

   private void completeLogin(Player var1, boolean var2) {
      this.logged.add(var1.getUniqueId());
      this.joinedAt.remove(var1.getUniqueId());
      this.fails.remove(var1.getUniqueId());
      PlayerData var3 = this.pl.data().get(var1);
      // Une fois identifie, on arrive dans le hub : c'est la qu'on choisit son
      // mode de jeu. Si le monde du hub n'a pas ete depose sur le serveur, on
      // repart directement en survie comme avant.
      boolean hub = this.pl.worlds().available(Zone.HUB);
      Location var4;
      GameMode var5;
      if (hub) {
         var4 = this.pl.worlds().spawnOf(Zone.HUB, var1);
         var5 = Zone.HUB.mode;
      } else {
         var4 = Util.loc(var3.returnLoc);
         if (var4 == null || this.pl.lobby().isLobby(var4.getWorld())) {
            var4 = this.pl.survivalSpawn();
         }

         var5 = GameMode.SURVIVAL;
         if (var3.savedGameMode != null) {
            try {
               var5 = GameMode.valueOf(var3.savedGameMode);
            } catch (IllegalArgumentException var8) {
            }
         }

         if (var5 == GameMode.ADVENTURE) {
            var5 = GameMode.SURVIVAL;
         }
      }

      for (Player var7 : Bukkit.getOnlinePlayers()) {
         if (!var7.equals(var1) && this.isLogged(var7)) {
            var7.showPlayer(this.pl, var1);
            var1.showPlayer(this.pl, var7);
         }
      }

      this.pl.ranks().apply(var1);
      GameMode var9 = var5;
      this.pl.lobby().leave(var1, var4, () -> {
         var1.setGameMode(var9);

         if (hub) {
            // Le groupe d'origine a ete releve a la connexion : l'inventaire
            // du monde quitte est range la ou il faut avant d'entrer au hub.
            this.pl.inventories().switchTo(var1, Zone.HUB.group);
            this.pl.worlds().arrive(var1, Zone.HUB);
         } else {
            this.pl.inventories().switchTo(var1, Zone.SURVIE.group);
            this.pl.fly().onLogin(var1);
            this.pl.graves().onLogin(var1);
            this.pl.votes().onLogin(var1);
         }

         this.pl.tab().refresh(var1);
      });
      Msg.broadcast(Msg.mm("<#55FF88>+</#55FF88> ").append(this.pl.ranks().display(var1)));
      var1.showTitle(
         Title.title(
            Msg.mm(var2 ? "<gradient:#4FC3FF:#FF5FAE><bold>Bienvenue !</bold></gradient>" : "<gradient:#4FC3FF:#FF5FAE><bold>Bon retour !</bold></gradient>"),
            Msg.mm("<gray>Serveur du BDE de l'IMT</gray>"),
            Times.times(Duration.ofMillis(300L), Duration.ofSeconds(2L), Duration.ofMillis(800L))
         )
      );
      Util.sound(var1, "entity.player.levelup", 0.7F, 1.3F);
      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (var1.isOnline()) {
            this.pl.announcer().onLogin(var1, var2 || !var3.guideSeen);
         }
      }, 30L);
      this.pl.getLogger().info(var1.getName() + " s'est identifie.");
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPreLogin(AsyncPlayerPreLoginEvent var1) {
      if (var1.getLoginResult() == Result.ALLOWED) {
         String var2 = var1.getName();
         String var3 = var2.toLowerCase(Locale.ROOT);
         if (!Util.validName(var2)) {
            var1.disallow(Result.KICK_OTHER, Msg.mm("<#FF5555>Pseudo invalide.</#FF5555>\n<gray>3 à 16 caractères : lettres, chiffres et _ uniquement.</gray>"));
         } else if (this.onlineNames.contains(var3)) {
            var1.disallow(Result.KICK_OTHER, Msg.mm("<#FF5555>Ce pseudo est déjà connecté sur le serveur.</#FF5555>"));
         } else {
            Long var4 = this.lockout.get(var3);
            if (var4 != null && var4 > System.currentTimeMillis()) {
               var1.disallow(
                  Result.KICK_OTHER,
                  Msg.mm(
                     "<#FF5555>Trop d'essais ratés.</#FF5555>\n<gray>Réessaie dans <t>.</gray>", Msg.p("t", Util.duration(var4 - System.currentTimeMillis()))
                  )
               );
            } else {
               Auth.Account var5 = this.accounts.get(var3);
               if (var5 != null && !var5.name().equals(var2)) {
                  var1.disallow(
                     Result.KICK_OTHER,
                     Msg.mm(
                        "<#FF5555>Attention aux majuscules !</#FF5555>\n<gray>Ce compte s'appelle <white><n></white> : reconnecte-toi avec exactement ce pseudo.</gray>",
                        Msg.p("n", var5.name())
                     )
                  );
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onJoin(PlayerJoinEvent var1) {
      var1.joinMessage(null);
      Player var2 = var1.getPlayer();
      this.onlineNames.add(var2.getName().toLowerCase(Locale.ROOT));
      this.logged.remove(var2.getUniqueId());
      PlayerData var3 = this.pl.data().get(var2);
      if (!this.pl.lobby().isLobby(var2.getWorld()) && !var2.isDead()) {
         var3.returnLoc = Util.loc(var2.getLocation());
         var3.savedGameMode = var2.getGameMode().name();
         var3.touch();
         this.pl.data().save(var3);
      }

      this.joinedAt.put(var2.getUniqueId(), System.currentTimeMillis());
      this.fails.remove(var2.getUniqueId());

      for (Player var5 : Bukkit.getOnlinePlayers()) {
         if (!var5.equals(var2)) {
            var5.hidePlayer(this.pl, var2);
            var2.hidePlayer(this.pl, var5);
         }
      }

      this.pl.inventories().setCurrentGroup(var2, this.pl.worlds().zoneOf(var2).group);
      this.pl.lobby().enter(var2);
      Bukkit.getScheduler().runTaskLater(this.pl, () -> this.prompt(var2, true), 15L);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onRespawn(PlayerRespawnEvent var1) {
      Player var2 = var1.getPlayer();
      if (!this.isLogged(var2)) {
         PlayerData var3 = this.pl.data().get(var2);
         var3.returnLoc = Util.loc(var1.getRespawnLocation());
         var3.touch();
         var1.setRespawnLocation(this.pl.lobby().spawn());
         Bukkit.getScheduler().runTask(this.pl, () -> {
            if (var2.isOnline() && !this.isLogged(var2)) {
               this.pl.lobby().enter(var2);
            }
         });
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent var1) {
      Player var2 = var1.getPlayer();
      var1.quitMessage(null);
      boolean var3 = this.logged.remove(var2.getUniqueId());
      PlayerData var4 = this.pl.data().get(var2);
      if (var3) {
         Msg.broadcast(Msg.mm("<#FF5555>-</#FF5555> ").append(this.pl.ranks().display(var2)));
         if (!this.pl.lobby().isLobby(var2.getWorld()) && !var2.isDead()) {
            var4.returnLoc = Util.loc(var2.getLocation());
            var4.savedGameMode = var2.getGameMode().name();
         }
      }

      this.pl.data().save(var4);
      this.joinedAt.remove(var2.getUniqueId());
      this.fails.remove(var2.getUniqueId());
      this.busy.remove(var2.getUniqueId());
      this.onlineNames.remove(var2.getName().toLowerCase(Locale.ROOT));
   }

   private void prompt(Player var1, boolean var2) {
      if (var1.isOnline() && !this.isLogged(var1)) {
         boolean var3 = this.hasAccount(var1.getName());
         if (var3) {
            Msg.poulpy(
               var1,
               "Bon retour <white><n></white> ! Connecte-toi avec <click:suggest_command:'/login '><hover:show_text:'<gray>Clique pour écrire la commande'><#4FC3FF>/login ‹mot de passe›</#4FC3FF></hover></click>.",
               Msg.p("n", var1.getName())
            );
         } else if (this.isAdminName(var1.getName()) && this.adminCode != null) {
            Msg.poulpy(
               var1,
               "Salut le Mobutu ! Crée ton mot de passe avec le code secret : <#55FF88>/register ‹mot de passe› ‹mot de passe› ‹code›</#55FF88> <gray>(code dans plugins/BDEIMT/CODE-ADMIN.txt, via FileZilla)</gray>"
            );
         } else {
            Msg.poulpy(
               var1,
               "Bienvenue <white><n></white> ! Pour ta première connexion, crée ton mot de passe : <click:suggest_command:'/register '><hover:show_text:'<gray>Clique pour écrire la commande'><#55FF88>/register ‹mot de passe› ‹mot de passe›</#55FF88></hover></click> <gray>(le même deux fois)</gray>. Ensuite, il te suffira de taper <#4FC3FF>/login ‹mot de passe›</#4FC3FF> à chaque connexion.",
               Msg.p("n", var1.getName())
            );
         }

         if (var2) {
            var1.showTitle(
               Title.title(
                  Msg.mm("<gradient:#4FC3FF:#B66BFF:#FF5FAE><bold>BDE IMT</bold></gradient>"),
                  Msg.mm(var3 ? "<gray>Tape</gray> <#4FC3FF>/login ‹mot de passe›</#4FC3FF>" : "<gray>Tape</gray> <#55FF88>/register ‹mdp› ‹mdp›</#55FF88>"),
                  Times.times(Duration.ofMillis(500L), Duration.ofSeconds(6L), Duration.ofMillis(800L))
               )
            );
         }
      }
   }

   public void tick() {
      long var1 = System.currentTimeMillis();

      for (Entry var4 : this.joinedAt.entrySet()) {
         Player var5 = Bukkit.getPlayer((UUID)var4.getKey());
         if (var5 != null && !this.isLogged(var5)) {
            long var6 = var1 - (Long)var4.getValue();
            if (var6 > 180000L) {
               var5.kick(Msg.mm("<#FFB020>Temps écoulé pour te connecter.</#FFB020>\n<gray>Reviens quand tu veux !</gray>"));
            } else if (var6 > 5000L && var6 / 1000L % 20L == 0L) {
               this.prompt(var5, false);
            }

            var5.sendActionBar(
               Msg.mm(
                  this.hasAccount(var5.getName()) ? "<#4FC3FF>/login ‹mot de passe›</#4FC3FF>" : "<#55FF88>/register ‹mot de passe› ‹mot de passe›</#55FF88>"
               )
            );
         }
      }
   }

   private boolean blocked(Player var1) {
      return var1 != null && !this.isLogged(var1);
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onMove(PlayerMoveEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         Location var2 = var1.getFrom();
         Location var3 = var1.getTo();
         if (var2.getX() != var3.getX() || var2.getY() != var3.getY() || var2.getZ() != var3.getZ()) {
            Location var4 = var2.clone();
            var4.setYaw(var3.getYaw());
            var4.setPitch(var3.getPitch());
            var1.setTo(var4);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onTeleport(PlayerTeleportEvent var1) {
      if (this.blocked(var1.getPlayer()) && var1.getCause() != TeleportCause.PLUGIN && var1.getCause() != TeleportCause.UNKNOWN) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
         Msg.err(var1.getPlayer(), "Identifie-toi d'abord (et n'écris jamais ton mot de passe dans le chat !).");
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onCommand(PlayerCommandPreprocessEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         String var2 = var1.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
         int var3 = var2.indexOf(58);
         if (var3 >= 0) {
            var2 = var2.substring(var3 + 1);
         }

         if (!ALLOWED.contains(var2)) {
            var1.setCancelled(true);
            this.prompt(var1.getPlayer(), false);
         }
      }
   }

   @EventHandler
   public void onCommandList(PlayerCommandSendEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.getCommands().removeIf(var0 -> {
            String var1x = var0.toLowerCase(Locale.ROOT);
            int var2 = var1x.indexOf(58);
            return !ALLOWED.contains(var2 >= 0 ? var1x.substring(var2 + 1) : var1x);
         });
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onInteract(PlayerInteractEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onInteractEntity(PlayerInteractEntityEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDrop(PlayerDropItemEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPickup(EntityPickupItemEvent var1) {
      if (var1.getEntity() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onClick(InventoryClickEvent var1) {
      if (var1.getWhoClicked() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getWhoClicked() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDamage(EntityDamageEvent var1) {
      if (var1.getEntity() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onFood(FoodLevelChangeEvent var1) {
      if (var1.getEntity() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onBreak(BlockBreakEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlace(BlockPlaceEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onConsume(PlayerItemConsumeEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onSwap(PlayerSwapHandItemsEvent var1) {
      if (this.blocked(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onTarget(EntityTargetEvent var1) {
      if (var1.getTarget() instanceof Player var2 && this.blocked(var2)) {
         var1.setCancelled(true);
      }
   }

   public Component statusLine(Player var1) {
      return Msg.mm(this.isLogged(var1) ? "<green>identifié</green>" : "<red>non identifié</red>");
   }

   private record Account(String name, String salt, String hash, int iterations, long created, long lastLogin, String lastIp) {
   }
}
