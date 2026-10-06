package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

/**
 * La place centrale du skyblock.
 *
 * <p>Une plateforme assez grande pour une trentaine de personnes, avec de quoi
 * progresser en dehors de son ile :
 *
 * <ul>
 *   <li>un champ de ble qui repousse toutes les 20 minutes ;
 *   <li>un enclos ou des vaches et des moutons reviennent toutes les 30 minutes ;
 *   <li>une salle de mine avec de gros blocs de minerai — communs, moyens et
 *       rares — qui se reforment chaque heure ;
 *   <li>un marche de trois PNJ qui echangent des denrees contre du minerai.
 * </ul>
 *
 * <p>On ne casse rien ici, sauf le ble du champ et le minerai de la mine.
 */
public final class SkyHub implements Listener, Worlds.BuildRule, org.bukkit.command.CommandExecutor {
   private static final int VERSION = 1;
   private static final int FLOOR = 64;
   private static final int HALF = 40;
   /** Le champ de ble. */
   private static final int[] FARM = new int[]{-36, -12, -12, 12};
   /** L'enclos des betes. */
   private static final int[] PASTURE = new int[]{12, 36, -12, 12};
   /** La salle de mine. */
   private static final int[] MINE = new int[]{-12, 12, -36, -12};
   /** Le marche. */
   private static final int[] MARKET = new int[]{-12, 12, 12, 36};
   private static final int ANIMALS = 12;

   /** Un filon : sa matiere et le centre de son cube. */
   private record Vein(Material material, int x, int z, int size) {
   }

   private static final List<SkyHub.Vein> VEINS = List.of(
      new SkyHub.Vein(Material.COAL_ORE, -7, -32, 3),
      new SkyHub.Vein(Material.COPPER_ORE, 0, -32, 3),
      new SkyHub.Vein(Material.IRON_ORE, 7, -32, 3),
      new SkyHub.Vein(Material.REDSTONE_ORE, -7, -24, 2),
      new SkyHub.Vein(Material.LAPIS_ORE, 0, -24, 2),
      new SkyHub.Vein(Material.GOLD_ORE, 7, -24, 2),
      new SkyHub.Vein(Material.DIAMOND_ORE, -4, -17, 1),
      new SkyHub.Vein(Material.EMERALD_ORE, 4, -17, 1)
   );

   private final BDEIMT pl;
   private final NamespacedKey key;

   public SkyHub(BDEIMT pl) {
      this.pl = pl;
      this.key = new NamespacedKey(pl, "skyhub");
   }

   // ----------------------------------------------------------------- monde

   public void init() {
      if (Bukkit.getWorld(Zone.SKYHUB.world) == null) {
         try {
            Bukkit.createWorld(
               new WorldCreator(Zone.SKYHUB.world)
                  .environment(Environment.NORMAL)
                  .generator(new Skyblock.VoidGenerator())
                  .generateStructures(false)
            );
         } catch (Throwable t) {
            this.pl.getLogger().warning("Hub du skyblock : " + t.getMessage());
            return;
         }
      }

      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null) {
         return;
      }

      for (int cx = -3; cx <= 3; cx++) {
         for (int cz = -3; cz <= 3; cz++) {
            w.addPluginChunkTicket(cx, cz, this.pl);
         }
      }

      this.pl.worlds().tune(Zone.SKYHUB);
      this.pl.worlds().registerBuildRule(Zone.SKYHUB, this);

      if (this.pl.getConfig().getInt("mondes.skyhub.version") < VERSION) {
         this.build(w);
         this.pl.getConfig().set("mondes.skyhub.version", VERSION);
         this.pl.getConfig().set("mondes.skyhub.spawn", Util.loc(new Location(w, 0.5, FLOOR + 1.0, 0.5, 0.0F, 0.0F)));
         this.pl.saveConfig();
         this.pl.worlds().tune(Zone.SKYHUB);
      }

