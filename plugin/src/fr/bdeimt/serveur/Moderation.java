package fr.bdeimt.serveur;

import io.papermc.paper.ban.BanListType;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

public final class Moderation implements CommandExecutor, TabCompleter, Listener {
   private final BDEIMT pl;

   public Moderation(BDEIMT var1) {
      this.pl = var1;
   }

   private boolean allowed(CommandSender var1) {
      if (var1 instanceof Player var2) {
         if (this.pl.ranks().isStaff(var2)) {
            return true;
         } else {
            Msg.err(var2, "Commande réservée au staff.");
            return false;
         }
      } else {
         return true;
      }
   }

   private boolean protectedTarget(CommandSender var1, PlayerData var2) {
      Ranks.Rank var3 = this.pl.ranks().compute(var2.uuid, var2.name);
      if (var3 == Ranks.Rank.JOUEUR) {
         return false;
      } else if (var1 instanceof Player var4 && this.pl.ranks().rankOf(var4) == Ranks.Rank.MOBUTU && var3 != Ranks.Rank.MOBUTU) {
         return false;
      } else if (!(var1 instanceof Player)) {
         return false;
      } else {
         Msg.err(var1, "Impossible de sanctionner un membre du staff.");
         return true;
      }
   }

   /** Le Mobutu et la console n'ont pas de limite ; un moderateur, si. */
   private boolean limited(CommandSender sender) {
      return sender instanceof Player p && !this.pl.ranks().isAdmin(p);
   }

   private static String reason(String[] var0, int var1) {
      return var0.length <= var1 ? "Non précisée" : String.join(" ", Arrays.copyOfRange(var0, var1, var0.length));
   }

   private static String by(CommandSender var0) {
      return var0 instanceof Player var1 ? var1.getName() : "Console";
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName();
      if (var5.equals("fly")) {
         this.fly(var1, var4);
         return true;
      } else if (!this.allowed(var1)) {
         return true;
      } else {
         switch (var5) {
            case "mute":
               this.mute(var1, var4);
               break;
            case "unmute":
               this.unmute(var1, var4);
               break;
            case "tempban":
               this.ban(var1, var4, true);
               break;
            case "ban":
               this.ban(var1, var4, false);
               break;
            case "unban":
               this.unban(var1, var4);
               break;
            case "kick":
               this.kick(var1, var4);
         }

         return true;
      }
   }

   private PlayerData target(CommandSender var1, String var2) {
      PlayerData var3 = this.pl.data().byName(var2);
      if (var3 == null) {
         Msg.err(var1, "Joueur inconnu : <white><n></white>.", Msg.p("n", var2));
      }

      return var3;
   }

   private void mute(CommandSender var1, String[] var2) {
      if (var2.length < 1) {
         Msg.err(var1, "Utilisation : <white>/mute ‹joueur› [durée] [raison]</white>  <gray>(ex : /mute Bob 30m spam)");
      } else {
         PlayerData var3 = this.target(var1, var2[0]);
         if (var3 != null && !this.protectedTarget(var1, var3)) {
            long var4 = var2.length >= 2 ? Util.parseDuration(var2[1]) : -1L;

            // Un moderateur ne rend muet que 24 h au plus, jamais « pour toujours ».
            if (this.limited(var1) && (var4 <= 0L || var4 > ModoCommand.MAX_MUTE)) {
               if (var4 > ModoCommand.MAX_MUTE) {
                  Msg.info(var1, "Un modérateur rend muet 24 h au maximum : durée ramenée à 24 h.");
               }

               var4 = var4 <= 0L ? 3600000L : ModoCommand.MAX_MUTE;
            }

            int var6 = var2.length >= 2 && Util.parseDuration(var2[1]) > 0L ? 2 : 1;
            String var7 = reason(var2, var6);
            var3.mutedUntil = var4 > 0L ? System.currentTimeMillis() + var4 : -1L;
            var3.muteReason = var7;
            var3.touch();
            this.pl.data().save(var3);
            String var8 = var4 > 0L ? "pour " + Util.duration(var4) : "jusqu'à nouvel ordre";
            Msg.staff(
               "<white><a></white> a rendu muet <white><n></white> " + var8 + " <dark_gray>(<r>)</dark_gray>",
               Msg.p("a", by(var1)),
               Msg.p("n", var3.name),
               Msg.p("r", var7)
            );
            Player var9 = Bukkit.getPlayer(var3.uuid);
            if (var9 != null) {
               Msg.err(var9, "Tu es muet " + var8 + ". Raison : <white><r></white>", Msg.p("r", var7));
            }
         }
      }
   }

   private void unmute(CommandSender var1, String[] var2) {
      if (var2.length < 1) {
         Msg.err(var1, "Utilisation : <white>/unmute ‹joueur›</white>");
      } else {
         PlayerData var3 = this.target(var1, var2[0]);
         if (var3 != null) {
            var3.mutedUntil = 0L;
            var3.muteReason = null;
            var3.touch();
            this.pl.data().save(var3);
            Msg.staff("<white><a></white> a rendu la parole à <white><n></white>.", Msg.p("a", by(var1)), Msg.p("n", var3.name));
            Player var4 = Bukkit.getPlayer(var3.uuid);
            if (var4 != null) {
               Msg.ok(var4, "Tu peux de nouveau parler.");
            }
         }
      }
   }

