package fr.bdeimt.serveur;

import java.time.Duration;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
   public static final MiniMessage MM = MiniMessage.miniMessage();
   public static final String IA_POULPY = "<dark_gray>[</dark_gray><gradient:#4FC3FF:#1E6BFF><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#2E8BFF><bold>Poulpy</bold></#2E8BFF> <dark_gray>»</dark_gray> <#CFEAFF>";
   public static final String IA_PANTHERE = "<dark_gray>[</dark_gray><gradient:#FF9AC8:#FF2E93><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#FF5FAE><bold>LaPanthèreRose</bold></#FF5FAE> <dark_gray>»</dark_gray> <#FFD6EA>";
   private static final String OK = "<#55FF88>✔</#55FF88> <gray>";
   private static final String ERR = "<#FF5555>✖</#FF5555> <#FFB3B3>";
   private static final String INFO = "<dark_aqua>➜</dark_aqua> <gray>";

   private Msg() {
   }

   public static Component mm(String var0, TagResolver... var1) {
      return MM.deserialize(var0, var1);
   }

   public static TagResolver p(String var0, Object var1) {
      return Placeholder.unparsed(var0, String.valueOf(var1));
   }

   public static TagResolver c(String var0, Component var1) {
      return Placeholder.component(var0, var1);
   }

   public static void poulpy(Audience var0, String var1, TagResolver... var2) {
      var0.sendMessage(
         mm(
            "<dark_gray>[</dark_gray><gradient:#4FC3FF:#1E6BFF><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#2E8BFF><bold>Poulpy</bold></#2E8BFF> <dark_gray>»</dark_gray> <#CFEAFF>"
               + var1,
            var2
         )
      );
   }

   public static void panthere(Audience var0, String var1, TagResolver... var2) {
      var0.sendMessage(
         mm(
            "<dark_gray>[</dark_gray><gradient:#FF9AC8:#FF2E93><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#FF5FAE><bold>LaPanthèreRose</bold></#FF5FAE> <dark_gray>»</dark_gray> <#FFD6EA>"
               + var1,
            var2
         )
      );
   }

   public static void ok(Audience var0, String var1, TagResolver... var2) {
      var0.sendMessage(mm("<#55FF88>✔</#55FF88> <gray>" + var1, var2));
   }

   public static void err(Audience var0, String var1, TagResolver... var2) {
      var0.sendMessage(mm("<#FF5555>✖</#FF5555> <#FFB3B3>" + var1, var2));
   }

   public static void info(Audience var0, String var1, TagResolver... var2) {
      var0.sendMessage(mm("<dark_aqua>➜</dark_aqua> <gray>" + var1, var2));
   }

   /**
    * Un gros message au milieu de l'ecran, pour ce qu'il ne faut pas rater :
    * une demande qui attend une reponse, un avertissement avant une action
    * irreversible, l'ouverture du Traq.
    *
    * <p>Le chat defile et se perd ; un titre, non. A reserver aux choses
    * reellement importantes, sinon plus rien ne ressort.
    */
   public static void big(Player var0, String var1, String var2, long var3) {
      try {
         var0.showTitle(
            Title.title(
               mm(var1),
               mm(var2),
               Times.times(Duration.ofMillis(200L), Duration.ofMillis(Math.max(600L, var3)), Duration.ofMillis(600L))
            )
         );
      } catch (Throwable var6) {
      }
   }

   /** Le meme, avec un son d'alerte. */
   public static void alert(Player var0, String var1, String var2) {
      big(var0, var1, var2, 3500L);
      Util.sound(var0, "block.note_block.pling", 0.9F, 1.5F);
   }

   /** Un avertissement avant quelque chose d'irreversible : rouge, et long. */
   public static void danger(Player var0, String var1, String var2) {
      big(var0, "<#FF5555><bold>" + var1 + "</bold></#FF5555>", var2, 6000L);
      Util.sound(var0, "block.note_block.bass", 1.0F, 0.6F);
   }

   /** Une barre discrete au-dessus de la barre d'objets. */
   public static void bar(Player var0, String var1, TagResolver... var2) {
      try {
         var0.sendActionBar(mm(var1, var2));
      } catch (Throwable var4) {
      }
   }

   public static void raw(Audience var0, String var1, TagResolver... var2) {
      var0.sendMessage(mm(var1, var2));
   }

   public static void poulpyAll(String var0, TagResolver... var1) {
      broadcast(
         mm(
            "<dark_gray>[</dark_gray><gradient:#4FC3FF:#1E6BFF><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#2E8BFF><bold>Poulpy</bold></#2E8BFF> <dark_gray>»</dark_gray> <#CFEAFF>"
               + var0,
            var1
         )
      );
   }

   public static void panthereAll(String var0, TagResolver... var1) {
      broadcast(
         mm(
            "<dark_gray>[</dark_gray><gradient:#FF9AC8:#FF2E93><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#FF5FAE><bold>LaPanthèreRose</bold></#FF5FAE> <dark_gray>»</dark_gray> <#FFD6EA>"
               + var0,
            var1
         )
      );
   }

   public static void broadcast(Component var0) {
      BDEIMT var1 = BDEIMT.get();

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         if (var1.auth().isLogged(var3)) {
            var3.sendMessage(var0);
         }
      }

      Bukkit.getConsoleSender().sendMessage(var0);
   }

   /** Comme broadcast, mais seulement pour ceux qui sont dans la survie. */
   public static void broadcastSurvie(Component var0) {
      BDEIMT var1 = BDEIMT.get();

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         if (var1.auth().isLogged(var3) && var1.worlds().zoneOf(var3) == Zone.SURVIE) {
            var3.sendMessage(var0);
         }
      }

      Bukkit.getConsoleSender().sendMessage(var0);
   }

   /** Un message de LaPanthereRose, pour la survie seulement. */
   public static void panthereSurvie(String var0, TagResolver... var1) {
      broadcastSurvie(
         mm(
            "<dark_gray>[</dark_gray><gradient:#FF9AC8:#FF2E93><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#FF5FAE><bold>LaPanthèreRose</bold></#FF5FAE> <dark_gray>»</dark_gray> <#FFD6EA>"
               + var0,
            var1
         )
      );
   }

   /** Un message de Poulpy, pour la survie seulement. */
   public static void poulpySurvie(String var0, TagResolver... var1) {
      broadcastSurvie(
         mm(
            "<dark_gray>[</dark_gray><gradient:#4FC3FF:#1E6BFF><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#2E8BFF><bold>Poulpy</bold></#2E8BFF> <dark_gray>»</dark_gray> <#CFEAFF>"
               + var0,
            var1
         )
      );
   }

   /** Un message de Poulpy pour ceux d'un groupe de zones (lobby, skyblock...). */
   public static void poulpyGroup(String group, String var0, TagResolver... var1) {
      BDEIMT pl = BDEIMT.get();
      Component c = mm(
         "<dark_gray>[</dark_gray><gradient:#4FC3FF:#1E6BFF><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#2E8BFF><bold>Poulpy</bold></#2E8BFF> <dark_gray>»</dark_gray> <#CFEAFF>"
            + var0,
         var1
      );

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (pl.auth().isLogged(p) && pl.worlds().zoneOf(p).group.equals(group)) {
            p.sendMessage(c);
         }
      }
   }

   public static void staff(String var0, TagResolver... var1) {
      Component var2 = mm("<dark_gray>[</dark_gray><gold>Staff</gold><dark_gray>]</dark_gray> <gray>" + var0, var1);
      BDEIMT var3 = BDEIMT.get();

      for (Player var5 : Bukkit.getOnlinePlayers()) {
         if (var3.auth().isLogged(var5) && var3.ranks().isStaff(var5)) {
            var5.sendMessage(var2);
         }
      }

      Bukkit.getConsoleSender().sendMessage(var2);
   }

   public static boolean noConsole(CommandSender var0) {
      if (var0 instanceof Player) {
         return false;
      } else {
         var0.sendMessage("Commande reservee aux joueurs.");
         return true;
      }
   }
}
