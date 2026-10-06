package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;

/**
 * Le skyblock : une ile par joueur, toutes dans le meme monde.
 *
 * <p>Un seul monde vide, decoupe en une grille de {@value #SPACING} blocs :
 * c'est de loin le plus economique pour le serveur, bien plus qu'un monde
 * par joueur, qui lui couterait un dossier, une sauvegarde et des chunks
 * charges a chaque fois.
 *
 * <p>Chacun n'a qu'une ile, peut la rendre pour en reprendre une neuve, et
 * reapparait toujours chez lui — y compris apres une chute dans le vide.
 */
public final class Skyblock implements Listener, CommandExecutor, TabCompleter, Worlds.BuildRule {
   /** Distance entre deux iles. */
   public static final int SPACING = 256;
   /** Demi-cote de la zone ou le proprietaire a le droit de construire. */
   public static final int RADIUS = 100;
   /** Hauteur de la plateforme de depart. */
   public static final int ISLAND_Y = 72;
   private static final int MAX_MEMBERS = 5;

   /** Une ile : sa case dans la grille, son proprietaire, ses invites. */
   public static final class Island {
      public final int x;
      public final int z;
      public volatile UUID owner;
      public volatile String ownerName;
      public volatile long created;
      public final Set<UUID> members = new LinkedHashSet<>();

      Island(int x, int z, UUID owner, String ownerName) {
         this.x = x;
         this.z = z;
         this.owner = owner;
         this.ownerName = ownerName;
      }

      public String key() {
         return this.x + "," + this.z;
      }
   }

   private final BDEIMT pl;
   private final File file;
   private final Map<String, Skyblock.Island> islands = new ConcurrentHashMap<>();

   public Skyblock(BDEIMT pl) {
      this.pl = pl;
      this.file = new File(pl.getDataFolder(), "skyblock.yml");
   }

   // ----------------------------------------------------------------- monde

   public void init() {
      if (Bukkit.getWorld(Zone.SKYBLOCK.world) == null) {
         try {
            Bukkit.createWorld(
               new WorldCreator(Zone.SKYBLOCK.world)
                  .environment(Environment.NORMAL)
                  .generator(new Skyblock.VoidGenerator())
                  .generateStructures(false)
            );
         } catch (Throwable t) {
            this.pl.getLogger().warning("Monde du skyblock : " + t.getMessage());
            return;
         }
      }

      this.pl.worlds().tune(Zone.SKYBLOCK);
      this.pl.worlds().registerBuildRule(Zone.SKYBLOCK, this);
      this.pl.worlds().registerSpawn(Zone.SKYBLOCK, this::arrival);
      this.load();
   }

   private Location arrival(Player p) {
      Skyblock.Island mine = this.islandOf(p.getUniqueId());
      return mine == null ? null : this.home(mine);
   }

