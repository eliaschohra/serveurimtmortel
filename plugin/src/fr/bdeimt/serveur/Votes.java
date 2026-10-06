package fr.bdeimt.serveur;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public final class Votes implements CommandExecutor {
   public static final long COOLDOWN = 3600000L;
   private static final String OBJECTIVE = "bdeimt_votes";
   private final BDEIMT pl;
   private final Loots loots = new Loots();
   private final Set<UUID> rolling = new HashSet<>();

   public Votes(BDEIMT var1) {
      this.pl = var1;
   }

   public Loots loots() {
      return this.loots;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         if (var2.getName().equals("probavote")) {
            new Votes.ProbaMenu(0).open(var5);
            return true;
         } else if (var4.length >= 1) {
            // Dans le skyblock, /vote ‹pseudo› est une voix pour l'ile de quelqu'un.
            if (this.pl.worlds().zoneOf(var5).group.equals(Zone.SKYBLOCK.group)) {
               this.pl.skyblock().voteIsland(var5, var4[0]);
               return true;
            }

            // En survie, /vote ‹liste› prend le kit d'une liste du BDE.
            if (!this.pl.kits().voteList(var5, var4[0])) {
               Msg.err(var5, "Liste inconnue. Les listes : <white>imtmortel</white>, <white>zimtzimt</white>, <white>ascension</white>, <white>bartbart</white>, <white>wizart</white>, <white>passion</white>.");
            }

            return true;
         } else {
            this.vote(var5);
            return true;
         }
      }
   }

   private void vote(Player var1) {
      PlayerData var2 = this.pl.data().get(var1);
      long var3 = System.currentTimeMillis();
      long var5 = var2.lastVote + 3600000L;
      if (var3 < var5 && !this.pl.ranks().isAdmin(var1)) {
         Msg.panthere(
            var1,
            "Patience ! Ton prochain <white>/vote</white> sera possible dans <white><t></white>. En attendant, regarde tes chances avec <click:run_command:'/probavote'><#FF9AC8><u>/probavote</u></#FF9AC8></click>.",
            Msg.p("t", Util.duration(var5 - var3))
         );
      } else if (this.rolling.add(var1.getUniqueId())) {
         Loots.Loot var7 = this.loots.roll();
         var2.lastVote = var3;
         var2.votes++;
         synchronized (var2) {
            var2.pendingLoots.add(var7.id);
         }

         var2.touch();
         this.pl.data().save(var2);
         this.updateSidebar();
         this.animate(var1, var7, var2.votes);
      }
   }

   private void animate(final Player var1, final Loots.Loot var2, final int var3) {
      final List var4 = this.loots.all();
      (new BukkitRunnable() {
            int step = 0;

            public void run() {
               if (!var1.isOnline()) {
                  Votes.this.rolling.remove(var1.getUniqueId());
                  this.cancel();
               } else if (this.step < 14) {
                  Loots.Loot var1x = (Loots.Loot)var4.get(ThreadLocalRandom.current().nextInt(var4.size()));
                  var1.showTitle(
                     Title.title(
                        Msg.mm("<#FF9AC8>⚄ Tirage... ⚄</#FF9AC8>"),
                        Msg.mm(var1x.tier.styled(var1x.name)),
                        Times.times(Duration.ZERO, Duration.ofMillis(400L), Duration.ZERO)
                     )
                  );
                  Util.sound(var1, "block.note_block.hat", 0.8F, 0.6F + this.step * 0.08F);
                  this.step++;
               } else {
                  this.cancel();
                  Votes.this.rolling.remove(var1.getUniqueId());
                  Votes.this.reveal(var1, var2, var3);
               }
            }
         })
         .runTaskTimer(this.pl, 0L, 2L);
   }

   private void reveal(Player var1, Loots.Loot var2, int var3) {
      PlayerData var4 = this.pl.data().get(var1);
      boolean var5;
      synchronized (var4) {
         var5 = var4.pendingLoots.remove(var2.id);
      }

      if (var5) {
         this.grant(var1, var2);
         var4.touch();
         this.pl.data().save(var4);
         var1.showTitle(
            Title.title(
               Msg.mm(var2.tier.styled("<bold>✦ " + var2.tier.label + " ✦</bold>")),
               Msg.mm(var2.tier.styled(var2.name)),
               Times.times(Duration.ZERO, Duration.ofMillis(2500L), Duration.ofMillis(700L))
            )
         );
         switch (var2.tier) {
            case COMMUN:
               Util.sound(var1, "entity.experience_orb.pickup", 1.0F, 1.0F);
               break;
            case PEU_COMMUN:
               Util.sound(var1, "entity.player.levelup", 0.8F, 1.5F);
               break;
            case RARE:
               Util.sound(var1, "entity.player.levelup", 1.0F, 1.0F);
               break;
            default:
               Util.sound(var1, "ui.toast.challenge_complete", 1.0F, 1.0F);
         }

         Msg.panthere(
            var1,
            "Vote n°<white><n></white> : tu gagnes <lot> <dark_gray>(<rar>, <ch>)</dark_gray>. <gray>Prochain /vote dans 1 h.</gray>",
            Msg.p("n", var3),
            Msg.c("lot", Msg.mm(var2.tier.styled(var2.name))),
            Msg.c("rar", Msg.mm(var2.tier.styled(var2.tier.label))),
            Msg.p("ch", Util.pct(var2.chance))
         );
         if (var2.tier.announced()) {
            Component var6 = Msg.mm(var2.tier.styled("<bold>" + var2.tier.label + "</bold>") + "\n<gray>Chance : " + Util.pct(var2.chance) + "</gray>");
            Component var7 = Msg.mm(
                  "<dark_gray>[</dark_gray><gradient:#FF9AC8:#FF2E93><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#FF5FAE><bold>LaPanthèreRose</bold></#FF5FAE> <dark_gray>»</dark_gray> <#FFD6EA>"
                     + var2.tier.styled("<bold>✦ INCROYABLE ✦</bold>")
                     + " "
               )
               .append(this.pl.ranks().display(var1))
               .append(Msg.mm(" <#FFD6EA>vient de gagner</#FFD6EA> "))
               .append(Msg.mm(var2.tier.styled("<bold>" + var2.name + "</bold>")).hoverEvent(HoverEvent.showText(var6)))
               .append(Msg.mm(" <#FFD6EA>au /vote ! <dark_gray>(" + Util.pct(var2.chance) + " de chance)</dark_gray>"));
            Msg.broadcast(var7);

            for (Player var9 : Bukkit.getOnlinePlayers()) {
               if (!var9.equals(var1) && this.pl.auth().isLogged(var9)) {
                  Util.sound(var9, "ui.toast.challenge_complete", 0.6F, 1.2F);
               }
            }

            if (var2.tier == Loots.Tier.MYTHIQUE) {
               Util.soundAll("entity.ender_dragon.growl", 0.5F, 1.3F);
            }
         }
      }
   }

   public void grant(Player var1, Loots.Loot var2) {
      List var3 = var2.items.get();
      if (!var3.isEmpty() && Util.give(var1, var3)) {
         Msg.panthere(var1, "Ton inventaire était plein : le reste est tombé à tes pieds.");
      }

      if (var2.flySeconds > 0) {
         this.pl.fly().add(var1, var2.flySeconds);
      }

      if (var2.bonusAura > 0L) {
         PlayerData var4 = this.pl.data().get(var1);
         var4.aura = var4.aura + var2.bonusAura;
         var4.touch();
         this.updateSidebar();
      }

      if (var2.bonusVotes > 0) {
         PlayerData var5 = this.pl.data().get(var1);
         var5.votes = var5.votes + var2.bonusVotes;
         var5.touch();
         this.updateSidebar();
      }
   }

   public void onLogin(Player var1) {
      PlayerData var2 = this.pl.data().get(var1);
      ArrayList<String> var3;
      synchronized (var2) {
         var3 = new ArrayList<>(var2.pendingLoots);
         var2.pendingLoots.clear();
      }

      if (!var3.isEmpty()) {
         for (String var5 : var3) {
            Loots.Loot var6 = this.loots.get(var5);
            if (var6 != null) {
               this.grant(var1, var6);
               Msg.panthere(var1, "Voici le lot de ton dernier vote : <lot>.", Msg.c("lot", Msg.mm(var6.tier.styled(var6.name))));
            }
         }

         var2.touch();
         this.pl.data().save(var2);
      }
   }

   public void updateSidebar() {
      Scoreboard var1 = Bukkit.getScoreboardManager().getMainScoreboard();
      Objective var2 = var1.getObjective("bdeimt_votes");
      if (var2 == null) {
         var2 = var1.registerNewObjective("bdeimt_votes", Criteria.DUMMY, Msg.mm("<gradient:#FF9AC8:#FF2E93><bold>★ CLASSEMENTS ★</bold></gradient>"));
      } else {
         var2.displayName(Msg.mm("<gradient:#FF9AC8:#FF2E93><bold>★ CLASSEMENTS ★</bold></gradient>"));
      }

      var2.setDisplaySlot(DisplaySlot.SIDEBAR);
      LinkedHashMap<String, Votes.Line> var3 = new LinkedHashMap<>();
      int var4 = 100;
      var3.put("~vh", new Votes.Line(var4--, "<#FF9AC8><bold>Votes</bold>", ""));
      List var5 = this.pl.data().ranking();
      if (var5.isEmpty()) {
         var3.put("~v0", new Votes.Line(var4--, "<gray>Faites /vote !", ""));
      }

      for (int var6 = 0; var6 < Math.min(5, var5.size()); var6++) {
         PlayerData var7 = (PlayerData)var5.get(var6);
         var3.put("~v" + (var6 + 1), new Votes.Line(var4--, "<gray>" + (var6 + 1) + ".</gray> <white>" + var7.name, "<#FFC93C>" + var7.votes));
      }

      var3.put("~sep", new Votes.Line(var4--, " ", ""));
      var3.put("~ah", new Votes.Line(var4--, "<gradient:#C77DFF:#FF2E93><bold>Aura</bold></gradient>", ""));
      List var19 = this.pl.data().auraRanking();
      if (var19.isEmpty()) {
         var3.put("~a0", new Votes.Line(var4--, "<gray>Critiques en combo !", ""));
      }

      String[] var20 = new String[]{"<#FFD700>①</#FFD700>", "<#C0C0C0>②</#C0C0C0>", "<#CD7F32>③</#CD7F32>"};

      for (int var8 = 0; var8 < Math.min(3, var19.size()); var8++) {
         PlayerData var9 = (PlayerData)var19.get(var8);
         var3.put("~a" + (var8 + 1), new Votes.Line(var4--, var20[var8] + " <white>" + var9.name, "<#C77DFF>" + Aura.compact(var9.aura)));
      }

      var3.put("~lh", new Votes.Line(var4--, "<gradient:#55FF88:#4FC3FF><bold>Listes</bold></gradient>", ""));
      List var21 = this.pl.kits().listRanking();

      for (int var22 = 0; var22 < Math.min(3, var21.size()); var22++) {
         Entry var10 = (Entry)var21.get(var22);
         var3.put(
            "~l" + (var22 + 1),
            new Votes.Line(var4--, var20[var22] + " <white>" + this.pl.kits().listName((String)var10.getKey()), "<#55FF88>" + var10.getValue())
         );
      }

      for (String var25 : new ArrayList<String>(var1.getEntries())) {
         if (!var3.containsKey(var25) && var2.getScore(var25).isScoreSet()) {
            var2.getScore(var25).resetScore();
         }
      }

      for (Entry<String, Votes.Line> var26 : var3.entrySet()) {
         Votes.Line var11 = (Votes.Line)var26.getValue();
         Score var12 = var2.getScore((String)var26.getKey());
         var12.setScore(var11.order());

         try {
            var12.customName(Msg.mm(var11.name()));
            var12.numberFormat((NumberFormat)(var11.value().isEmpty() ? NumberFormat.blank() : NumberFormat.fixed(Msg.mm(var11.value()))));
         } catch (Throwable var14) {
         }
      }
   }

   private record Line(int order, String name, String value) {
   }

   private final class ProbaMenu extends Menu {
      private static final int PER_PAGE = 45;
      private final int page;
      private final int pages;

      ProbaMenu(int nullx) {
         super(6, Msg.mm("<#FF2E93><bold>Lots du /vote</bold></#FF2E93> <dark_gray>— page " + (nullx + 1) + "</dark_gray>"));
         List var3 = Votes.this.loots.sortedRarestFirst();
         this.pages = (var3.size() + 45 - 1) / 45;
         this.page = Math.max(0, Math.min(nullx, this.pages - 1));
         int var4 = this.page * 45;

         for (int var5 = 0; var5 < 45 && var4 + var5 < var3.size(); var5++) {
            this.inv.setItem(var5, this.icon((Loots.Loot)var3.get(var4 + var5)));
         }

         ItemStack var11 = Util.item(Material.BLACK_STAINED_GLASS_PANE, 1, " ");

         for (int var6 = 45; var6 < 54; var6++) {
            this.inv.setItem(var6, var11);
         }

         if (this.page > 0) {
            this.inv.setItem(45, Util.item(Material.ARROW, 1, "<white>← Page précédente"));
         }

         if (this.page < this.pages - 1) {
            this.inv.setItem(53, Util.item(Material.ARROW, 1, "<white>Page suivante →"));
         }

         ArrayList<String> var12 = new ArrayList<>();
         var12.add("<gray>Un /vote par heure : un lot au hasard.");
         var12.add("<gray>Les lots <#FFC93C>Légendaires</#FFC93C> <gray>et <#FF2E93>Mythiques</#FF2E93>");
         var12.add("<gray>sont annoncés à tout le serveur !");
         var12.add(" ");

         for (Loots.Tier var10 : Loots.Tier.values()) {
            var12.add(var10.styled(var10.label) + " <dark_gray>— <white>" + Util.pct(var10.share / 100.0) + " au total");
         }

         this.inv.setItem(49, Util.item(Material.NETHER_STAR, 1, "<#FF9AC8><bold>Comment ça marche ?</bold>", var12.toArray(new String[0])));
      }

      private ItemStack icon(Loots.Loot var1) {
         ItemStack var2 = new ItemStack(var1.icon, Math.max(1, Math.min(64, var1.iconAmount)));
         ArrayList var3 = new ArrayList();
         var3.add("<gray>Rareté : " + var1.tier.styled(var1.tier.label));
         var3.add("<gray>Chance : <white>" + Util.pct(var1.chance));
         if (var1.tier.announced()) {
            var3.add("<#FFC93C>★ Annoncé à tout le serveur");
         }

         Util.named(var2, var1.tier.styled("<bold>" + var1.name + "</bold>"));
         Util.lore(var2, var3);
         return var2;
      }

      @Override
      public void click(Player var1, int var2) {
         if (var2 == 45 && this.page > 0) {
            Votes.this.new ProbaMenu(this.page - 1).open(var1);
         } else if (var2 == 53 && this.page < this.pages - 1) {
            Votes.this.new ProbaMenu(this.page + 1).open(var1);
         }
      }
   }
}
