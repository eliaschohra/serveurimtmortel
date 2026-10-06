package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionType;

public final class Loots {
   private final Map<String, Loots.Loot> byId = new LinkedHashMap<>();
   private final List<Loots.Loot> list = new ArrayList<>();

   public Loots() {
      this.define();
      EnumMap<Loots.Tier, Double> var1 = new EnumMap<>(Loots.Tier.class);

      for (Loots.Loot var3 : this.list) {
         var1.merge(var3.tier, var3.weight, Double::sum);
      }

      for (Loots.Loot var5 : this.list) {
         var5.chance = var5.tier.share / 100.0 * (var5.weight / (Double)var1.get(var5.tier));
      }
   }

   public List<Loots.Loot> all() {
      return Collections.unmodifiableList(this.list);
   }

   public Loots.Loot get(String var1) {
      return this.byId.get(var1);
   }

   public Loots.Loot roll() {
      double var1 = ThreadLocalRandom.current().nextDouble();
      double var3 = 0.0;

      for (Loots.Loot var6 : this.list) {
         var3 += var6.chance;
         if (var1 < var3) {
            return var6;
         }
      }

      return this.list.get(0);
   }

   public List<Loots.Loot> sortedRarestFirst() {
      ArrayList<Loots.Loot> var1 = new ArrayList<>(this.list);
      var1.sort((var0, var1x) -> var0.tier != var1x.tier ? var1x.tier.ordinal() - var0.tier.ordinal() : Double.compare(var0.chance, var1x.chance));
      return var1;
   }

   private void add(String var1, Loots.Tier var2, double var3, String var5, Material var6, int var7, Supplier<List<ItemStack>> var8) {
      Loots.Loot var9 = new Loots.Loot(var1, var2, var3, var5, var6, var7, var8, 0, 0);
      this.byId.put(var1, var9);
      this.list.add(var9);
   }

   private void simple(String var1, Loots.Tier var2, double var3, String var5, Material var6, int var7) {
      this.add(var1, var2, var3, var5, var6, var7, () -> List.of(new ItemStack(var6, var7)));
   }

   private void special(String var1, Loots.Tier var2, double var3, String var5, Material var6, int var7, int var8) {
      this.special(var1, var2, var3, var5, var6, var7, var8, 0L);
   }

   private void special(String var1, Loots.Tier var2, double var3, String var5, Material var6, int var7, int var8, long var9) {
      Loots.Loot var11 = new Loots.Loot(var1, var2, var3, var5, var6, 1, List::of, var7, var8);
      var11.bonusAura = var9;
      this.byId.put(var1, var11);
      this.list.add(var11);
   }

   private void fun(String var1, Loots.Tier var2, double var3, String var5, Material var6, int var7, Supplier<List<ItemStack>> var8) {
      this.add(var1, var2, var3, var5, var6, var7, var8);
   }

   private static ItemStack named(ItemStack var0, String var1, String... var2) {
      Util.named(var0, var1);
      if (var2.length > 0) {
         Util.lore(var0, List.of(var2));
      }

      return var0;
   }

   private static List<ItemStack> set(String var0, int var1, String var2) {
      String[] var3 = new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
      ArrayList var4 = new ArrayList();

      for (String var8 : var3) {
         var4.add(FunItems.dyed(Material.valueOf(var0 + "_" + var8), var1, var2));
      }

      return var4;
   }

   static List<ItemStack> armor(String var0) {
      return List.of(
         new ItemStack(Material.valueOf(var0 + "_HELMET")),
         new ItemStack(Material.valueOf(var0 + "_CHESTPLATE")),
         new ItemStack(Material.valueOf(var0 + "_LEGGINGS")),
         new ItemStack(Material.valueOf(var0 + "_BOOTS"))
      );
   }

   static List<ItemStack> armorEnchanted(String var0, int var1, boolean var2) {
      ArrayList var3 = new ArrayList();

      for (ItemStack var5 : armor(var0)) {
         var5.addUnsafeEnchantment(Enchantment.PROTECTION, var1);
         var5.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
         if (var2) {
            var5.addUnsafeEnchantment(Enchantment.MENDING, 1);
         }

         var3.add(var5);
      }

      return var3;
   }

   static ItemStack book(Enchantment var0, int var1) {
      ItemStack var2 = new ItemStack(Material.ENCHANTED_BOOK);
      if (var2.getItemMeta() instanceof EnchantmentStorageMeta var3) {
         var3.addStoredEnchant(var0, var1, true);
         var2.setItemMeta(var3);
      }

      return var2;
   }

   private static ItemStack e(Material var0, Object... var1) {
      ItemStack var2 = new ItemStack(var0);

      for (byte var3 = 0; var3 + 1 < var1.length; var3 += 2) {
         var2.addUnsafeEnchantment((Enchantment)var1[var3], (Integer)var1[var3 + 1]);
      }

      return var2;
   }

