package fr.bdeimt.serveur;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

public final class FunItems implements Listener {
   private final Map<UUID, Long> cooldown = new HashMap<>();

   private static NamespacedKey key() {
      return new NamespacedKey(BDEIMT.get(), "fun");
   }

   public static ItemStack make(Material var0, int var1, String var2, String var3, String... var4) {
      ItemStack var5 = Util.item(var0, var1, var2, var4);
      if (var3 != null) {
         ItemMeta var6 = var5.getItemMeta();
         var6.getPersistentDataContainer().set(key(), PersistentDataType.STRING, var3);
         var5.setItemMeta(var6);
      }

      return var5;
   }

   public static ItemStack drink(int var0, String var1, int var2, String var3, String... var4) {
      ItemStack var5 = make(Material.POTION, var0, var1, var3, var4);
      if (var5.getItemMeta() instanceof PotionMeta var6) {
         var6.setBasePotionType(PotionType.WATER);
         var6.setColor(Color.fromRGB(var2));
         var5.setItemMeta(var6);
      }

      return var5;
   }

   public static ItemStack dyed(Material var0, int var1, String var2, String... var3) {
      ItemStack var4 = Util.item(var0, 1, var2, var3);
      if (var4.getItemMeta() instanceof LeatherArmorMeta var5) {
         var5.setColor(Color.fromRGB(var1));
         var4.setItemMeta(var5);
      }

      return var4;
   }

