package fr.bdeimt.serveur;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class Lobby implements Listener {
   public static final String WORLD = "bdeimt_lobby";
   private static final int VERSION = 1;
   private static final int TOP = 100;
   private static final int WALL_Z = -21;
   private static final int WALL_BOTTOM = 107;
   private final BDEIMT pl;
   private final NamespacedKey tag;
   private World world;
   private Location spawn;

   public Lobby(BDEIMT var1) {
      this.pl = var1;
      this.tag = new NamespacedKey(var1, "lobby");
   }

   public World world() {
      return this.world;
   }

   public Location spawn() {
      return this.spawn.clone();
   }

   public boolean isLobby(World var1) {
      return var1 != null && var1.getName().equals("bdeimt_lobby");
   }

   public void init() {
      WorldCreator var1 = new WorldCreator("bdeimt_lobby").environment(Environment.NORMAL).generator(new Lobby.VoidGenerator()).generateStructures(false);
      this.world = Bukkit.createWorld(var1);
      if (this.world == null) {
         throw new IllegalStateException("Impossible de creer le monde du lobby");
      } else {
         this.spawn = new Location(this.world, 0.5, 101.0, 0.5, 180.0F, 0.0F);
         this.rule(false, "doDaylightCycle", "advance_time");
         this.rule(false, "doWeatherCycle", "advance_weather");
         this.rule(false, "doMobSpawning", "spawn_mobs");
         this.rule(0, "randomTickSpeed", "random_tick_speed");
         this.rule(false, "doPatrolSpawning", "spawn_patrols");
         this.rule(false, "doTraderSpawning", "spawn_wandering_traders");
         this.rule(false, "doInsomnia", "spawn_phantoms");
         this.rule(false, "fallDamage", "fall_damage");
         this.rule(false, "pvp");
         safe(() -> this.world.setDifficulty(Difficulty.PEACEFUL));
         this.world.setTime(6000L);
         this.world.setStorm(false);
         this.world.setThundering(false);
         this.world.setSpawnLocation(0, 101, 0);

         for (int var2 = -3; var2 <= 1; var2++) {
            for (int var3 = -3; var3 <= 1; var3++) {
               this.world.addPluginChunkTicket(var2, var3, this.pl);
            }
         }

         if (this.pl.state().lobbyVersion < 1) {
            this.buildIsland();
            this.pl.state().lobbyVersion = 1;
            this.pl.state().save();
         }

         this.decorate();
         Bukkit.getScheduler().runTaskTimer(this.pl, this::maintain, 100L, 100L);
      }
   }

   private void rule(Object var1, String... var2) {
      try {
         for (GameRule var6 : GameRule.values()) {
            String var7 = var6.getName();

            for (String var11 : var2) {
               if (var7.equalsIgnoreCase(var11) || var7.endsWith(":" + var11)) {
                  this.set(var6, var1);
                  return;
               }
            }
         }
      } catch (Throwable var12) {
      }
   }

   @SuppressWarnings("unchecked")
   private <T> void set(GameRule<T> var1, Object var2) {
      if (var1.getType().isInstance(var2)) {
         this.world.setGameRule(var1, (T)var2);
      }
   }

   private static void safe(Runnable var0) {
      try {
         var0.run();
      } catch (Throwable var2) {
      }
   }

   private void maintain() {
      if (this.world != null) {
         this.world.setTime(6000L);
         if (this.world.hasStorm()) {
            this.world.setStorm(false);
         }

         for (Player var2 : this.world.getPlayers()) {
            if (var2.getLocation().getY() < 80.0) {
               var2.teleport(this.spawn);
            }

            if (!this.pl.auth().isLogged(var2) && var2.getWalkSpeed() != 0.0F) {
               this.freeze(var2);
            }
         }
      }
   }

   private void buildIsland() {
      Random var1 = new Random(2026L);
      byte var2 = 12;

      for (int var3 = 0; var3 <= var2; var3++) {
         double var4 = 10.5 * Math.pow(1.0 - (double)var3 / (var2 + 1), 0.8);
         int var6 = 100 - var3;

         for (int var7 = -12; var7 <= 12; var7++) {
            for (int var8 = -12; var8 <= 12; var8++) {
               double var9 = (Math.sin(var7 * 1.7 + var8 * 0.9 + var3) + Math.cos(var8 * 1.3 - var7 * 0.6)) * 0.55;
               double var11 = Math.sqrt(var7 * var7 + var8 * var8) + (var3 == 0 ? var9 * 0.4 : var9);
               if (!(var11 > var4)) {
                  Material var13;
                  if (var3 == 0) {
                     var13 = Material.GRASS_BLOCK;
                  } else if (var3 <= 2) {
                     var13 = Material.DIRT;
                  } else {
                     int var14 = var1.nextInt(10);
                     var13 = var14 == 0 ? Material.ANDESITE : (var14 == 1 ? Material.COBBLESTONE : (var14 == 2 ? Material.TUFF : Material.STONE));
                  }

                  this.world.getBlockAt(var7, var6, var8).setType(var13, false);
               }
            }
         }
      }

      for (int var15 = -1; var15 <= 1; var15++) {
         for (int var17 = -1; var17 <= 1; var17++) {
            this.world.getBlockAt(var15, 100, var17).setType(Material.POLISHED_ANDESITE, false);
         }
      }

      this.world.getBlockAt(0, 100, 0).setType(Material.CHISELED_STONE_BRICKS, false);
      Material[] var16 = new Material[]{
         Material.SHORT_GRASS,
         Material.SHORT_GRASS,
         Material.SHORT_GRASS,
         Material.POPPY,
         Material.DANDELION,
         Material.CORNFLOWER,
         Material.AZURE_BLUET,
         Material.OXEYE_DAISY,
         Material.PINK_TULIP
      };

      for (int var18 = -9; var18 <= 9; var18++) {
         for (int var5 = -9; var5 <= 9; var5++) {
            if ((Math.abs(var18) > 2 || Math.abs(var5) > 2) && (var5 >= 0 || Math.abs(var18) > 4)) {
               Block var23 = this.world.getBlockAt(var18, 100, var5);
               if (var23.getType() == Material.GRASS_BLOCK && var1.nextInt(4) == 0) {
                  this.world.getBlockAt(var18, 101, var5).setType(var16[var1.nextInt(var16.length)], false);
               }
            }
         }
      }

      int[][] var19 = new int[][]{{-6, -5}, {6, -5}, {-6, 6}, {6, 6}};

      for (int[] var28 : var19) {
         Block var30 = this.world.getBlockAt(var28[0], 100, var28[1]);
         if (!var30.getType().isAir()) {
            this.world.getBlockAt(var28[0], 101, var28[1]).setType(Material.DARK_OAK_FENCE, false);
            this.world.getBlockAt(var28[0], 102, var28[1]).setType(Material.DARK_OAK_FENCE, false);
            this.world.getBlockAt(var28[0], 103, var28[1]).setType(Material.LANTERN, false);
         }
      }

      for (int var21 = 1; var21 <= 4; var21++) {
         this.world.getBlockAt(5, 100 + var21, 7).setType(Material.CHERRY_LOG, false);
      }

      for (int var22 = 3; var22 <= 7; var22++) {
         for (int var25 = 5; var25 <= 9; var25++) {
            for (int var27 = 4; var27 <= 6; var27++) {
               if (Math.abs(var22 - 5) + Math.abs(var25 - 7) + Math.abs(var27 - 5) <= 3) {
                  Block var29 = this.world.getBlockAt(var22, 100 + var27, var25);
                  if (var29.getType().isAir()) {
                     var29.setType(Material.CHERRY_LEAVES, false);
                  }
               }
            }
         }
      }

      this.pl.getLogger().info("Ile du lobby construite.");
   }

   public void decorate() {
      for (Entity var2 : this.world.getEntities()) {
         if (var2.getPersistentDataContainer().has(this.tag, PersistentDataType.BYTE)) {
            var2.remove();
         }
      }

      this.text(
         new Location(this.world, 0.5, 102.95, -9.5),
         1.6F,
         "<gradient:#4FC3FF:#B66BFF:#FF5FAE><bold>✦ Bienvenue sur le serveur du BDE de l'IMT ✦</bold></gradient>\n<#E8E8E8>Survie entre étudiants, entraide et bonne ambiance :</#E8E8E8>\n<#E8E8E8>installez-vous, on n'attendait plus que vous !</#E8E8E8>"
      );
      this.text(
         new Location(this.world, 0.5, 101.45, -6.0),
         0.95F,
         "<#FFD25E><bold>Première connexion ?</bold></#FFD25E> <white>Il est nécessaire de créer votre mot de passe :</white>\n<#55FF88><bold>/register</bold></#55FF88> <gray>‹mot de passe› ‹mot de passe›</gray>\n \n<white>Ensuite, il vous suffira d'entrer à chaque connexion :</white>\n<#4FC3FF><bold>/login</bold></#4FC3FF> <gray>‹mot de passe›</gray>"
      );
      this.buildImageWall();
   }

   private void text(Location var1, float var2, String var3) {
      this.world.spawn(var1, TextDisplay.class, var3x -> {
         var3x.text(Msg.mm(var3));
         var3x.setBillboard(Billboard.VERTICAL);
         var3x.setAlignment(TextAlignment.CENTER);
         var3x.setLineWidth(420);
         var3x.setShadowed(true);
         var3x.setBackgroundColor(Color.fromARGB(110, 10, 10, 25));
         var3x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(var2, var2, var2), new AxisAngle4f()));
         var3x.setPersistent(false);
         var3x.getPersistentDataContainer().set(this.tag, PersistentDataType.BYTE, (byte)1);
      });
   }

   private File findImage(String var1) {
      File var2 = new File(this.pl.getDataFolder(), "images");
      File[] var3 = var2.listFiles();
      if (var3 == null) {
         return null;
      } else {
         for (File var7 : var3) {
            if (var7.getName().equalsIgnoreCase(var1)) {
               return var7;
            }
         }

         return null;
      }
   }

   private void buildImageWall() {
      File var1 = this.findImage("BDEIMT.png");
      if (var1 == null) {
         this.pl.getLogger().warning("Image du lobby absente : depose BDEIMT.png dans plugins/BDEIMT/images puis /imt reload");
      } else {
         BufferedImage var2;
         try {
            var2 = ImageIO.read(var1);
         } catch (Exception var33) {
            var2 = null;
         }

         if (var2 == null) {
            this.pl.getLogger().warning("BDEIMT.png illisible (ce doit etre un vrai PNG).");
         } else {
            double var3 = (double)var2.getWidth() / var2.getHeight();
            int var5 = 4;
            int var6 = 3;
            double var7 = Double.MAX_VALUE;

            for (int var9 = 1; var9 <= 8; var9++) {
               for (int var10 = 1; var10 <= 5; var10++) {
                  double var11 = Math.abs(Math.log((double)var9 / var10 / var3));
                  double var13 = var11 * 3.0 - var9 * var10 * 0.03;
                  if (var13 < var7) {
                     var7 = var13;
                     var5 = var9;
                     var6 = var10;
                  }
               }
            }

            int var34 = var5 * 128;
            int var35 = var6 * 128;
            double var36 = Math.min((double)var34 / var2.getWidth(), (double)var35 / var2.getHeight());
            int var37 = Math.max(1, (int)Math.round(var2.getWidth() * var36));
            int var14 = Math.max(1, (int)Math.round(var2.getHeight() * var36));
            BufferedImage var15 = scale(var2, var37, var14);
            BufferedImage var16 = new BufferedImage(var34, var35, 2);
            Graphics2D var17 = var16.createGraphics();
            var17.drawImage(var15, (var34 - var37) / 2, (var35 - var14) / 2, null);
            var17.dispose();
            List var18 = this.pl.state().mapIds;
            int var19 = -((var5 - 1) / 2);
            int var20 = var5 * var6;
            int var21 = 0;

            for (int var22 = 0; var22 < var6; var22++) {
               for (int var23 = 0; var23 < var5; var21++) {
                  BufferedImage var24 = var16.getSubimage(var23 * 128, var22 * 128, 128, 128);
                  MapView var25 = null;
                  if (var21 < var18.size()) {
                     try {
                        var25 = Bukkit.getMap((Integer)var18.get(var21));
                     } catch (Throwable var32) {
                     }
                  }

                  if (var25 == null) {
                     var25 = Bukkit.createMap(this.world);
                     if (var21 < var18.size()) {
                        var18.set(var21, var25.getId());
                     } else {
                        var18.add(var25.getId());
                     }
                  }

                  for (MapRenderer var27 : List.copyOf(var25.getRenderers())) {
                     var25.removeRenderer(var27);
                  }

                  var25.setScale(Scale.CLOSEST);
                  var25.setTrackingPosition(false);
                  var25.setUnlimitedTracking(false);
                  var25.addRenderer(new Lobby.TileRenderer(var24));
                  ItemStack var38 = new ItemStack(Material.FILLED_MAP);
                  MapMeta var39 = (MapMeta)var38.getItemMeta();
                  var39.setMapView(var25);
                  var38.setItemMeta(var39);
                  int var28 = var19 + var23;
                  int var29 = 107 + (var6 - 1 - var22);
                  this.world.getBlockAt(var28, var29, -21).setType(Material.BARRIER, false);
                  Location var30 = new Location(this.world, var28, var29, -20.0);
                  this.world.spawn(var30, GlowItemFrame.class, var2x -> {
                     var2x.setFacingDirection(BlockFace.SOUTH, true);
                     var2x.setItem(var38, false);
                     var2x.setVisible(false);
                     var2x.setFixed(true);
                     var2x.setInvulnerable(true);
                     var2x.setPersistent(false);
                     var2x.getPersistentDataContainer().set(this.tag, PersistentDataType.BYTE, (byte)1);
                  });
                  var23++;
               }
            }

            while (var18.size() > var20) {
               var18.remove(var18.size() - 1);
            }

            this.pl.state().save();
            this.pl.getLogger().info("Image du lobby affichee sur " + var5 + " x " + var6 + " cartes.");
         }
      }
   }

   private static BufferedImage scale(BufferedImage var0, int var1, int var2) {
      BufferedImage var3 = var0;
      int var4 = var0.getWidth();

      for (int var5 = var0.getHeight(); var4 / 2 >= var1 && var5 / 2 >= var2; var3 = draw(var3, var4, var5)) {
         var4 /= 2;
         var5 /= 2;
      }

      return draw(var3, var1, var2);
   }

   private static BufferedImage draw(BufferedImage var0, int var1, int var2) {
      BufferedImage var3 = new BufferedImage(var1, var2, 2);
      Graphics2D var4 = var3.createGraphics();
      var4.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      var4.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      var4.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      var4.drawImage(var0, 0, 0, var1, var2, null);
      var4.dispose();
      return var3;
   }

   public void enter(Player var1) {
      if (var1.isInsideVehicle()) {
         var1.leaveVehicle();
      }

      var1.setFallDistance(0.0F);
      var1.setFireTicks(0);
      var1.setAllowFlight(false);
      var1.setFlying(false);
      var1.teleport(this.spawn);
      var1.setGameMode(GameMode.ADVENTURE);
      this.freeze(var1);
   }

   private void freeze(Player var1) {
      var1.setWalkSpeed(0.0F);
      var1.setFlySpeed(0.0F);
   }

   public void leave(Player var1, Location var2, Runnable var3) {
      var1.setWalkSpeed(0.2F);
      var1.setFlySpeed(0.1F);
      var1.setFallDistance(0.0F);
      var1.teleportAsync(var2).whenComplete((var3x, var4) -> Bukkit.getScheduler().runTask(this.pl, () -> {
         if (var1.isOnline()) {
            if (this.isLobby(var1.getWorld())) {
               var1.teleport(this.pl.survivalSpawn());
            }

            var3.run();
         }
      }));
   }

   private boolean canEdit(Player var1) {
      return var1.isOp() && this.pl.auth().isLogged(var1) && var1.getGameMode() == GameMode.CREATIVE;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onSpawn(CreatureSpawnEvent var1) {
      if (this.isLobby(var1.getLocation().getWorld())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent var1) {
      if (this.isLobby(var1.getEntity().getWorld())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent var1) {
      if (this.isLobby(var1.getBlock().getWorld()) && !this.canEdit(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent var1) {
      if (this.isLobby(var1.getBlock().getWorld()) && !this.canEdit(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent var1) {
      if (this.isLobby(var1.getPlayer().getWorld()) && !this.canEdit(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onInteractEntity(PlayerInteractEntityEvent var1) {
      if (this.isLobby(var1.getPlayer().getWorld()) && var1.getRightClicked() instanceof ItemFrame) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onHangingBreak(HangingBreakEvent var1) {
      Hanging var2 = var1.getEntity();
      if (var2.getPersistentDataContainer().has(this.tag, PersistentDataType.BYTE)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onFrameHit(EntityDamageByEntityEvent var1) {
      if (var1.getEntity().getPersistentDataContainer().has(this.tag, PersistentDataType.BYTE)) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onWeather(WeatherChangeEvent var1) {
      if (this.isLobby(var1.getWorld()) && var1.toWeatherState()) {
         var1.setCancelled(true);
      }
   }

   private static final class TileRenderer extends MapRenderer {
      private final BufferedImage img;
      private boolean done;

      TileRenderer(BufferedImage var1) {
         super(false);
         this.img = var1;
      }

      public void render(MapView var1, MapCanvas var2, Player var3) {
         if (!this.done) {
            var2.drawImage(0, 0, this.img);
            this.done = true;
         }
      }
   }

   public static final class VoidGenerator extends ChunkGenerator {
      public boolean shouldGenerateNoise() {
         return false;
      }

      public boolean shouldGenerateSurface() {
         return false;
      }

      public boolean shouldGenerateCaves() {
         return false;
      }

      public boolean shouldGenerateDecorations() {
         return false;
      }

      public boolean shouldGenerateMobs() {
         return false;
      }

      public boolean shouldGenerateStructures() {
         return false;
      }

      public Location getFixedSpawnLocation(World var1, Random var2) {
         return new Location(var1, 0.5, 101.0, 0.5);
      }

      public BiomeProvider getDefaultBiomeProvider(WorldInfo var1) {
         return new BiomeProvider() {
            public Biome getBiome(WorldInfo var1, int var2, int var3, int var4) {
               return Biome.PLAINS;
            }

            public List<Biome> getBiomes(WorldInfo var1) {
               return List.of(Biome.PLAINS);
            }
         };
      }
   }
}
