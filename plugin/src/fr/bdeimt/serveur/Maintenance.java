package fr.bdeimt.serveur;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.scheduler.BukkitTask;

public final class Maintenance implements CommandExecutor, TabCompleter, Listener {
   private final BDEIMT pl;
   private BukkitTask task;
   private BossBar bar;
   private int total;
   private volatile int left = -1;
   private String reason = "maintenance";

   public Maintenance(BDEIMT var1) {
      this.pl = var1;
   }

   public boolean running() {
      return this.task != null;
   }

   public int secondsLeft() {
      return this.task != null ? this.left : -1;
   }

   public String reason() {
      return this.reason;
   }

   public String start(int var1, String var2, String var3) {
      if (this.task != null) {
         return "Une fermeture est déjà programmée (" + Util.duration(this.left * 1000L) + " restantes).";
      } else {
         this.total = Math.max(10, Math.min(3600, var1));
         this.left = this.total;
         this.reason = var2 != null && !var2.isBlank() ? var2 : "maintenance";
         this.bar = BossBar.bossBar(Msg.mm(this.barText()), 1.0F, Color.RED, Overlay.NOTCHED_10);

         for (Player var5 : Bukkit.getOnlinePlayers()) {
            var5.showBossBar(this.bar);
         }

         Msg.panthereAll(
            "<#FF5555><bold>⚠ Le serveur ferme dans <t> (<r>).</bold></#FF5555> Mettez-vous à l'abri, on revient très vite !",
            Msg.p("t", Util.duration(this.total * 1000L)),
            Msg.p("r", this.reason)
         );

         for (Player var7 : Bukkit.getOnlinePlayers()) {
            var7.showTitle(
               Title.title(
                  Msg.mm("<#FF5555><bold>⚠ FERMETURE</bold></#FF5555>"),
                  Msg.mm("<#FFD25E>Le serveur ferme dans <white><t></white> (<r>)", Msg.p("t", Util.duration(this.total * 1000L)), Msg.p("r", this.reason)),
                  Times.times(Duration.ofMillis(300L), Duration.ofSeconds(4L), Duration.ofSeconds(1L))
               )
            );
            Util.sound(var7, "block.note_block.bell", 1.0F, 0.6F);
         }

         this.pl.getLogger().info("Fermeture programmee dans " + this.total + " s par " + var3 + " (" + this.reason + ").");
         this.task = Bukkit.getScheduler().runTaskTimer(this.pl, this::tick, 20L, 20L);
         return null;
      }
   }

   public boolean cancel(String var1) {
      if (this.task == null) {
         return false;
      } else {
         this.task.cancel();
         this.task = null;
         this.left = -1;
         this.hideBar();
         Msg.panthereAll("<#55FF88><bold>Fermeture annulée</bold></#55FF88>, vous pouvez continuer à jouer !");
         this.pl.getLogger().info("Fermeture annulee par " + var1 + ".");
         return true;
      }
   }

   private String barText() {
      return "<#FF5555><bold>Fermeture pour " + this.reason.replace("<", "") + "</bold></#FF5555> <white>dans " + clock(this.left) + "</white>";
   }

   private static String clock(int var0) {
      return var0 / 60 + ":" + String.format(Locale.ROOT, "%02d", var0 % 60);
   }

   private void hideBar() {
      if (this.bar != null) {
         for (Player var2 : Bukkit.getOnlinePlayers()) {
            var2.hideBossBar(this.bar);
         }

         this.bar = null;
      }
   }

   private void tick() {
      this.left--;
      if (this.bar != null) {
         this.bar.name(Msg.mm(this.barText()));
         this.bar.progress(Math.max(0.0F, Math.min(1.0F, (float)this.left / this.total)));
      }

      if (this.left == 120 || this.left == 60 || this.left == 30) {
         Msg.panthereAll("<#FF5555>Le serveur ferme dans <white><t></white>.</#FF5555>", Msg.p("t", Util.duration(this.left * 1000L)));

         for (Player var2 : Bukkit.getOnlinePlayers()) {
            Util.sound(var2, "block.note_block.bell", 1.0F, 0.8F);
         }
      }

      if (this.left <= 10 && this.left > 0) {
         for (Player var4 : Bukkit.getOnlinePlayers()) {
            var4.showTitle(
               Title.title(
                  Msg.mm("<#FF5555><bold>" + this.left + "</bold></#FF5555>"),
                  Msg.mm("<gray>Fermeture du serveur..."),
                  Times.times(Duration.ZERO, Duration.ofMillis(1100L), Duration.ZERO)
               )
            );
            Util.sound(var4, "block.note_block.hat", 1.0F, 1.0F + (10 - this.left) * 0.08F);
         }
      }

      if (this.left <= 0) {
         this.finish();
      }
   }

   private void finish() {
      this.task.cancel();
      this.task = null;
      this.hideBar();
      boolean var1 = this.reason.toLowerCase(Locale.ROOT).startsWith("redémarrage") || this.reason.toLowerCase(Locale.ROOT).startsWith("redemarrage");
      if (!var1) {
         this.pl.state().maintenance = true;
         this.pl.state().save();
      }

      try {
         this.pl.data().saveAll();
      } catch (Throwable var4) {
      }

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         var3.kick(
            Msg.mm(
               "<#FF5555><bold>Le serveur ferme (<r>).</bold></#FF5555>\n\n"
                  + (var1 ? "<gray>Il redémarre tout seul : reviens dans une minute !" : "<gray>On fait quelques réglages : reviens un peu plus tard !"),
               Msg.p("r", this.reason)
            )
         );
      }