   public Location home(Skyblock.Island island) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);
      return w == null ? null : new Location(w, island.x * SPACING + 0.5, ISLAND_Y + 1.0, island.z * SPACING + 0.5, 0.0F, 0.0F);
   }

   /** L'ile qui contient ce point, ou null si on est entre deux iles. */
   public Skyblock.Island at(Location l) {
      int gx = Math.floorDiv(l.getBlockX() + SPACING / 2, SPACING);
      int gz = Math.floorDiv(l.getBlockZ() + SPACING / 2, SPACING);
      Skyblock.Island island = this.islands.get(gx + "," + gz);

      if (island == null) {
         return null;
      }

      return Math.abs(l.getBlockX() - gx * SPACING) <= RADIUS && Math.abs(l.getBlockZ() - gz * SPACING) <= RADIUS ? island : null;
   }

   public Skyblock.Island islandOf(UUID uuid) {
      for (Skyblock.Island island : this.islands.values()) {
         if (island.owner.equals(uuid)) {
            return island;
         }
      }

      return null;
   }

   @Override
   public boolean canBuild(Player p, Location at) {
      Skyblock.Island island = this.at(at);
      return island != null && (island.owner.equals(p.getUniqueId()) || island.members.contains(p.getUniqueId()));
   }

   // ------------------------------------------------------------ chargement

   public void load() {
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection root = yml.getConfigurationSection("iles");
      this.islands.clear();

      if (root != null) {
         for (String key : root.getKeys(false)) {
            try {
               String[] parts = key.split(",");
               Skyblock.Island island = new Skyblock.Island(
                  Integer.parseInt(parts[0]),
                  Integer.parseInt(parts[1]),
                  UUID.fromString(root.getString(key + ".proprietaire")),
                  root.getString(key + ".nom", "?")
               );
               island.created = root.getLong(key + ".creee");

               for (String uuid : root.getStringList(key + ".membres")) {
                  island.members.add(UUID.fromString(uuid));
               }

               this.islands.put(island.key(), island);
            } catch (Exception ex) {
               this.pl.getLogger().warning("Ile illisible : " + key);
            }
         }
      }

      this.pl.getLogger().info(this.islands.size() + " ile(s) skyblock chargee(s).");
   }

   public void save() {
      YamlConfiguration yml = new YamlConfiguration();

      for (Skyblock.Island island : this.islands.values()) {
         String path = "iles." + island.key();
         yml.set(path + ".proprietaire", island.owner.toString());
         yml.set(path + ".nom", island.ownerName);
         yml.set(path + ".creee", island.created);
         List<String> members = new ArrayList<>();

         for (UUID uuid : island.members) {
            members.add(uuid.toString());
         }

         yml.set(path + ".membres", members);
      }

      try {
         yml.save(this.file);
      } catch (IOException e) {
         this.pl.getLogger().warning("skyblock.yml : " + e.getMessage());
      }
   }

   // ------------------------------------------------------------ fabrication

   private int[] freeSlot() {
      int x = 0;
      int z = 0;
      int dx = 0;
      int dz = -1;

      for (int i = 0; i < 40000; i++) {
         if (!this.islands.containsKey(x + "," + z)) {
            return new int[]{x, z};
         }

         if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
            int tmp = dx;
            dx = -dz;
            dz = tmp;
         }

         x += dx;
         z += dz;
      }

      return new int[]{0, 0};
   }

   /** Pose l'ile de depart : une plateforme, un arbre et un coffre. */
   private void build(Skyblock.Island island) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);
      if (w == null) {
         return;
      }

      int cx = island.x * SPACING;
      int cz = island.z * SPACING;

      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            if (Math.abs(dx) + Math.abs(dz) > 6) {
               continue;
            }

            w.getBlockAt(cx + dx, ISLAND_Y, cz + dz).setType(Material.GRASS_BLOCK, false);
            w.getBlockAt(cx + dx, ISLAND_Y - 1, cz + dz).setType(Material.DIRT, false);
            w.getBlockAt(cx + dx, ISLAND_Y - 2, cz + dz).setType(Material.DIRT, false);
         }
      }

      w.getBlockAt(cx, ISLAND_Y - 3, cz).setType(Material.BEDROCK, false);

      try {
         w.generateTree(new Location(w, cx + 3, ISLAND_Y + 1, cz + 3), new Random(), TreeType.TREE);
      } catch (Throwable t) {
      }

      Block chest = w.getBlockAt(cx - 2, ISLAND_Y + 1, cz - 2);
      chest.setType(Material.CHEST, false);

      if (chest.getState() instanceof Chest state) {
         state.getBlockInventory()
            .addItem(
               new ItemStack(Material.LAVA_BUCKET),
               new ItemStack(Material.ICE, 2),
               new ItemStack(Material.BONE_MEAL, 8),
               new ItemStack(Material.OAK_SAPLING, 2),
               new ItemStack(Material.WHEAT_SEEDS, 4),
               new ItemStack(Material.PUMPKIN_SEEDS, 2),
               new ItemStack(Material.MELON_SEEDS, 2),
               new ItemStack(Material.SUGAR_CANE, 2),
               new ItemStack(Material.COBBLESTONE, 16),
               new ItemStack(Material.BREAD, 8)
            );
         state.update();
      }
   }

   // ------------------------------------------------------------- evenements

   @EventHandler(priority = EventPriority.HIGH)
   public void onRespawn(PlayerRespawnEvent e) {
      Player p = e.getPlayer();
      if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK && this.pl.worlds().zoneOf(p) != Zone.SKYHUB) {
         return;
      }

      Skyblock.Island mine = this.islandOf(p.getUniqueId());
      Location home = mine == null ? this.pl.worlds().spawnOf(Zone.SKYHUB, p) : this.home(mine);

      if (home != null) {
         e.setRespawnLocation(home);
      }
   }

   /** Tomber dans le vide ramene chez soi plutot que de tuer. */
   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent e) {
      Player p = e.getPlayer();
      if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK || e.getTo().getY() > 0.0) {
         return;
      }

      Skyblock.Island mine = this.islandOf(p.getUniqueId());
      Location home = mine == null ? this.pl.worlds().spawnOf(Zone.SKYHUB, p) : this.home(mine);

      if (home != null) {
         p.setFallDistance(0.0F);
         p.teleport(home);
         Msg.err(p, "Tu es tombé dans le vide. Te revoilà chez toi.");
      }
   }

   // --------------------------------------------------------------- commande

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;
      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      String sub = args.length == 0 ? "tp" : args[0].toLowerCase(Locale.ROOT);

      switch (sub) {
         case "creer", "create", "créer", "nouvelle" -> this.create(p);
         case "tp", "home", "aller" -> this.goHome(p, args);
         case "invite", "inviter" -> this.invite(p, args);
         case "retirer", "kick" -> this.uninvite(p, args);
         case "supprimer", "delete", "reset" -> this.delete(p, args);
         case "hub", "spawn" -> this.pl.worlds().send(p, Zone.SKYHUB);
         case "info" -> this.info(p);
         default -> this.help(p);
      }

      return true;
   }

   private void help(Player p) {
      Msg.raw(p, "<dark_gray>———— <#7FE3FF>Ton île</#7FE3FF> ————</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile creer</#55FF88> <dark_gray>— fabriquer ton île</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile</#55FF88> <dark_gray>— rentrer chez toi</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile tp</#55FF88> <gray>‹joueur›</gray> <dark_gray>— aller chez quelqu'un</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile invite</#55FF88> <gray>‹joueur›</gray> <dark_gray>— l'autoriser a construire</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile hub</#55FF88> <dark_gray>— la place centrale : fermes, mine, marché</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile supprimer confirmer</#55FF88> <dark_gray>— repartir de zéro</dark_gray>");
   }

   private void create(Player p) {
      if (this.islandOf(p.getUniqueId()) != null) {
         Msg.err(p, "Tu as déjà une île. Une seule par personne !");
         Msg.info(p, "Pour repartir de zéro : <white>/ile supprimer confirmer</white>.");
         return;
      }

      if (!this.pl.worlds().available(Zone.SKYBLOCK)) {
         Msg.err(p, "Le skyblock n'est pas encore ouvert.");
         return;
      }

      int[] slot = this.freeSlot();
      Skyblock.Island island = new Skyblock.Island(slot[0], slot[1], p.getUniqueId(), p.getName());
      island.created = System.currentTimeMillis();
      this.islands.put(island.key(), island);
      this.build(island);
      this.save();

      if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK) {
         this.pl.worlds().send(p, Zone.SKYBLOCK);
      } else {
         Location home = this.home(island);

         if (home != null) {
            p.teleport(home);
         }
      }

      Msg.ok(p, "Ton île est prête ! Le coffre contient de quoi démarrer.");
      Msg.info(p, "<gray>Attention au seau de lave : c'est lui qui fait la pierre.</gray>");
      Util.sound(p, "entity.player.levelup", 0.7F, 1.4F);
   }

   private void goHome(Player p, String[] args) {
      Skyblock.Island target;

      if (args.length >= 2) {
         UUID uuid = this.resolve(args[1]);
         target = uuid == null ? null : this.islandOf(uuid);

         if (target == null) {
            Msg.err(p, "Ce joueur n'a pas d'île.");
            return;
         }
      } else {
         target = this.islandOf(p.getUniqueId());

         if (target == null) {
            Msg.err(p, "Tu n'as pas encore d'île. <white>/ile creer</white>");
            return;
         }
      }

      if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK) {
         this.pl.worlds().send(p, Zone.SKYBLOCK);
      }

      Location home = this.home(target);

      if (home != null) {
         p.teleport(home);
         Msg.ok(p, "Bienvenue sur l'île de <white>" + target.ownerName + "</white>.");
      }
   }

   private void invite(Player p, String[] args) {
      Skyblock.Island island = this.islandOf(p.getUniqueId());

      if (island == null) {
         Msg.err(p, "Tu n'as pas d'île.");
         return;
      }

      if (args.length < 2) {
         Msg.err(p, "/ile invite ‹joueur›");
         return;
      }

      Player target = Bukkit.getPlayerExact(args[1]);

      if (target == null || !this.pl.auth().isLogged(target)) {
         Msg.err(p, "Ce joueur n'est pas connecté.");
         return;
      }

      if (island.members.size() + 1 >= MAX_MEMBERS) {
         Msg.err(p, "Ton île est pleine (" + MAX_MEMBERS + " personnes au total).");
         return;
      }

      island.members.add(target.getUniqueId());
      this.save();
      Msg.ok(p, "<white>" + target.getName() + "</white> peut maintenant construire sur ton île.");
      Msg.poulpy(target, "<white>" + p.getName() + "</white> t'ouvre son île. <gray>(/ile tp " + p.getName() + ")</gray>");
   }

   private void uninvite(Player p, String[] args) {
      Skyblock.Island island = this.islandOf(p.getUniqueId());

      if (island == null || args.length < 2) {
         Msg.err(p, "/ile retirer ‹joueur›");
         return;
      }

      UUID uuid = this.resolve(args[1]);

      if (uuid == null || !island.members.remove(uuid)) {
         Msg.err(p, "Ce joueur n'est pas invité sur ton île.");
         return;
      }

      this.save();
      Msg.ok(p, "<white>" + args[1] + "</white> ne peut plus construire chez toi.");
   }

   private void delete(Player p, String[] args) {
      Skyblock.Island island = this.islandOf(p.getUniqueId());

      if (island == null) {
         Msg.err(p, "Tu n'as pas d'île.");
         return;
      }

      if (args.length < 2 || !args[1].equalsIgnoreCase("confirmer")) {
         Msg.err(p, "Cette action efface ton île. Confirme avec <white>/ile supprimer confirmer</white>.");
         return;
      }

      this.wipe(island);
      this.islands.remove(island.key());
      this.save();
      Msg.ok(p, "Île effacée. Tu peux en refaire une avec <white>/ile creer</white>.");

      Location hub = this.pl.worlds().spawnOf(Zone.SKYHUB, p);

      if (hub != null && this.pl.worlds().available(Zone.SKYHUB)) {
         this.pl.worlds().send(p, Zone.SKYHUB);
      }
   }

   /**
    * Vide la zone d'une ile rendue, pour que la place reste utilisable.
    *
    * <p>Le travail est etale sur plusieurs ticks, quelques colonnes a la fois :
    * effacer d'un coup les deux cent mille colonnes d'une ile figerait le
    * serveur plusieurs secondes. Comme le monde est vide, on saute d'emblee
    * toutes les colonnes ou il n'y a rien.
    */
   private void wipe(Skyblock.Island island) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);

      if (w == null) {
         return;
      }

      int cx = island.x * SPACING;
      int cz = island.z * SPACING;
      int bottom = w.getMinHeight();
      int[] cursor = new int[]{-RADIUS};
      Bukkit.getScheduler().runTaskTimer(this.pl, task -> {
         int done = 0;

         while (cursor[0] <= RADIUS && done < 8) {
            int dx = cursor[0]++;
            done++;

            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
               int top = w.getHighestBlockYAt(cx + dx, cz + dz);

               for (int y = top; y >= bottom; y--) {
                  Block block = w.getBlockAt(cx + dx, y, cz + dz);

                  if (!block.getType().isAir()) {
                     block.setType(Material.AIR, false);
                  }
               }
            }
         }

         if (cursor[0] > RADIUS) {
            task.cancel();
            this.pl.getLogger().info("Ile " + island.key() + " effacee.");
         }
      }, 1L, 1L);
   }

   private UUID resolve(String name) {
      Player online = Bukkit.getPlayerExact(name);

      if (online != null) {
         return online.getUniqueId();
      }

      for (Skyblock.Island island : this.islands.values()) {
         if (island.ownerName.equalsIgnoreCase(name)) {
            return island.owner;
         }
      }

      return null;
   }

   private void info(Player p) {
      Skyblock.Island mine = this.islandOf(p.getUniqueId());

      if (mine == null) {
         Msg.info(p, "Tu n'as pas encore d'île : <white>/ile creer</white>.");
         return;
      }

      Msg.raw(p, "<dark_gray>———— <#7FE3FF>Ton île</#7FE3FF> ————</dark_gray>");
      Msg.raw(p, "<gray>Case : <white>" + mine.key() + "</white>   ·   Invités : <white>" + mine.members.size() + "</white> / " + (MAX_MEMBERS - 1) + "</gray>");
      Msg.raw(p, "<gray>Zone de construction : <white>" + RADIUS * 2 + " × " + RADIUS * 2 + "</white> blocs autour du centre.</gray>");
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();

      if (args.length == 1) {
         out.addAll(List.of("creer", "tp", "invite", "retirer", "hub", "info", "supprimer"));
         String start = args[0].toLowerCase(Locale.ROOT);
         out.removeIf(s -> !s.startsWith(start));
      } else if (args.length == 2) {
         for (Player other : Bukkit.getOnlinePlayers()) {
            out.add(other.getName());
         }

         String start = args[1].toLowerCase(Locale.ROOT);
         out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(start));
      }

      return out;
   }

   /** Un monde entierement vide : les iles sont posees par le plugin. */
   public static final class VoidGenerator extends ChunkGenerator {
      @Override
      public boolean shouldGenerateNoise() {
         return false;
      }

      @Override
      public boolean shouldGenerateSurface() {
         return false;
      }

      @Override
      public boolean shouldGenerateCaves() {
         return false;
      }

      @Override
      public boolean shouldGenerateDecorations() {
         return false;
      }

      @Override
      public boolean shouldGenerateMobs() {
         return false;
      }

      @Override
      public boolean shouldGenerateStructures() {
         return false;
      }

      @Override
      public Location getFixedSpawnLocation(World world, Random random) {
         return new Location(world, 0.5, ISLAND_Y + 1.0, 0.5);
      }

      @Override
      public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
         return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
               return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
               return List.of(Biome.PLAINS);
            }
         };
      }
   }
}
