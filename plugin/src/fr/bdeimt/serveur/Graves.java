package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class Graves implements Listener, CommandExecutor {
   private static final long LIFE = 86400000L;
   private static final int MAX_PER_PLAYER = 2;
   private final BDEIMT pl;
   private final File file;
   private final NamespacedKey key;
   private final Map<String, Graves.Grave> graves = new LinkedHashMap<>();
   private final Map<UUID, BossBar> bars = new HashMap<>();
   private int tickCount;

   public Graves(BDEIMT var1) {
      this.pl = var1;
      this.file = new File(var1.getDataFolder(), "tombes.yml");
      this.key = new NamespacedKey(var1, "tombe");
   }

   public void load() {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection var2 = var1.getConfigurationSection("tombes");
      if (var2 != null) {
         Iterator var3 = var2.getKeys(false).iterator();

         while (true) {
            String var4;
            ConfigurationSection var5;
            Graves.Grave var6;
            while (true) {
               if (!var3.hasNext()) {
                  return;
               }

               var4 = (String)var3.next();
               var5 = var2.getConfigurationSection(var4);
               if (var5 != null) {
                  var6 = new Graves.Grave();
                  var6.id = var4;

                  try {
                     var6.owner = UUID.fromString(var5.getString("proprietaire", ""));
                     break;
                  } catch (IllegalArgumentException var11) {
                  }
               }
            }

            var6.ownerName = var5.getString("nom", "?");
            var6.world = var5.getString("monde");
            var6.x = var5.getDouble("x");
            var6.y = var5.getDouble("y");
            var6.z = var5.getDouble("z");
            var6.created = var5.getLong("cree");
            var6.items = new ArrayList<>(var5.getStringList("objets"));
            var6.xp = var5.getInt("xp");

            for (String var8 : var5.getStringList("entites")) {
               try {
                  var6.entities.add(UUID.fromString(var8));
               } catch (IllegalArgumentException var10) {
               }
            }

            this.graves.put(var4, var6);
         }
      }
   }

   public void save() {
      YamlConfiguration var1 = new YamlConfiguration();

      for (Graves.Grave var3 : this.graves.values()) {
         String var4 = "tombes." + var3.id + ".";
         var1.set(var4 + "proprietaire", var3.owner.toString());
         var1.set(var4 + "nom", var3.ownerName);
         var1.set(var4 + "monde", var3.world);
         var1.set(var4 + "x", var3.x);
         var1.set(var4 + "y", var3.y);
         var1.set(var4 + "z", var3.z);
         var1.set(var4 + "cree", var3.created);
         var1.set(var4 + "objets", var3.items);
         var1.set(var4 + "xp", var3.xp);
         ArrayList var5 = new ArrayList();

         for (UUID var7 : var3.entities) {
            var5.add(var7.toString());
         }

         var1.set(var4 + "entites", var5);
      }

      try {
         var1.save(this.file);
      } catch (IOException var8) {
         this.pl.getLogger().warning("tombes.yml : " + var8.getMessage());
      }
   }

   private List<Graves.Grave> of(UUID var1) {
      ArrayList<Graves.Grave> var2 = new ArrayList<>();

      for (Graves.Grave var4 : this.graves.values()) {
         if (var4.owner.equals(var1)) {
            var2.add(var4);
         }
      }

      var2.sort((var0, var1x) -> Long.compare(var0.created, var1x.created));
      return var2;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDeath(PlayerDeathEvent var1) {
      Player var2 = var1.getEntity();
      if (!var1.getKeepInventory() && this.pl.worlds().zoneOf(var2) == Zone.SURVIE) {
         List<ItemStack> var3 = Util.copy(var1.getDrops());
         int var4 = var1.getDroppedExp();
         if (!var3.isEmpty() || var4 > 0) {
            Location var5 = this.safeSpot(var2.getLocation());
            Graves.Grave var6 = new Graves.Grave();
            var6.id = UUID.randomUUID().toString().substring(0, 8);
            var6.owner = var2.getUniqueId();
            var6.ownerName = var2.getName();
            var6.world = var5.getWorld().getName();
            var6.x = var5.getX();
            var6.y = var5.getY();
            var6.z = var5.getZ();
            var6.created = System.currentTimeMillis();

            for (ItemStack var8 : var3) {
               var6.items.add(Base64.getEncoder().encodeToString(var8.serializeAsBytes()));
            }

            var6.xp = var4;
            var1.getDrops().clear();
            var1.setDroppedExp(0);
            List var9 = this.of(var2.getUniqueId());

            while (var9.size() >= 2) {
               Graves.Grave var10 = (Graves.Grave)var9.remove(0);
               this.collapse(var10, true);
               Msg.poulpy(
                  var2,
                  "Ta plus ancienne tombe s'est effondrée : son contenu est tombé au sol en <white><c></white>.",
                  Msg.p("c", (int)var10.x + ", " + (int)var10.y + ", " + (int)var10.z)
               );
            }

            this.graves.put(var6.id, var6);
            this.spawn(var6);
            this.save();
            Bukkit.getScheduler()
               .runTaskLater(
                  this.pl,
                  () -> {
                     if (var2.isOnline()) {
                        Msg.poulpy(
                           var2,
                           "Ton stuff t'attend dans ta tombe pendant <white>24 h</white> en <white><c></white> (<w>). Suis la barre en haut de l'écran, puis clic droit sur la tombe.",
                           Msg.p("c", Util.coords(var5)),
                           Msg.p("w", Util.worldName(var5.getWorld()))
                        );
                     }
                  },
                  40L
               );
         }
      }
   }

   private Location safeSpot(Location var1) {
      World var2 = var1.getWorld();
      Location var3 = var1.clone();
      if (var3.getY() < var2.getMinHeight() + 1) {
         var3 = var2.getSpawnLocation().clone();
         if (var2.getEnvironment() == Environment.THE_END) {
            var3 = new Location(var2, 0.5, var2.getHighestBlockYAt(0, 0) + 1, 0.5);
            if (var3.getY() < 10.0) {
               var3 = var2.getSpawnLocation().clone();
            }
         }
      }

      if (var3.getY() > var2.getMaxHeight() - 2) {
         var3.setY(var2.getMaxHeight() - 2);
      }

      Block var4 = var3.getBlock();

      for (int var5 = 0;
         var5 < 40 && var4.getY() < var2.getMaxHeight() - 2 && (var4.isLiquid() || var4.getRelative(BlockFace.UP).isLiquid() || var4.getType().isSolid());
         var5++
      ) {
         var4 = var4.getRelative(BlockFace.UP);
      }

      return new Location(var2, var4.getX() + 0.5, var4.getY(), var4.getZ() + 0.5);
   }

   private void spawn(Graves.Grave var1) {
      Location var2 = var1.location();
      if (var2 != null) {
         var1.entities.clear();
         BlockDisplay var3 = (BlockDisplay)var2.getWorld()
            .spawn(
               var2,
               BlockDisplay.class,
               var2x -> {
                  var2x.setBlock(Material.MOSSY_STONE_BRICKS.createBlockData());
                  var2x.setTransformation(
                     new Transformation(new Vector3f(-0.35F, 0.0F, -0.1F), new AxisAngle4f(), new Vector3f(0.7F, 0.95F, 0.2F), new AxisAngle4f())
                  );
                  this.tag(var2x, var1);
               }
            );
         TextDisplay var4 = (TextDisplay)var2.getWorld()
            .spawn(
               var2.clone().add(0.0, 1.25, 0.0),
               TextDisplay.class,
               var2x -> {
                  var2x.text(
                     Msg.mm("<#BFBFBF>⚰ Tombe de <white><n></white>\n<dark_gray>Clic droit pour récupérer (ou piller)</dark_gray>", Msg.p("n", var1.ownerName))
                  );
                  var2x.setBillboard(Billboard.CENTER);
                  var2x.setShadowed(true);
                  var2x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8F, 0.8F, 0.8F), new AxisAngle4f()));
                  this.tag(var2x, var1);
               }
            );
         Interaction var5 = (Interaction)var2.getWorld().spawn(var2.clone().add(0.0, 0.0, 0.0), Interaction.class, var2x -> {
            var2x.setInteractionWidth(0.9F);
            var2x.setInteractionHeight(1.15F);
            var2x.setResponsive(true);
            this.tag(var2x, var1);
         });
         var1.entities.add(var3.getUniqueId());
         var1.entities.add(var4.getUniqueId());
         var1.entities.add(var5.getUniqueId());
      }
   }

   private void tag(Entity var1, Graves.Grave var2) {
      var1.setPersistent(true);
      var1.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, var2.id);
   }

   private void despawn(Graves.Grave var1) {
      Location var2 = var1.location();
      if (var2 != null) {
         Chunk var3 = var2.getChunk();

         for (Entity var7 : var3.getEntities()) {
            String var8 = (String)var7.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
            if (var1.id.equals(var8)) {
               var7.remove();
            }
         }
      }

      for (UUID var10 : var1.entities) {
         Entity var11 = Bukkit.getEntity(var10);
         if (var11 != null) {
            var11.remove();
         }
      }
   }

   private List<ItemStack> contents(Graves.Grave var1) {
      ArrayList var2 = new ArrayList();

      for (String var4 : var1.items) {
         try {
            var2.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(var4)));
         } catch (Exception var6) {
         }
      }

      return var2;
   }

   private void collapse(Graves.Grave var1, boolean var2) {
      Location var3 = var1.location();
      if (var2 && var3 != null) {
         for (ItemStack var5 : this.contents(var1)) {
            var3.getWorld().dropItemNaturally(var3, var5);
         }
      }

      this.despawn(var1);
      this.graves.remove(var1.id);
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onClick(PlayerInteractEntityEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND) {
         String var2 = (String)var1.getRightClicked().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
         if (var2 != null) {
            var1.setCancelled(true);
            this.open(var1.getPlayer(), var2);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      String var2 = (String)var1.getEntity().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
      if (var2 != null) {
         var1.setCancelled(true);
         if (var1.getDamager() instanceof Player var3) {
            this.open(var3, var2);
         }
      }
   }

   private void open(Player var1, String var2) {
      if (this.pl.auth().isLogged(var1)) {
         Graves.Grave var3 = this.graves.get(var2);
         if (var3 != null) {
            List<ItemStack> var4 = this.contents(var3);
            PlayerInventory var5 = var1.getInventory();
            ArrayList var6 = new ArrayList();

            for (ItemStack var8 : var4) {
               EquipmentSlot var9;
               try {
                  var9 = var8.getType().getEquipmentSlot();
               } catch (Throwable var11) {
                  var9 = EquipmentSlot.HAND;
               }

               if (var9 == EquipmentSlot.HEAD && empty(var5.getHelmet())) {
                  var5.setHelmet(var8);
               } else if (var9 == EquipmentSlot.CHEST && empty(var5.getChestplate())) {
                  var5.setChestplate(var8);
               } else if (var9 == EquipmentSlot.LEGS && empty(var5.getLeggings())) {
                  var5.setLeggings(var8);
               } else if (var9 == EquipmentSlot.FEET && empty(var5.getBoots())) {
                  var5.setBoots(var8);
               } else {
                  var6.add(var8);
               }
            }

            boolean var12 = Util.give(var1, var6);
            if (var3.xp > 0) {
               var1.giveExp(var3.xp);
            }

            this.despawn(var3);
            this.graves.remove(var3.id);
            this.save();
            Util.sound(var1, "block.ender_chest.open", 1.0F, 1.0F);
            if (var3.owner.equals(var1.getUniqueId())) {
               Msg.ok(var1, "Tombe récupérée !" + (var12 ? " Inventaire plein : le reste est à tes pieds." : ""));
            } else {
               Msg.ok(
                  var1,
                  "Tu as pillé la tombe de <white><n></white> !" + (var12 ? " Inventaire plein : le reste est à tes pieds." : ""),
                  Msg.p("n", var3.ownerName)
               );
               Player var13 = Bukkit.getPlayer(var3.owner);
               if (var13 != null) {
                  Msg.poulpy(var13, "<white><a></white> vient de piller ta tombe. Ouch.", Msg.p("a", var1.getName()));
                  Util.sound(var13, "entity.villager.no", 1.0F, 0.8F);
                  this.updateBar(var13);
               }

               Msg.staff("<white><a></white> a pillé la tombe de <white><n></white>.", Msg.p("a", var1.getName()), Msg.p("n", var3.ownerName));
            }

            this.updateBar(var1);
         }
      }
   }

   private static boolean empty(ItemStack var0) {
      return var0 == null || var0.getType().isAir();
   }

   @EventHandler
   public void onEntitiesLoad(EntitiesLoadEvent var1) {
      for (Entity var3 : var1.getEntities()) {
         String var4 = (String)var3.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
         if (var4 != null && !this.graves.containsKey(var4)) {
            var3.remove();
         }
      }
   }

   public void onLogin(Player var1) {
      this.updateBar(var1);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      BossBar var2 = this.bars.remove(var1.getPlayer().getUniqueId());
      if (var2 != null) {
         var1.getPlayer().hideBossBar(var2);
      }
   }

   public void tick() {
      for (Player var2 : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(var2)) {
            this.updateBar(var2);
         }
      }

      if (++this.tickCount % 60 == 0) {
         this.expire();
      }
   }

   private void expire() {
      boolean var1 = false;

      for (Graves.Grave var3 : new ArrayList<>(this.graves.values())) {
         if (var3.left() > 0L) {
            Location var4 = var3.location();
            if (var4 != null && var4.isChunkLoaded() && !var3.entities.isEmpty() && Bukkit.getEntity(var3.entities.get(0)) == null) {
               this.despawn(var3);
               this.spawn(var3);
               var1 = true;
            }
         } else {
            this.collapse(var3, false);
            var1 = true;
            Player var5 = Bukkit.getPlayer(var3.owner);
            if (var5 != null) {
               Msg.poulpy(var5, "Une de tes tombes a disparu après 24 h.");
            }
         }
      }

      if (var1) {
         this.save();
      }
   }

   private void updateBar(Player var1) {
      List<Graves.Grave> var2 = this.of(var1.getUniqueId());
      BossBar var3 = this.bars.get(var1.getUniqueId());
      if (!var2.isEmpty() && !this.pl.lobby().isLobby(var1.getWorld())) {
         Graves.Grave var4 = null;
         double var5 = Double.MAX_VALUE;

         for (Graves.Grave var8 : var2) {
            if (var8.world.equals(var1.getWorld().getName())) {
               double var9 = var1.getLocation().distanceSquared(new Location(var1.getWorld(), var8.x, var8.y, var8.z));
               if (var9 < var5) {
                  var5 = var9;
                  var4 = var8;
               }
            }
         }

         Graves.Grave var15 = var4 != null ? var4 : (Graves.Grave)var2.get(var2.size() - 1);
         String var16 = var2.size() > 1 ? " <dark_gray>(+" + (var2.size() - 1) + " autre)</dark_gray>" : "";
         String var10 = Util.duration(var15.left());
         String var14;
         if (var4 != null) {
            double var11 = Math.sqrt(var5);
            var14 = "<#C77DFF>⚰ Ta tombe</#C77DFF> <white>"
               + (int)Math.round(var11)
               + " m</white> <#FFC93C>"
               + arrow(var1, var4)
               + "</#FFC93C> <dark_gray>·</dark_gray> <gray>"
               + (int)var4.x
               + ", "
               + (int)var4.y
               + ", "
               + (int)var4.z
               + " · encore "
               + var10
               + "</gray>"
               + var16;
         } else {
            Location var17 = var15.location();
            var14 = "<#C77DFF>⚰ Ta tombe est dans "
               + Util.worldName(var17 == null ? null : var17.getWorld())
               + "</#C77DFF> <gray>("
               + (int)var15.x
               + ", "
               + (int)var15.y
               + ", "
               + (int)var15.z
               + ") · encore "
               + var10
               + "</gray>"
               + var16;
         }

         float var18 = (float)Math.max(0.0, Math.min(1.0, var15.left() / 8.64E7));
         if (var3 == null) {
            var3 = BossBar.bossBar(Msg.mm(var14), var18, Color.PURPLE, Overlay.PROGRESS);
            this.bars.put(var1.getUniqueId(), var3);
            var1.showBossBar(var3);
         } else {
            var3.name(Msg.mm(var14));
            var3.progress(var18);
         }
      } else {
         if (var3 != null) {
            var1.hideBossBar(var3);
            this.bars.remove(var1.getUniqueId());
         }
      }
   }

   private static String arrow(Player var0, Graves.Grave var1) {
      double var2 = var1.x - var0.getLocation().getX();
      double var4 = var1.z - var0.getLocation().getZ();
      if (var2 * var2 + var4 * var4 < 4.0) {
         return "●";
      } else {
         double var6 = Math.toDegrees(Math.atan2(-var2, var4));
         double var8 = var6 - var0.getLocation().getYaw();
         var8 = (var8 % 360.0 + 540.0) % 360.0 - 180.0;
         String[] var10 = new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
         int var11 = (int)Math.round(var8 / 45.0);
         var11 = (var11 % 8 + 8) % 8;
         return var10[var11];
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         List<Graves.Grave> var6 = this.of(var5.getUniqueId());
         if (var6.isEmpty()) {
            Msg.poulpy(var5, "Tu n'as aucune tombe. À ta mort, ton stuff y est rangé 24 h (2 tombes max).");
            return true;
         } else {
            Msg.poulpy(var5, "Tes tombes :");

            for (Graves.Grave var8 : var6) {
               Location var9 = var8.location();
               Msg.raw(
                  var5,
                  "  <#C77DFF>⚰</#C77DFF> <white><c></white> <gray>dans <w> · encore <t></gray>",
                  Msg.p("c", (int)var8.x + ", " + (int)var8.y + ", " + (int)var8.z),
                  Msg.p("w", Util.worldName(var9 == null ? null : var9.getWorld())),
                  Msg.p("t", Util.duration(var8.left()))
               );
            }

            return true;
         }
      }
   }

   public void hideAll() {
      for (Entry var2 : this.bars.entrySet()) {
         Player var3 = Bukkit.getPlayer((UUID)var2.getKey());
         if (var3 != null) {
            var3.hideBossBar((BossBar)var2.getValue());
         }
      }

      this.bars.clear();
   }

   private static final class Grave {
      String id;
      UUID owner;
      String ownerName;
      String world;
      double x;
      double y;
      double z;
      long created;
      List<String> items = new ArrayList<>();
      int xp;
      List<UUID> entities = new ArrayList<>();

      Location location() {
         World var1 = Bukkit.getWorld(this.world);
         return var1 == null ? null : new Location(var1, this.x, this.y, this.z);
      }

      long left() {
         return this.created + 86400000L - System.currentTimeMillis();
      }
   }
}