      this.pl.getLogger().info("Fermeture : arret du serveur.");
      Bukkit.getScheduler().runTaskLater(this.pl, Bukkit::shutdown, 20L);
   }

   public boolean closed() {
      return this.pl.state().maintenance;
   }

   public void reopen(String var1) {
      this.pl.state().maintenance = false;
      this.pl.state().save();
      this.pl.getLogger().info("Maintenance terminee par " + var1 + " : serveur ouvert a tous.");
      Msg.staff("<white><a></white> a rouvert le serveur à tout le monde.", Msg.p("a", var1));
   }

   public void close(String var1) {
      this.pl.state().maintenance = true;
      this.pl.state().save();

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         if (this.pl.ranks().compute(var3.getUniqueId(), var3.getName()) == Ranks.Rank.JOUEUR && !var3.isOp()) {
            var3.kick(Msg.mm("<#FF5555><bold>Le serveur passe en maintenance.</bold></#FF5555>\n\n<gray>Reviens un peu plus tard !"));
         }
      }

      this.pl.getLogger().info("Mode maintenance active par " + var1 + ".");
      Msg.staff("<white><a></white> a mis le serveur en maintenance (staff uniquement).", Msg.p("a", var1));
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onPreLogin(AsyncPlayerPreLoginEvent var1) {
      if (this.pl.state().maintenance && var1.getLoginResult() == Result.ALLOWED) {
         if (!var1.getName().equals(this.pl.adminName()) && !this.pl.state().modos.containsKey(var1.getUniqueId())) {
            if (!Bukkit.getOfflinePlayer(var1.getUniqueId()).isOp()) {
               var1.disallow(
                  Result.KICK_OTHER,
                  Msg.mm("<#FFB020><bold>Serveur en maintenance</bold></#FFB020>\n\n<gray>On fait quelques réglages, reviens un peu plus tard !")
               );
            }
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      if (this.bar != null) {
         var1.getPlayer().showBossBar(this.bar);
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var1 instanceof Player var5 && !var5.isOp() && !this.pl.ranks().isAdmin(var5)) {
         Msg.err(var1, "Commande réservée aux OP.");
         return true;
      } else {
         String var11 = var1 instanceof Player var6 ? var6.getName() : "Console";
         String var12 = var4.length > 0 ? var4[0].toLowerCase(Locale.ROOT) : "";
         if (!var12.equals("annuler") && !var12.equals("stop") && !var12.equals("cancel")) {
            if (!var12.equals("fin") && !var12.equals("ouvrir") && !var12.equals("off")) {
               if (var12.equals("activer") || var12.equals("on")) {
                  this.close(var11);
                  Msg.ok(var1, "Mode maintenance : seuls le staff et les OP peuvent se connecter. <gray>Fin : /maintenance fin");
                  return true;
               } else if (var12.equals("redemarrer") || var12.equals("redémarrer")) {
                  String var13 = this.start(180, "redémarrage", var11);
                  if (var13 != null) {
                     Msg.err(var1, var13);
                  } else {
                     Msg.ok(var1, "Redémarrage dans <white>3 min</white>. <gray>Annuler : /maintenance annuler");
                  }

                  return true;
               } else if (!var12.equals("etat") && !var12.equals("état")) {
                  int var7 = 180;
                  byte var8 = 0;
                  if (!var12.isEmpty()) {
                     long var9 = Util.parseDuration(var12);
                     if (var9 > 0L) {
                        var7 = (int)(var9 / 1000L);
                        var8 = 1;
                     } else if (var12.matches("\\d+")) {
                        var7 = Integer.parseInt(var12) * 60;
                        var8 = 1;
                     }
                  }

                  String var14 = var4.length > var8 ? String.join(" ", Arrays.copyOfRange(var4, var8, var4.length)) : "maintenance";
                  String var10 = this.start(var7, var14, var11);
                  if (var10 != null) {
                     Msg.err(var1, var10);
                  } else {
                     Msg.ok(var1, "Fermeture dans <white><t></white>. <gray>Annuler : /maintenance annuler", Msg.p("t", Util.duration(var7 * 1000L)));
                  }

                  return true;
               } else {
                  Msg.info(
                     var1,
                     (this.running() ? "Fermeture dans <white><t></white>." : "Aucune fermeture programmée.")
                        + (this.closed() ? " <#FFB020>Mode maintenance actif</#FFB020> (/maintenance fin)." : " Serveur ouvert à tous."),
                     Msg.p("t", Util.duration(Math.max(0, this.left) * 1000L))
                  );
                  return true;
               }
            } else {
               if (!this.closed()) {
                  Msg.err(var1, "Le serveur est déjà ouvert à tous.");
               } else {
                  this.reopen(var11);
                  Msg.ok(var1, "Maintenance terminée : tout le monde peut se reconnecter.");
               }

               return true;
            }
         } else {
            if (!this.cancel(var11)) {
               Msg.err(var1, "Aucune fermeture en cours.");
            }

            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var4.length == 1) {
         var5.addAll(List.of("3m", "1m", "5m", "10m", "redemarrer", "annuler", "activer", "fin", "etat"));
         var5.removeIf(var1x -> !var1x.startsWith(var4[0].toLowerCase(Locale.ROOT)));
      }

      return var5;
   }
}