   private void define() {
      Loots.Tier var1 = Loots.Tier.COMMUN;
      Loots.Tier var2 = Loots.Tier.PEU_COMMUN;
      Loots.Tier var3 = Loots.Tier.RARE;
      Loots.Tier var4 = Loots.Tier.EPIQUE;
      Loots.Tier var5 = Loots.Tier.LEGENDAIRE;
      Loots.Tier var6 = Loots.Tier.MYTHIQUE;
      this.simple("xp10", var1, 1.4, "10 fioles d'expérience", Material.EXPERIENCE_BOTTLE, 10);
      this.simple("pain", var1, 1.1, "16 pains", Material.BREAD, 16);
      this.simple("steak12", var1, 1.1, "12 steaks cuits", Material.COOKED_BEEF, 12);
      this.simple("pommes", var1, 1.0, "16 pommes", Material.APPLE, 16);
      this.simple("torches", var1, 1.2, "32 torches", Material.TORCH, 32);
      this.simple("chene", var1, 1.1, "16 bûches de chêne", Material.OAK_LOG, 16);
      this.simple("bouleau", var1, 0.9, "16 bûches de bouleau", Material.BIRCH_LOG, 16);
      this.simple("pierre", var1, 1.1, "64 pierres", Material.COBBLESTONE, 64);
      this.simple("charbon", var1, 1.1, "16 charbons", Material.COAL, 16);
      this.simple("fleches16", var1, 1.0, "16 flèches", Material.ARROW, 16);
      this.simple("fer4", var1, 1.0, "4 lingots de fer", Material.IRON_INGOT, 4);
      this.simple("os", var1, 0.9, "16 poudres d'os", Material.BONE_MEAL, 16);
      this.simple("verre", var1, 0.9, "32 blocs de verre", Material.GLASS, 32);
      this.simple("laine", var1, 0.8, "16 laines blanches", Material.WHITE_WOOL, 16);
      this.simple("cookies", var1, 0.8, "16 cookies", Material.COOKIE, 16);
      this.simple("carottes", var1, 1.0, "16 carottes", Material.CARROT, 16);
      this.simple("patates", var1, 1.0, "16 pommes de terre cuites", Material.BAKED_POTATO, 16);
      this.simple("seau", var1, 0.8, "Un seau", Material.BUCKET, 1);
      this.simple("graines", var1, 0.8, "16 graines de blé", Material.WHEAT_SEEDS, 16);
      this.simple("planches", var1, 1.1, "32 planches de chêne", Material.OAK_PLANKS, 32);
      this.simple("ficelle", var1, 0.8, "16 ficelles", Material.STRING, 16);
      this.simple("cuir", var1, 0.8, "16 cuirs", Material.LEATHER, 16);
      this.simple("sable", var1, 0.8, "16 blocs de sable", Material.SAND, 16);
      this.simple("pasteque", var1, 0.8, "16 tranches de pastèque", Material.MELON_SLICE, 16);
      this.simple("canne_sucre", var1, 0.8, "16 cannes à sucre", Material.SUGAR_CANE, 16);
      this.simple("echelles", var1, 0.8, "16 échelles", Material.LADDER, 16);
      this.simple("lit", var1, 0.9, "Un lit", Material.WHITE_BED, 1);
      this.simple("cuivre", var1, 0.8, "16 lingots de cuivre", Material.COPPER_INGOT, 16);
      this.simple("peche", var1, 0.8, "Une canne à pêche", Material.FISHING_ROD, 1);
      this.add("kit_cuir", var1, 1.2, "Kit armure en cuir", Material.LEATHER_CHESTPLATE, 1, () -> armor("LEATHER"));
      this.simple("xp20", var2, 1.2, "20 fioles d'expérience", Material.EXPERIENCE_BOTTLE, 20);
      this.simple("fer12", var2, 1.1, "12 lingots de fer", Material.IRON_INGOT, 12);
      this.simple("or8", var2, 1.0, "8 lingots d'or", Material.GOLD_INGOT, 8);
      this.simple("lapis", var2, 0.9, "16 lapis-lazuli", Material.LAPIS_LAZULI, 16);
      this.simple("redstone", var2, 0.9, "16 poudres de redstone", Material.REDSTONE, 16);
      this.simple("diamant1", var2, 0.9, "1 diamant", Material.DIAMOND, 1);
      this.simple("steak32", var2, 1.0, "32 steaks cuits", Material.COOKED_BEEF, 32);
      this.simple("pomme_or1", var2, 0.9, "1 pomme dorée", Material.GOLDEN_APPLE, 1);
      this.add("kit_maille", var2, 1.0, "Kit armure en mailles", Material.CHAINMAIL_CHESTPLATE, 1, () -> armor("CHAINMAIL"));
      this.simple("epee_fer", var2, 1.0, "Épée en fer", Material.IRON_SWORD, 1);
      this.simple("pioche_fer", var2, 1.0, "Pioche en fer", Material.IRON_PICKAXE, 1);
      this.simple("hache_fer", var2, 0.9, "Hache en fer", Material.IRON_AXE, 1);
      this.simple("bouclier", var2, 0.9, "Bouclier", Material.SHIELD, 1);
      this.add("arc", var2, 1.0, "Arc + 32 flèches", Material.BOW, 1, () -> List.of(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32)));
      this.simple("obsidienne", var2, 0.8, "4 blocs d'obsidienne", Material.OBSIDIAN, 4);
      this.simple("perles2", var2, 0.8, "2 perles de l'Ender", Material.ENDER_PEARL, 2);
      this.simple("quartz", var2, 0.8, "16 quartz du Nether", Material.QUARTZ, 16);
      this.simple("emeraudes", var2, 0.8, "8 émeraudes", Material.EMERALD, 8);
      this.simple("seau_eau", var2, 0.9, "Un seau d'eau", Material.WATER_BUCKET, 1);
      this.simple("seau_lave", var2, 0.7, "Un seau de lave", Material.LAVA_BUCKET, 1);
      this.simple("livres", var2, 0.8, "8 livres", Material.BOOK, 8);
      this.simple("coffres", var2, 0.9, "2 coffres", Material.CHEST, 2);
      this.simple("selle", var2, 0.7, "Une selle", Material.SADDLE, 1);
      this.simple("carottes_or", var2, 0.8, "8 carottes dorées", Material.GOLDEN_CARROT, 8);
      this.simple("briques", var2, 0.9, "64 briques de pierre", Material.STONE_BRICKS, 64);
      this.simple("xp32", var3, 1.2, "32 fioles d'expérience", Material.EXPERIENCE_BOTTLE, 32);
      this.add("kit_fer", var3, 1.1, "Kit armure en fer", Material.IRON_CHESTPLATE, 1, () -> armor("IRON"));
      this.simple("diamant3", var3, 1.0, "3 diamants", Material.DIAMOND, 3);
      this.simple("blocs_fer", var3, 0.9, "2 blocs de fer", Material.IRON_BLOCK, 2);
      this.simple("pomme_or3", var3, 1.0, "3 pommes dorées", Material.GOLDEN_APPLE, 3);
      this.add(
         "arbalete",
         var3,
         0.8,
         "Arbalète Charge rapide II",
         Material.CROSSBOW,
         1,
         () -> List.of(e(Material.CROSSBOW, Enchantment.QUICK_CHARGE, 2), new ItemStack(Material.ARROW, 32))
      );
      this.add("epee_fer_t2", var3, 0.9, "Épée en fer Tranchant II", Material.IRON_SWORD, 1, () -> List.of(e(Material.IRON_SWORD, Enchantment.SHARPNESS, 2)));
      this.add(
         "pioche_fer_e3",
         var3,
         0.9,
         "Pioche en fer Efficacité III",
         Material.IRON_PICKAXE,
         1,
         () -> List.of(e(Material.IRON_PICKAXE, Enchantment.EFFICIENCY, 3))
      );
      this.add("livre_solidite", var3, 0.9, "Livre : Solidité III", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.UNBREAKING, 3)));
      this.add("livre_protection", var3, 0.9, "Livre : Protection II", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.PROTECTION, 2)));
      this.simple("table_enchant", var3, 0.7, "Table d'enchantement", Material.ENCHANTING_TABLE, 1);
      this.simple("perles8", var3, 0.8, "8 perles de l'Ender", Material.ENDER_PEARL, 8);
      this.simple("enclume", var3, 0.8, "Une enclume", Material.ANVIL, 1);
      this.add("pot_force", var3, 0.9, "2 potions de force", Material.POTION, 2, () -> List.of(Util.potion(Material.POTION, PotionType.STRENGTH, 2)));
      this.add("pot_vitesse", var3, 0.9, "2 potions de rapidité", Material.POTION, 2, () -> List.of(Util.potion(Material.POTION, PotionType.SWIFTNESS, 2)));
      this.add(
         "pot_feu",
         var3,
         0.9,
         "2 potions de résistance au feu",
         Material.POTION,
         2,
         () -> List.of(Util.potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 2))
      );
      this.simple("oeuf_cheval", var3, 0.7, "Œuf de cheval", Material.HORSE_SPAWN_EGG, 1);
      this.simple("oeuf_loup", var3, 0.7, "Œuf de loup", Material.WOLF_SPAWN_EGG, 1);
      this.add(
         "kit_pecheur",
         var3,
         0.8,
         "Kit pêcheur (canne Chance de la mer II + Appât II)",
         Material.FISHING_ROD,
         1,
         () -> List.of(e(Material.FISHING_ROD, Enchantment.LUCK_OF_THE_SEA, 2, Enchantment.LURE, 2, Enchantment.UNBREAKING, 2))
      );
      this.add(
         "kit_dresseur",
         var3,
         0.8,
         "Kit dresseur (selle, étiquette, 2 laisses)",
         Material.NAME_TAG,
         1,
         () -> List.of(new ItemStack(Material.SADDLE), new ItemStack(Material.NAME_TAG), new ItemStack(Material.LEAD, 2))
      );
      this.simple("xp64", var4, 1.2, "Un stack de 64 fioles d'expérience", Material.EXPERIENCE_BOTTLE, 64);
      this.add("kit_diamant", var4, 0.9, "Kit armure en diamant", Material.DIAMOND_CHESTPLATE, 1, () -> armor("DIAMOND"));
      this.simple("epee_diamant", var4, 1.0, "Épée en diamant", Material.DIAMOND_SWORD, 1);
      this.add(
         "pioche_diamant",
         var4,
         1.0,
         "Pioche en diamant Efficacité III",
         Material.DIAMOND_PICKAXE,
         1,
         () -> List.of(e(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3))
      );
      this.add("livre_mending", var4, 0.8, "Livre : Raccommodage", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.MENDING, 1)));
      this.simple("diamant8", var4, 0.9, "8 diamants", Material.DIAMOND, 8);
      this.simple("trident", var4, 0.7, "Trident", Material.TRIDENT, 1);
      this.simple("totem", var4, 0.8, "Totem d'immortalité", Material.TOTEM_OF_UNDYING, 1);
      this.simple("pomme_or6", var4, 1.0, "6 pommes dorées", Material.GOLDEN_APPLE, 6);
      this.simple("shulker", var4, 0.8, "Boîte de Shulker", Material.PURPLE_SHULKER_BOX, 1);
      this.add("livre_eff4", var4, 0.9, "Livre : Efficacité IV", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.EFFICIENCY, 4)));
      this.add(
         "arc_p4", var4, 0.9, "Arc Puissance IV", Material.BOW, 1, () -> List.of(e(Material.BOW, Enchantment.POWER, 4), new ItemStack(Material.ARROW, 64))
      );
      this.special("bonus_votes", var4, 0.9, "+3 votes au classement", Material.PAPER, 0, 3);
      this.special("fly15", var4, 1.0, "Fly pendant 15 minutes", Material.FEATHER, 900, 0);
      this.special("fly1h", var5, 1.5, "Fly pendant 1 heure", Material.ELYTRA, 3600, 0);
      this.simple("elytra", var5, 1.0, "Élytres", Material.ELYTRA, 1);
      this.simple("notch", var5, 1.0, "Pomme de Notch", Material.ENCHANTED_GOLDEN_APPLE, 1);
      this.add(
         "epee_leg",
         var5,
         0.8,
         "Épée « Croc de la Panthère »",
         Material.DIAMOND_SWORD,
         1,
         () -> List.of(
            Util.named(
               e(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3),
               "<#FFC93C><bold>Croc de la Panthère</bold></#FFC93C>"
            )
         )
      );
      this.simple("netherite", var5, 0.8, "1 lingot de Netherite", Material.NETHERITE_INGOT, 1);
      this.add("kit_diamant_p3", var5, 0.7, "Kit diamant Protection III", Material.DIAMOND_HELMET, 1, () -> armorEnchanted("DIAMOND", 3, false));
      this.simple("totems2", var5, 0.6, "2 totems d'immortalité", Material.TOTEM_OF_UNDYING, 2);
      this.simple("balise", var5, 0.6, "Une balise", Material.BEACON, 1);
      this.add(
         "epee_mythique",
         var6,
         0.4,
         "Lame de LaPanthèreRose (Netherite, enchantée au max)",
         Material.NETHERITE_SWORD,
         1,
         () -> List.of(
            Util.named(
               e(
                  Material.NETHERITE_SWORD,
                  Enchantment.SHARPNESS,
                  5,
                  Enchantment.FIRE_ASPECT,
                  2,
                  Enchantment.LOOTING,
                  3,
                  Enchantment.SWEEPING_EDGE,
                  3,
                  Enchantment.UNBREAKING,
                  3,
                  Enchantment.MENDING,
                  1
               ),
               "<gradient:#FF2E93:#FFC93C><bold>Lame de LaPanthèreRose</bold></gradient>"
            )
         )
      );
      this.add(
         "armure_netherite",
         var6,
         0.2,
         "Armure complète en Netherite (Protection IV)",
         Material.NETHERITE_CHESTPLATE,
         1,
         () -> armorEnchanted("NETHERITE", 4, true)
      );
      this.add(
         "jackpot",
         var6,
         0.4,
         "Jackpot : 16 diamants, 1 pomme de Notch et 64 fioles d'XP",
         Material.NETHER_STAR,
         1,
         () -> List.of(new ItemStack(Material.DIAMOND, 16), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), new ItemStack(Material.EXPERIENCE_BOTTLE, 64))
      );
      this.defineFun();
   }

   private void defineFun() {
      Loots.Tier var1 = Loots.Tier.COMMUN;
      Loots.Tier var2 = Loots.Tier.PEU_COMMUN;
      Loots.Tier var3 = Loots.Tier.RARE;
      Loots.Tier var4 = Loots.Tier.EPIQUE;
      Loots.Tier var5 = Loots.Tier.LEGENDAIRE;
      Loots.Tier var6 = Loots.Tier.MYTHIQUE;
      String var7 = "<gray>";
      this.fun(
         "ticket_cafet",
         var1,
         1.0,
         "Ticket de cafet (périmé)",
         Material.PAPER,
         1,
         () -> List.of(FunItems.make(Material.PAPER, 1, "<#FFD25E>Ticket de cafet", null, var7 + "Valable hier.", var7 + "La dame de la cafet a dit non."))
      );
      this.fun(
         "poly_maths",
         var1,
         1.0,
         "Poly d'analyse jamais ouvert",
         Material.BOOK,
         1,
         () -> List.of(FunItems.make(Material.BOOK, 1, "<#4FC3FF>Poly d'analyse", null, var7 + "Encore sous plastique.", var7 + "Partiel dans 2 jours."))
      );
      this.fun(
         "carte_etu",
         var1,
         1.0,
         "Carte étudiante démagnétisée",
         Material.PAPER,
         1,
         () -> List.of(FunItems.make(Material.PAPER, 1, "<#BFBFBF>Carte étudiante", null, var7 + "Ne bipe plus depuis la rentrée."))
      );
      this.fun(
         "cookies_cafet",
         var1,
         1.0,
         "8 cookies de la cafet (un peu durs)",
         Material.COOKIE,
         8,
         () -> List.of(FunItems.make(Material.COOKIE, 8, "<#C68B59>Cookie de la cafet", null, var7 + "Attention aux dents."))
      );
      this.fun(
         "baguettes",
         var1,
         1.0,
         "12 baguettes de la veille",
         Material.BREAD,
         12,
         () -> List.of(FunItems.make(Material.BREAD, 12, "<#E8C07D>Baguette de la veille", null, var7 + "Peut servir d'arme."))
      );
      this.fun(
         "chaussette_seule",
         var1,
         1.0,
         "Chaussette orpheline",
         Material.LEATHER_BOOTS,
         1,
         () -> List.of(FunItems.dyed(Material.LEATHER_BOOTS, 16777215, "<white>Chaussette orpheline", var7 + "Sa jumelle est partie au WEI."))
      );
      this.fun(
         "gobelets",
         var1,
         1.0,
         "8 gobelets réutilisables du BDE",
         Material.GLASS_BOTTLE,
         8,
         () -> List.of(FunItems.make(Material.GLASS_BOTTLE, 8, "<#4FC3FF>Gobelet du BDE", null, var7 + "Réutilisable. Jamais rendu."))
      );
      this.fun(
         "kebab",
         var1,
         1.0,
         "3 kebabs de 3 h du mat",
         Material.COOKED_BEEF,
         3,
         () -> List.of(FunItems.make(Material.COOKED_BEEF, 3, "<#E8A33D>Kebab de 3 h du mat", "kebab", var7 + "Salade, tomate, oignons... regrets."))
      );
      this.fun(
         "cafe_amphi",
         var1,
         1.0,
         "2 cafés de l'amphi",
         Material.POTION,
         2,
         () -> List.of(FunItems.drink(2, "<#6F4E37>Café de l'amphi", 7294519, "cafe", var7 + "Célérité et rapidité.", var7 + "Goût de carton."))
      );
      this.fun(
         "canard",
         var1,
         0.9,
         "Canard en plastique (coin coin)",
         Material.YELLOW_DYE,
         1,
         () -> List.of(FunItems.make(Material.YELLOW_DYE, 1, "<#FFE14D>Canard en plastique", "canard", var7 + "Clic droit : coin coin."))
      );
      this.fun(
         "eclairs",
         var1,
         1.0,
         "6 éclairs au chocolat",
         Material.PUMPKIN_PIE,
         6,
         () -> List.of(FunItems.make(Material.PUMPKIN_PIE, 6, "<#5C3A21>Éclair au chocolat", null, var7 + "Volés à la boulangerie d'en face."))
      );
      this.fun(
         "pates",
         var1,
         1.0,
         "16 pâtes au beurre du dimanche soir",
         Material.BAKED_POTATO,
         16,
         () -> List.of(FunItems.make(Material.BAKED_POTATO, 16, "<#F5DEB3>Pâtes au beurre", null, var7 + "Le repas de la fin du mois."))
      );
      this.fun(
         "bougies",
         var1,
         1.0,
         "32 bougies d'anniversaire XXL",
         Material.TORCH,
         32,
         () -> List.of(FunItems.make(Material.TORCH, 32, "<#FFC93C>Bougie d'anniversaire XXL", null, var7 + "Joyeux anniversaire à personne."))
      );
      this.fun(
         "confettis",
         var1,
         1.0,
         "16 confettis du gala",
         Material.PINK_DYE,
         16,
         () -> List.of(FunItems.make(Material.PINK_DYE, 16, "<#FF9AC8>Confettis du gala", null, var7 + "On en retrouve encore en juin."))
      );
      this.fun(
         "chips",
         var1,
         1.0,
         "12 chips au paprika",
         Material.DRIED_KELP,
         12,
         () -> List.of(FunItems.make(Material.DRIED_KELP, 12, "<#E8A33D>Chips au paprika", null, var7 + "Le dîner des champions."))
      );
      this.fun(
         "pommes_talensac",
         var1,
         1.0,
         "8 pommes du marché de Talensac",
         Material.APPLE,
         8,
         () -> List.of(FunItems.make(Material.APPLE, 8, "<#FF5555>Pomme de Talensac", null, var7 + "Bio, locale et chère."))
      );
      this.fun(
         "eau_soiree",
         var1,
         1.0,
         "3 bouteilles d'eau (pour la soirée, promis)",
         Material.POTION,
         3,
         () -> List.of(Util.named(Util.potion(Material.POTION, PotionType.WATER, 3), "<#4FC3FF>Bouteille d'eau de soirée"))
      );
      this.fun(
         "stylos",
         var1,
         1.0,
         "16 stylos 4 couleurs",
         Material.STICK,
         16,
         () -> List.of(FunItems.make(Material.STICK, 16, "<#4FC3FF>Stylo 4 couleurs", null, var7 + "Le vert n'écrit plus."))
      );
      this.fun(
         "tong",
         var1,
         0.9,
         "Tong de la plage de Pornic",
         Material.LEATHER,
         1,
         () -> List.of(FunItems.make(Material.LEATHER, 1, "<#FFD25E>Tong de Pornic", "tong", var7 + "Frappe : fait faire demi-tour."))
      );
      this.fun(
         "parapluie",
         var1,
         1.0,
         "Parapluie breton",
         Material.STICK,
         1,
         () -> List.of(FunItems.make(Material.STICK, 1, "<#4F6FFF>Parapluie breton", null, var7 + "Il pleut toujours. Toujours."))
      );
      this.fun(
         "galettes",
         var1,
         1.0,
         "8 galettes-saucisses",
         Material.COOKED_PORKCHOP,
         8,
         () -> List.of(FunItems.make(Material.COOKED_PORKCHOP, 8, "<#C68B59>Galette-saucisse", null, var7 + "« Je t'aime » en breton."))
      );
      this.fun(
         "caramel",
         var1,
         1.0,
         "3 caramels au beurre salé",
         Material.HONEY_BOTTLE,
         3,
         () -> List.of(FunItems.make(Material.HONEY_BOTTLE, 3, "<#E8A33D>Caramel au beurre salé", null, var7 + "Le beurre doux est interdit ici."))
      );
      this.fun(
         "rateau",
         var1,
         0.9,
         "Râteau de soirée",
         Material.WOODEN_HOE,
         1,
         () -> List.of(FunItems.make(Material.WOODEN_HOE, 1, "<#FF9AC8>Râteau de soirée", "rateau", var7 + "Frappe : fait décoller (de honte)."))
      );
      this.fun(
         "morue",
         var1,
         0.9,
         "Morue de la gifle",
         Material.COD,
         1,
         () -> List.of(FunItems.make(Material.COD, 1, "<#4FC3FF>Morue de la gifle", "gifle", var7 + "Frappe : ralentit et humilie."))
      );
      this.fun(
         "balai",
         var1,
         0.9,
         "Balai du concierge",
         Material.BRUSH,
         1,
         () -> List.of(FunItems.make(Material.BRUSH, 1, "<#BFBFBF>Balai du concierge", "balai", var7 + "Frappe : sol glissant (nausée)."))
      );
      this.fun(
         "lanternes",
         var1,
         1.0,
         "4 lanternes de la MDE",
         Material.LANTERN,
         4,
         () -> List.of(FunItems.make(Material.LANTERN, 4, "<#FFC93C>Lanterne de la MDE", null, var7 + "Empruntée. Définitivement."))
      );
      this.fun(
         "plaids",
         var1,
         1.0,
         "8 plaids de révision",
         Material.WHITE_CARPET,
         8,
         () -> List.of(FunItems.make(Material.WHITE_CARPET, 8, "<white>Plaid de révision", null, var7 + "On s'endort dessus en 10 min."))
      );
      this.fun(
         "tickets_traq",
         var1,
         1.0,
         "3 tickets boisson du Traq",
         Material.PAPER,
         3,
         () -> List.of(
            FunItems.make(Material.PAPER, 3, "<#FFC93C>Ticket boisson du Traq", null, var7 + "Non remboursable.", var7 + "Valable quand le Traq est ouvert.")
         )
      );
      this.fun(
         "chewing",
         var1,
         1.0,
         "4 chewing-gums de l'amphi",
         Material.SLIME_BALL,
         4,
         () -> List.of(FunItems.make(Material.SLIME_BALL, 4, "<#7FFF7F>Chewing-gum de l'amphi", null, var7 + "Décollé de sous une table."))
      );
      this.fun(
         "kit_rentree",
         var1,
         1.0,
         "Kit de rentrée (déjà en retard)",
         Material.WRITABLE_BOOK,
         1,
         () -> List.of(
            FunItems.make(Material.WRITABLE_BOOK, 1, "<#4FC3FF>Cahier de rentrée", null, var7 + "Page 1 : vide."),
            new ItemStack(Material.PAPER, 16),
            new ItemStack(Material.INK_SAC, 4)
         )
      );
      this.fun(
         "pinte_traq",
         var2,
         1.0,
         "2 pintes du Traq",
         Material.POTION,
         2,
         () -> List.of(FunItems.drink(2, "<#FFC93C>Pinte du Traq", 15905597, "pinte", var7 + "Force, mais la tête tourne."))
      );
      this.fun(
         "taureau",
         var2,
         1.0,
         "2 canettes de Taureau Rouge",
         Material.POTION,
         2,
         () -> List.of(FunItems.drink(2, "<#FF5555>Taureau Rouge", 1986815, "redbull", var7 + "Vitesse et saut. Te donne presque des ailes."))
      );
      this.fun(
         "soupe",
         var2,
         1.0,
         "2 soupes mystère de la cafet",
         Material.MUSHROOM_STEW,
         1,
         () -> List.of(
            FunItems.make(Material.MUSHROOM_STEW, 1, "<#8B6B3D>Soupe mystère", "soupe", var7 + "Régénère. Ralentit. Inquiète."),
            FunItems.make(Material.MUSHROOM_STEW, 1, "<#8B6B3D>Soupe mystère", "soupe", var7 + "Régénère. Ralentit. Inquiète.")
         )
      );
      this.fun(
         "poele",
         var2,
         1.0,
         "Poêle de la cafet",
         Material.IRON_SHOVEL,
         1,
         () -> List.of(
            Util.ench(FunItems.make(Material.IRON_SHOVEL, 1, "<#BFBFBF>Poêle de la cafet", "poele", var7 + "Frappe : BONK."), Enchantment.SHARPNESS, 2)
         )
      );
      this.fun(
         "fourchette",
         var2,
         1.0,
         "Fourchette du RU",
         Material.IRON_HOE,
         1,
         () -> List.of(FunItems.make(Material.IRON_HOE, 1, "<#BFBFBF>Fourchette du RU", "fourchette", var7 + "Frappe : pique (poison)."))
      );
      this.fun(
         "lance_patate",
         var2,
         1.0,
         "Lance-patate",
         Material.CARROT_ON_A_STICK,
         1,
         () -> List.of(FunItems.make(Material.CARROT_ON_A_STICK, 1, "<#E8C07D>Lance-patate", "lance_patate", var7 + "Frappe : envoie la cible en l'air."))
      );
      this.fun(
         "corne_wei",
         var2,
         1.0,
         "Corne de brume du WEI",
         Material.GOAT_HORN,
         1,
         () -> List.of(FunItems.make(Material.GOAT_HORN, 1, "<#FFC93C>Corne de brume du WEI", null, var7 + "Réveille tout le camping."))
      );
      this.fun(
         "disco",
         var2,
         1.0,
         "Boule à facettes de poche",
         Material.AMETHYST_SHARD,
         1,
         () -> List.of(FunItems.make(Material.AMETHYST_SHARD, 1, "<#C77DFF>Boule à facettes", "disco", var7 + "Clic droit : c'est la fête."))
      );
      this.fun(
         "tambour",
         var2,
         1.0,
         "Baguettes de batterie",
         Material.STICK,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(Material.STICK, 1, "<#FF9AC8>Baguettes de batterie", "tambour", var7 + "Clic droit : boum tchak."), Enchantment.UNBREAKING, 1
            )
         )
      );
      this.fun(
         "lunettes",
         var2,
         1.0,
         "Lunettes de soleil (portées en amphi)",
         Material.LEATHER_HELMET,
         1,
         () -> List.of(
            Util.ench(
               FunItems.dyed(Material.LEATHER_HELMET, 1118481, "<#333333>Lunettes de soleil", var7 + "Portées en amphi, à 8 h, en novembre."),
               Enchantment.PROTECTION,
               2
            )
         )
      );
      this.fun("costard", var2, 1.0, "Costard du gala", Material.LEATHER_CHESTPLATE, 1, () -> set("LEATHER", 1118498, "<#333355>Costard du gala"));
      this.fun("pyjama", var2, 1.0, "Pyjama de révisions", Material.LEATHER_LEGGINGS, 1, () -> set("LEATHER", 16751304, "<#FF9AC8>Pyjama de révisions"));
      this.fun(
         "eastpak",
         var2,
         1.0,
         "Sac Eastpak troué",
         Material.BUNDLE,
         1,
         () -> List.of(FunItems.make(Material.BUNDLE, 1, "<#4F6FFF>Sac Eastpak troué", null, var7 + "Contient 3 ans de miettes."))
      );
      this.fun(
         "casque_facteur",
         var2,
         1.0,
         "Casque du facteur",
         Material.IRON_HELMET,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(Material.IRON_HELMET, 1, "<#FFD25E>Casque du facteur", null, var7 + "On remet le facteur sur le vélo ?"),
               Enchantment.PROTECTION,
               2
            )
         )
      );
      this.fun(
         "velo_facteur",
         var2,
         1.0,
         "Le vélo du facteur (œuf de cheval)",
         Material.HORSE_SPAWN_EGG,
         1,
         () -> List.of(Util.named(new ItemStack(Material.HORSE_SPAWN_EGG), "Le vélo du facteur"), new ItemStack(Material.SADDLE))
      );
      this.fun(
         "chat_oniris",
         var2,
         1.0,
         "Chat échappé d'Oniris",
         Material.CAT_SPAWN_EGG,
         1,
         () -> List.of(Util.named(new ItemStack(Material.CAT_SPAWN_EGG), "Chat d'Oniris"), new ItemStack(Material.COD, 8))
      );
      this.fun(
         "poules_wei",
         var2,
         1.0,
         "2 poules mascottes du WEI",
         Material.CHICKEN_SPAWN_EGG,
         2,
         () -> List.of(Util.named(new ItemStack(Material.CHICKEN_SPAWN_EGG, 2), "Mascotte du WEI"))
      );
      this.fun(
         "frites",
         var2,
         1.0,
         "16 frites de 2 h du mat",
         Material.BAKED_POTATO,
         16,
         () -> List.of(FunItems.make(Material.BAKED_POTATO, 16, "<#FFD25E>Frite de 2 h du mat", null, var7 + "Froides mais sauvées."))
      );
      this.fun(
         "crepes",
         var2,
         1.0,
         "8 crêpes au sucre",
         Material.PUMPKIN_PIE,
         8,
         () -> List.of(FunItems.make(Material.PUMPKIN_PIE, 8, "<#F5DEB3>Crêpe au sucre", null, var7 + "Bretagne oblige."))
      );
      this.fun(
         "pupitre",
         var2,
         1.0,
         "Pupitre d'amphi (bouclier)",
         Material.SHIELD,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(Material.SHIELD, 1, "<#8B6B3D>Pupitre d'amphi", null, var7 + "Bloque aussi les questions du prof."), Enchantment.UNBREAKING, 2
            )
         )
      );
      this.fun(
         "regle",
         var2,
         1.0,
         "Règle en bois d'un mètre (Tranchant V)",
         Material.WOODEN_SWORD,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(Material.WOODEN_SWORD, 1, "<#C68B59>Règle d'un mètre", null, var7 + "Le prof de dessin la cherche."), Enchantment.SHARPNESS, 5
            )
         )
      );
      this.fun(
         "elastique",
         var2,
         1.0,
         "Élastique de bureau (arc Frappe II)",
         Material.BOW,
         1,
         () -> List.of(
            Util.ench(FunItems.make(Material.BOW, 1, "<#FFD25E>Élastique de bureau", null, var7 + "Projette les gens. Et les trombones."), Enchantment.PUNCH, 2),
            new ItemStack(Material.ARROW, 32)
         )
      );
      this.fun(
         "pelle_tarte",
         var2,
         1.0,
         "Pelle à tarte (Efficacité III)",
         Material.IRON_SHOVEL,
         1,
         () -> List.of(
            Util.ench(FunItems.make(Material.IRON_SHOVEL, 1, "<#F5DEB3>Pelle à tarte", null, var7 + "Pour creuser dans la tarte."), Enchantment.EFFICIENCY, 3)
         )
      );
      this.fun(
         "shots_xp",
         var2,
         1.0,
         "24 shots d'expérience",
         Material.EXPERIENCE_BOTTLE,
         24,
         () -> List.of(FunItems.make(Material.EXPERIENCE_BOTTLE, 24, "<#55FF88>Shot d'expérience", null, var7 + "Avec modération."))
      );
      this.fun(
         "chaussette_wei",
         var2,
         1.0,
         "Chaussette du WEI (jamais lavée)",
         Material.LEATHER_BOOTS,
         1,
         () -> List.of(FunItems.make(Material.LEATHER_BOOTS, 1, "<#7FB800>Chaussette du WEI", "chaussette", var7 + "Frappe : assombrit la vue (l'odeur)."))
      );
      this.fun(
         "matraque",
         var3,
         1.0,
         "Matraque du respo VSS",
         Material.BLAZE_ROD,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(
                  Material.BLAZE_ROD, 1, "<#FF5555>Matraque du respo VSS", "matraque", var7 + "Frappe : ralentit fortement.", var7 + "Ici on se respecte."
               ),
               Enchantment.KNOCKBACK,
               1
            )
         )
      );
      this.fun(
         "ventilo",
         var3,
         1.0,
         "Ventilo de l'amphi en juin",
         Material.BREEZE_ROD,
         1,
         () -> List.of(FunItems.make(Material.BREEZE_ROD, 1, "<#BFEFFF>Ventilo de l'amphi", "ventilo", var7 + "Frappe : souffle la cible au loin."))
      );
      this.fun(
         "eternuements",
         var3,
         1.0,
         "16 éternuements de grippe (charges de vent)",
         Material.WIND_CHARGE,
         16,
         () -> List.of(FunItems.make(Material.WIND_CHARGE, 16, "<#BFEFFF>Éternuement de grippe", null, var7 + "À lancer. Pas de mouchoir."))
      );
      this.fun(
         "baguette_chaos",
         var3,
         1.0,
         "Baguette du magicien de soirée",
         Material.STICK,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(Material.STICK, 1, "<#C77DFF>Baguette du magicien", "chaos", var7 + "Frappe : un effet au hasard."), Enchantment.UNBREAKING, 1
            )
         )
      );
      this.fun("livre_sharp6", var3, 0.9, "Grimoire du Tranchant VI", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.SHARPNESS, 6)));
      this.fun("livre_eff6", var3, 0.9, "Grimoire de l'Efficacité VI", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.EFFICIENCY, 6)));
      this.fun("livre_prot5", var3, 0.9, "Grimoire de Protection V", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.PROTECTION, 5)));
      this.fun(
         "vodka_jet",
         var3,
         1.0,
         "2 Vodka Jet",
         Material.POTION,
         2,
         () -> List.of(FunItems.drink(2, "<#4FC3FF>Vodka Jet", 14544639, "vodka", var7 + "Vitesse, nausée, trou noir."))
      );
      this.fun(
         "captain",
         var3,
         1.0,
         "2 pintes de Captain",
         Material.POTION,
         2,
         () -> List.of(FunItems.drink(2, "<#C68B59>Pinte de Captain", 9127187, "captain", var7 + "Résistance. Et mal de mer."))
      );
      this.fun(
         "benite",
         var3,
         1.0,
         "3 eaux bénites de l'aumônerie",
         Material.POTION,
         3,
         () -> List.of(FunItems.drink(3, "<white>Eau bénite", 16777215, "benite", var7 + "Retire les mauvais effets."))
      );
      this.fun(
         "pioche_partiels",
         var3,
         1.0,
         "Pioche des partiels (Efficacité IV, Fortune II)",
         Material.DIAMOND_PICKAXE,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  FunItems.make(Material.DIAMOND_PICKAXE, 1, "<#4FC3FF>Pioche des partiels", null, var7 + "Creuse plus vite que ta moyenne."),
                  Enchantment.EFFICIENCY,
                  4
               ),
               Enchantment.FORTUNE,
               2
            )
         )
      );
      this.fun(
         "epee_bde",
         var3,
         1.0,
         "Épée du BDE (Tranchant IV, Butin II)",
         Material.IRON_SWORD,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(FunItems.make(Material.IRON_SWORD, 1, "<#FFC93C>Épée du BDE", null, var7 + "Prêtée. À rendre (jamais)."), Enchantment.SHARPNESS, 4),
               Enchantment.LOOTING,
               2
            )
         )
      );
      this.fun(
         "baskets_cross",
         var3,
         1.0,
         "Baskets du cross de l'école",
         Material.DIAMOND_BOOTS,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  FunItems.make(Material.DIAMOND_BOOTS, 1, "<#55FF88>Baskets du cross", null, var7 + "Dernier, mais arrivé."), Enchantment.FEATHER_FALLING, 4
               ),
               Enchantment.DEPTH_STRIDER,
               3
            )
         )
      );
      this.fun(
         "ailes_cassees",
         var3,
         1.0,
         "Ailes de soirée (un peu cassées)",
         Material.ELYTRA,
         1,
         () -> List.of(Util.usesLeft(FunItems.make(Material.ELYTRA, 1, "<#C77DFF>Ailes de soirée", null, var7 + "Encore 30 secondes de vol."), 30))
      );
      this.fun(
         "fourche_festnoz",
         var3,
         1.0,
         "Fourche du Fest-Noz (trident Loyauté III)",
         Material.TRIDENT,
         1,
         () -> List.of(
            Util.ench(
               FunItems.make(Material.TRIDENT, 1, "<#4F6FFF>Fourche du Fest-Noz", null, var7 + "Revient toujours, comme l'an dro."), Enchantment.LOYALTY, 3
            )
         )
      );
      this.fun(
         "videur",
         var3,
         1.0,
         "Le videur du Traq (œuf de golem)",
         Material.IRON_GOLEM_SPAWN_EGG,
         1,
         () -> List.of(Util.named(new ItemStack(Material.IRON_GOLEM_SPAWN_EGG), "Videur du Traq"))
      );
      this.fun(
         "axolotl",
         var3,
         1.0,
         "Mascotte de l'aquarium (axolotl)",
         Material.AXOLOTL_BUCKET,
         1,
         () -> List.of(Util.named(new ItemStack(Material.AXOLOTL_BUCKET), "<#FF9AC8>Mascotte de l'aquarium"))
      );
      this.fun(
         "perroquet",
         var3,
         1.0,
         "Perroquet qui répète les ragots",
         Material.PARROT_SPAWN_EGG,
         1,
         () -> List.of(Util.named(new ItemStack(Material.PARROT_SPAWN_EGG), "Perroquet à ragots"))
      );
      this.fun(
         "invisi",
         var3,
         1.0,
         "2 potions « je rentre discret de soirée »",
         Material.POTION,
         2,
         () -> List.of(Util.named(Util.potion(Material.POTION, PotionType.LONG_INVISIBILITY, 2), "<#BFBFBF>Je rentre discret"))
      );
      this.fun(
         "caisse_bde",
         var3,
         0.8,
         "Caisse du BDE (vidée à moitié)",
         Material.GOLD_INGOT,
         16,
         () -> List.of(new ItemStack(Material.DIAMOND, 4), new ItemStack(Material.GOLD_INGOT, 16), new ItemStack(Material.EMERALD, 8))
      );
      this.fun("livre_sharp8", var4, 0.9, "Grimoire interdit (Tranchant VIII)", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.SHARPNESS, 8)));
      this.fun("livre_prot6", var4, 0.9, "Grimoire de Protection VI", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.PROTECTION, 6)));
      this.fun(
         "epee_respo",
         var4,
         1.0,
         "Épée du respo soirée (Tranchant VII)",
         Material.DIAMOND_SWORD,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  Util.ench(
                     FunItems.make(Material.DIAMOND_SWORD, 1, "<#C77DFF>Épée du respo soirée", null, var7 + "Fin de soirée : tout le monde dehors."),
                     Enchantment.SHARPNESS,
                     7
                  ),
                  Enchantment.FIRE_ASPECT,
                  2
               ),
               Enchantment.KNOCKBACK,
               2
            )
         )
      );
      this.fun(
         "pioche_stagiaire",
         var4,
         1.0,
         "Pioche du stagiaire surmotivé (Efficacité VIII)",
         Material.DIAMOND_PICKAXE,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  FunItems.make(Material.DIAMOND_PICKAXE, 1, "<#4FC3FF>Pioche du stagiaire", null, var7 + "Il ne dort jamais."), Enchantment.EFFICIENCY, 8
               ),
               Enchantment.UNBREAKING,
               3
            )
         )
      );
      this.fun(
         "arc_dimanche",
         var4,
         1.0,
         "Arc du tir à l'arc du dimanche (Puissance VII)",
         Material.BOW,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  Util.ench(FunItems.make(Material.BOW, 1, "<#FFC93C>Arc du dimanche", null, var7 + "Vise bien, pour une fois."), Enchantment.POWER, 7),
                  Enchantment.FLAME,
                  1
               ),
               Enchantment.INFINITY,
               1
            ),
            new ItemStack(Material.ARROW)
         )
      );
      this.fun("armure_gala", var4, 0.8, "Armure de gala (diamant Protection IV)", Material.DIAMOND_CHESTPLATE, 1, () -> armorEnchanted("DIAMOND", 4, false));
      this.fun(
         "ailes_bde",
         var4,
         0.9,
         "Ailes du BDE (élytres + 32 fusées)",
         Material.ELYTRA,
         1,
         () -> List.of(
            Util.ench(FunItems.make(Material.ELYTRA, 1, "<#4FC3FF>Ailes du BDE", null, var7 + "Pour arriver en retard avec style."), Enchantment.UNBREAKING, 3),
            new ItemStack(Material.FIREWORK_ROCKET, 32)
         )
      );
      this.fun(
         "pommes_or8",
         var4,
         1.0,
         "8 pommes d'or du marché",
         Material.GOLDEN_APPLE,
         8,
         () -> List.of(FunItems.make(Material.GOLDEN_APPLE, 8, "<#FFC93C>Pomme d'or du marché", null, var7 + "Prix au kilo : indécent."))
      );
      this.fun(
         "totem_gueule",
         var4,
         0.8,
         "Totem anti-gueule-de-bois",
         Material.TOTEM_OF_UNDYING,
         1,
         () -> List.of(FunItems.make(Material.TOTEM_OF_UNDYING, 1, "<#55FF88>Totem anti-gueule-de-bois", null, var7 + "Te sauve une fois. Pas le lendemain."))
      );
      this.fun(
         "marteau",
         var4,
         0.8,
         "Marteau du concours de force (masse Densité III)",
         Material.MACE,
         1,
         () -> List.of(Util.ench(FunItems.make(Material.MACE, 1, "<#BFBFBF>Marteau de foire", null, var7 + "Tape fort. Très fort."), Enchantment.DENSITY, 3))
      );
      this.fun(
         "chatons",
         var4,
         0.9,
         "Portée de chatons d'Oniris (3 œufs)",
         Material.CAT_SPAWN_EGG,
         3,
         () -> List.of(Util.named(new ItemStack(Material.CAT_SPAWN_EGG, 3), "Chaton d'Oniris"), new ItemStack(Material.COD, 16))
      );
      this.special("fly30", var4, 1.0, "Fly pendant 30 minutes", Material.FEATHER, 1800, 0);
      this.special("bourrage", var4, 0.9, "Bourrage d'urne (+5 votes)", Material.PAPER, 0, 5);
      this.special("aura_president", var4, 0.9, "Aura de président (+10⁶ aura)", Material.NETHER_STAR, 0, 0, 1000000L);
      this.fun(
         "livre_sharp10", var5, 0.9, "Le Tranchant Ultime (livre Tranchant X)", Material.ENCHANTED_BOOK, 1, () -> List.of(book(Enchantment.SHARPNESS, 10))
      );
      this.fun(
         "foreuse",
         var5,
         0.9,
         "Foreuse du Pôle Log (Efficacité X)",
         Material.NETHERITE_PICKAXE,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  Util.ench(
                     FunItems.make(Material.NETHERITE_PICKAXE, 1, "<#FFC93C>Foreuse du Pôle Log", null, var7 + "Creuse jusqu'au bar."),
                     Enchantment.EFFICIENCY,
                     10
                  ),
                  Enchantment.FORTUNE,
                  4
               ),
               Enchantment.UNBREAKING,
               5
            )
         )
      );
      this.fun(
         "lame_gala",
         var5,
         0.9,
         "Lame du Gala (Tranchant X)",
         Material.NETHERITE_SWORD,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  Util.ench(
                     FunItems.make(Material.NETHERITE_SWORD, 1, "<#FFC93C><bold>Lame du Gala</bold>", null, var7 + "Tenue correcte exigée."),
                     Enchantment.SHARPNESS,
                     10
                  ),
                  Enchantment.LOOTING,
                  5
               ),
               Enchantment.FIRE_ASPECT,
               2
            )
         )
      );
      this.fun("armure_videur", var5, 0.8, "Armure du Videur (Netherite Protection VI)", Material.NETHERITE_CHESTPLATE, 1, () -> {
         ArrayList var0 = new ArrayList();

         for (ItemStack var2x : armor("NETHERITE")) {
            var2x.addUnsafeEnchantment(Enchantment.PROTECTION, 6);
            var2x.addUnsafeEnchantment(Enchantment.UNBREAKING, 5);
            var0.add(var2x);
         }

         return var0;
      });
      this.special("fly2h", var5, 1.0, "Fly de soirée (2 heures)", Material.ELYTRA, 7200, 0);
      this.fun(
         "notch3",
         var5,
         0.8,
         "Trois pommes de Notch (une par repas)",
         Material.ENCHANTED_GOLDEN_APPLE,
         3,
         () -> List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3))
      );
      this.special("aura_legende", var5, 0.9, "Aura légendaire (+10⁹ aura)", Material.NETHER_STAR, 0, 0, 1000000000L);
      this.fun(
         "trident_neptune",
         var6,
         0.25,
         "Trident de Neptune (Empalement X)",
         Material.TRIDENT,
         1,
         () -> List.of(
            Util.ench(
               Util.ench(
                  Util.ench(
                     Util.ench(
                        FunItems.make(
                           Material.TRIDENT,
                           1,
                           "<gradient:#4FC3FF:#C77DFF><bold>Trident de Neptune</bold></gradient>",
                           null,
                           var7 + "Le dieu des mers de Nantes-Atlantique."
                        ),
                        Enchantment.LOYALTY,
                        5
                     ),
                     Enchantment.IMPALING,
                     10
                  ),
                  Enchantment.CHANNELING,
                  1
               ),
               Enchantment.UNBREAKING,
               10
            )
         )
      );
      this.fun(
         "arc_divin",
         var6,
         0.25,
         "Arc de Cupidon divin (Puissance X, incassable)",
         Material.BOW,
         1,
         () -> List.of(
            Util.unbreakable(
               Util.ench(
                  Util.ench(
                     Util.ench(
                        Util.ench(
                           FunItems.make(
                              Material.BOW,
                              1,
                              "<gradient:#FF9AC8:#FF2E93><bold>Arc de Cupidon divin</bold></gradient>",
                              null,
                              var7 + "Chaque flèche est un coup de foudre."
                           ),
                           Enchantment.POWER,
                           10
                        ),
                        Enchantment.PUNCH,
                        5
                     ),
                     Enchantment.FLAME,
                     1
                  ),
                  Enchantment.INFINITY,
                  1
               )
            ),
            new ItemStack(Material.ARROW)
         )
      );
      this.fun(
         "caisse_noire",
         var6,
         0.25,
         "Caisse noire du BDE (32 diamants, 4 netherite, balise)",
         Material.BEACON,
         1,
         () -> List.of(new ItemStack(Material.DIAMOND, 32), new ItemStack(Material.NETHERITE_INGOT, 4), new ItemStack(Material.BEACON))
      );
      this.special("ascension", var6, 0.25, "Ascension mythique (6 h de fly + 10¹² aura)", Material.NETHER_STAR, 21600, 0, 1000000000000L);
   }

   public static final class Loot {
      public final String id;
      public final Loots.Tier tier;
      public final double weight;
      public final String name;
      public final Material icon;
      public final int iconAmount;
      public final Supplier<List<ItemStack>> items;
      public final int flySeconds;
      public final int bonusVotes;
      public long bonusAura;
      public double chance;

      Loot(String var1, Loots.Tier var2, double var3, String var5, Material var6, int var7, Supplier<List<ItemStack>> var8, int var9, int var10) {
         this.id = var1;
         this.tier = var2;
         this.weight = var3;
         this.name = var5;
         this.icon = var6;
         this.iconAmount = var7;
         this.items = var8;
         this.flySeconds = var9;
         this.bonusVotes = var10;
      }
   }

   public static enum Tier {
      COMMUN("Commun", "<#BFBFBF>", 40.0, Material.LIGHT_GRAY_DYE),
      PEU_COMMUN("Peu commun", "<#55FF55>", 28.0, Material.LIME_DYE),
      RARE("Rare", "<#4FC3FF>", 18.0, Material.LIGHT_BLUE_DYE),
      EPIQUE("Épique", "<#C77DFF>", 9.5, Material.PURPLE_DYE),
      LEGENDAIRE("Légendaire", "<#FFC93C>", 3.5, Material.ORANGE_DYE),
      MYTHIQUE("Mythique", "<#FF2E93>", 1.0, Material.MAGENTA_DYE);

      public final String label;
      public final String color;
      public final double share;
      public final Material dye;

      private Tier(String nullxx, String nullxxx, double nullxxxx, Material nullxxxxx) {
         this.label = nullxx;
         this.color = nullxxx;
         this.share = nullxxxx;
         this.dye = nullxxxxx;
      }

      public boolean announced() {
         return this == LEGENDAIRE || this == MYTHIQUE;
      }

      public String styled(String var1) {
         return this == MYTHIQUE ? "<gradient:#FF2E93:#FFC93C>" + var1 + "</gradient>" : this.color + var1;
      }
   }
}