   private void ban(CommandSender var1, String[] var2, boolean var3) {
      if (var2.length < (var3 ? 2 : 1)) {
         Msg.err(
            var1,
            var3
               ? "Utilisation : <white>/tempban ‹joueur› ‹durée› [raison]</white>  <gray>(ex : 2h, 3j, 1sem)"
               : "Utilisation : <white>/ban ‹joueur› [raison]</white>"
         );
      } else {
         PlayerData var4 = this.target(var1, var2[0]);
         if (var4 != null && !this.protectedTarget(var1, var4)) {
            long var5 = var3 ? Util.parseDuration(var2[1]) : -1L;

            if (!var3 && this.limited(var1)) {
               Msg.err(var1, "Le bannissement définitif est réservé au Mobutu. Utilise <white>/tempban ‹pseudo› ‹durée› ‹raison›</white> (7 jours max).");
               return;
            }

            if (var3 && this.limited(var1) && var5 > ModoCommand.MAX_TEMPBAN) {
               Msg.info(var1, "Un modérateur bannit 7 jours au maximum : durée ramenée à 7 jours.");
               var5 = ModoCommand.MAX_TEMPBAN;
            }

            if (var3 && var5 <= 0L) {
               Msg.err(var1, "Durée invalide. Exemples : <white>30m</white>, <white>2h</white>, <white>3j</white>, <white>1sem</white>.");
            } else {
               String var7 = reason(var2, var3 ? 2 : 1);
               Date var8 = var3 ? new Date(System.currentTimeMillis() + var5) : null;
               OfflinePlayer var9 = Bukkit.getOfflinePlayer(var4.uuid);
               var9.ban(var7, var8, by(var1));
               Player var10 = Bukkit.getPlayer(var4.uuid);
               if (var10 != null) {
                  var10.kick(banScreen(var7, var8));
               }

               String var11 = var3 ? "pour " + Util.duration(var5) : "définitivement";
               Msg.staff(
                  "<white><a></white> a banni <white><n></white> " + var11 + " <dark_gray>(<r>)</dark_gray>",
                  Msg.p("a", by(var1)),
                  Msg.p("n", var4.name),
                  Msg.p("r", var7)
               );
            }
         }
      }
   }

   private void unban(CommandSender var1, String[] var2) {
      if (var2.length < 1) {
         Msg.err(var1, "Utilisation : <white>/unban ‹joueur›</white>");
      } else {
         PlayerData var3 = this.target(var1, var2[0]);
         if (var3 != null) {
            OfflinePlayer var4 = Bukkit.getOfflinePlayer(var3.uuid);
            if (!var4.isBanned()) {
               Msg.err(var1, "<white><n></white> n'est pas banni.", Msg.p("n", var3.name));
            } else {
               ((ProfileBanList)Bukkit.getBanList(BanListType.PROFILE)).pardon(var4.getPlayerProfile());
               Msg.staff("<white><a></white> a débanni <white><n></white>.", Msg.p("a", by(var1)), Msg.p("n", var3.name));
            }
         }
      }
   }

   private void kick(CommandSender var1, String[] var2) {
      if (var2.length < 1) {
         Msg.err(var1, "Utilisation : <white>/kick ‹joueur› [raison]</white>");
      } else {
         Player var3 = Bukkit.getPlayerExact(var2[0]);
         if (var3 == null) {
            Msg.err(var1, "Joueur non connecté.");
         } else {
            PlayerData var4 = this.pl.data().get(var3);
            if (!this.protectedTarget(var1, var4)) {
               String var5 = reason(var2, 1);
               var3.kick(
                  Msg.mm(
                     "<#FFB020><bold>Tu as été expulsé du serveur.</bold></#FFB020>\n\n<gray>Raison : <white><r></white>\n<gray>Tu peux revenir.",
                     Msg.p("r", var5)
                  )
               );
               Msg.staff(
                  "<white><a></white> a expulsé <white><n></white> <dark_gray>(<r>)</dark_gray>",
                  Msg.p("a", by(var1)),
                  Msg.p("n", var3.getName()),
                  Msg.p("r", var5)
               );
            }
         }
      }
   }

   private void fly(CommandSender var1, String[] var2) {
      if (!Msg.noConsole(var1)) {
         Player var3 = (Player)var1;
         if (this.pl.ranks().isStaff(var3)) {
            this.pl.fly().toggleStaff(var3);
         } else {
            this.pl.fly().toggleTimed(var3);
         }
      }
   }

   private static Component banScreen(String var0, Date var1) {
      String var2 = var1 == null
         ? "<#FF5555>définitif</#FF5555>"
         : "jusqu'au <white>" + new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(var1) + "</white>";
      return Msg.mm(
         "<#FF5555><bold>Tu es banni du serveur.</bold></#FF5555>\n\n<gray>Raison : <white><r></white>\n<gray>Bannissement " + var2,
         Msg.p("r", var0 == null ? "Non précisée" : var0)
      );
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onLogin(PlayerLoginEvent var1) {
      if (var1.getResult() == Result.KICK_BANNED) {
         try {
            BanList var2 = Bukkit.getBanList(BanListType.PROFILE);
            BanEntry var3 = var2.getBanEntry(var1.getPlayer().getPlayerProfile());
            if (var3 != null) {
               var1.kickMessage(banScreen(var3.getReason(), var3.getExpiration()));
            }
         } catch (Throwable var4) {
         }
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var4.length == 1 && !var2.getName().equals("fly")) {
         for (Player var7 : Bukkit.getOnlinePlayers()) {
            var5.add(var7.getName());
         }

         String var8 = var4[0].toLowerCase(Locale.ROOT);
         var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var8));
      } else if (var4.length == 2 && (var2.getName().equals("tempban") || var2.getName().equals("mute"))) {
         var5.addAll(List.of("10m", "30m", "1h", "6h", "1j", "3j", "1sem"));
      }

      return var5;
   }
}