      this.regrowFarm();
      this.regrowVeins();
      this.restock();
      this.merchants();
   }

   /** Monte la plateforme, les enclos et les salles. Une seule fois. */
   private void build(World w) {
      this.pl.getLogger().info("Construction du hub skyblock...");

      for (int x = -HALF; x <= HALF; x++) {
         for (int z = -HALF; z <= HALF; z++) {
            boolean path = Math.abs(x) <= 3 || Math.abs(z) <= 3;
            w.getBlockAt(x, FLOOR - 1, z).setType(Material.STONE, false);
            w.getBlockAt(x, FLOOR, z).setType(path ? Material.SMOOTH_STONE : Material.GRASS_BLOCK, false);

            // Un garde-corps tout autour, pour ne pas tomber dans le vide.
            if (Math.abs(x) == HALF || Math.abs(z) == HALF) {
               w.getBlockAt(x, FLOOR + 1, z).setType(Material.OAK_FENCE, false);
               w.getBlockAt(x, FLOOR + 2, z).setType(Material.OAK_FENCE, false);
            }
         }
      }

      this.buildFarm(w);
      this.buildPasture(w);
      this.buildMine(w);
      this.buildMarket(w);
      this.pl.getLogger().info("Hub skyblock construit.");
   }

   private void buildFarm(World w) {
      for (int x = FARM[0]; x <= FARM[1]; x++) {
         for (int z = FARM[2]; z <= FARM[3]; z++) {
            // Une rigole d'eau toutes les cinq rangees : tout reste irrigue.
            if (Math.floorMod(z - FARM[2], 5) == 2) {
               w.getBlockAt(x, FLOOR, z).setType(Material.WATER, false);
            } else {
               w.getBlockAt(x, FLOOR, z).setType(Material.FARMLAND, false);
               w.getBlockAt(x, FLOOR + 1, z).setType(Material.WHEAT, false);
            }
         }
      }
   }

   private void buildPasture(World w) {
      for (int x = PASTURE[0] - 1; x <= PASTURE[1] + 1; x++) {
         for (int z = PASTURE[2] - 1; z <= PASTURE[3] + 1; z++) {
            boolean border = x == PASTURE[0] - 1 || x == PASTURE[1] + 1 || z == PASTURE[2] - 1 || z == PASTURE[3] + 1;

            if (border) {
               w.getBlockAt(x, FLOOR + 1, z).setType(Material.OAK_FENCE, false);
            } else {
               w.getBlockAt(x, FLOOR, z).setType(Material.GRASS_BLOCK, false);
            }
         }
      }

      // Une porte pour entrer dans l'enclos.
      w.getBlockAt(PASTURE[0] - 1, FLOOR + 1, 0).setType(Material.AIR, false);
   }

   private void buildMine(World w) {
      for (int x = MINE[0] - 1; x <= MINE[1] + 1; x++) {
         for (int z = MINE[2] - 1; z <= MINE[3] + 1; z++) {
            boolean wall = x == MINE[0] - 1 || x == MINE[1] + 1 || z == MINE[2] - 1 || z == MINE[3] + 1;

            for (int y = FLOOR + 1; y <= FLOOR + 8; y++) {
               w.getBlockAt(x, y, z).setType(wall && !(z == MINE[3] + 1 && Math.abs(x) <= 1 && y <= FLOOR + 2) ? Material.DEEPSLATE_BRICKS : Material.AIR, false);
            }

            if (!wall) {
               w.getBlockAt(x, FLOOR, z).setType(Material.DEEPSLATE, false);
            }

            w.getBlockAt(x, FLOOR + 9, z).setType(Material.DEEPSLATE_BRICKS, false);
         }
      }

      for (int x = MINE[0]; x <= MINE[1]; x += 6) {
         for (int z = MINE[2]; z <= MINE[3]; z += 6) {
            w.getBlockAt(x, FLOOR + 8, z).setType(Material.LANTERN, false);
         }
      }
   }

   private void buildMarket(World w) {
      for (int x = MARKET[0]; x <= MARKET[1]; x++) {
         for (int z = MARKET[2]; z <= MARKET[3]; z++) {
            w.getBlockAt(x, FLOOR, z).setType(Material.POLISHED_ANDESITE, false);
         }
      }

      int[][] stalls = new int[][]{{-7, 20}, {0, 20}, {7, 20}};

      for (int[] stall : stalls) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;

               if (corner) {
                  w.getBlockAt(stall[0] + dx, FLOOR + 1, stall[1] + dz).setType(Material.OAK_FENCE, false);
                  w.getBlockAt(stall[0] + dx, FLOOR + 2, stall[1] + dz).setType(Material.OAK_FENCE, false);
                  w.getBlockAt(stall[0] + dx, FLOOR + 3, stall[1] + dz).setType(Material.RED_WOOL, false);
               } else if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                  w.getBlockAt(stall[0] + dx, FLOOR + 3, stall[1] + dz).setType(Material.WHITE_WOOL, false);
               } else {
                  w.getBlockAt(stall[0] + dx, FLOOR + 3, stall[1] + dz).setType(Material.RED_WOOL, false);
               }
            }
         }
      }
   }

   // ------------------------------------------------------------- repousses

   private static boolean inside(int[] zone, int x, int z) {
      return x >= zone[0] && x <= zone[1] && z >= zone[2] && z <= zone[3];
   }

   /** Le ble murit d'un coup : toutes les 20 minutes. */
   public void regrowFarm() {
      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null) {
         return;
      }

      for (int x = FARM[0]; x <= FARM[1]; x++) {
         for (int z = FARM[2]; z <= FARM[3]; z++) {
            if (Math.floorMod(z - FARM[2], 5) == 2) {
               continue;
            }

            Block ground = w.getBlockAt(x, FLOOR, z);

            if (ground.getType() != Material.FARMLAND) {
               ground.setType(Material.FARMLAND, false);
            }

            Block crop = w.getBlockAt(x, FLOOR + 1, z);

            if (crop.getType() != Material.WHEAT) {
               crop.setType(Material.WHEAT, false);
            }

            if (crop.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
               age.setAge(age.getMaximumAge());
               crop.setBlockData(age, false);
            }
         }
      }
   }

   /** Les filons se reforment chaque heure. */
   public void regrowVeins() {
      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null) {
         return;
      }

      for (SkyHub.Vein vein : VEINS) {
         for (int dx = -vein.size(); dx <= vein.size(); dx++) {
            for (int dz = -vein.size(); dz <= vein.size(); dz++) {
               for (int dy = 1; dy <= vein.size() * 2 + 1; dy++) {
                  Block block = w.getBlockAt(vein.x() + dx, FLOOR + dy, vein.z() + dz);

                  if (block.getType().isAir() || block.getType() == vein.material()) {
                     block.setType(vein.material(), false);
                  }
               }
            }
         }
      }
   }

   /** Vaches et moutons reviennent toutes les 30 minutes, sans jamais s'entasser. */
   public void restock() {
      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null) {
         return;
      }

      int cows = 0;
      int sheep = 0;

      for (Entity entity : w.getEntities()) {
         if (entity instanceof Cow) {
            cows++;
         } else if (entity instanceof Sheep) {
            sheep++;
         }
      }

      this.spawnAnimals(w, EntityType.COW, ANIMALS - cows);
      this.spawnAnimals(w, EntityType.SHEEP, ANIMALS - sheep);
   }

   private void spawnAnimals(World w, EntityType type, int howMany) {
      java.util.Random random = new java.util.Random();

      for (int i = 0; i < howMany; i++) {
         int x = PASTURE[0] + random.nextInt(PASTURE[1] - PASTURE[0] + 1);
         int z = PASTURE[2] + random.nextInt(PASTURE[3] - PASTURE[2] + 1);

         try {
            Entity entity = w.spawnEntity(new Location(w, x + 0.5, FLOOR + 1.0, z + 0.5), type);

            if (entity instanceof Animals animal) {
               animal.setRemoveWhenFarAway(false);
            }
         } catch (Throwable t) {
            return;
         }
      }
   }

   // -------------------------------------------------------------- marchands

   /** Les trois marchands : le fermier, l'eleveur et le mineur. */
   public void merchants() {
      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null) {
         return;
      }

      for (Entity entity : w.getEntities()) {
         if (entity.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
            entity.remove();
         }
      }

      this.merchant(
         w,
         -7,
         20,
         "fermier",
         "<#A3E635>Le Fermier</#A3E635>",
         Profession.FARMER,
         List.of(
            trade(new ItemStack(Material.IRON_INGOT), new ItemStack(Material.WHEAT, 32)),
            trade(new ItemStack(Material.IRON_INGOT), new ItemStack(Material.BREAD, 16)),
            trade(new ItemStack(Material.COAL, 2), new ItemStack(Material.WHEAT_SEEDS, 32)),
            trade(new ItemStack(Material.COPPER_INGOT, 2), new ItemStack(Material.PUMPKIN, 8))
         )
      );
      this.merchant(
         w,
         0,
         20,
         "eleveur",
         "<#FFD25E>L'Éleveur</#FFD25E>",
         Profession.SHEPHERD,
         List.of(
            trade(new ItemStack(Material.IRON_INGOT), new ItemStack(Material.LEATHER, 12)),
            trade(new ItemStack(Material.IRON_INGOT), new ItemStack(Material.WHITE_WOOL, 16)),
            trade(new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.COOKED_BEEF, 24)),
            trade(new ItemStack(Material.EMERALD), new ItemStack(Material.IRON_INGOT, 16))
         )
      );
      this.merchant(
         w,
         7,
         20,
         "mineur",
         "<#7FA6FF>Le Mineur</#7FA6FF>",
         Profession.TOOLSMITH,
         List.of(
            trade(new ItemStack(Material.COAL), new ItemStack(Material.COBBLESTONE, 32)),
            trade(new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COAL, 16)),
            trade(new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.IRON_INGOT, 12)),
            trade(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT, 24))
         )
      );
   }

   private static MerchantRecipe trade(ItemStack result, ItemStack cost) {
      MerchantRecipe recipe = new MerchantRecipe(result, 999999);
      recipe.addIngredient(cost);
      recipe.setExperienceReward(false);
      return recipe;
   }

   private void merchant(World w, int x, int z, String id, String name, Profession profession, List<MerchantRecipe> trades) {
      Location at = new Location(w, x + 0.5, FLOOR + 1.0, z + 0.5, 180.0F, 0.0F);

      try {
         w.spawn(at, Villager.class, villager -> {
            villager.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
            villager.customName(Msg.mm(name));
            villager.setCustomNameVisible(true);
            villager.setProfession(profession);
            villager.setVillagerLevel(5);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(false);
            villager.setRemoveWhenFarAway(false);
            villager.setRecipes(new ArrayList<>(trades));
         });
      } catch (Throwable t) {
         this.pl.getLogger().warning("Marchand " + id + " : " + t.getMessage());
      }
   }

   /** {@code /marche} : rejoindre les etals du hub skyblock. */
   @Override
   public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;

      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      if (!this.pl.worlds().available(Zone.SKYHUB)) {
         Msg.err(p, "Le skyblock n'est pas encore ouvert.");
         return true;
      }

      if (this.pl.worlds().zoneOf(p).group.equals(Zone.SKYHUB.group)) {
         World w = this.pl.worlds().world(Zone.SKYHUB);

         if (w != null) {
            p.teleport(new Location(w, 0.5, FLOOR + 1.0, 14.5, 180.0F, 0.0F));
         }
      } else {
         this.pl.worlds().send(p, Zone.SKYHUB);
      }

      Msg.poulpy(p, "Le Fermier, l'Éleveur et le Mineur achètent tes denrées contre du minerai. <gray>Clic droit pour voir leurs offres.</gray>");
      return true;
   }

   @EventHandler(ignoreCancelled = true)
   public void onDamage(EntityDamageEvent e) {
      if (e.getEntity().getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
         e.setCancelled(true);
      }
   }

   // ------------------------------------------------------------- les regles

   /** On ne casse que le ble du champ et le minerai de la mine. */
   @Override
   public boolean canBuild(Player p, Location at) {
      int x = at.getBlockX();
      int y = at.getBlockY();
      int z = at.getBlockZ();
      Material material = at.getBlock().getType();

      if (inside(FARM, x, z) && y == FLOOR + 1 && material == Material.WHEAT) {
         return true;
      }

      if (inside(MINE, x, z) && y > FLOOR && y < FLOOR + 9 && material.name().endsWith("_ORE")) {
         return true;
      }

      return false;
   }

   /** Retire marchands et betes au /stop. */
   public void clearAll() {
      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null) {
         return;
      }

      for (Entity entity : w.getEntities()) {
         if (entity.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
            entity.remove();
         }
      }
   }
}
