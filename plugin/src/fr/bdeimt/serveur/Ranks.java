package fr.bdeimt.serveur;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class Ranks implements Listener {
   private static final String[] MODO_PERMS = new String[]{
      "sv.use",
      "sv.see",
      "sv.list",
      "sv.toggleitems",
      "coreprotect.inspect",
      "coreprotect.lookup",
      "coreprotect.rollback",
      "coreprotect.restore",
      "coreprotect.co",
      "coreprotect.help",
      "grim.alerts",
      "grim.alerts.enable-on-join",
      "grim.verbose",
      "bdeimt.modo"
   };
   private final BDEIMT pl;
   private final Map<UUID, Ranks.Rank> cache = new ConcurrentHashMap<>();
   private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
   private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

   public Ranks(BDEIMT var1) {
      this.pl = var1;
   }

   public Ranks.Rank rankOf(Player var1) {
      Ranks.Rank var2 = this.cache.get(var1.getUniqueId());
      return var2 != null ? var2 : this.compute(var1.getUniqueId(), var1.getName());
   }

   public Ranks.Rank compute(UUID var1, String var2) {
      if (var2 != null && var2.equals(this.pl.adminName())) {
         return Ranks.Rank.MOBUTU;
      } else {
         return this.pl.state().modos.containsKey(var1) ? Ranks.Rank.MODO : Ranks.Rank.JOUEUR;
      }
   }

   public boolean isAdmin(Player var1) {
      return this.pl.auth().isLogged(var1) && this.rankOf(var1) == Ranks.Rank.MOBUTU;
   }

   public boolean isStaff(Player var1) {
      Ranks.Rank var2 = this.rankOf(var1);
      return this.pl.auth().isLogged(var1) && (var2 == Ranks.Rank.MOBUTU || var2 == Ranks.Rank.MODO);
   }

   public String prefix(Player var1) {
      return this.rankOf(var1).prefix;
   }

   public void apply(Player var1) {
      Ranks.Rank var2 = this.compute(var1.getUniqueId(), var1.getName());
      this.cache.put(var1.getUniqueId(), var2);
      Scoreboard var3 = Bukkit.getScoreboardManager().getMainScoreboard();

      for (Ranks.Rank var7 : Ranks.Rank.values()) {
         Team var8 = this.team(var3, var7);
         if (var7 == var2) {
            var8.addEntry(var1.getName());
         } else if (var8.hasEntry(var1.getName())) {
            var8.removeEntry(var1.getName());
         }
      }

      PermissionAttachment var11 = this.attachments.remove(var1.getUniqueId());
      if (var11 != null) {
         try {
            var1.removeAttachment(var11);
         } catch (IllegalArgumentException var10) {
         }
      }

      if (var2 == Ranks.Rank.MODO) {
         PermissionAttachment var12 = var1.addAttachment(this.pl);

         for (String var9 : MODO_PERMS) {
            var12.setPermission(var9, true);
         }

         this.attachments.put(var1.getUniqueId(), var12);
      }

      var1.recalculatePermissions();
      var1.updateCommands();
   }

   public void setupTeams() {
      Scoreboard var1 = Bukkit.getScoreboardManager().getMainScoreboard();

      for (Ranks.Rank var5 : Ranks.Rank.values()) {
         this.team(var1, var5);
      }
   }

   private Team team(Scoreboard var1, Ranks.Rank var2) {
      Team var3 = var1.getTeam(var2.team);
      if (var3 == null) {
         var3 = var1.registerNewTeam(var2.team);
      }

      var3.prefix(Msg.mm(var2.prefix + " "));
      var3.color(NamedTextColor.WHITE);
      return var3;
   }

   public void forget(Player var1) {
      this.cache.remove(var1.getUniqueId());
      PermissionAttachment var2 = this.attachments.remove(var1.getUniqueId());
      if (var2 != null) {
         try {
            var1.removeAttachment(var2);
         } catch (IllegalArgumentException var4) {
         }
      }

      this.lastChat.remove(var1.getUniqueId());
      this.lastMessage.remove(var1.getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.forget(var1.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent var1) {
      Player var2 = var1.getPlayer();
      if (this.pl.auth().isLogged(var2)) {
         PlayerData var3 = this.pl.data().get(var2.getUniqueId());
         if (var3 != null && var3.isMuted()) {
            var1.setCancelled(true);
            String var9 = var3.mutedUntil == -1L ? "jusqu'à nouvel ordre" : "encore " + Util.duration(var3.mutedUntil - System.currentTimeMillis());
            Msg.err(var2, "Tu es muet <white><fin></white>.", Msg.p("fin", var9));
         } else {
            long var4 = System.currentTimeMillis();
            String var6 = PlainTextComponentSerializer.plainText().serialize(var1.message());
            if (!this.isStaff(var2)) {
               Long var7 = this.lastChat.get(var2.getUniqueId());
               if (var7 != null && var4 - var7 < 1000L) {
                  var1.setCancelled(true);
                  Msg.err(var2, "Doucement, un message par seconde.");
                  return;
               }

               String var8 = this.lastMessage.get(var2.getUniqueId());
               if (var8 != null && var8.equalsIgnoreCase(var6) && var7 != null && var4 - var7 < 15000L) {
                  var1.setCancelled(true);
                  Msg.err(var2, "Tu viens d'envoyer ce message.");
                  return;
               }
            }

            this.lastChat.put(var2.getUniqueId(), var4);
            this.lastMessage.put(var2.getUniqueId(), var6);
            // Chaque univers a son chat : on ne lit que les messages des gens
            // qui sont dans le meme groupe de mondes que soi.
            String var11 = this.pl.worlds().zoneOf(var2).group;
            var1.viewers().removeIf(var2x -> {
               if (var2x instanceof Player var3x) {
                  if (!this.pl.auth().isLogged(var3x)) {
                     return true;
                  } else if (!this.pl.worlds().zoneOf(var3x).group.equals(var11)) {
                     return true;
                  } else {
                     PlayerData var4x = this.pl.data().get(var3x.getUniqueId());
                     return var4x != null && var4x.ignores.contains(var2.getUniqueId());
                  }
               } else {
                  return false;
               }
            });
            Component var10 = Msg.mm(this.prefix(var2) + this.pl.lists().tabSuffix(var2));
            var1.renderer(
               ChatRenderer.viewerUnaware(
                  (var1x, var2x, var3x) -> var10.append(Component.space())
                     .append(Component.text(var1x.getName(), NamedTextColor.WHITE))
                     .append(Msg.mm(" <dark_gray>»</dark_gray> "))
                     .append(var3x.colorIfAbsent(NamedTextColor.WHITE))
               )
            );
         }
      }
   }

   public Component display(Player var1) {
      return Msg.mm(this.prefix(var1) + " <white><n></white>", Msg.p("n", var1.getName()));
   }

   public static boolean isAudiencePlayer(Audience var0) {
      return var0 instanceof Player;
   }

   public static enum Rank {
      MOBUTU("0_mobutu", "<#FF3B3B><bold>[Mobutu]</bold></#FF3B3B>"),
      MODO("1_modo", "<#FFB020><bold>[Modérateur]</bold></#FFB020>"),
      JOUEUR("2_joueur", "<#3DDC6A><bold>[Joueur]</bold></#3DDC6A>");

      public final String team;
      public final String prefix;

      private Rank(String nullxx, String nullxxx) {
         this.team = nullxx;
         this.prefix = nullxxx;
      }
   }
}
