package fr.bdeimt.serveur;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

public final class HealthBars implements Listener {
   private static final String OBJECTIVE = "bdeimt_vie";
   private static final int SEGMENTS = 10;
   private static final long SHOW_TICKS = 120L;
   private final BDEIMT pl;
   private final NamespacedKey key;
   private final Map<UUID, BukkitTask> hide = new HashMap<>();

   public HealthBars(BDEIMT var1) {
      this.pl = var1;
      this.key = new NamespacedKey(var1, "barre_vie");
   }

   public void setupPlayers() {
      Scoreboard var1 = Bukkit.getScoreboardManager().getMainScoreboard();
      Objective var2 = var1.getObjective("bdeimt_vie");
      if (var2 == null) {
         var2 = var1.registerNewObjective("bdeimt_vie", Criteria.HEALTH, Msg.mm("<#FF5555>❤</#FF5555>"), RenderType.HEARTS);
      }

      var2.setDisplaySlot(DisplaySlot.BELOW_NAME);
   }

   private static boolean wanted(Entity var0) {
      return var0 instanceof LivingEntity && !(var0 instanceof Player) && !(var0 instanceof ArmorStand);
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent var1) {
      if (wanted(var1.getEntity())) {
         LivingEntity var2 = (LivingEntity)var1.getEntity();
         if (var2.customName() == null || var2.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            Bukkit.getScheduler().runTask(this.pl, () -> this.show(var2));
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHeal(EntityRegainHealthEvent var1) {
      if (wanted(var1.getEntity())) {
         LivingEntity var2 = (LivingEntity)var1.getEntity();
         if (var2.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            Bukkit.getScheduler().runTask(this.pl, () -> this.show(var2));
         }
      }
   }

   private void show(LivingEntity var1) {
      if (var1.isValid() && !var1.isDead()) {
         double var2 = maxHealth(var1);
         double var4 = Math.max(0.0, Math.min(var2, var1.getHealth()));
         double var6 = var2 <= 0.0 ? 0.0 : var4 / var2;
         int var8 = (int)Math.ceil(var6 * 10.0);
         String var9 = var6 > 0.6 ? "<#55FF55>" : (var6 > 0.3 ? "<#FFD25E>" : "<#FF5555>");
         String var10 = var9
            + "■".repeat(var8)
            + "<dark_gray>"
            + "■".repeat(10 - var8)
            + "</dark_gray> <white>"
            + (int)Math.ceil(var4)
            + "</white><gray>/"
            + (int)Math.round(var2)
            + "</gray> <#FF5555>❤</#FF5555>";
         var1.customName(Msg.mm(var10));
         var1.setCustomNameVisible(true);
         var1.getPersistentDataContainer().set(this.key, PersistentDataType.BYTE, (byte)1);
         BukkitTask var11 = this.hide.remove(var1.getUniqueId());
         if (var11 != null) {
            var11.cancel();
         }

         this.hide.put(var1.getUniqueId(), Bukkit.getScheduler().runTaskLater(this.pl, () -> this.clear(var1), 120L));
      }
   }

   private void clear(LivingEntity var1) {
      this.hide.remove(var1.getUniqueId());
      if (var1.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
         var1.customName(null);
         var1.setCustomNameVisible(false);
         var1.getPersistentDataContainer().remove(this.key);
      }
   }

   private static double maxHealth(LivingEntity var0) {
      try {
         AttributeInstance var1 = var0.getAttribute((Attribute)Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health")));
         if (var1 != null) {
            return var1.getValue();
         }
      } catch (Throwable var2) {
      }

      return Math.max(1.0, var0.getHealth());
   }

   @EventHandler
   public void onLoad(EntitiesLoadEvent var1) {
      for (Entity var3 : var1.getEntities()) {
         if (var3 instanceof LivingEntity var4 && var4.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            this.clear(var4);
         }
      }
   }

   public void clearAll() {
      for (World var2 : Bukkit.getWorlds()) {
         for (LivingEntity var4 : var2.getLivingEntities()) {
            if (var4.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
               this.clear(var4);
            }
         }
      }
   }
}
