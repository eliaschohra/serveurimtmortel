package fr.bdeimt.serveur;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class Aura implements Listener, CommandExecutor {
   private static final long COMBO_WINDOW_MS = 5000L;
   private static final int MAX_COMBO = 20;
   private static final long[] REWARDS = new long[]{
      100L,
      500L,
      1000L,
      5000L,
      10000L,
      50000L,
      100000L,
      500000L,
      1000000L,
      5000000L,
      10000000L,
      50000000L,
      100000000L,
      500000000L,
      1000000000L,
      5000000000L,
      10000000000L,
      50000000000L,
      100000000000L,
      1000000000000L
   };
   private final BDEIMT pl;
   private final Map<UUID, Aura.Combo> combos = new HashMap<>();
   private long lastBoard;
   private static final DecimalFormatSymbols SYM = new DecimalFormatSymbols(Locale.FRANCE);
   private static final DecimalFormat PLAIN = new DecimalFormat("#,##0", SYM);
   private static final DecimalFormat SCI = new DecimalFormat("0.##", SYM);
   private static final char[] SUP = new char[]{'⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹'};
   private static final float[] SCALE = new float[]{0.5F, 0.56F, 0.63F, 0.75F, 0.84F, 1.0F, 1.12F, 1.26F, 1.5F, 1.68F, 2.0F};
   private final Map<UUID, BukkitTask> music = new HashMap<>();

   public Aura(BDEIMT var1) {
      this.pl = var1;
   }

   public static String fmt(long var0) {
      if (var0 < 1000000L) {
         return PLAIN.format(var0);
      } else {
         int var2 = (int)Math.floor(Math.log10(var0));
         double var3 = var0 / Math.pow(10.0, var2);
         StringBuilder var5 = new StringBuilder();

         for (char var9 : String.valueOf(var2).toCharArray()) {
            var5.append(SUP[var9 - '0']);
         }

         String var10 = SCI.format(var3);
         return (var10.equals("1") ? "" : var10 + "·") + "10" + var5;
      }
   }

   public static String compact(long var0) {
      return fmt(var0);
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      Player var2 = this.attacker(var1);
      if (var2 != null && this.pl.auth().isLogged(var2) && this.pl.worlds().zoneOf(var2) == Zone.SURVIE) {
         if (!var1.isCritical()) {
            Aura.Combo var11 = this.combos.get(var2.getUniqueId());
            if (var11 != null && var11.size >= 3) {
               var2.sendActionBar(Msg.mm("<#FF5555>Combo cassé (coup non critique)</#FF5555>"));
            }

            this.combos.remove(var2.getUniqueId());
         } else if (var1.getEntity() instanceof LivingEntity var3 && !var3.equals(var2)) {
            if (!(var3 instanceof Player var12 && !this.pl.auth().isLogged(var12))) {
               Aura.Combo var13 = this.combos.computeIfAbsent(var2.getUniqueId(), var0 -> new Aura.Combo());
               long var5 = System.currentTimeMillis();
               if (var5 - var13.last > 5000L) {
                  var13.size = 0;
               }

               var13.last = var5;
               var13.lastTarget = var3.getUniqueId();
               var13.size = Math.min(20, var13.size + 1);
               long var7 = REWARDS[var13.size - 1];
               PlayerData var9 = this.pl.data().get(var2);
               var9.aura += var7;
               var9.touch();
               if (var5 - this.lastBoard > 2000L) {
                  this.lastBoard = var5;
                  this.pl.votes().updateSidebar();
               }

               this.feedback(var2, var3, var13.size, var7);
               this.music(var2, var13.size);
               if (var3 instanceof Player var10) {
                  this.hitByCombo(var10, var2, var13.size);
                  this.music(var10, var13.size);
               }
            }
         }
      }
   }

   private Player attacker(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var5) {
         return var5;
      } else {
         return var1.getDamager() instanceof Projectile var2 && var2.getShooter() instanceof Player var6 ? var6 : null;
      }
   }

   private void feedback(Player var1, LivingEntity var2, int var3, long var4) {
      String var6 = tierColor(var3);
      var1.sendActionBar(Msg.mm(var6 + "<bold>+" + fmt(var4) + " AURA</bold></" + strip(var6) + "> <gray>Combo <white>×" + var3 + "</white></gray>"));
      Util.sound(var1, "block.note_block.pling", 0.6F, Math.min(2.0F, 0.6F + var3 * 0.15F));
      Location var7 = var2.getEyeLocation().add(0.0, 0.8 + (Math.random() * 0.4 - 0.2), 0.0);
      TextDisplay var8 = (TextDisplay)var7.getWorld().spawn(var7, TextDisplay.class, var3x -> {
         var3x.text(Msg.mm(var6 + "<bold>+" + fmt(var4) + " AURA</bold>"));
         var3x.setBillboard(Billboard.CENTER);
         var3x.setShadowed(true);
         var3x.setPersistent(false);
         var3x.setTeleportDuration(25);
      });
      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (var8.isValid()) {
            var8.teleport(var8.getLocation().add(0.0, 1.2, 0.0));
         }
      }, 2L);
      Bukkit.getScheduler().runTaskLater(this.pl, var8::remove, 30L);
      if (var3 == 3 || var3 == 5 || var3 == 10 || var3 == 15 || var3 == 20) {
         var1.showTitle(
            Title.title(
               Msg.mm(var6 + "<bold>COMBO ×" + var3 + "</bold>"),
               Msg.mm("<gray>+<white>" + fmt(var4) + "</white> aura</gray>"),
               Times.times(Duration.ZERO, Duration.ofMillis(900L), Duration.ofMillis(300L))
            )
         );

         String var9 = switch (var3) {
            case 3 -> "entity.experience_orb.pickup";
            case 5 -> "block.note_block.bell";
            case 10 -> "entity.ender_dragon.growl";
            case 15 -> "ui.toast.challenge_complete";
            default -> "entity.wither.spawn";
         };
         Util.sound(var1, var9, 1.0F, var3 >= 15 ? 1.0F : 1.4F);
         if (var3 >= 10 && var2 instanceof Player) {
            Msg.broadcast(
               Msg.mm(
                     "<dark_gray>[</dark_gray><gradient:#FF9AC8:#FF2E93><bold>IA</bold></gradient><dark_gray>]</dark_gray> <#FF5FAE><bold>LaPanthèreRose</bold></#FF5FAE> <dark_gray>»</dark_gray> <#FFD6EA>"
                        + var6
                        + "<bold>★ COMBO ×"
                        + var3
                        + " ★</bold></"
                        + strip(var6)
                        + "> "
                  )
                  .append(this.pl.ranks().display(var1))
                  .append(Msg.mm(" <#FFD6EA>enchaîne les critiques sur</#FFD6EA> "))
                  .append(this.pl.ranks().display((Player)var2))
                  .append(Msg.mm(" <gray>(+" + fmt(var4) + " aura)</gray>"))
            );
         }
      }
   }

   private void hitByCombo(Player var1, Player var2, int var3) {
      if (var3 >= 5) {
         int var4 = Math.min(2, (var3 - 5) / 5);
         var1.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60 + var3 * 5, var4, false, false, false));
         if (var3 >= 6) {
            var1.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40 + var3 * 4, 0, false, false, false));
         }

         if (var3 >= 6) {
            var1.showTitle(
               Title.title(
                  Msg.mm("<#FF2E93><bold>COMBO SUBI ×" + var3 + "</bold></#FF2E93>"),
                  Msg.mm("<#FFD6EA>" + var2.getName() + " t'enchaîne !</#FFD6EA>"),
                  Times.times(Duration.ZERO, Duration.ofMillis(700L), Duration.ofMillis(200L))
               )
            );
         }
      }
   }

   private void music(Player var1, int var2) {
      BukkitTask var3 = this.music.remove(var1.getUniqueId());
      if (var3 != null) {
         var3.cancel();
      }

      if (var2 >= 2) {
         int var4 = Math.min(16, var2);
         float var5 = Math.min(1.0F, 0.25F + var2 * 0.04F);
         String var6 = var2 < 8 ? "block.note_block.harp" : (var2 < 14 ? "block.note_block.bell" : "block.note_block.chime");
         int[] var7 = new int[]{0};
         this.music.put(var1.getUniqueId(), Bukkit.getScheduler().runTaskTimer(this.pl, () -> {
            if (var1.isOnline() && var7[0] < var4) {
               float var8 = SCALE[Math.min(SCALE.length - 1, (var7[0] + var2 / 3) % SCALE.length)];
               Util.sound(var1, var6, var5, var8);
               if (var2 >= 10 && var7[0] % 4 == 0) {
                  Util.sound(var1, "block.note_block.basedrum", var5, 1.0F);
               }

               var7[0]++;
            } else {
               BukkitTask var7x = this.music.remove(var1.getUniqueId());
               if (var7x != null) {
                  var7x.cancel();
               }
            }
         }, 0L, 3L));
      }
   }

   private static String tierColor(int var0) {
      if (var0 < 3) {
         return "<#BFBFBF>";
      } else if (var0 < 5) {
         return "<#55FF88>";
      } else if (var0 < 10) {
         return "<#4FC3FF>";
      } else if (var0 < 15) {
         return "<#C77DFF>";
      } else {
         return var0 < 20 ? "<#FFC93C>" : "<gradient:#FF2E93:#FFC93C>";
      }
   }

   private static String strip(String var0) {
      return var0.startsWith("<gradient") ? "gradient" : var0.substring(1, var0.length() - 1);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent var1) {
      PlayerData var2 = this.pl.data().get(var1.getEntity().getUniqueId());
      if (var2 != null && var2.aura > 0L) {
         long var3 = var2.aura;
         var2.aura = 0L;
         var2.touch();
         this.combos.remove(var1.getEntity().getUniqueId());
         Msg.panthere(var1.getEntity(), "Ton aura s'évapore avec toi : <white>-" + fmt(var3) + "</white> perdus.");
         this.pl.votes().updateSidebar();
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.combos.remove(var1.getPlayer().getUniqueId());
      BukkitTask var2 = this.music.remove(var1.getPlayer().getUniqueId());
      if (var2 != null) {
         var2.cancel();
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         PlayerData var6 = this.pl.data().get(var5);
         int var7 = this.pl.data().auraRankOf(var5.getUniqueId());
         Msg.panthere(
            var5,
            "Ton aura : <white><a></white> <gray>(rang <white><r></white>)</gray>",
            Msg.p("a", fmt(var6.aura)),
            Msg.p("r", var7 == 0 ? "non classé" : "n°" + var7)
         );
         Msg.panthere(
            var5,
            "L'aura se gagne en enchaînant les coups critiques (frappe en retombant d'un saut). Un coup normal ou 5 s d'attente cassent le combo. Mourir = tout perdre."
         );
         List var8 = this.pl.data().auraRanking();
         if (!var8.isEmpty()) {
            Msg.raw(var5, " <#FFC93C>Top aura :</#FFC93C>");

            for (int var9 = 0; var9 < Math.min(3, var8.size()); var9++) {
               PlayerData var10 = (PlayerData)var8.get(var9);
               Msg.raw(var5, "  <#FFC93C>" + (var9 + 1) + ".</#FFC93C> <white>" + var10.name + "</white> <gray>— " + fmt(var10.aura));
            }
         }

         return true;
      }
   }

   static {
      SYM.setGroupingSeparator(' ');
      SYM.setDecimalSeparator(',');
   }

   private static final class Combo {
      int size;
      long last;
      UUID lastTarget;
   }
}
