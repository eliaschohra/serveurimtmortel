package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class Kits implements CommandExecutor, TabCompleter, Listener {
   private static final long H = 3600000L;
   private final BDEIMT pl;
   private final Map<String, Kits.Kit> kits = new LinkedHashMap<>();
   private final List<String> voteKits = List.of("imtmortel", "zimtzimt", "ascension", "bartbart", "wizart", "passion");

   public Kits(BDEIMT var1) {
      this.pl = var1;
      this.define();
   }

   private void add(Kits.Kit var1) {
      this.kits.put(var1.id(), var1);
   }

   /**
    * Les bottes de Silas : des bottes en fer Agilite givree I, deja usees a
    * moitie — elles ont vecu.
    */
   static ItemStack silasBoots() {
      ItemStack var0 = e(Material.IRON_BOOTS, Enchantment.FROST_WALKER, 1);
      var0.editMeta(var1 -> {
         if (var1 instanceof org.bukkit.inventory.meta.Damageable var2) {
            var2.setDamage(Material.IRON_BOOTS.getMaxDurability() / 2);
         }
      });
      return Util.lore(
         Util.named(var0, "<#7FE3FF>Bottes de Silas</#7FE3FF>"),
         List.of(
            "<gray>Les bottes que Silas portait à son âge d'or",
            "<gray>sur League of Legends. Il glissait sur ses",
            "<gray>ennemis tel un surfeur de glace."
         )
      );
   }

   /**
    * {@code /vote ‹liste›} : prendre le kit d'une liste, et lui donner une voix.
    *
    * @return false si ce n'est pas un nom de liste connu
    */
   public boolean voteList(Player var1, String var2) {
      String var3 = var2.toLowerCase(Locale.ROOT);

      if (var3.equals("brest")) {
         this.pl.fun().brest(var1);
         return true;
      }

      if (!this.voteKits.contains(var3)) {
         return false;
      }

      this.claim(var1, this.kits.get(var3));
      return true;
   }

   private static ItemStack e(Material var0, Object... var1) {
      ItemStack var2 = new ItemStack(var0);

      for (byte var3 = 0; var3 + 1 < var1.length; var3 += 2) {
         var2.addUnsafeEnchantment((Enchantment)var1[var3], (Integer)var1[var3 + 1]);
      }

      return var2;
   }

   private static ItemStack n(int var0, Material var1) {
      return new ItemStack(var1, var0);
   }

   private void define() {
      this.add(
         new Kits.Kit(
            "depart",
            "/kit",
            "Kit de départ",
            Material.LEATHER_CHESTPLATE,
            7200000L,
            Kits.Req.TOUS,
            null,
            false,
            List.of("Armure complète en cuir", "Épée en bois, pioche en pierre", "12 steaks cuits"),
            () -> {
               ArrayList var0 = new ArrayList<>(Loots.armor("LEATHER"));
               var0.add(n(1, Material.WOODEN_SWORD));
               var0.add(n(1, Material.STONE_PICKAXE));
               var0.add(n(12, Material.COOKED_BEEF));
               return var0;
            }
         )
      );
      this.add(
         new Kits.Kit(
            "topvote",
            "/kit topvote",
            "Kit Top Vote",
            Material.CHAINMAIL_CHESTPLATE,
            10800000L,
            Kits.Req.TOP10,
            null,
            false,
            List.of(
               "Casque et bottes en fer", "Plastron et jambières en mailles", "Épée et pioche en fer", "24 steaks cuits, 1 pomme dorée", "Arc + 64 flèches"
            ),
            () -> List.of(
               n(1, Material.IRON_HELMET),
               n(1, Material.CHAINMAIL_CHESTPLATE),
               n(1, Material.CHAINMAIL_LEGGINGS),
               n(1, Material.IRON_BOOTS),
               n(1, Material.IRON_SWORD),
               n(1, Material.IRON_PICKAXE),
               n(24, Material.COOKED_BEEF),
               n(1, Material.GOLDEN_APPLE),
               n(1, Material.BOW),
               n(64, Material.ARROW)
            )
         )
      );
      this.add(
         new Kits.Kit(
            "top1",
            "/kit top1",
            "Kit Champion (n°1 des votes)",
            Material.DIAMOND_CHESTPLATE,
            86400000L,
            Kits.Req.TOP1,
            null,
            false,
            List.of(
               "Fer + plastron et bottes en diamant",
               "Toute l'armure Protection II",
               "Épée Tranchant II, Recul I, Aura de feu I",
               "Arc Infinité + Flamme",
               "Pioche diamant Solidité II, Efficacité II",
               "Potion jetable de Régénération II",
               "1 pomme dorée, 32 steaks cuits"
            ),
            () -> {
               ArrayList var0 = new ArrayList();

               for (Material var4 : new Material[]{Material.IRON_HELMET, Material.DIAMOND_CHESTPLATE, Material.IRON_LEGGINGS, Material.DIAMOND_BOOTS}) {
                  var0.add(e(var4, Enchantment.PROTECTION, 2));
               }

               var0.add(e(Material.IRON_SWORD, Enchantment.SHARPNESS, 2, Enchantment.KNOCKBACK, 1, Enchantment.FIRE_ASPECT, 1));
               var0.add(e(Material.BOW, Enchantment.INFINITY, 1, Enchantment.FLAME, 1));
               var0.add(n(1, Material.ARROW));
               var0.add(e(Material.DIAMOND_PICKAXE, Enchantment.UNBREAKING, 2, Enchantment.EFFICIENCY, 2));
               var0.add(Util.potion(Material.SPLASH_POTION, PotionType.STRONG_REGENERATION, 1));
               var0.add(n(1, Material.GOLDEN_APPLE));
               var0.add(n(32, Material.COOKED_BEEF));
               return var0;
            }
         )
      );
      this.add(
         new Kits.Kit(
            "imtmortel",
            "/vote imtmortel",
            "IMTmortel",
            Material.GOLDEN_CARROT,
            7200000L,
            Kits.Req.TOUS,
            "vote",
            false,
            List.of("Potion de soin instantané", "16 carottes dorées", "1 seau d'eau", "16 fioles d'expérience"),
            () -> List.of(
               Util.potion(Material.POTION, PotionType.HEALING, 1),
               n(16, Material.GOLDEN_CARROT),
               n(1, Material.WATER_BUCKET),
               n(16, Material.EXPERIENCE_BOTTLE)
            )
         )
      );
      this.add(
         new Kits.Kit(
            "zimtzimt",
            "/vote zimtzimt",
            "ZIMTzimt",
            Material.ROTTEN_FLESH,
            7200000L,
            Kits.Req.TOUS,
            "vote",
            false,
            List.of("Potion jetable de dégâts instantanés", "16 chairs putréfiées"),
            () -> List.of(Util.potion(Material.SPLASH_POTION, PotionType.HARMING, 1), n(16, Material.ROTTEN_FLESH))
         )
      );
      this.add(
         new Kits.Kit(
            "ascension",
            "/vote ascension",
            "Ascension",
            Material.POLAR_BEAR_SPAWN_EGG,
            7200000L,
            Kits.Req.TOUS,
            "vote",
            false,
            List.of("Un œuf d'ours polaire", "Les bottes de Silas (Agilité givrée I)"),
            () -> List.of(n(1, Material.POLAR_BEAR_SPAWN_EGG), silasBoots())
         )
      );
      this.add(
         new Kits.Kit(
            "bartbart",
            "/vote bartbart",
            "BartBart",
            Material.GOLDEN_AXE,
            7200000L,
            Kits.Req.TOUS,
            "vote",
            false,
            List.of("Une hache en or... à 1 point de vie", "(elle cassera au premier coup)"),
            () -> List.of(Util.named(Util.usesLeft(n(1, Material.GOLDEN_AXE), 1), "<#FFC93C>Hache de BartBart"))
         )
      );
      this.add(
         new Kits.Kit(
            "wizart",
            "/vote wizart",
            "WizArt",
            Material.STICK,
            7200000L,
            Kits.Req.TOUS,
            "vote",
            false,
            List.of("Un bâton Recul X"),
            () -> List.of(Util.named(e(Material.STICK, Enchantment.KNOCKBACK, 10), "<#C77DFF>Baguette de WizArt"))
         )
      );
      this.add(
         new Kits.Kit(
            "passion",
            "/vote passion",
            "Passion",
            Material.BOW,
            7200000L,
            Kits.Req.TOUS,
            "vote",
            false,
            List.of("L'Arc de Cupidon (Flamme)", "2 flèches, et il ne tiendra que 2 tirs"),
            () -> List.of(
               Util.lore(
                  Util.named(Util.usesLeft(e(Material.BOW, Enchantment.FLAME, 1), 2), "<#FF5FAE>Arc de Cupidon"),
                  List.of("<gray>Deux flèches. Deux cœurs.", "<gray>Vise bien, il casse après.")
               ),
               n(2, Material.ARROW)
            )
         )
      );
      this.add(new Kits.Kit("mobutu", "/kit mobutu", "Mobutu", Material.NETHERITE_CHESTPLATE, 0L, Kits.Req.ADMIN, null, true, List.of(), this::mobutu));
      this.add(
         new Kits.Kit(
            "mobutu-chantier", "/kit mobutu-chantier", "Mobutu : chantier", Material.BRICKS, 0L, Kits.Req.ADMIN, null, true, List.of(), this::chantier
         )
      );
      this.add(
         new Kits.Kit("mobutu-fete", "/kit mobutu-fete", "Mobutu : fête", Material.FIREWORK_ROCKET, 0L, Kits.Req.ADMIN, null, true, List.of(), this::fete)
      );
      this.add(
         new Kits.Kit(
            "mobutu-potions", "/kit mobutu-potions", "Mobutu : potions", Material.BREWING_STAND, 0L, Kits.Req.ADMIN, null, true, List.of(), this::potions
         )
      );
      this.add(new Kits.Kit("mobutu-voyage", "/kit mobutu-voyage", "Mobutu : voyage", Material.ELYTRA, 0L, Kits.Req.ADMIN, null, true, List.of(), this::voyage));
      this.add(
         new Kits.Kit("mobutu-farces", "/kit mobutu-farces", "Mobutu : farces", Material.SLIME_BALL, 0L, Kits.Req.ADMIN, null, true, List.of(), this::farces)
      );
   }

   private static String gold(String var0) {
      return "<gradient:#FF3B3B:#FFC93C><bold>" + var0 + "</bold></gradient>";
   }

   private List<ItemStack> mobutu() {
      ArrayList<ItemStack> var1 = new ArrayList<>();
      var1.add(
         Util.named(
            Util.unbreakable(
               e(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 5, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1, Enchantment.THORNS, 3)
            ),
            gold("Couronne du Mobutu")
         )
      );
      var1.add(Util.named(Util.unbreakable(e(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 5, Enchantment.THORNS, 3)), gold("Cuirasse du Mobutu")));
      var1.add(
         Util.named(
            Util.unbreakable(e(Material.NETHERITE_LEGGINGS, Enchantment.PROTECTION, 5, Enchantment.SWIFT_SNEAK, 3, Enchantment.THORNS, 3)),
            gold("Jambières du Mobutu")
         )
      );
      var1.add(
         Util.named(
            Util.unbreakable(
               e(
                  Material.NETHERITE_BOOTS,
                  Enchantment.PROTECTION,
                  5,
                  Enchantment.FEATHER_FALLING,
                  10,
                  Enchantment.DEPTH_STRIDER,
                  3,
                  Enchantment.SOUL_SPEED,
                  3,
                  Enchantment.THORNS,
                  3
               )
            ),
            gold("Bottes du Mobutu")
         )
      );
      var1.add(
         Util.named(
            Util.unbreakable(
               e(
                  Material.NETHERITE_SWORD,
                  Enchantment.SHARPNESS,
                  10,
                  Enchantment.FIRE_ASPECT,
                  3,
                  Enchantment.LOOTING,
                  5,
                  Enchantment.SWEEPING_EDGE,
                  5,
                  Enchantment.KNOCKBACK,
                  2
               )
            ),
            gold("Machette du Mobutu")
         )
      );
      var1.add(Util.named(Util.unbreakable(e(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 10, Enchantment.FORTUNE, 5)), gold("Pioche du Mobutu")));
      var1.add(Util.named(Util.unbreakable(e(Material.NETHERITE_AXE, Enchantment.EFFICIENCY, 10, Enchantment.SHARPNESS, 10)), gold("Hache du Mobutu")));
      var1.add(Util.named(Util.unbreakable(e(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 10)), gold("Pelle du Mobutu")));
      var1.add(
         Util.named(
            Util.unbreakable(e(Material.BOW, Enchantment.POWER, 10, Enchantment.PUNCH, 3, Enchantment.FLAME, 1, Enchantment.INFINITY, 1)),
            gold("Arc du Mobutu")
         )
      );
      var1.add(n(1, Material.ARROW));
      var1.add(Util.named(Util.unbreakable(e(Material.MACE, Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 3)), gold("Masse du Mobutu")));
      var1.add(
         Util.named(
            Util.unbreakable(e(Material.TRIDENT, Enchantment.LOYALTY, 3, Enchantment.IMPALING, 5, Enchantment.CHANNELING, 1)), gold("Trident du Mobutu")
         )
      );
      var1.add(Util.unbreakable(n(1, Material.ELYTRA)));
      var1.add(firework(64, 3));
      var1.add(n(16, Material.ENCHANTED_GOLDEN_APPLE));
      var1.add(n(4, Material.TOTEM_OF_UNDYING));
      var1.add(n(64, Material.GOLDEN_CARROT));
      var1.add(n(16, Material.ENDER_PEARL));
      return var1;
   }

   private List<ItemStack> chantier() {
      ArrayList var1 = new ArrayList();

      for (Material var5 : new Material[]{
         Material.STONE_BRICKS,
         Material.OAK_PLANKS,
         Material.SPRUCE_PLANKS,
         Material.GLASS,
         Material.SMOOTH_STONE,
         Material.QUARTZ_BLOCK,
         Material.DEEPSLATE_TILES,
         Material.OAK_LOG,
         Material.WHITE_CONCRETE,
         Material.BRICKS
      }) {
         var1.add(n(64, var5));
      }

      var1.add(n(32, Material.LANTERN));
      var1.add(Util.unbreakable(e(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1)));
      var1.add(Util.unbreakable(e(Material.NETHERITE_AXE, Enchantment.EFFICIENCY, 5)));
      var1.add(Util.unbreakable(e(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 5)));
      var1.add(n(16, Material.SCAFFOLDING));
      return var1;
   }

   private List<ItemStack> fete() {
      ArrayList var1 = new ArrayList();
      var1.add(firework(64, 1));
      var1.add(firework(64, 2));
      var1.add(n(8, Material.CAKE));
      var1.add(n(1, Material.JUKEBOX));
      var1.add(n(1, Material.MUSIC_DISC_PIGSTEP));
      var1.add(n(1, Material.MUSIC_DISC_CAT));
      var1.add(n(1, Material.MUSIC_DISC_OTHERSIDE));
      var1.add(n(32, Material.NOTE_BLOCK));
      var1.add(n(16, Material.PINK_WOOL));
      var1.add(n(16, Material.LIGHT_BLUE_WOOL));
      var1.add(n(16, Material.YELLOW_WOOL));
      var1.add(n(16, Material.SEA_LANTERN));
      return var1;
   }

   private List<ItemStack> potions() {
      ArrayList var1 = new ArrayList();
      PotionType[] var2 = new PotionType[]{
         PotionType.STRONG_STRENGTH,
         PotionType.STRONG_SWIFTNESS,
         PotionType.STRONG_REGENERATION,
         PotionType.LONG_FIRE_RESISTANCE,
         PotionType.LONG_NIGHT_VISION,
         PotionType.LONG_INVISIBILITY,
         PotionType.STRONG_HEALING,
         PotionType.LONG_WATER_BREATHING,
         PotionType.STRONG_TURTLE_MASTER
      };

      for (PotionType var6 : var2) {
         var1.add(Util.potion(Material.SPLASH_POTION, var6, 2));
      }

      var1.add(n(1, Material.BREWING_STAND));
      var1.add(n(16, Material.BLAZE_POWDER));
      var1.add(n(16, Material.NETHER_WART));
      return var1;
   }

   private List<ItemStack> voyage() {
      ArrayList var1 = new ArrayList();
      var1.add(Util.named(Util.unbreakable(n(1, Material.ELYTRA)), gold("Ailes du Mobutu")));
      var1.add(firework(64, 3));
      var1.add(firework(64, 3));
      var1.add(firework(64, 3));
      var1.add(n(16, Material.ENDER_PEARL));
      var1.add(n(1, Material.SADDLE));
      var1.add(n(1, Material.HORSE_SPAWN_EGG));
      var1.add(n(1, Material.COMPASS));
      var1.add(n(1, Material.RECOVERY_COMPASS));
      var1.add(n(64, Material.GOLDEN_CARROT));
      var1.add(n(1, Material.BLACK_SHULKER_BOX));
      return var1;
   }

   private List<ItemStack> farces() {
      ArrayList var1 = new ArrayList();
      var1.add(Util.named(e(Material.STICK, Enchantment.KNOCKBACK, 20), gold("Bâton de l'Expulsion")));
      var1.add(n(16, Material.SNOWBALL));
      var1.add(Util.customPotion(Material.SPLASH_POTION, 8, "<#C77DFF>Potion de lévitation", 13565951, new PotionEffect(PotionEffectType.LEVITATION, 100, 1)));
      var1.add(Util.customPotion(Material.SPLASH_POTION, 8, "<#8B8B8B>Potion d'escargot", 5926017, new PotionEffect(PotionEffectType.SLOWNESS, 200, 3)));
      var1.add(n(16, Material.COBWEB));
      var1.add(n(4, Material.LLAMA_SPAWN_EGG));
      var1.add(n(16, Material.SLIME_BALL));
      var1.add(n(1, Material.GOAT_HORN));
      return var1;
   }

   private static ItemStack firework(int var0, int var1) {
      ItemStack var2 = new ItemStack(Material.FIREWORK_ROCKET, var0);
      if (var2.getItemMeta() instanceof FireworkMeta var3) {
         var3.setPower(var1);
         var3.addEffect(
            FireworkEffect.builder()
               .with(Type.BALL_LARGE)
               .withColor(new Color[]{Color.fromRGB(5227519), Color.fromRGB(16736174), Color.fromRGB(16763196)})
               .withFade(Color.WHITE)
               .trail(true)
               .flicker(true)
               .build()
         );
         var2.setItemMeta(var3);
      }

      return var2;
   }

   public Kits.Kit get(String var1) {
      return this.kits.get(var1);
   }

   public List<Kits.Kit> publicKits() {
      ArrayList var1 = new ArrayList();

      for (Kits.Kit var3 : this.kits.values()) {
         if (!var3.hidden()) {
            var1.add(var3);
         }
      }

      return var1;
   }

   public String denied(Player var1, Kits.Kit var2) {
      int var3 = this.pl.data().rankOf(var1.getUniqueId());

      return switch (var2.req()) {
         case TOUS -> null;
         case TOP10 -> var3 >= 1 && var3 <= 10 ? null : "Réservé au Top 10 des votes" + (var3 > 0 ? " (tu es n°" + var3 + ")" : "");
         case TOP1 -> var3 == 1 ? null : "Réservé au n°1 des votes" + (var3 > 0 ? " (tu es n°" + var3 + ")" : "");
         case ADMIN -> this.pl.ranks().isAdmin(var1) ? null : "Kit inconnu";
      };
   }

   public long remaining(Player var1, Kits.Kit var2) {
      // L'admin peut voter pour une liste autant qu'il veut (pour tester les sons).
      if ("vote".equals(var2.group()) && this.pl.ranks().isAdmin(var1)) {
         return 0L;
      }

      if (var2.cooldown() <= 0L) {
         return 0L;
      } else {
         PlayerData var3 = this.pl.data().get(var1);
         long var4 = System.currentTimeMillis();
         long var6 = 0L;
         Long var8 = var3.kitUses.get(var2.key());
         if (var8 != null) {
            var6 = Math.max(0L, var8 + var2.cooldown() - var4);
         }

         String var9 = var2.id().equals("topvote") ? "top1" : (var2.id().equals("top1") ? "topvote" : null);
         if (var9 != null) {
            Long var10 = var3.kitUses.get(var9);
            if (var10 != null) {
               var6 = Math.max(var6, var10 + 7200000L - var4);
            }
         }

         return var6;
      }
   }

   private boolean blockedByOtherTop(Player var1, Kits.Kit var2) {
      String var3 = var2.id().equals("topvote") ? "top1" : (var2.id().equals("top1") ? "topvote" : null);
      if (var3 == null) {
         return false;
      } else {
         PlayerData var4 = this.pl.data().get(var1);
         Long var5 = var4.kitUses.get(var3);
         Long var6 = var4.kitUses.get(var2.key());
         long var7 = System.currentTimeMillis();
         long var9 = var5 == null ? 0L : var5 + 7200000L - var7;
         long var11 = var6 == null ? 0L : var6 + var2.cooldown() - var7;
         return var9 > 0L && var9 >= var11;
      }
   }

   public void claim(Player var1, Kits.Kit var2) {
      String var3 = this.denied(var1, var2);
      if (var3 != null) {
         Msg.err(var1, "<r>.", Msg.p("r", var3));
      } else {
         long var4 = this.remaining(var1, var2);
         if (var4 > 0L) {
            if (var2.group() != null) {
               Msg.err(var1, "Tu as déjà pris un kit de liste. Le prochain sera dispo dans <white><t></white>.", Msg.p("t", Util.duration(var4)));
            } else if (this.blockedByOtherTop(var1, var2)) {
               Msg.err(var1, "Tu as pris l'autre kit du top il y a moins de 2 h. Dispo dans <white><t></white>.", Msg.p("t", Util.duration(var4)));
            } else {
               Msg.err(var1, "Kit <white><k></white> dispo dans <white><t></white>.", Msg.p("k", var2.name()), Msg.p("t", Util.duration(var4)));
            }

            Util.sound(var1, "entity.villager.no", 0.8F, 1.0F);
         } else {
            PlayerData var6 = this.pl.data().get(var1);
            if (var2.cooldown() > 0L) {
               var6.kitUses.put(var2.key(), System.currentTimeMillis());
               var6.touch();
               this.pl.data().save(var6);
            }

            if ("vote".equals(var2.group())) {
               this.pl.state().listVotes.merge(var2.id(), 1, Integer::sum);
               this.pl.state().save();
               this.pl.votes().updateSidebar();
               this.pl.sounds().playList(var1, var2.id());
            }

            boolean var7 = Util.give(var1, var2.items().get());
            Util.sound(var1, "entity.item.pickup", 1.0F, 0.8F);
            Util.sound(var1, "block.chest.open", 0.6F, 1.2F);
            if (var2.req() == Kits.Req.ADMIN) {
               Msg.ok(var1, "Kit <k> livré, Mobutu.", Msg.c("k", Msg.mm(gold(var2.name()))));
            } else {
               Msg.poulpy(
                  var1,
                  "Kit <white><k></white> récupéré ! Prochain dans <white><t></white>.",
                  Msg.p("k", var2.name()),
                  Msg.p("t", Util.duration(var2.cooldown()))
               );
            }

            if (var7) {
               Msg.info(var1, "Inventaire plein : le reste est tombé à tes pieds.");
            }
         }
      }
   }

   public List<String> voteKitIds() {
      return this.voteKits;
   }

   public List<Entry<String, Integer>> listRanking() {
      ArrayList<Entry<String, Integer>> var1 = new ArrayList<>();

      for (String var3 : this.voteKits) {
         var1.add(Map.entry(var3, this.pl.state().listVotes.getOrDefault(var3, 0)));
      }

      var1.sort((var0, var1x) -> Integer.compare((Integer)var1x.getValue(), (Integer)var0.getValue()));
      return var1;
   }

   public String listName(String var1) {
      Kits.Kit var2 = this.kits.get(var1);
      return var2 == null ? var1 : var2.name();
   }

   private void showLists(Player var1) {
      Msg.raw(var1, " ");
      Msg.raw(var1, "<#FF9AC8><bold>\ud83d\uddf3 Les listes les plus votées</bold></#FF9AC8> <dark_gray>(1 /vote ‹liste› = 1 voix)");
      int var2 = 1;

      for (Entry var4 : this.listRanking()) {
         String var5 = var2 == 1 ? "<#FFD700>" : (var2 == 2 ? "<#C0C0C0>" : (var2 == 3 ? "<#CD7F32>" : "<gray>"));
         Msg.raw(
            var1,
            " "
               + var5
               + var2
               + ". <white>"
               + this.listName((String)var4.getKey())
               + "</white> <dark_gray>— <#4FC3FF>"
               + var4.getValue()
               + " voix</#4FC3FF> <dark_gray>(/vote "
               + (String)var4.getKey()
               + ")"
         );
         var2++;
      }

      Msg.raw(var1, " ");
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         if (var2.getName().equals("kits")) {
            new Kits.KitMenu(var5).open(var5);
            return true;
         } else if (var2.getName().equals("listes")) {
            this.showLists(var5);
            return true;
         } else if (var4.length == 0) {
            this.claim(var5, this.kits.get("depart"));
            return true;
         } else {
            String var6 = var4[0].toLowerCase(Locale.ROOT);
            if (!var6.equals("help") && !var6.equals("aide") && !var6.equals("liste") && !var6.equals("list")) {
               if (var6.equals("vote")) {
                  if (var4.length < 2) {
                     Msg.poulpy(
                        var5,
                        "Kits des listes (un seul toutes les 2 h) : <white>imtmortel</white>, <white>zimtzimt</white>, <white>ascension</white>, <white>bartbart</white>, <white>wizart</white>, <white>passion</white>. Chaque kit pris = 1 voix pour la liste ! Exemple : <white>/vote wizart</white>"
                     );
                     return true;
                  }

                  var6 = var4[1].toLowerCase(Locale.ROOT);
                  if (var6.equals("brest")) {
                     if (this.pl.lobby().isLobby(var5.getWorld())) {
                        Msg.err(var5, "Pas dans le lobby.");
                        return true;
                     }

                     this.pl.fun().brest(var5);
                     return true;
                  }

                  if (!this.voteKits.contains(var6)) {
                     Msg.err(var5, "Kit vote inconnu. Tape <white>/help kit</white>.");
                     return true;
                  }
               }

               if (var6.equals("depart") || var6.equals("départ") || var6.equals("base")) {
                  var6 = "depart";
               }

               if (var6.equals("champion")) {
                  var6 = "top1";
               }

               Kits.Kit var7 = this.kits.get(var6);
               if (var7 != null && (!var7.hidden() || this.pl.ranks().isAdmin(var5))) {
                  this.claim(var5, var7);
                  return true;
               } else {
                  Msg.err(var5, "Kit inconnu. Tape <white>/help kit</white> pour voir les kits.");
                  return true;
               }
            } else {
               new Kits.KitMenu(var5).open(var5);
               return true;
            }
         }
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var1 instanceof Player var6 && !var2.getName().equals("kits")) {
         if (var4.length == 1) {
            var5.add("topvote");
            var5.add("top1");
            var5.add("vote");
            var5.add("help");
            if (this.pl.ranks().isAdmin(var6)) {
               for (Kits.Kit var8 : this.kits.values()) {
                  if (var8.hidden()) {
                     var5.add(var8.id());
                  }
               }
            }
         } else if (var4.length == 2 && var4[0].equalsIgnoreCase("vote")) {
            var5.addAll(this.voteKits);
         }

         String var9 = var4[var4.length - 1].toLowerCase(Locale.ROOT);
         var5.removeIf(var1x -> !var1x.startsWith(var9));
         return var5;
      } else {
         return var5;
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onHelp(PlayerCommandPreprocessEvent var1) {
      String var2 = var1.getMessage().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
      if (var2.equals("/help kit") || var2.equals("/help kits") || var2.equals("/aide kit") || var2.equals("/minecraft:help kit")) {
         var1.setCancelled(true);
         if (this.pl.auth().isLogged(var1.getPlayer())) {
            new Kits.KitMenu(var1.getPlayer()).open(var1.getPlayer());
         }
      }
   }

   public record Kit(
      String id,
      String command,
      String name,
      Material icon,
      long cooldown,
      Kits.Req req,
      String group,
      boolean hidden,
      List<String> contents,
      Supplier<List<ItemStack>> items
   ) {
      String key() {
         return this.group != null ? this.group : this.id;
      }
   }

   private final class KitMenu extends Menu {
      private final Map<Integer, Kits.Kit> slots = new LinkedHashMap<>();

      KitMenu(Player nullx) {
         super(5, Msg.mm("<#2E8BFF><bold>Kits du serveur</bold></#2E8BFF>"));
         int var3 = Kits.this.pl.data().rankOf(nullx.getUniqueId());
         PlayerData var4 = Kits.this.pl.data().get(nullx);
         this.inv
            .setItem(
               4,
               Util.item(
                  Material.BOOK,
                  1,
                  "<#4FC3FF><bold>Comment ça marche ?</bold>",
                  "<gray>Clique sur un kit <green>disponible</green> <gray>pour le prendre.",
                  "<gray>Ou tape la commande indiquée sous chaque kit.",
                  " ",
                  "<gray>Tes votes : <white>" + var4.votes + "</white>" + (var3 > 0 ? " <dark_gray>(n°" + var3 + ")" : ""),
                  "<gray>Top 10 → <white>/kit topvote</white> <gray>· n°1 → <white>/kit top1</white>"
               )
            );
         this.put(11, nullx, Kits.this.kits.get("depart"));
         this.put(13, nullx, Kits.this.kits.get("topvote"));
         this.put(15, nullx, Kits.this.kits.get("top1"));
         this.inv
            .setItem(
               22,
               Util.item(
                  Material.NETHER_STAR,
                  1,
                  "<#FF9AC8><bold>Kits vote</bold>",
                  "<gray>Un seul au choix toutes les <white>2 h</white><gray>.",
                  "<gray>Dès que tu en prends un, les autres",
                  "<gray>attendent aussi 2 h.",
                  " ",
                  "<gray>Chaque kit pris = <white>1 voix</white> <gray>pour sa liste.",
                  "<gray>Classement : <white>/listes</white>"
               )
            );
         int var5 = 29;

         for (String var7 : Kits.this.voteKits) {
            this.put(var5++, nullx, Kits.this.kits.get(var7));
         }

         this.fill();
      }

      private void put(int var1, Player var2, Kits.Kit var3) {
         ItemStack var4 = new ItemStack(var3.icon());
         ArrayList var5 = new ArrayList();

         for (String var7 : var3.contents()) {
            var5.add("<gray>• <white>" + var7);
         }

         var5.add(" ");
         var5.add("<gray>Commande : <#4FC3FF>" + var3.command());
         if ("vote".equals(var3.group())) {
            var5.add("<gray>Voix pour la liste : <#FF9AC8>" + Kits.this.pl.state().listVotes.getOrDefault(var3.id(), 0));
         }

         var5.add("<gray>Recharge : <white>" + Util.duration(var3.cooldown()) + (var3.group() != null ? " <dark_gray>(commune aux kits vote)" : ""));
         if (var3.req() == Kits.Req.TOP10) {
            var5.add("<gray>Condition : <#FFC93C>Top 10 des votes");
         }

         if (var3.req() == Kits.Req.TOP1) {
            var5.add("<gray>Condition : <#FFC93C>n°1 des votes");
         }

         if (var3.req() == Kits.Req.TOP10 || var3.req() == Kits.Req.TOP1) {
            var5.add("<dark_gray>Pas en même temps que l'autre kit du top (2 h d'écart)");
         }

         var5.add(" ");
         String var9 = Kits.this.denied(var2, var3);
         long var10 = Kits.this.remaining(var2, var3);
         if (var9 != null) {
            var5.add("<#FF5555>✖ " + var9);
         } else if (var10 > 0L) {
            var5.add("<#FFB020>⌚ Disponible dans " + Util.duration(var10));
         } else {
            var5.add("<#55FF88>✔ Disponible : clique pour le prendre !");
         }

         Util.named(var4, (var9 == null && var10 == 0L ? "<#55FF88>" : "<#BFBFBF>") + "<bold>" + var3.name() + "</bold>");
         Util.lore(var4, var5);
         this.inv.setItem(var1, var4);
         this.slots.put(var1, var3);
      }

      @Override
      public void click(Player var1, int var2) {
         Kits.Kit var3 = this.slots.get(var2);
         if (var3 != null) {
            var1.closeInventory();
            Kits.this.claim(var1, var3);
         }
      }
   }

   public static enum Req {
      TOUS,
      TOP10,
      TOP1,
      ADMIN;
   }
}
