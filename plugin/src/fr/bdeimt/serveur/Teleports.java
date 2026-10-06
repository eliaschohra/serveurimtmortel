package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class Teleports implements CommandExecutor, TabCompleter, Listener {
   private static final long REQUEST_LIFE = 60000L;
   private static final long REQUEST_COOLDOWN = 30000L;
   private static final long HOME_COOLDOWN = 30000L;
   private static final long COMBAT = 15000L;
   private static final int WARMUP_SECONDS = 3;
   private static final int MAX_HOMES = 1;
   private static final int MAX_HOMES_STAFF = 1;
   private final BDEIMT pl;
   private final Map<UUID, Map<UUID, Teleports.Request>> incoming = new HashMap<>();
   private final Map<UUID, Long> lastRequest = new HashMap<>();
   private final Map<UUID, Long> lastHome = new HashMap<>();
   private final Map<UUID, Long> lastCombat = new HashMap<>();
   private final Map<UUID, BukkitTask> warmups = new HashMap<>();

   public Teleports(BDEIMT var1) {
      this.pl = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         if (this.pl.lobby().isLobby(var5.getWorld()) && !var2.getName().equals("spawn")) {
            Msg.err(var5, "Impossible depuis le lobby.");
            return true;
         } else {
            String var6 = var2.getName();
            switch (var6) {
               case "tpa":
                  this.request(var5, var4, false);
                  break;
               case "tpahere":
                  this.request(var5, var4, true);
                  break;
               case "tpaccept":
                  this.answer(var5, var4, true);
                  break;
               case "tpdeny":
                  this.answer(var5, var4, false);
                  break;
               case "tpcancel":
                  this.cancelOwn(var5);
                  break;
               case "tpatoggle":
                  this.toggle(var5);
                  break;
               case "sethome":
                  this.setHome(var5, var4);
                  break;
               case "home":
                  this.home(var5, var4);
                  break;
               case "delhome":
                  this.delHome(var5, var4);
                  break;
               case "homes":
                  this.listHomes(var5);
                  break;
               case "spawn":
                  this.spawn(var5);
            }

            return true;
         }
      }
   }

   private void request(Player var1, String[] var2, boolean var3) {
      if (var2.length != 1) {
         Msg.err(var1, var3 ? "Utilisation : <white>/tpahere ‹joueur›</white>" : "Utilisation : <white>/tpa ‹joueur›</white>");
      } else {
         Player var4 = Bukkit.getPlayerExact(var2[0]);
         if (var4 == null || !this.pl.auth().isLogged(var4) || !var1.canSee(var4)) {
            Msg.err(var1, "Joueur introuvable.");
         } else if (var4.equals(var1)) {
            Msg.err(var1, "Tu ne peux pas t'envoyer une demande à toi-même.");
         } else if (this.pl.lobby().isLobby(var4.getWorld())) {
            Msg.err(var1, "Ce joueur n'est pas disponible.");
         } else if (!this.pl.worlds().zoneOf(var4).group.equals(this.pl.worlds().zoneOf(var1).group)) {
            Msg.err(var1, "<white><n></white> n'est pas dans le même monde que toi.", Msg.p("n", var4.getName()));
         } else {
            PlayerData var5 = this.pl.data().get(var4);
            if (!var5.tpaOff && !var5.ignores.contains(var1.getUniqueId())) {
               long var6 = System.currentTimeMillis();
               Long var8 = this.lastRequest.get(var1.getUniqueId());
               boolean var20 = this.pl.lists().sameList(var1, var4);
               if (var8 != null && var6 - var8 < 30000L && !var20 && !this.pl.ranks().isStaff(var1)) {
                  Msg.err(var1, "Attends <white><t></white> avant une nouvelle demande.", Msg.p("t", Util.duration(30000L - (var6 - var8))));
               } else {
                  this.lastRequest.put(var1.getUniqueId(), var6);
                  this.incoming
                     .computeIfAbsent(var4.getUniqueId(), var0 -> new HashMap<>())
                     .put(var1.getUniqueId(), new Teleports.Request(var1.getUniqueId(), var4.getUniqueId(), var3, var6));
                  Msg.ok(
                     var1,
                     "Demande envoyée à <white><n></white>. Elle expire dans 60 s. <click:run_command:'/tpcancel'><gray>[<red>Annuler</red>]</gray></click>",
                     Msg.p("n", var4.getName())
                  );
                  String var9 = var3 ? "souhaite que tu le rejoignes" : "souhaite se téléporter à toi";
                  Msg.raw(
                     var4,
                     "<#4FC3FF>✈</#4FC3FF> <white><n></white> <gray>"
                        + var9
                        + ".</gray> <click:run_command:'/tpaccept "
                        + var1.getName()
                        + "'><hover:show_text:'<green>Accepter'><#55FF88><bold>[Accepter]</bold></#55FF88></hover></click> <click:run_command:'/tpdeny "
                        + var1.getName()
                        + "'><hover:show_text:'<red>Refuser'><#FF5555><bold>[Refuser]</bold></#FF5555></hover></click>",
                     Msg.p("n", var1.getName())
                  );
                  Util.sound(var4, "block.note_block.pling", 0.8F, 1.6F);
               }
            } else {
               Msg.err(var1, "<white><n></white> n'accepte pas les demandes de téléportation.", Msg.p("n", var4.getName()));
            }
         }
      }
   }

   private Teleports.Request pick(Player var1, String[] var2) {
      Map<UUID, Teleports.Request> var3 = this.incoming.get(var1.getUniqueId());
      if (var3 == null) {
         return null;
      } else {
         long var4 = System.currentTimeMillis();
         var3.values().removeIf(var2x -> var4 - var2x.at() > 60000L || Bukkit.getPlayer(var2x.from()) == null);
         if (var3.isEmpty()) {
            return null;
         } else if (var2.length >= 1) {
            Player var9 = Bukkit.getPlayerExact(var2[0]);
            return var9 == null ? null : (Teleports.Request)var3.get(var9.getUniqueId());
         } else {
            Teleports.Request var6 = null;

            for (Teleports.Request var8 : var3.values()) {
               if (var6 == null || var8.at() > var6.at()) {
                  var6 = var8;
               }
            }

            return var6;
         }
      }
   }

   private void answer(Player var1, String[] var2, boolean var3) {
      Teleports.Request var4 = this.pick(var1, var2);
      if (var4 == null) {
         Msg.err(var1, "Aucune demande en attente.");
      } else {
         this.incoming.get(var1.getUniqueId()).remove(var4.from());
         Player var5 = Bukkit.getPlayer(var4.from());
         if (var5 == null) {
            Msg.err(var1, "Ce joueur s'est déconnecté.");
         } else if (!var3) {
            Msg.info(var1, "Demande de <white><n></white> refusée.", Msg.p("n", var5.getName()));
            Msg.err(var5, "<white><n></white> a refusé ta demande.", Msg.p("n", var1.getName()));
         } else {
            Player var6 = var4.here() ? var1 : var5;
            Player var7 = var4.here() ? var5 : var1;
            Msg.ok(var1, "Demande acceptée.");
            Msg.ok(var5, "<white><n></white> a accepté ta demande.", Msg.p("n", var1.getName()));
            this.warmup(
               var6,
               () -> var7.isOnline() && !this.pl.lobby().isLobby(var7.getWorld()) ? var7.getLocation() : null,
               "chez <white>" + var7.getName() + "</white>",
               this.pl.lists().sameList(var6, var7)
            );
         }
      }
   }

   private void cancelOwn(Player var1) {
      boolean var2 = false;

      for (Map var4 : this.incoming.values()) {
         if (var4.remove(var1.getUniqueId()) != null) {
            var2 = true;
         }
      }

      if (var2) {
         Msg.ok(var1, "Demande annulée.");
      } else {
         Msg.err(var1, "Tu n'as aucune demande en cours.");
      }
   }

   private void toggle(Player var1) {
      PlayerData var2 = this.pl.data().get(var1);
      var2.tpaOff = !var2.tpaOff;
      var2.touch();
      if (var2.tpaOff) {
         Msg.ok(var1, "Tu refuses désormais toutes les demandes de téléportation. <gray>(/tpatoggle pour réactiver)");
      } else {
         Msg.ok(var1, "Tu acceptes à nouveau les demandes de téléportation.");
      }
   }

   private int maxHomes(Player var1) {
      return this.pl.ranks().isStaff(var1) ? 1 : 1;
   }

   private static String homeName(String[] var0) {
      String var1 = var0.length >= 1 ? var0[0].toLowerCase(Locale.ROOT) : "maison";
      return var1.replaceAll("[^a-z0-9_-]", "");
   }

   private void setHome(Player var1, String[] var2) {
      String var3 = homeName(var2);
      if (!var3.isEmpty() && var3.length() <= 16) {
         PlayerData var4 = this.pl.data().get(var1);
         synchronized (var4) {
            if (!var4.homes.containsKey(var3) && var4.homes.size() >= this.maxHomes(var1)) {
               Msg.err(var1, "Tu as déjà <white><m></white> homes. Supprime-en un avec <white>/delhome ‹nom›</white>.", Msg.p("m", this.maxHomes(var1)));
               return;
            }

            var4.homes.put(var3, Util.loc(var1.getLocation()));
         }

         var4.touch();
         this.pl.data().save(var4);
         Msg.ok(var1, "Home <white><n></white> enregistré ici. Reviens-y avec <white>/home <n></white>.", Msg.p("n", var3));
         Util.sound(var1, "block.amethyst_block.chime", 1.0F, 1.2F);
      } else {
         Msg.err(var1, "Nom invalide (lettres, chiffres, 16 max).");
      }
   }

   private void home(Player var1, String[] var2) {
      PlayerData var3 = this.pl.data().get(var1);
      String var4;
      synchronized (var3) {
         if (var3.homes.isEmpty()) {
            Msg.err(var1, "Tu n'as pas de home. Crée-en un avec <white>/sethome</white>.");
            return;
         }

         var4 = var2.length >= 1 ? homeName(var2) : (var3.homes.containsKey("maison") ? "maison" : var3.homes.keySet().iterator().next());
      }

      String var5;
      synchronized (var3) {
         var5 = var3.homes.get(var4);
      }

      if (var5 == null) {
         this.listHomes(var1);
      } else {
         Location var6 = Util.loc(var5);
         if (var6 == null) {
            Msg.err(var1, "Ce home est dans un monde qui n'existe plus.");
         } else {
            long var7 = System.currentTimeMillis();
            Long var9 = this.lastHome.get(var1.getUniqueId());
            if (var9 != null && var7 - var9 < 30000L && !this.pl.ranks().isStaff(var1)) {
               Msg.err(var1, "Attends <white><t></white>.", Msg.p("t", Util.duration(30000L - (var7 - var9))));
            } else {
               this.warmup(var1, () -> var6, "au home <white>" + var4 + "</white>");
            }
         }
      }
   }

   private void delHome(Player var1, String[] var2) {
      if (var2.length < 1) {
         Msg.err(var1, "Utilisation : <white>/delhome ‹nom›</white>");
      } else {
         String var3 = homeName(var2);
         PlayerData var4 = this.pl.data().get(var1);
         boolean var5;
         synchronized (var4) {
            var5 = var4.homes.remove(var3) != null;
         }

         if (!var5) {
            Msg.err(var1, "Aucun home nommé <white><n></white>.", Msg.p("n", var3));
         } else {
            var4.touch();
            this.pl.data().save(var4);
            Msg.ok(var1, "Home <white><n></white> supprimé.", Msg.p("n", var3));
         }
      }
   }

   private void listHomes(Player var1) {
      PlayerData var2 = this.pl.data().get(var1);
      ArrayList var3;
      synchronized (var2) {
         var3 = new ArrayList<>(var2.homes.keySet());
      }

      if (var3.isEmpty()) {
         Msg.info(var1, "Aucun home. <white>/sethome [nom]</white> pour en créer un (<white><m></white> max).", Msg.p("m", this.maxHomes(var1)));
      } else {
         StringBuilder var4 = new StringBuilder("<gray>Tes homes (" + var3.size() + "/" + this.maxHomes(var1) + ") : ");

         for (int var5 = 0; var5 < var3.size(); var5++) {
            String var6 = (String)var3.get(var5);
            if (var5 > 0) {
               var4.append("<dark_gray>, </dark_gray>");
            }

            var4.append("<click:run_command:'/home ")
               .append(var6)
               .append("'><hover:show_text:'<gray>Clique pour y aller'><#4FC3FF><u>")
               .append(var6)
               .append("</u></#4FC3FF></hover></click>");
         }

         Msg.raw(var1, var4.toString());
      }
   }

   private void spawn(Player var1) {
      if (this.pl.lobby().isLobby(var1.getWorld()) && !this.pl.auth().isLogged(var1)) {
         return;
      }

      // Chaque univers a son point de depart : /spawn ramene a celui du monde
      // ou l'on se trouve, pas a celui de la survie.
      Zone var2 = this.pl.worlds().zoneOf(var1);
      this.warmup(var1, () -> this.pl.worlds().spawnOf(var2, var1), "au spawn de " + var2.shortLabel().toLowerCase(Locale.ROOT));
   }

   private void warmup(Player var1, Teleports.Dest var2, String var3) {
      this.warmup(var1, var2, var3, false);
   }

   /**
    * @param var10 vrai pour partir tout de suite, sans les trois secondes
    *              d'attente : c'est le cas entre membres d'une meme liste.
    */
   private void warmup(Player var1, Teleports.Dest var2, String var3, boolean var10) {
      Long var4 = this.lastCombat.get(var1.getUniqueId());
      if (var4 != null && System.currentTimeMillis() - var4 < 15000L && !this.pl.ranks().isStaff(var1)) {
         Msg.err(var1, "Tu es en plein combat ! Attends <white><t></white>.", Msg.p("t", Util.duration(15000L - (System.currentTimeMillis() - var4))));
      } else {
         BukkitTask var5 = this.warmups.remove(var1.getUniqueId());
         if (var5 != null) {
            var5.cancel();
         }

         Location var6 = var1.getLocation().clone();
         boolean var7 = var10 || this.pl.ranks().isStaff(var1);
         int[] var8 = new int[]{var7 ? 0 : 3};
         if (!var7) {
            Msg.info(var1, "Téléportation " + var3 + " dans <white>3 s</white>, ne bouge pas...");
         }

         BukkitTask var9 = Bukkit.getScheduler().runTaskTimer(this.pl, () -> {
            if (!var1.isOnline()) {
               this.stop(var1);
            } else if (!var1.getWorld().equals(var6.getWorld()) || var1.getLocation().distanceSquared(var6) > 0.5) {
               Msg.err(var1, "Téléportation annulée : tu as bougé.");
               this.stop(var1);
            } else if (var8[0] > 0) {
               var1.sendActionBar(Msg.mm("<#4FC3FF>✈ Téléportation dans <white>" + var8[0] + "</white>...</#4FC3FF>"));
               var8[0]--;
            } else {
               this.stop(var1);
               Location var6x = var2.get();
               if (var6x == null) {
                  Msg.err(var1, "Destination indisponible.");
               } else {
                  if (var3.startsWith("au home")) {
                     this.lastHome.put(var1.getUniqueId(), System.currentTimeMillis());
                  }

                  var1.teleportAsync(var6x).thenAccept(var1xx -> {
                     if (var1xx) {
                        var1.setNoDamageTicks(60);
                        var1.setFallDistance(0.0F);
                        Util.sound(var1, "entity.enderman.teleport", 0.7F, 1.2F);
                     }
                  });
               }
            }
         }, var7 ? 1L : 0L, 20L);
         this.warmups.put(var1.getUniqueId(), var9);
      }
   }

   private void stop(Player var1) {
      BukkitTask var2 = this.warmups.remove(var1.getUniqueId());
      if (var2 != null) {
         var2.cancel();
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent var1) {
      if (var1.getEntity() instanceof Player var2) {
         if (this.warmups.containsKey(var2.getUniqueId())) {
            this.stop(var2);
            Msg.err(var2, "Téléportation annulée : tu as pris des dégâts.");
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPvp(EntityDamageByEntityEvent var1) {
      if (var1.getEntity() instanceof Player var2) {
         Player var8 = null;
         if (var1.getDamager() instanceof Player var4) {
            var8 = var4;
         } else if (var1.getDamager() instanceof Projectile var5 && var5.getShooter() instanceof Player var6) {
            var8 = var6;
         }

         if (var8 != null && !var8.equals(var2)) {
            long var9 = System.currentTimeMillis();
            this.lastCombat.put(var2.getUniqueId(), var9);
            this.lastCombat.put(var8.getUniqueId(), var9);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.stop(var1.getPlayer());
      this.incoming.remove(var2);

      for (Map var4 : this.incoming.values()) {
         var4.remove(var2);
      }

      this.lastCombat.remove(var2);
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var1 instanceof Player var6 && var4.length == 1) {
         String var7 = var2.getName();
         if (!var7.equals("home") && !var7.equals("delhome")) {
            if (var7.equals("tpa") || var7.equals("tpahere") || var7.equals("tpaccept") || var7.equals("tpdeny")) {
               for (Player var9 : Bukkit.getOnlinePlayers()) {
                  if (!var9.equals(var6) && var6.canSee(var9) && this.pl.auth().isLogged(var9)) {
                     var5.add(var9.getName());
                  }
               }
            }
         } else {
            PlayerData var8 = this.pl.data().get(var6);
            synchronized (var8) {
               var5.addAll(var8.homes.keySet());
            }
         }

         String var13 = var4[0].toLowerCase(Locale.ROOT);
         var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var13));
         return var5;
      } else {
         return var5;
      }
   }

   private interface Dest {
      Location get();
   }

   private record Request(UUID from, UUID to, boolean here, long at) {
   }
}