   private static String tag(ItemStack var0) {
      return var0 != null && var0.hasItemMeta() ? (String)var0.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING) : null;
   }

   private static void fx(Player var0, PotionEffectType var1, int var2, int var3) {
      var0.addPotionEffect(new PotionEffect(var1, var2 * 20, var3, false, true, true));
   }

   private static void fx(LivingEntity var0, PotionEffectType var1, int var2, int var3) {
      var0.addPotionEffect(new PotionEffect(var1, var2 * 20, var3, false, true, true));
   }

   private static void particles(LivingEntity var0, Particle var1, int var2) {
      try {
         var0.getWorld().spawnParticle(var1, var0.getLocation().add(0.0, 1.0, 0.0), var2, 0.4, 0.5, 0.4, 0.05);
      } catch (Throwable var4) {
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var2 && var1.getEntity() instanceof LivingEntity var3) {
         String var9 = tag(var2.getInventory().getItemInMainHand());
         if (var9 != null) {
            switch (var9) {
               case "lance_patate":
                  var3.setVelocity(var3.getVelocity().add(new Vector(0.0, 1.3, 0.0)));
                  Util.sound(var2, "entity.firework_rocket.launch", 1.0F, 1.4F);
                  break;
               case "gifle":
                  fx(var3, PotionEffectType.SLOWNESS, 3, 1);
                  Util.sound(var2, "entity.cod.flop", 1.0F, 0.8F);
                  if (var3 instanceof Player var16) {
                     var16.sendActionBar(Msg.mm("<#4FC3FF>*SPLASH* Tu t'es pris une morue en pleine face.</#4FC3FF>"));
                  }
                  break;
               case "chaos":
                  PotionEffectType[] var15 = new PotionEffectType[]{
                     PotionEffectType.LEVITATION,
                     PotionEffectType.GLOWING,
                     PotionEffectType.SPEED,
                     PotionEffectType.JUMP_BOOST,
                     PotionEffectType.NAUSEA,
                     PotionEffectType.SLOWNESS
                  };
                  fx(var3, var15[ThreadLocalRandom.current().nextInt(var15.length)], 3, 1);
                  particles(var3, Particle.WITCH, 20);
                  Util.sound(var2, "entity.evoker.cast_spell", 1.0F, 1.3F);
                  break;
               case "ventilo":
                  Vector var14 = var3.getLocation().toVector().subtract(var2.getLocation().toVector()).setY(0);
                  if (var14.lengthSquared() > 1.0E-4) {
                     var3.setVelocity(var14.normalize().multiply(2.2).setY(0.6));
                  }

                  Util.sound(var2, "entity.breeze.wind_burst", 1.0F, 1.0F);
                  break;
               case "tong":
                  Location var13 = var3.getLocation();
                  var13.setYaw(var13.getYaw() + 180.0F);
                  var3.teleport(var13);
                  Util.sound(var2, "entity.slime.squish", 1.0F, 1.5F);
                  break;
               case "balai":
                  fx(var3, PotionEffectType.NAUSEA, 4, 0);
                  if (var3 instanceof Player var12) {
                     var12.sendActionBar(Msg.mm("<#FFD25E>Attention, sol glissant !</#FFD25E>"));
                  }
                  break;
               case "poele":
                  fx(var3, PotionEffectType.BLINDNESS, 1, 0);
                  Util.sound(var2, "block.anvil.land", 0.7F, 1.8F);
                  particles(var3, Particle.CRIT, 15);
                  break;
               case "fourchette":
                  fx(var3, PotionEffectType.POISON, 2, 0);
                  break;
               case "chaussette":
                  fx(var3, PotionEffectType.DARKNESS, 3, 0);
                  if (var3 instanceof Player var11) {
                     var11.sendActionBar(Msg.mm("<#7FB800>Cette odeur... insoutenable.</#7FB800>"));
                  }
                  break;
               case "rateau":
                  fx(var3, PotionEffectType.LEVITATION, 1, 1);
                  if (var3 instanceof Player var10) {
                     var10.sendActionBar(Msg.mm("<#FF9AC8>Tu viens de te prendre un râteau.</#FF9AC8>"));
                  }
                  break;
               case "matraque":
                  fx(var3, PotionEffectType.SLOWNESS, 3, 3);
                  if (var3 instanceof Player var7) {
                     var7.sendActionBar(Msg.mm("<#FF5555>Le respo VSS t'a à l'œil.</#FF5555>"));
                  }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.NORMAL
   )
   public void onUse(PlayerInteractEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND) {
         if (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player var2 = var1.getPlayer();
            String var3 = tag(var2.getInventory().getItemInMainHand());
            if (var3 != null && (var3.equals("canard") || var3.equals("disco") || var3.equals("tambour"))) {
               var1.setCancelled(true);
               long var4 = System.currentTimeMillis();
               Long var6 = this.cooldown.get(var2.getUniqueId());
               if (var6 == null || var4 - var6 >= 1500L) {
                  this.cooldown.put(var2.getUniqueId(), var4);
                  Location var7 = var2.getLocation();
                  switch (var3) {
                     case "canard":
                        for (Player var15 : var2.getWorld().getPlayers()) {
                           if (var15.getLocation().distanceSquared(var7) < 400.0) {
                              Util.sound(var15, "entity.chicken.ambient", 1.0F, 1.9F);
                           }
                        }

                        particles(var2, Particle.CLOUD, 6);
                        break;
                     case "disco":
                        for (Player var14 : var2.getWorld().getPlayers()) {
                           if (var14.getLocation().distanceSquared(var7) < 400.0) {
                              Util.sound(var14, "block.note_block.bell", 1.0F, 0.5F + ThreadLocalRandom.current().nextFloat() * 1.5F);
                           }
                        }

                        particles(var2, Particle.NOTE, 15);
                        particles(var2, Particle.END_ROD, 10);
                        break;
                     case "tambour":
                        for (Player var11 : var2.getWorld().getPlayers()) {
                           if (var11.getLocation().distanceSquared(var7) < 400.0) {
                              Util.sound(var11, "block.note_block.basedrum", 1.0F, 1.0F);
                              Util.sound(var11, "block.note_block.snare", 0.8F, 1.0F);
                           }
                        }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onConsume(PlayerItemConsumeEvent var1) {
      String var2 = tag(var1.getItem());
      if (var2 != null) {
         Player var3 = var1.getPlayer();
         BDEIMT.get()
            .getServer()
            .getScheduler()
            .runTask(
               BDEIMT.get(),
               () -> {
                  if (var3.isOnline()) {
                     switch (var2) {
                        case "pinte":
                           fx(var3, PotionEffectType.NAUSEA, 10, 0);
                           fx(var3, PotionEffectType.STRENGTH, 30, 0);
                           Msg.raw(var3, "<#FFC93C>*hips*</#FFC93C>");
                           break;
                        case "vodka":
                           fx(var3, PotionEffectType.SPEED, 20, 1);
                           fx(var3, PotionEffectType.NAUSEA, 15, 0);
                           fx(var3, PotionEffectType.BLINDNESS, 2, 0);
                           break;
                        case "captain":
                           fx(var3, PotionEffectType.NAUSEA, 20, 0);
                           fx(var3, PotionEffectType.RESISTANCE, 30, 0);
                           Msg.raw(var3, "<#C77DFF>À l'abordage !</#C77DFF>");
                           break;
                        case "redbull":
                           fx(var3, PotionEffectType.SPEED, 30, 1);
                           fx(var3, PotionEffectType.JUMP_BOOST, 30, 2);
                           Msg.raw(var3, "<#FF5555>Ça te donne des ailes (presque).</#FF5555>");
                           break;
                        case "cafe":
                           fx(var3, PotionEffectType.HASTE, 120, 1);
                           fx(var3, PotionEffectType.SPEED, 60, 0);
                           break;
                        case "kebab":
                           var3.setSaturation(Math.min(20.0F, var3.getSaturation() + 10.0F));
                           fx(var3, PotionEffectType.NAUSEA, 5, 0);
                           break;
                        case "soupe":
                           fx(var3, PotionEffectType.REGENERATION, 5, 1);
                           fx(var3, PotionEffectType.SLOWNESS, 5, 0);
                           Msg.raw(var3, "<gray>Le goût est... discutable.</gray>");
                           break;
                        case "benite":
                           for (PotionEffect var5 : var3.getActivePotionEffects()) {
                              PotionEffectType var6 = var5.getType();
                              if (var6.equals(PotionEffectType.NAUSEA)
                                 || var6.equals(PotionEffectType.BLINDNESS)
                                 || var6.equals(PotionEffectType.POISON)
                                 || var6.equals(PotionEffectType.SLOWNESS)
                                 || var6.equals(PotionEffectType.WEAKNESS)
                                 || var6.equals(PotionEffectType.DARKNESS)
                                 || var6.equals(PotionEffectType.WITHER)
                                 || var6.equals(PotionEffectType.HUNGER)
                                 || var6.equals(PotionEffectType.MINING_FATIGUE)) {
                                 var3.removePotionEffect(var6);
                              }
                           }

                           Msg.raw(var3, "<#FFFFFF>Tu te sens purifié.</#FFFFFF>");
                     }
                  }
               }
            );
      }
   }

   public static List<String> ids() {
      return List.of(
         "lance_patate",
         "gifle",
         "chaos",
         "ventilo",
         "tong",
         "balai",
         "poele",
         "fourchette",
         "chaussette",
         "rateau",
         "matraque",
         "canard",
         "disco",
         "tambour",
         "pinte",
         "vodka",
         "captain",
         "redbull",
         "cafe",
         "kebab",
         "soupe",
         "benite"
      );
   }
}
