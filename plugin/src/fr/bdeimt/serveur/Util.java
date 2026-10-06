package fr.bdeimt.serveur;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

public final class Util {
   private Util() {
   }

   public static ItemStack item(Material var0, int var1) {
      return new ItemStack(var0, Math.max(1, var1));
   }

   public static ItemStack item(Material var0, int var1, String var2, String... var3) {
      ItemStack var4 = new ItemStack(var0, Math.max(1, var1));
      ItemMeta var5 = var4.getItemMeta();
      if (var5 != null) {
         if (var2 != null) {
            var5.displayName(noItalic(Msg.mm(var2)));
         }

         if (var3.length > 0) {
            ArrayList var6 = new ArrayList();

            for (String var10 : var3) {
               var6.add(noItalic(Msg.mm(var10)));
            }

            var5.lore(var6);
         }

         var4.setItemMeta(var5);
      }

      return var4;
   }

   public static Component noItalic(Component var0) {
      return var0.decorationIfAbsent(TextDecoration.ITALIC, net.kyori.adventure.text.format.TextDecoration.State.FALSE);
   }

   public static ItemStack named(ItemStack var0, String var1) {
      ItemMeta var2 = var0.getItemMeta();
      if (var2 != null) {
         var2.displayName(noItalic(Msg.mm(var1)));
         var0.setItemMeta(var2);
      }

      return var0;
   }

   public static ItemStack lore(ItemStack var0, List<String> var1) {
      ItemMeta var2 = var0.getItemMeta();
      if (var2 != null) {
         ArrayList var3 = new ArrayList();

         for (String var5 : var1) {
            var3.add(noItalic(Msg.mm(var5)));
         }

         var2.lore(var3);
         var0.setItemMeta(var2);
      }

      return var0;
   }

   public static ItemStack ench(ItemStack var0, Enchantment var1, int var2) {
      var0.addUnsafeEnchantment(var1, var2);
      return var0;
   }

   public static ItemStack unbreakable(ItemStack var0) {
      ItemMeta var1 = var0.getItemMeta();
      if (var1 != null) {
         var1.setUnbreakable(true);
         var0.setItemMeta(var1);
      }

      return var0;
   }

   public static ItemStack usesLeft(ItemStack var0, int var1) {
      ItemMeta var2 = var0.getItemMeta();
      if (var2 instanceof Damageable var3) {
         short var4 = var0.getType().getMaxDurability();
         var3.setDamage(Math.max(0, var4 - var1));
         var0.setItemMeta(var2);
      }

      return var0;
   }

   public static ItemStack potion(Material var0, PotionType var1, int var2) {
      ItemStack var3 = new ItemStack(var0, Math.max(1, var2));
      if (var3.getItemMeta() instanceof PotionMeta var4) {
         var4.setBasePotionType(var1);
         var3.setItemMeta(var4);
      }

      return var3;
   }

   public static ItemStack customPotion(Material var0, int var1, String var2, int var3, PotionEffect... var4) {
      ItemStack var5 = new ItemStack(var0, Math.max(1, var1));
      if (var5.getItemMeta() instanceof PotionMeta var6) {
         for (PotionEffect var10 : var4) {
            var6.addCustomEffect(var10, true);
         }

         var6.setColor(Color.fromRGB(var3));
         if (var2 != null) {
            var6.displayName(noItalic(Msg.mm(var2)));
         }

         var5.setItemMeta(var6);
      }

      return var5;
   }

   public static List<ItemStack> copy(Collection<ItemStack> var0) {
      ArrayList var1 = new ArrayList();

      for (ItemStack var3 : var0) {
         if (var3 != null && !var3.getType().isAir()) {
            var1.add(var3.clone());
         }
      }

      return var1;
   }

   public static boolean give(Player var0, Collection<ItemStack> var1) {
      boolean var2 = false;

      for (ItemStack var4 : var1) {
         if (var4 != null && !var4.getType().isAir()) {
            HashMap<Integer, ItemStack> var5 = var0.getInventory().addItem(new ItemStack[]{var4.clone()});

            for (ItemStack var7 : var5.values()) {
               var0.getWorld().dropItemNaturally(var0.getLocation(), var7);
               var2 = true;
            }
         }
      }

      return var2;
   }

   public static String duration(long var0) {
      if (var0 < 0L) {
         var0 = 0L;
      }

      long var2 = (var0 + 999L) / 1000L;
      long var4 = var2 / 86400L;
      var2 %= 86400L;
      long var6 = var2 / 3600L;
      var2 %= 3600L;
      long var8 = var2 / 60L;
      var2 %= 60L;
      StringBuilder var10 = new StringBuilder();
      if (var4 > 0L) {
         var10.append(var4).append(" j ");
      }

      if (var6 > 0L) {
         var10.append(var6).append(" h ");
      }

      if (var8 > 0L && var4 == 0L) {
         var10.append(var6 > 0L ? String.format("%02d", var8) : String.valueOf(var8)).append(" min ");
      }

      if (var2 > 0L && var6 == 0L && var4 == 0L) {
         var10.append(var2).append(" s");
      }

      String var11 = var10.toString().trim();
      return var11.isEmpty() ? "0 s" : var11;
   }

