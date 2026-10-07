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
      /** Les voix recues par /vote ‹pseudo›, une par joueur et par jour. */
      public volatile int votes;
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
   private final Map<UUID, Long> lastVote = new ConcurrentHashMap<>();
   /** Ceux qui ont deja recu leurs pieces de depart : une seule fois par joueur. */
   private final Set<UUID> startingGiven = ConcurrentHashMap.newKeySet();
   /** Invitations en attente : invite -> « case de l'ile|moment ». */
   private final Map<UUID, String> invites = new ConcurrentHashMap<>();

   public Skyblock(BDEIMT pl) {
      this.pl = pl;
      this.file = new File(pl.getDataFolder(), "skyblock.yml");
   }

   // ----------------------------------------------------------------- monde

   public void init() {
      if (!this.pl.worlds().enabled(Zone.SKYBLOCK)) {
         this.pl.getLogger().info("Skyblock ferme (modes.skyblock dans config.yml) : son monde n'est pas charge.");
         return;
      }

      World already = Bukkit.getWorld(Zone.SKYBLOCK.world);

      // Charge par quelqu'un d'autre (Multiverse) sans notre generateur : les
      // chunks neufs se rempliraient de terrain et de mer autour des iles. On
      // le decharge et on le recharge avec le bon, avant l'arrivee des joueurs.
      if (already != null && !(already.getGenerator() instanceof Skyblock.VoidGenerator)) {
         this.pl.getLogger().warning("Le monde du skyblock etait charge sans le generateur vide (par Multiverse ?) : rechargement.");

         if (already.getPlayers().isEmpty() && Bukkit.unloadWorld(already, true)) {
            already = null;
         } else {
            this.pl.getLogger().severe("Impossible de recharger le skyblock avec le generateur vide : du terrain pourrait apparaitre autour des iles.");
         }
      }

      if (already == null) {
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
      Skyblock.Island mine = this.homeIslandOf(p.getUniqueId());
      return mine == null ? null : this.home(mine);
   }

   /** L'ile ou l'on habite : la sienne, ou celle de l'ami qui nous heberge. */
   public Skyblock.Island homeIslandOf(UUID uuid) {
      Skyblock.Island mine = this.islandOf(uuid);
      return mine != null ? mine : this.guestOf(uuid);
   }

   public Location home(Skyblock.Island island) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);

      if (w == null) {
         return null;
      }

      this.model();
      Location wanted = new Location(
         w,
         island.x * SPACING + this.homeOffset[0] + 0.5,
         bedrockY() + this.homeOffset[1],
         island.z * SPACING + this.homeOffset[2] + 0.5,
         this.homeYaw,
         0.0F
      );
      return this.safe(wanted, island);
   }

   /**
    * Un point d'arrivee ou l'on tient debout.
    *
    * <p>Le joueur a pu creuser sous son point d'arrivee, ou construire
    * dessus ; et les iles faites avant la nouvelle ile n'ont pas la meme
    * forme. On cherche donc, autour du point prevu, la premiere colonne avec
    * un sol sous les pieds et de l'air pour la tete. Si l'ile a entierement
    * disparu, on repose un bloc de terre pour ne pas tomber dans le vide.
    */
   private Location safe(Location wanted, Skyblock.Island island) {
      if (standable(wanted)) {
         return wanted;
      }

      World w = wanted.getWorld();
      int cx = wanted.getBlockX();
      int cz = wanted.getBlockZ();

      for (int r = 0; r <= 8; r++) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                  continue;
               }

               Block top = w.getHighestBlockAt(cx + dx, cz + dz);

               if (top.getType().isSolid() && top.getY() > w.getMinHeight()) {
                  Location at = top.getLocation().add(0.5, 1.0, 0.5);
                  at.setYaw(wanted.getYaw());

                  if (standable(at)) {
                     return at;
                  }
               }
            }
         }
      }

      Block floor = wanted.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
      floor.setType(Material.DIRT, false);
      return wanted;
   }

   private static boolean standable(Location at) {
      Block feet = at.getBlock();
      Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
      Block below = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
      return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid() && below.getType().isSolid();
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

   /** L'ile dont on est proprietaire (la plus recente si skyblock.yml en compte deux). */
   public Skyblock.Island islandOf(UUID uuid) {
      Skyblock.Island found = null;

      for (Skyblock.Island island : this.islands.values()) {
         if (island.owner.equals(uuid) && (found == null || island.created > found.created)) {
            found = island;
         }
      }

      return found;
   }

   /** L'ile d'un ami sur laquelle on a ete invite, ou null. */
   public Skyblock.Island guestOf(UUID uuid) {
      for (Skyblock.Island island : this.islands.values()) {
         if (island.members.contains(uuid)) {
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

   /** Vrai une fois skyblock.yml lu : avant, on ne doit surtout pas l'ecraser. */
   private volatile boolean loaded;

   public boolean isLoaded() {
      return this.loaded;
   }

   public java.util.Collection<Skyblock.Island> all() {
      return java.util.Collections.unmodifiableCollection(this.islands.values());
   }

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
               island.votes = root.getInt(key + ".voix");

               for (String uuid : root.getStringList(key + ".membres")) {
                  island.members.add(UUID.fromString(uuid));
               }

               this.islands.put(island.key(), island);
            } catch (Exception ex) {
               this.pl.getLogger().warning("Ile illisible : " + key);
            }
         }
      }

      ConfigurationSection votes = yml.getConfigurationSection("derniers-votes");

      if (votes != null) {
         for (String key : votes.getKeys(false)) {
            try {
               this.lastVote.put(UUID.fromString(key), votes.getLong(key));
            } catch (IllegalArgumentException ex) {
            }
         }
      }

      for (String uuid : yml.getStringList("pieces-de-depart")) {
         try {
            this.startingGiven.add(UUID.fromString(uuid));
         } catch (IllegalArgumentException ex) {
         }
      }

      this.checkIntegrity();
      this.loaded = true;
      this.pl.getLogger().info(this.islands.size() + " ile(s) skyblock chargee(s).");
   }

   /**
    * Remet de l'ordre si skyblock.yml s'est contredit : quelqu'un qui possede
    * deux iles garde la plus recente (l'autre reste sur la carte, sans
    * proprietaire actif, et n'est jamais effacee) ; quelqu'un qui a sa propre
    * ile n'est plus compte comme invite chez un autre.
    */
   private void checkIntegrity() {
      Map<UUID, Skyblock.Island> newest = new java.util.HashMap<>();

      for (Skyblock.Island island : this.islands.values()) {
         Skyblock.Island other = newest.get(island.owner);

         if (other == null || island.created > other.created) {
            newest.put(island.owner, island);
         }
      }

      for (Skyblock.Island island : this.islands.values()) {
         if (newest.get(island.owner) != island) {
            this.pl.getLogger().warning("Ile " + island.key() + " : " + island.ownerName + " en a deja une autre (" + newest.get(island.owner).key() + "). Elle est gardee telle quelle, voir /imt skyblock diag.");
         }

         island.members.removeIf(member -> {
            boolean ownsOne = newest.containsKey(member);

            if (ownsOne) {
               this.pl.getLogger().warning("Ile " + island.key() + " : un invite possede deja sa propre ile, il est retire des invites.");
            }

            return ownsOne;
         });
      }
   }

   public void save() {
      // Skyblock ferme au demarrage : rien n'a ete lu. Sauvegarder maintenant
      // remplacerait skyblock.yml par un fichier vide, et toutes les iles
      // seraient oubliees (puis reconstruites les unes sur les autres).
      if (!this.loaded) {
         return;
      }

      YamlConfiguration yml = new YamlConfiguration();

      for (Skyblock.Island island : this.islands.values()) {
         String path = "iles." + island.key();
         yml.set(path + ".proprietaire", island.owner.toString());
         yml.set(path + ".nom", island.ownerName);
         yml.set(path + ".creee", island.created);
         yml.set(path + ".voix", island.votes);
         List<String> members = new ArrayList<>();

         for (UUID uuid : island.members) {
            members.add(uuid.toString());
         }

         yml.set(path + ".membres", members);
      }

      for (Map.Entry<UUID, Long> entry : this.lastVote.entrySet()) {
         yml.set("derniers-votes." + entry.getKey(), entry.getValue());
      }

      List<String> given = new ArrayList<>();

      for (UUID uuid : this.startingGiven) {
         given.add(uuid.toString());
      }

      yml.set("pieces-de-depart", given);

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
         if (!this.islands.containsKey(x + "," + z) && !this.occupied(x, z)) {
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

   /**
    * Y a-t-il deja quelque chose au centre de cette case ? Une ile peut exister
    * sur la carte sans etre dans skyblock.yml (fichier perdu, vieille version) :
    * on ne construit jamais par-dessus.
    */
   private boolean occupied(int gx, int gz) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);

      if (w == null) {
         return false;
      }

      int cx = gx * SPACING;
      int cz = gz * SPACING;

      if (!w.isChunkGenerated(cx >> 4, cz >> 4)) {
         return false;
      }

      for (int dx = -6; dx <= 6; dx += 2) {
         for (int dz = -6; dz <= 6; dz += 2) {
            if (w.getHighestBlockYAt(cx + dx, cz + dz) > w.getMinHeight()
               && !w.getHighestBlockAt(cx + dx, cz + dz).getType().isAir()) {
               this.pl.getLogger().info("Case " + gx + "," + gz + " deja occupee sur la carte : on en prend une autre.");
               return true;
            }
         }
      }

      return false;
   }

   /** Le modele de l'ile, lu une fois dans ile-classique.txt. */
   private List<String> model;
   /** Ou l'on arrive sur l'ile, par rapport a son bloc de bedrock. */
   private int[] homeOffset = new int[]{0, 3, 0};
   private float homeYaw = 0.0F;

   private List<String> model() {
      if (this.model != null) {
         return this.model;
      }

      List<String> lines = new ArrayList<>();

      try (java.io.InputStream in = this.pl.getResource("ile-classique.txt")) {
         if (in != null) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
               line = line.trim();

               if (!line.isEmpty() && !line.startsWith("#")) {
                  lines.add(line);

                  if (line.startsWith("maison ")) {
                     String[] p = line.split(" ");
                     this.homeOffset = new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
                     this.homeYaw = p.length > 4 ? Float.parseFloat(p[4]) : 0.0F;
                  }
               }
            }
         }
      } catch (Exception ex) {
         this.pl.getLogger().warning("Modele de l'ile illisible : " + ex.getMessage());
      }

      this.model = lines;
      return lines;
   }

   /** La hauteur du bloc de bedrock : l'herbe de l'ile arrive ainsi a ISLAND_Y. */
   private static int bedrockY() {
      return ISLAND_Y - 2;
   }

   /**
    * Pose l'ile de depart : le grand L de terre et d'herbe du Skyblock
    * originel, son chene, son coffre, le bloc de bedrock et une vache.
    */
   private void build(Skyblock.Island island) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);
      if (w == null) {
         return;
      }

      int cx = island.x * SPACING;
      int cy = bedrockY();
      int cz = island.z * SPACING;
      Block chest = null;
      List<String[]> chestItems = new ArrayList<>();
      List<String[]> entities = new ArrayList<>();

      for (String line : this.model()) {
         String[] p = line.split(" ", 5);

         try {
            switch (p[0]) {
               case "bloc" -> {
                  Block block = w.getBlockAt(cx + Integer.parseInt(p[1]), cy + Integer.parseInt(p[2]), cz + Integer.parseInt(p[3]));
                  block.setBlockData(Bukkit.createBlockData(p[4]), false);

                  if (p[4].startsWith("minecraft:chest")) {
                     chest = block;
                  }
               }
               case "coffre" -> chestItems.add(p);
               case "entite" -> entities.add(p);
               default -> {
               }
            }
         } catch (Exception ex) {
            this.pl.getLogger().warning("Ligne du modele ignoree : " + line + " (" + ex.getMessage() + ")");
         }
      }

      if (chest != null && chest.getState() instanceof Chest state) {
         org.bukkit.inventory.Inventory inv = state.getBlockInventory();

         for (String[] p : chestItems) {
            try {
               int slot = Integer.parseInt(p[1]);
               Material material = Material.valueOf(p[2]);
               int amount = Integer.parseInt(p[3].split(" ")[0]);

               if (slot >= 0 && slot < inv.getSize()) {
                  inv.setItem(slot, new ItemStack(material, amount));
               }
            } catch (Exception ex) {
               this.pl.getLogger().warning("Objet du coffre ignore : " + String.join(" ", p));
            }
         }
      }

      for (String[] p : entities) {
         try {
            String[] rest = p[4].split(" ", 2);
            Location at = new Location(w, cx + Integer.parseInt(p[1]) + 0.5, cy + Integer.parseInt(p[2]), cz + Integer.parseInt(p[3]) + 0.5);
            org.bukkit.entity.Entity entity = w.spawnEntity(at, org.bukkit.entity.EntityType.valueOf(rest[0]));

            if (rest.length > 1 && !rest[1].isBlank()) {
               entity.customName(Msg.mm("<white>" + rest[1].trim() + "</white>"));
               entity.setCustomNameVisible(true);
            }

            entity.setPersistent(true);
         } catch (Exception ex) {
            this.pl.getLogger().warning("Entite du modele ignoree : " + String.join(" ", p));
         }
      }
   }

   // ------------------------------------------------------------- evenements

   /** Ceux qui viennent de tomber dans le vide, pour le leur dire au retour. */
   private final Set<UUID> fellInVoid = ConcurrentHashMap.newKeySet();

   /**
    * Mort sur une ile : on reapparait sur son ile (ou celle de l'ami qui nous
    * heberge), jamais au hub ni au lobby du skyblock. Un lit pose sur son ile
    * reste respecte, comme dans le jeu normal.
    */
   @EventHandler(priority = EventPriority.HIGH)
   public void onRespawn(PlayerRespawnEvent e) {
      Player p = e.getPlayer();
      Zone zone = this.pl.worlds().zoneOf(p);
      if (zone != Zone.SKYBLOCK && zone != Zone.SKYHUB) {
         return;
      }

      boolean bed = (e.isBedSpawn() || e.isAnchorSpawn())
         && e.getRespawnLocation().getWorld() != null
         && this.pl.worlds().zoneOf(e.getRespawnLocation().getWorld()) == Zone.SKYBLOCK;

      if (!bed) {
         Skyblock.Island mine = this.homeIslandOf(p.getUniqueId());
         Location home = mine == null ? this.pl.worlds().spawnOf(Zone.SKYHUB, p) : this.home(mine);

         if (home != null) {
            e.setRespawnLocation(home);
         }
      }

      if (this.fellInVoid.remove(p.getUniqueId())) {
         Bukkit.getScheduler().runTaskLater(this.pl, () -> {
            if (p.isOnline()) {
               Msg.big(p, "<#FF5555><bold>Tombé dans le vide</bold></#FF5555>", "<gray>ton stuff est perdu — te revoilà sur ton île</gray>", 3500L);
               Msg.info(p, "<gray>Astuce : pose des blocs autour du bord de ton île pour ne pas tomber.</gray>");
            }
         }, 5L);
      }
   }

   /**
    * Tomber de son ile tue, comme dans le skyblock classique : le stuff tombe
    * avec soi dans le vide et il est perdu. Pas de tombe ici. On n'attend pas
    * que le jeu le fasse (bien plus bas, apres de longues secondes de chute).
    */
   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent e) {
      Player p = e.getPlayer();
      if (e.getTo().getY() > ISLAND_Y - 40 || this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK || p.isDead()) {
         return;
      }

      if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
         return;
      }

      // Quarante blocs sous l'ile, et en pleine chute : quelqu'un qui construit
      // vers le bas, les pieds sur ses blocs, n'est pas « tombe ».
      if (p.getFallDistance() < 6.0F) {
         return;
      }

      this.fellInVoid.add(p.getUniqueId());

      try {
         p.damage(
            10000.0,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.OUT_OF_WORLD).build()
         );
      } catch (Throwable t) {
         p.setHealth(0.0);
      }

      if (!p.isDead()) {
         // Un totem d'immortalite l'a sauve... mais pas du vide.
         p.setHealth(0.0);
      }
   }

   // --------------------------------------------------------------- bordure

   /** La case de grille dont chaque joueur voit la bordure. */
   private final Map<UUID, String> borderCell = new ConcurrentHashMap<>();

   /**
    * Chacun ne voit que la bordure de l'ile ou il se trouve : un mur de
    * {@value #RADIUS} blocs autour du centre, infranchissable. Personne ne
    * peut donc partir a pied (ou en pont) jusque chez les autres ; pour
    * visiter, il faut {@code /ile tp ‹pseudo›} ou {@code /tpa}.
    */
   public void updateBorder(Player p) {
      if (!p.isOnline()) {
         return;
      }

      if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK) {
         if (this.borderCell.remove(p.getUniqueId()) != null) {
            try {
               p.setWorldBorder(null);
            } catch (Throwable t) {
            }
         }

         return;
      }

      Location l = p.getLocation();
      int gx = Math.floorDiv(l.getBlockX() + SPACING / 2, SPACING);
      int gz = Math.floorDiv(l.getBlockZ() + SPACING / 2, SPACING);
      String cell = gx + "," + gz;

      if (cell.equals(this.borderCell.get(p.getUniqueId()))) {
         return;
      }

      try {
         org.bukkit.WorldBorder border = Bukkit.createWorldBorder();
         border.setCenter(gx * SPACING + 0.5, gz * SPACING + 0.5);
         border.setSize(RADIUS * 2.0 + 1.0);
         border.setWarningDistance(3);
         border.setDamageAmount(0.0);
         p.setWorldBorder(border);
         this.borderCell.put(p.getUniqueId(), cell);
      } catch (Throwable t) {
      }
   }

   /** Chaque seconde : la bordure suit le joueur d'une ile a l'autre. */
   public void tick() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         this.updateBorder(p);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
      Player p = e.getPlayer();
      Bukkit.getScheduler().runTask(this.pl, () -> this.updateBorder(p));
   }

   @EventHandler
   public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
      this.borderCell.remove(e.getPlayer().getUniqueId());
      this.fellInVoid.remove(e.getPlayer().getUniqueId());
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
         case "accept", "accepter", "oui" -> this.accept(p, args);
         case "refuse", "refuser", "non" -> this.refuse(p);
         case "quitter", "partir", "leave" -> this.leave(p);
         case "retirer", "kick" -> this.uninvite(p, args);
         case "supprimer", "delete", "reset" -> this.delete(p, args);
         case "hub", "spawn" -> this.pl.worlds().send(p, Zone.SKYHUB);
         case "info" -> this.info(p);
         case "aide", "help", "?" -> this.help(p);
         default -> this.help(p);
      }

      return true;
   }

   private void help(Player p) {
      Msg.raw(p, "<dark_gray>———— <#7FE3FF>Ton île</#7FE3FF> ————</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile creer</#55FF88> <dark_gray>— fabriquer ton île</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile</#55FF88> <dark_gray>— rentrer chez toi</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile tp</#55FF88> <gray>‹joueur›</gray> <dark_gray>— aller chez quelqu'un</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile invite</#55FF88> <gray>‹joueur›</gray> <dark_gray>— l'inviter a te rejoindre</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile accept</#55FF88> <dark_gray>— accepter une invitation</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile quitter</#55FF88> <dark_gray>— repartir de l'île d'un ami</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile hub</#55FF88> <dark_gray>— le lobby du skyblock : marchands, marché</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile retirer</#55FF88> <gray>‹joueur›</gray> <dark_gray>— retirer quelqu'un de ton île</dark_gray>");
      Msg.raw(p, " <#55FF88>/ile info</#55FF88> <dark_gray>— ton île, tes invités</dark_gray>");
      Msg.raw(p, "<#FF5555> ⚠ Rejoindre l'île d'un ami efface la tienne, pour toujours.</#FF5555>");
      Msg.raw(p, "<#FF5555> ⚠ Tomber dans le vide = mort : ton stuff est perdu.</#FF5555>");
      Msg.raw(p, " <#55FF88>/ile supprimer confirmer</#55FF88> <dark_gray>— repartir de zéro</dark_gray>");
   }

   private void create(Player p) {
      if (this.islandOf(p.getUniqueId()) != null) {
         Msg.err(p, "Tu as déjà une île. Une seule par personne !");
         Msg.info(p, "Pour repartir de zéro : <white>/ile supprimer confirmer</white>.");
         return;
      }

      Skyblock.Island host = this.guestOf(p.getUniqueId());

      if (host != null) {
         Msg.err(p, "Tu habites sur l'île de <white>" + host.ownerName + "</white>.");
         Msg.info(p, "Quitte-la d'abord avec <white>/ile quitter</white>, puis refais <white>/ile creer</white>.");
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

      if (this.startingGiven.add(p.getUniqueId())) {
         this.pl.economy().give(p.getUniqueId(), Economy.STARTING_MONEY);
         Msg.info(p, "Tu reçois <#FFD25E>" + Economy.format(Economy.STARTING_MONEY) + "</#FFD25E> pour démarrer. <gray>(/solde)</gray>");
      }

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
         target = this.homeIslandOf(p.getUniqueId());

         if (target == null) {
            Msg.err(p, "Tu n'as pas encore d'île. <white>/ile creer</white>");
            Msg.info(p, "<gray>Le lobby du skyblock (marchands, marché) : <white>/ile hub</white>.</gray>");
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

   /**
    * Invite quelqu'un a venir habiter sur son ile.
    *
    * <p>Rejoindre l'ile d'un ami n'est pas anodin : on ne peut pas habiter
    * chez quelqu'un et garder son ile a soi. Si l'invite en a une, elle sera
    * effacee — on le lui dit en gros a l'ecran, et il doit confirmer.
    */
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

      if (target.equals(p)) {
         Msg.err(p, "Tu es déjà chez toi.");
         return;
      }

      if (island.members.contains(target.getUniqueId())) {
         Msg.err(p, "<white>" + target.getName() + "</white> habite déjà chez toi.");
         return;
      }

      if (island.members.size() + 1 >= MAX_MEMBERS) {
         Msg.err(p, "Ton île est pleine (" + MAX_MEMBERS + " personnes au total).");
         return;
      }

      this.invites.put(target.getUniqueId(), island.key() + "|" + System.currentTimeMillis());
      Skyblock.Island theirs = this.islandOf(target.getUniqueId());
      Msg.ok(p, "Invitation envoyée à <white>" + target.getName() + "</white>.");

      if (theirs != null) {
         Msg.info(p, "<gray>Il a déjà une île : s'il accepte, elle sera effacée. Il en est prévenu.</gray>");
      }

      Msg.raw(
         target,
         "<dark_gray>» </dark_gray><white>" + p.getName() + "</white> <gray>t'invite à venir habiter sur son île.</gray>"
      );

      if (theirs == null) {
         Msg.alert(target, "<#7FE3FF><bold>Invitation</bold></#7FE3FF>", "<white>" + p.getName() + "</white> <gray>t'invite sur son île</gray>");
         Msg.raw(target, "<gray>   <click:run_command:'/ile accept'><#55FF88>[Accepter]</#55FF88></click>   <click:run_command:'/ile refuse'><#FF5555>[Refuser]</#FF5555></click>");
      } else {
         Msg.danger(target, "ATTENTION", "<#FFB3B3>accepter effacera ton île définitivement</#FFB3B3>");
         Msg.raw(target, "<#FF5555>⚠ Tu as déjà une île. On ne peut pas habiter chez quelqu'un et garder la sienne.</#FF5555>");
         Msg.raw(target, "<#FFB3B3>   Accepter effacera <bold>toute ta construction</bold>, sans retour possible.</#FFB3B3>");
         Msg.raw(target, "<gray>   <click:run_command:'/ile accept'><#FFD25E>[J'ai compris, voir la suite]</#FFD25E></click>   <click:run_command:'/ile refuse'><#55FF88>[Garder mon île]</#55FF88></click>");
      }

      Util.sound(target, "entity.experience_orb.pickup", 0.8F, 1.2F);
   }

   /** Accepte une invitation. Deux fois, si cela doit effacer sa propre ile. */
   private void accept(Player p, String[] args) {
      String pending = this.invites.get(p.getUniqueId());

      if (pending == null) {
         Msg.err(p, "Aucune invitation en attente.");
         return;
      }

      String[] parts = pending.split("\\|");

      if (parts.length > 1 && System.currentTimeMillis() - Long.parseLong(parts[1]) > 300000L) {
         this.invites.remove(p.getUniqueId());
         Msg.err(p, "Cette invitation a expiré.");
         return;
      }

      Skyblock.Island island = this.islands.get(parts[0]);

      if (island == null) {
         this.invites.remove(p.getUniqueId());
         Msg.err(p, "Cette île n'existe plus.");
         return;
      }

      if (island.members.size() + 1 >= MAX_MEMBERS) {
         Msg.err(p, "Cette île est pleine.");
         return;
      }

      Skyblock.Island mine = this.islandOf(p.getUniqueId());
      boolean confirmed = args.length >= 2 && args[1].equalsIgnoreCase("confirmer");

      if (mine != null && !confirmed) {
         Msg.danger(p, "DERNIER AVERTISSEMENT", "<#FFB3B3>ton île va être effacée</#FFB3B3>");
         Msg.raw(p, "<dark_gray>" + "─".repeat(42) + "</dark_gray>");
         Msg.raw(p, "<#FF5555><bold>  Ton île " + mine.key() + " sera effacée, et tout ce qu'il y a dessus.</bold></#FF5555>");
         Msg.raw(p, "<#FFB3B3>  Coffres, constructions, animaux : tout disparaît.</#FFB3B3>");
         Msg.raw(p, "<#FFB3B3>  Il n'y a aucun moyen de revenir en arrière.</#FFB3B3>");
         Msg.raw(p, "<gray>  Si tu es sûr : <click:run_command:'/ile accept confirmer'><#FF5555>[/ile accept confirmer]</#FF5555></click></gray>");
         Msg.raw(p, "<gray>  Sinon : <click:run_command:'/ile refuse'><#55FF88>[/ile refuse]</#55FF88></click></gray>");
         Msg.raw(p, "<dark_gray>" + "─".repeat(42) + "</dark_gray>");
         return;
      }

      this.invites.remove(p.getUniqueId());

      if (mine != null) {
         this.wipe(mine);
         this.islands.remove(mine.key());
         Msg.err(p, "Ton ancienne île est en train d'être effacée.");
      }

      island.members.add(p.getUniqueId());
      this.save();
      Location home = this.home(island);

      if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK) {
         this.pl.worlds().send(p, Zone.SKYBLOCK);
      }

      if (home != null) {
         p.teleport(home);
      }

      Msg.ok(p, "Te voilà chez <white>" + island.ownerName + "</white>.");
      Player owner = Bukkit.getPlayer(island.owner);

      if (owner != null) {
         Msg.ok(owner, "<white>" + p.getName() + "</white> a rejoint ton île.");
         Msg.alert(owner, "<#55FF88><bold>+ " + p.getName() + "</bold></#55FF88>", "<gray>a rejoint ton île</gray>");
      }

      Util.sound(p, "entity.player.levelup", 0.7F, 1.4F);
   }

   private void refuse(Player p) {

      if (this.invites.remove(p.getUniqueId()) == null) {
         Msg.err(p, "Aucune invitation en attente.");
      } else {
         Msg.ok(p, "Invitation refusée. Tu gardes ton île.");
      }
   }

   /** Repartir de chez un ami, pour pouvoir refaire son ile a soi. */
   private void leave(Player p) {
      Skyblock.Island host = this.guestOf(p.getUniqueId());

      if (host == null) {
         Msg.err(p, "Tu n'habites chez personne.");
         return;
      }

      host.members.remove(p.getUniqueId());
      this.save();
      Msg.ok(p, "Tu as quitté l'île de <white>" + host.ownerName + "</white>. <gray>/ile creer pour refaire la tienne.</gray>");
      Player owner = Bukkit.getPlayer(host.owner);

      if (owner != null) {
         Msg.info(owner, "<white>" + p.getName() + "</white> a quitté ton île.");
      }

      Location hub = this.pl.worlds().spawnOf(Zone.SKYHUB, p);

      if (hub != null && this.pl.worlds().available(Zone.SKYHUB)) {
         this.pl.worlds().send(p, Zone.SKYHUB);
      }
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

   // ------------------------------------------------------------- les voix

   /**
    * {@code /vote ‹pseudo›} dans le skyblock : une voix pour l'ile de
    * quelqu'un, une fois par jour. Pas pour la sienne.
    */
   public void voteIsland(Player p, String name) {
      long now = System.currentTimeMillis();
      Long last = this.lastVote.get(p.getUniqueId());

      if (last != null && now - last < 86400000L && !this.pl.ranks().isAdmin(p)) {
         Msg.err(p, "Une voix par jour. Prochaine dans <white>" + Util.duration(86400000L - (now - last)) + "</white>.");
         return;
      }

      UUID owner = this.resolve(name);
      Skyblock.Island island = owner == null ? null : this.islandOf(owner);

      if (island == null) {
         Msg.err(p, "<white>" + name.replace("<", "") + "</white> n'a pas d'île.");
         return;
      }

      if (island.owner.equals(p.getUniqueId()) || island.members.contains(p.getUniqueId())) {
         Msg.err(p, "On ne vote pas pour sa propre île.");
         return;
      }

      island.votes++;
      this.lastVote.put(p.getUniqueId(), now);
      this.save();
      Msg.ok(p, "Ta voix du jour va à l'île de <white>" + island.ownerName + "</white>.");
      Player online = Bukkit.getPlayer(island.owner);

      if (online != null) {
         Msg.alert(online, "<#7FE3FF><bold>+1 voix</bold></#7FE3FF>", "<white>" + p.getName() + "</white> <gray>a voté pour ton île</gray>");
      }
   }

   /** Les iles les plus aimees, pour le panneau lateral. */
   public List<Skyblock.Island> ranking() {
      List<Skyblock.Island> all = new ArrayList<>(this.islands.values());
      all.sort((a, b) -> b.votes != a.votes ? Integer.compare(b.votes, a.votes) : a.ownerName.compareToIgnoreCase(b.ownerName));
      return all;
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
      Skyblock.Island mine = this.homeIslandOf(p.getUniqueId());

      if (mine == null) {
         Msg.info(p, "Tu n'as pas encore d'île : <white>/ile creer</white>.");
         return;
      }

      Msg.raw(p, "<dark_gray>———— <#7FE3FF>Ton île</#7FE3FF> ————</dark_gray>");
      Msg.raw(p, "<gray>Case : <white>" + mine.key() + "</white> <dark_gray>(x " + mine.x * SPACING + ", z " + mine.z * SPACING + ")</dark_gray>   ·   Invités : <white>" + mine.members.size() + "</white> / " + (MAX_MEMBERS - 1) + "</gray>");

      if (!mine.owner.equals(p.getUniqueId())) {
         Msg.raw(p, "<gray>Tu habites chez <white>" + mine.ownerName + "</white>.</gray>");
      }
      Msg.raw(p, "<gray>Zone de construction : <white>" + RADIUS * 2 + " × " + RADIUS * 2 + "</white> blocs autour du centre.</gray>");
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();

      if (args.length == 1) {
         out.addAll(List.of("creer", "tp", "invite", "accept", "refuse", "quitter", "retirer", "hub", "info", "supprimer", "aide"));
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
