package fr.bdeimt.serveur;

import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;

public final class Dragon {
   private final BDEIMT pl;

   public Dragon(BDEIMT var1) {
      this.pl = var1;
   }

   private long period() {
      return this.pl.getConfig().getLong("dragon-toutes-les-heures", 48L) * 3600000L;
   }

   public void init() {
      if (this.pl.state().nextDragon <= 0L) {
         this.pl.state().nextDragon = System.currentTimeMillis() + this.period();
         this.pl.state().save();
      }
   }

   public long next() {
      return this.pl.state().nextDragon;
   }

   public void check() {
      long var1 = System.currentTimeMillis();
      long var3 = this.pl.state().nextDragon;
      if (!this.pl.state().dragonWarned && var3 - var1 <= 3600000L && var3 > var1) {
         this.pl.state().dragonWarned = true;
         this.pl.state().save();
         Msg.poulpySurvie("<#C77DFF>L'Ender Dragon renaîtra dans 1 heure</#C77DFF> dans l'End. Préparez vos arcs !");
      }

      if (var1 >= var3) {
         this.respawn(false);
      }
   }

   public String respawn(boolean var1) {
      this.pl.state().nextDragon = System.currentTimeMillis() + this.period();
      this.pl.state().dragonWarned = false;
      this.pl.state().save();
      World var2 = null;

      for (World var4 : Bukkit.getWorlds()) {
         if (var4.getEnvironment() == Environment.THE_END) {
            var2 = var4;
            break;
         }
      }

      if (var2 == null) {
         return "Pas de monde End.";
      } else {
         DragonBattle var14 = var2.getEnderDragonBattle();
         if (var14 == null) {
            return "Pas de combat de dragon dans ce monde.";
         } else {
            EnderDragon var15 = var14.getEnderDragon();
            if (var15 == null || var15.isDead()) {
               Location var5 = var14.getEndPortalLocation();
               if (var5 == null) {
                  return "Le portail de sortie n'existe pas encore (le dragon n'a jamais été tué).";
               } else {
                  var5.getChunk().load();
                  ArrayList<EnderCrystal> var6 = new ArrayList<>();
                  int[][] var7 = new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}};

                  for (int[] var11 : var7) {
                     Location var12 = var5.clone().add(var11[0] + 0.5, 1.0, var11[1] + 0.5);
                     var6.add((EnderCrystal)var2.spawn(var12, EnderCrystal.class, var0 -> var0.setShowingBottom(true)));
                  }

                  boolean var16;
                  try {
                     var16 = var14.initiateRespawn(var6);
                  } catch (Throwable var13) {
                     var14.initiateRespawn();
                     var16 = true;
                  }

                  if (var16) {
                     Msg.poulpySurvie("<#C77DFF><bold>L'Ender Dragon renaît dans l'End !</bold></#C77DFF> Rassemblez-vous, et que le meilleur gagne.");
                     Util.soundSurvie("entity.ender_dragon.growl", 0.6F, 0.9F);
                     return "Respawn lancé.";
                  } else {
                     for (EnderCrystal var18 : var6) {
                        var18.remove();
                     }

                     return "Le jeu a refusé le respawn (déjà en cours ?).";
                  }
               }
            } else if (var1) {
               return "Le dragon est déjà vivant.";
            } else {
               Msg.poulpySurvie("<#C77DFF>L'Ender Dragon règne toujours sur l'End</#C77DFF>... Qui osera l'affronter ?");
               return "Déjà vivant.";
            }
         }
      }
   }
}