   public static String clock(long var0) {
      if (var0 < 0L) {
         var0 = 0L;
      }

      long var2 = var0 / 3600L;
      long var4 = var0 % 3600L / 60L;
      long var6 = var0 % 60L;
      return var2 > 0L ? String.format("%d:%02d:%02d", var2, var4, var6) : String.format("%02d:%02d", var4, var6);
   }

   public static long parseDuration(String var0) {
      if (var0 == null) {
         return -1L;
      } else {
         String var1 = var0.trim().toLowerCase(Locale.ROOT);
         long var2 = 0L;
         StringBuilder var4 = new StringBuilder();
         boolean var5 = false;

         for (int var6 = 0; var6 < var1.length(); var6++) {
            char var7 = var1.charAt(var6);
            if (Character.isDigit(var7)) {
               var4.append(var7);
            } else {
               if (var4.length() == 0) {
                  return -1L;
               }

               long var8 = Long.parseLong(var4.toString());
               var4.setLength(0);
               String var10;
               if (var1.startsWith("sem", var6)) {
                  var10 = "w";
                  var6 += 2;
               } else if (var1.startsWith("min", var6)) {
                  var10 = "m";
                  var6 += 2;
               } else {
                  var10 = String.valueOf(var7);
               }

               switch (var10) {
                  case "s":
                     var2 += var8 * 1000L;
                     break;
                  case "m":
                     var2 += var8 * 60000L;
                     break;
                  case "h":
                     var2 += var8 * 3600000L;
                     break;
                  case "j":
                  case "d":
                     var2 += var8 * 86400000L;
                     break;
                  case "w":
                     var2 += var8 * 7L * 86400000L;
                     break;
                  default:
                     return -1L;
               }

               var5 = true;
            }
         }

         if (var4.length() > 0) {
            var2 += Long.parseLong(var4.toString()) * 60000L;
            var5 = true;
         }

         return var5 && var2 > 0L ? var2 : -1L;
      }
   }

   public static String loc(Location var0) {
      return var0 != null && var0.getWorld() != null
         ? var0.getWorld().getName() + ";" + var0.getX() + ";" + var0.getY() + ";" + var0.getZ() + ";" + var0.getYaw() + ";" + var0.getPitch()
         : null;
   }

   public static Location loc(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         try {
            String[] var1 = var0.split(";");
            World var2 = Bukkit.getWorld(var1[0]);
            return var2 == null
               ? null
               : new Location(
                  var2,
                  Double.parseDouble(var1[1]),
                  Double.parseDouble(var1[2]),
                  Double.parseDouble(var1[3]),
                  Float.parseFloat(var1[4]),
                  Float.parseFloat(var1[5])
               );
         } catch (Exception var3) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String coords(Location var0) {
      return var0.getBlockX() + ", " + var0.getBlockY() + ", " + var0.getBlockZ();
   }

   public static String worldName(World var0) {
      if (var0 == null) {
         return "?";
      } else {
         return switch (var0.getEnvironment()) {
            case NETHER -> "le Nether";
            case THE_END -> "l'End";
            default -> "la Surface";
         };
      }
   }

   public static UUID offlineUuid(String var0) {
      return UUID.nameUUIDFromBytes(("OfflinePlayer:" + var0).getBytes(StandardCharsets.UTF_8));
   }

   public static boolean validName(String var0) {
      return var0 != null && var0.matches("[A-Za-z0-9_]{3,16}");
   }

   public static void sound(Player var0, String var1, float var2, float var3) {
      try {
         var0.playSound(Sound.sound(Key.key(var1), Source.MASTER, var2, var3));
      } catch (Throwable var5) {
      }
   }

   public static void soundAll(String var0, float var1, float var2) {
      for (Player var4 : Bukkit.getOnlinePlayers()) {
         sound(var4, var0, var1, var2);
      }
   }

   public static String pct(double var0) {
      double var2 = var0 * 100.0;
      String var4;
      if (var2 >= 10.0) {
         var4 = String.format(Locale.FRANCE, "%.1f", var2);
      } else if (var2 >= 1.0) {
         var4 = String.format(Locale.FRANCE, "%.2f", var2);
      } else if (var2 >= 0.1) {
         var4 = String.format(Locale.FRANCE, "%.2f", var2);
      } else {
         var4 = String.format(Locale.FRANCE, "%.3f", var2);
      }

      return var4 + " %";
   }
}
