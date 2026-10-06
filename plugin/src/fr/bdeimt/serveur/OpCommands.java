package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class OpCommands implements CommandExecutor, TabCompleter {
   private final BDEIMT pl;
   private final AdminCommand admin;

   public OpCommands(BDEIMT var1, AdminCommand var2) {
      this.pl = var1;
      this.admin = var2;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var1 instanceof Player var5 && !var5.isOp() && !this.pl.ranks().isAdmin(var5)) {
         Msg.err(var1, "Commande réservée aux OP.");
         return true;
      } else if (var4.length == 0) {
         Msg.info(var1, "<white>/moderateur ajouter ‹pseudo›</white> · <white>/moderateur retirer ‹pseudo›</white> · <white>/moderateur liste</white>");
         return true;
      } else {
         String[] var6 = new String[var4.length + 1];
         var6[0] = "moderateur";
         System.arraycopy(var4, 0, var6, 1, var4.length);
         this.admin.modo(var1, var6);
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var4.length == 1) {
         var5.addAll(List.of("ajouter", "retirer", "liste"));
      } else if (var4.length == 2) {
         if (var4[0].equalsIgnoreCase("retirer")) {
            var5.addAll(this.pl.state().modos.values());
         } else {
            for (Player var7 : Bukkit.getOnlinePlayers()) {
               var5.add(var7.getName());
            }
         }
      }

      String var8 = var4[var4.length - 1].toLowerCase(Locale.ROOT);
      var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var8));
      return var5;
   }
}
