package fr.bdeimt.serveur;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Fly implements Listener {
   private final BDEIMT pl;
   private final Set<UUID> staffFly = new HashSet<>();
   private final Set<UUID> paused = new HashSet<>();

   public Fly(BDEIMT var1) {
      this.pl = var1;
   }

   public void add(Player var1, int var2) {
      PlayerData var3 = this.pl.data().get(var1);
      var3.flySeconds += var2;
      var3.touch();
      this.paused.remove(var1.getUniqueId());
      this.apply(var1);
      Msg.panthere(
         var1,
         "<white>Fly activé</white> pour <white><t></white> au total ! Double saut pour décoller, <white>/fly</white> pour le mettre en pause.",
         Msg.p("t", Util.duration(var3.flySeconds * 1000L))
      );
   }

   private boolean survivalLike(Player var1) {
      return var1.getGameMode() == GameMode.SURVIVAL || var1.getGameMode() == GameMode.ADVENTURE;
   }

   public void apply(Player var1) {
      if (this.pl.auth().isLogged(var1) && !this.pl.lobby().isLobby(var1.getWorld()) && this.survivalLike(var1)) {
         PlayerData var2 = this.pl.data().get(var1);
         boolean var3 = this.staffFly.contains(var1.getUniqueId()) || var2.flySeconds > 0 && !this.paused.contains(var1.getUniqueId());
         if (var1.getAllowFlight() != var3) {
            var1.setAllowFlight(var3);
            if (!var3) {
               var1.setFlying(false);
            }
         }
      }
   }

   public void onLogin(Player var1) {
      PlayerData var2 = this.pl.data().get(var1);
      if (var2.flySeconds > 0) {
         this.apply(var1);
         Msg.panthere(
            var1, "Il te reste <white><t></white> de fly. <white>/fly</white> pour le mettre en pause.", Msg.p("t", Util.duration(var2.flySeconds * 1000L))
         );
      }
   }

   public void toggleStaff(Player var1) {
      if (!this.survivalLike(var1)) {
         Msg.info(var1, "Tu voles déjà dans ce mode de jeu.");
      } else {
         if (this.staffFly.remove(var1.getUniqueId())) {
            Msg.ok(var1, "Fly <red>désactivé</red>.");
            if (var1.isFlying()) {
               var1.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false));
            }
         } else {
            this.staffFly.add(var1.getUniqueId());
            Msg.ok(var1, "Fly <green>activé</green>.");
         }

         this.apply(var1);
      }
   }

   public void toggleTimed(Player var1) {
      PlayerData var2 = this.pl.data().get(var1);
      if (var2.flySeconds <= 0) {
         Msg.err(var1, "Tu n'as pas de fly. Tente ta chance au <white>/vote</white> (lot Épique ou Légendaire) !");
      } else {
         if (this.paused.remove(var1.getUniqueId())) {
            Msg.ok(var1, "Fly réactivé : il te reste <white><t></white>.", Msg.p("t", Util.duration(var2.flySeconds * 1000L)));
         } else {
            this.paused.add(var1.getUniqueId());
            if (var1.isFlying()) {
               var1.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false));
            }

            Msg.ok(var1, "Fly en pause : le temps restant (<white><t></white>) est conservé.", Msg.p("t", Util.duration(var2.flySeconds * 1000L)));
         }

         this.apply(var1);
      }
   }

   public void tick() {
      for (Player var2 : this.pl.getServer().getOnlinePlayers()) {
         if (this.pl.auth().isLogged(var2) && !this.staffFly.contains(var2.getUniqueId())) {
            PlayerData var3 = this.pl.data().get(var2);
            if (var3.flySeconds > 0 && !this.paused.contains(var2.getUniqueId()) && this.survivalLike(var2)) {
               this.apply(var2);
               var3.flySeconds--;
               var3.touch();
               if (var3.flySeconds == 10) {
                  Msg.panthere(var2, "Plus que <white>10 secondes</white> de fly, pose-toi !");
               }

               if (var3.flySeconds <= 0) {
                  boolean var4 = var2.isFlying();
                  var2.setFlying(false);
                  var2.setAllowFlight(false);
                  if (var4) {
                     var2.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0, false, false));
                  }

                  Msg.panthere(var2, "Ton fly est terminé ! <gray>(chute ralentie pendant 15 s)");
               } else {
                  var2.sendActionBar(Msg.mm("<#FF9AC8>✈ Fly</#FF9AC8> <white>" + Util.clock(var3.flySeconds) + "</white>"));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onWorld(PlayerChangedWorldEvent var1) {
      this.pl.getServer().getScheduler().runTask(this.pl, () -> this.apply(var1.getPlayer()));
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onGamemode(PlayerGameModeChangeEvent var1) {
      this.pl.getServer().getScheduler().runTask(this.pl, () -> {
         if (var1.getPlayer().isOnline()) {
            this.apply(var1.getPlayer());
         }
      });
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.staffFly.remove(var1.getPlayer().getUniqueId());
      this.paused.remove(var1.getPlayer().getUniqueId());
   }
}
