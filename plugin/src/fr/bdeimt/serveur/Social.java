package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class Social implements CommandExecutor, TabCompleter, Listener {
   private final BDEIMT pl;
   private final Map<UUID, UUID> lastPartner = new HashMap<>();

   public Social(BDEIMT var1) {
      this.pl = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         String var6 = var2.getName();
         switch (var6) {
            case "msg":
               if (var4.length < 2) {
                  Msg.err(var5, "Utilisation : <white>/msg ‹joueur› ‹message›</white>");
                  return true;
               }

               Player var10 = Bukkit.getPlayerExact(var4[0]);
               this.send(var5, var10, String.join(" ", Arrays.copyOfRange(var4, 1, var4.length)));
               break;
            case "r":
               if (var4.length < 1) {
                  Msg.err(var5, "Utilisation : <white>/r ‹message›</white>");
                  return true;
               }

               UUID var8 = this.lastPartner.get(var5.getUniqueId());
               Player var9 = var8 == null ? null : Bukkit.getPlayer(var8);
               if (var9 == null) {
                  Msg.err(var5, "Personne à qui répondre.");
                  return true;
               }

               this.send(var5, var9, String.join(" ", var4));
               break;
            case "ignore":
               this.ignore(var5, var4);
         }

         return true;
      }
   }

   private void send(Player var1, Player var2, String var3) {
      if (var2 == null || !this.pl.auth().isLogged(var2) || !var1.canSee(var2)) {
         Msg.err(var1, "Joueur introuvable.");
      } else if (var2.equals(var1)) {
         Msg.err(var1, "Tu te parles à toi-même ?");
      } else {
         PlayerData var4 = this.pl.data().get(var1);
         if (var4.isMuted()) {
            Msg.err(var1, "Tu es muet : impossible d'envoyer des messages privés.");
         } else {
            PlayerData var5 = this.pl.data().get(var2);
            if (var5.ignores.contains(var1.getUniqueId())) {
               Msg.err(var1, "<white><n></white> ne reçoit pas tes messages.", Msg.p("n", var2.getName()));
            } else if (var4.ignores.contains(var2.getUniqueId())) {
               Msg.err(var1, "Tu ignores <white><n></white> (/ignore <n> pour arrêter).", Msg.p("n", var2.getName()));
            } else {
               if (var3.length() > 256) {
                  var3 = var3.substring(0, 256);
               }

               Msg.raw(var1, "<#C77DFF>✉ Toi → <white><n></white> :</#C77DFF> <#E8D7FF><m></#E8D7FF>", Msg.p("n", var2.getName()), Msg.p("m", var3));
               Msg.raw(
                  var2,
                  "<#C77DFF>✉ <white><n></white> → toi :</#C77DFF> <#E8D7FF><m></#E8D7FF> <click:suggest_command:'/r '><hover:show_text:'<gray>Répondre'><dark_gray>[répondre]</dark_gray></hover></click>",
                  Msg.p("n", var1.getName()),
                  Msg.p("m", var3)
               );
               Util.sound(var2, "entity.item.pickup", 0.6F, 1.8F);
               this.lastPartner.put(var1.getUniqueId(), var2.getUniqueId());
               this.lastPartner.put(var2.getUniqueId(), var1.getUniqueId());
            }
         }
      }
   }

   private void ignore(Player var1, String[] var2) {
      PlayerData var3 = this.pl.data().get(var1);
      if (var2.length >= 1 && !var2[0].equalsIgnoreCase("liste")) {
         PlayerData var8 = this.pl.data().byName(var2[0]);
         if (var8 == null) {
            Msg.err(var1, "Joueur inconnu.");
         } else if (var8.uuid.equals(var1.getUniqueId())) {
            Msg.err(var1, "Tu ne peux pas t'ignorer toi-même.");
         } else if (this.pl.ranks().compute(var8.uuid, var8.name) != Ranks.Rank.JOUEUR) {
            Msg.err(var1, "Tu ne peux pas ignorer le staff.");
         } else {
            if (var3.ignores.remove(var8.uuid)) {
               Msg.ok(var1, "Tu n'ignores plus <white><n></white>.", Msg.p("n", var8.name));
            } else {
               var3.ignores.add(var8.uuid);
               Msg.ok(var1, "Tu ignores <white><n></white> : tu ne verras plus ses messages, ni ses demandes.", Msg.p("n", var8.name));
            }

            var3.touch();
         }
      } else if (var3.ignores.isEmpty()) {
         Msg.info(var1, "Tu n'ignores personne. <white>/ignore ‹joueur›</white> pour ne plus voir quelqu'un.");
      } else {
         ArrayList var4 = new ArrayList();

         for (UUID var6 : var3.ignores) {
            PlayerData var7 = this.pl.data().get(var6);
            var4.add(var7 != null ? var7.name : var6.toString().substring(0, 8));
         }

         Msg.info(var1, "Tu ignores : <white><l></white>", Msg.p("l", String.join(", ", var4)));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.lastPartner.remove(var1.getPlayer().getUniqueId());
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var1 instanceof Player var6 && var4.length == 1 && !var2.getName().equals("r")) {
         for (Player var8 : Bukkit.getOnlinePlayers()) {
            if (!var8.equals(var6) && var6.canSee(var8) && this.pl.auth().isLogged(var8)) {
               var5.add(var8.getName());
            }
         }

         String var9 = var4[0].toLowerCase(Locale.ROOT);
         var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var9));
         return var5;
      } else {
         return var5;
      }
   }
}
