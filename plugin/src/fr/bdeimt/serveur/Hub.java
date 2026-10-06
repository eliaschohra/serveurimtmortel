package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Le hub : la place ou l'on arrive une fois identifie.
 *
 * <p>On y choisit son mode de jeu, soit avec la boussole au milieu de
 * l'inventaire, soit en marchant dans un portail pose par le staff avec
 * {@code /imt portail}.
 */
public final class Hub implements Listener, org.bukkit.command.CommandExecutor {
   /** La boussole est toujours au milieu de la barre d'objets. */
   public static final int COMPASS_SLOT = 4;
   private final BDEIMT pl;
   private final NamespacedKey compassKey;
   private final NamespacedKey holoKey;

   public Hub(BDEIMT pl) {
      this.pl = pl;
      this.compassKey = new NamespacedKey(pl, "boussole");
      this.holoKey = new NamespacedKey(pl, "hub_holo");
   }

   // -------------------------------------------------------------- boussole

   public ItemStack compass() {
      // Pas une vraie boussole : c'est la baguette de navigation de WorldEdit,
      // qui teleporte au clic gauche et au clic droit tous ceux qui en ont
      // le droit. La boussole de recuperation lui ressemble et ne fait rien.
      ItemStack item = Util.item(
         Material.RECOVERY_COMPASS,
         1,
         "<gradient:#4FC3FF:#B66BFF><bold>Choisir un mode de jeu</bold></gradient>",
         "<gray>Clic droit pour ouvrir le menu.</gray>",
         "<dark_gray>Survie · Parkour · Skyblock · Parcelles</dark_gray>"
      );
      item.editMeta(meta -> meta.getPersistentDataContainer().set(this.compassKey, PersistentDataType.BYTE, (byte)1));
      return item;
   }

   public void giveCompass(Player p) {
      p.getInventory().setItem(COMPASS_SLOT, this.compass());
      p.getInventory().setHeldItemSlot(COMPASS_SLOT);
   }

   private boolean isCompass(ItemStack item) {
      return item != null
         && (item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS)
         && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(this.compassKey, PersistentDataType.BYTE);
   }

   /** Clic droit : le menu. Clic gauche : rien du tout. */
   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onUse(PlayerInteractEvent e) {
      if (!this.isCompass(e.getItem())) {
         return;
      }

      e.setCancelled(true);
      e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
      e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

      if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && this.pl.auth().isLogged(e.getPlayer())) {
         new Hub.Chooser(this.pl).open(e.getPlayer());
         Util.sound(e.getPlayer(), "ui.button.click", 0.5F, 1.6F);
      }
   }

   /** On ne passe pas la boussole dans l'autre main. */
   @EventHandler(ignoreCancelled = true)
   public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
      if (this.isCompass(e.getMainHandItem()) || this.isCompass(e.getOffHandItem())) {
         e.setCancelled(true);
      }
   }

   /** {@code /hub} : revenir choisir un mode de jeu. */
   @Override
   public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;
      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      if (command.getName().equalsIgnoreCase("musique")) {
         this.toggleMusic(p);
         return true;
      }

      // Si la carte du hub n'a pas ete deposee sur le serveur, on ouvre quand
      // meme le menu : sinon on serait prisonnier de la survie, sans aucun
      // moyen d'atteindre le skyblock ou les parcelles.
      if (this.pl.worlds().zoneOf(p) == Zone.HUB || !this.pl.worlds().available(Zone.HUB)) {
         new Hub.Chooser(this.pl).open(p);
         Util.sound(p, "ui.button.click", 0.5F, 1.6F);
      } else {
         this.pl.worlds().send(p, Zone.HUB);
      }

      return true;
   }

   // -------------------------------------------------------------- portails

   /** Un portail : une zone a rejoindre et un point de passage. */
   private record Portal(Zone zone, Location at, double radius) {
   }

   private final List<Hub.Portal> portals = new ArrayList<>();

   public void loadPortals() {
      this.portals.clear();

      for (String line : this.pl.getConfig().getStringList("portails")) {
         try {
            String[] parts = line.split("@", 2);
            Zone zone = Zone.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            String[] rest = parts[1].split("\\|");
            Location at = Util.loc(rest[0]);
            double radius = rest.length > 1 ? Double.parseDouble(rest[1]) : 2.0;
            if (at != null) {
               this.portals.add(new Hub.Portal(zone, at, radius));
            }
         } catch (Exception ex) {
            this.pl.getLogger().warning("Portail illisible dans config.yml : " + line);
         }
      }

      if (!this.portals.isEmpty()) {
         this.pl.getLogger().info(this.portals.size() + " portail(s) charge(s).");
      }
   }

   /** Ajoute un portail a l'endroit ou se trouve le staff. */
   public void addPortal(Zone zone, Location at, double radius) {
      Hub.Portal portal = new Hub.Portal(zone, at, radius);
      this.portals.add(portal);
      this.portalTitle(portal);
      List<String> lines = new ArrayList<>(this.pl.getConfig().getStringList("portails"));
      lines.add(zone.name() + "@" + Util.loc(at) + "|" + radius);
      this.pl.getConfig().set("portails", lines);
      this.pl.saveConfig();
   }

   public int clearPortals(World world) {
      int before = this.portals.size();
      this.portals.removeIf(portal -> portal.at().getWorld() != null && portal.at().getWorld().equals(world));
      this.portalTitles.entrySet().removeIf(entry -> {
         if (entry.getKey().at().getWorld() != null && entry.getKey().at().getWorld().equals(world)) {
            entry.getValue().remove();
            return true;
         }

         return false;
      });
      List<String> lines = new ArrayList<>();

      for (Hub.Portal portal : this.portals) {
         lines.add(portal.zone().name() + "@" + Util.loc(portal.at()) + "|" + portal.radius());
      }

      this.pl.getConfig().set("portails", lines);
      this.pl.saveConfig();
      return before - this.portals.size();
   }

   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent e) {
      Player p = e.getPlayer();

      // Sous la carte du lobby : on remonte au point d'arrivee. Les degats y
      // sont coupes, donc sans ca on tomberait dans le vide indefiniment.
      if (e.getTo().getY() < e.getTo().getWorld().getMinHeight() - 5 && this.pl.worlds().zoneOf(p) == Zone.HUB) {
         Location back = this.pl.auth().isLogged(p) ? this.pl.worlds().spawnOf(Zone.HUB, p) : this.pl.lobby().loginSpawn();

         if (back != null) {
            p.setFallDistance(0.0F);
            p.teleport(back);
         }

         return;
      }

      if (this.portals.isEmpty() || e.getTo().getBlockX() == e.getFrom().getBlockX() && e.getTo().getBlockZ() == e.getFrom().getBlockZ() && e.getTo().getBlockY() == e.getFrom().getBlockY()) {
         return;
      }

      if (!this.pl.auth().isLogged(p)) {
         return;
      }

      for (Hub.Portal portal : this.portals) {
         Location at = portal.at();
         if (at.getWorld() != null && at.getWorld().equals(e.getTo().getWorld()) && at.distanceSquared(e.getTo()) <= portal.radius() * portal.radius()) {
            if (this.pl.worlds().zoneOf(p) != portal.zone()) {
               this.pl.worlds().send(p, portal.zone());
            }

            return;
         }
      }
   }

   // ----------------------------------------------------------- hologrammes

   /** Les titres poses au-dessus de chaque portail, pour mettre a jour leur compteur. */
   private final java.util.Map<Hub.Portal, TextDisplay> portalTitles = new java.util.HashMap<>();

   /**
    * Pose les hologrammes du lobby : la presentation du serveur a l'entree,
    * et le titre de chaque mode au-dessus de son portail.
    */
   public void decorate() {
      World w = this.pl.worlds().world(Zone.HUB);

      if (w == null) {
         return;
      }

      for (Entity entity : w.getEntities()) {
         if (entity.getPersistentDataContainer().has(this.holoKey, PersistentDataType.BYTE)) {
            entity.remove();
         }
      }

      this.portalTitles.clear();
      Location spawn = this.pl.worlds().spawnOf(Zone.HUB, null);

      if (spawn == null) {
         return;
      }

      double yaw = Math.toRadians(spawn.getYaw());
      double dx = -Math.sin(yaw);
      double dz = Math.cos(yaw);

      // La presentation, quelques pas devant le point d'arrivee.
      this.hologram(
         spawn.clone().add(dx * 6.0, 2.8, dz * 6.0),
         1.6F,
         "<gradient:#4FC3FF:#B66BFF:#FF5FAE><bold>✦ Lobby du BDE de l'IMT ✦</bold></gradient>\n"
            + "<#E8E8E8>Le serveur des étudiants d'IMT Atlantique</#E8E8E8>\n \n"
            + "<#55FF88>Survie</#55FF88> <dark_gray>·</dark_gray> <#FFD25E>Parkour du mois</#FFD25E> <dark_gray>·</dark_gray> "
            + "<#C48BFF>Parcelles créatives</#C48BFF> <dark_gray>·</dark_gray> <#7FE3FF>Skyblock</#7FE3FF>\n \n"
            + "<white>Avance jusqu'aux portails, ou clic droit sur la</white> <#4FC3FF>boussole</#4FC3FF><white>.</white>\n"
            + "<gray>/guide pour tout savoir  ·  /hub pour revenir ici</gray>"
      );

      for (Hub.Portal portal : this.portals) {
         if (portal.at().getWorld() != null && portal.at().getWorld().equals(w)) {
            this.portalTitle(portal);
         }
      }
   }

   /** Le titre d'un mode, au-dessus de son portail. */
   private void portalTitle(Hub.Portal portal) {
      World w = portal.at().getWorld();

      if (w == null) {
         return;
      }

      Location at = portal.at().clone().add(0.0, portal.radius() + 2.4, 0.0);
      TextDisplay display = w.spawn(at, TextDisplay.class, d -> {
         d.text(Msg.mm(this.portalText(portal.zone())));
         d.setBillboard(Billboard.CENTER);
         d.setAlignment(TextAlignment.CENTER);
         d.setLineWidth(260);
         d.setShadowed(true);
         d.setBackgroundColor(Color.fromARGB(120, 10, 10, 25));
         d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.8F, 1.8F, 1.8F), new AxisAngle4f()));
         d.setPersistent(false);
         d.getPersistentDataContainer().set(this.holoKey, PersistentDataType.BYTE, (byte)1);
      });
      this.portalTitles.put(portal, display);
   }

   private String portalText(Zone zone) {
      String name = zone == Zone.SKYHUB || zone == Zone.SKYBLOCK ? "Skyblock" : zone.shortLabel();
      String what = switch (zone) {
         case SURVIE -> "La survie du serveur";
         case PARKOUR -> "Le parkour du mois : " + this.pl.parkour().mapName();
         case PARCELLES -> "Ta parcelle en créatif";
         case SKYHUB, SKYBLOCK -> "Ton île dans le ciel";
         default -> "";
      };

      if (!this.pl.worlds().available(zone)) {
         return "<dark_gray><bold>" + name + "</bold></dark_gray>\n<#FFD25E>Bientôt disponible</#FFD25E>";
      }

      return zone.color + "<bold>" + name + "</bold>\n<gray>" + what + "</gray>\n<white>" + this.pl.worlds().count(zone) + "</white> <gray>joueur(s)</gray>";
   }

   // ------------------------------------------------------- vie du lobby

   private long ticks;

   /**
    * Toutes les trois ticks : une colonne de particules arc-en-ciel tourne au
    * centre de chaque portail, pour qu'on les repere de loin. Seulement quand
    * quelqu'un est assez pres pour la voir.
    */
   public void particles() {
      this.ticks += 3;

      for (Hub.Portal portal : this.portals) {
         Location at = portal.at();
         World w = at.getWorld();

         if (w == null || w.getPlayers().isEmpty()) {
            continue;
         }

         boolean seen = false;

         for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) < 2304.0) {
               seen = true;
               break;
            }
         }

         if (!seen) {
            continue;
         }

         double radius = Math.max(0.6, portal.radius() * 0.55);

         for (int i = 0; i < 10; i++) {
            double height = (i * 0.45 + this.ticks * 0.06) % 4.5;
            double angle = this.ticks * 0.25 + i * 0.63;
            float hue = (float)((this.ticks * 0.01 + i / 10.0) % 1.0);
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F);
            org.bukkit.Color color = org.bukkit.Color.fromRGB(rgb & 0xFFFFFF);
            Location point = at.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            w.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, new org.bukkit.Particle.DustOptions(color, 1.3F));
         }
      }
   }

   /** Toutes les cinq secondes : le nombre de joueurs sous chaque titre de portail. */
   public void refreshTitles() {
      for (java.util.Map.Entry<Hub.Portal, TextDisplay> entry : this.portalTitles.entrySet()) {
         if (entry.getValue().isValid()) {
            entry.getValue().text(Msg.mm(this.portalText(entry.getKey().zone())));
         }
      }
   }

   // ------------------------------------------------------------ musique

   /** Disques doux de Minecraft, et leur duree en secondes. */
   private static final String[][] PLAYLIST = new String[][]{
      {"music_disc.creator_music_box", "73"},
      {"music_disc.cat", "185"},
      {"music_disc.far", "174"},
      {"music_disc.wait", "238"},
      {"music_disc.strad", "188"},
      {"music_disc.relic", "218"}
   };
   private final java.util.Map<java.util.UUID, long[]> music = new java.util.concurrent.ConcurrentHashMap<>();
   private final java.util.Set<java.util.UUID> musicOff = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /**
    * Chaque seconde : un fond musical doux dans le lobby, un disque apres
    * l'autre, comme un juke-box. Le son suit le joueur. On le coupe en
    * quittant le lobby, ou pour de bon avec /musique.
    */
   public void musicTick() {
      long now = System.currentTimeMillis();

      for (Player p : Bukkit.getOnlinePlayers()) {
         java.util.UUID id = p.getUniqueId();
         boolean here = this.pl.worlds().zoneOf(p) == Zone.HUB && !this.musicOff.contains(id);
         long[] state = this.music.get(id);

         if (!here) {
            if (state != null) {
               this.music.remove(id);
               this.stopMusic(p);
            }

            continue;
         }

         if (state != null && now < state[1]) {
            continue;
         }

         int index = state == null ? java.util.concurrent.ThreadLocalRandom.current().nextInt(PLAYLIST.length) : (int)((state[0] + 1) % PLAYLIST.length);
         String[] track = PLAYLIST[index];

         try {
            p.playSound(
               net.kyori.adventure.sound.Sound.sound(
                  net.kyori.adventure.key.Key.key(track[0]), net.kyori.adventure.sound.Sound.Source.RECORD, 0.45F, 1.0F
               ),
               net.kyori.adventure.sound.Sound.Emitter.self()
            );
         } catch (Throwable t) {
         }

         // Quelques secondes de silence entre deux morceaux.
         this.music.put(id, new long[]{index, now + (Long.parseLong(track[1]) + 6L) * 1000L});
      }
   }

   private void stopMusic(Player p) {
      try {
         p.stopSound(net.kyori.adventure.sound.SoundStop.source(net.kyori.adventure.sound.Sound.Source.RECORD));
      } catch (Throwable t) {
      }
   }

   /** {@code /musique} : couper ou remettre la musique du lobby. */
   public void toggleMusic(Player p) {
      if (this.musicOff.remove(p.getUniqueId())) {
         Msg.ok(p, "Musique du lobby remise.");
      } else {
         this.musicOff.add(p.getUniqueId());
         this.music.remove(p.getUniqueId());
         this.stopMusic(p);
         Msg.ok(p, "Musique du lobby coupée. <gray>(/musique pour la remettre)</gray>");
      }
   }

   private void hologram(Location at, float scale, String text) {
      World w = at.getWorld();
      if (w == null) {
         return;
      }

      w.spawn(at, TextDisplay.class, display -> {
         display.text(Msg.mm(text));
         display.setBillboard(Billboard.VERTICAL);
         display.setAlignment(TextAlignment.CENTER);
         display.setLineWidth(420);
         display.setShadowed(true);
         display.setBackgroundColor(Color.fromARGB(110, 10, 10, 25));
         display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
         display.setPersistent(false);
         display.getPersistentDataContainer().set(this.holoKey, PersistentDataType.BYTE, (byte)1);
      });
   }

   // ------------------------------------------------------------- le menu

   /** Le menu ouvert par la boussole. */
   public static final class Chooser extends Menu {
      private static final int[] SLOTS = new int[]{10, 12, 14, 16};
      private final BDEIMT pl;
      private final List<Zone> shown = new ArrayList<>();

      public Chooser(BDEIMT pl) {
         super(3, Msg.mm("<dark_gray>» <#4FC3FF>Modes de jeu"));
         this.pl = pl;
         this.build();
      }

      private void build() {
         Zone[] order = new Zone[]{Zone.SURVIE, Zone.PARKOUR, Zone.SKYHUB, Zone.PARCELLES};
         // Le nom de la carte du mois s'affiche sur l'icone du parkour.

         for (int i = 0; i < order.length && i < SLOTS.length; i++) {
            Zone zone = order[i];
            this.shown.add(zone);
            boolean open = this.pl.worlds().available(zone);
            int here = this.pl.worlds().count(zone);
            this.inv.setItem(SLOTS[i], open ? this.open(zone, here) : this.soon(zone));
         }

         this.inv.setItem(
            22,
            Util.item(
               Material.WRITTEN_BOOK,
               1,
               "<#FFD25E><bold>Le guide du serveur</bold></#FFD25E>",
               "<gray>Toutes les commandes, les regles</gray>",
               "<gray>et quelques secrets bien caches.</gray>",
               " ",
               "<dark_gray>Clic pour lire</dark_gray>"
            )
         );
         this.fill();
      }

      /** Le nom affiche dans le menu : « Skyblock » plutot que « Hub Skyblock ». */
      private static String title(Zone zone) {
         return zone == Zone.SKYHUB ? "Skyblock" : zone.shortLabel();
      }

      private ItemStack open(Zone zone, int here) {
         return Util.item(
            icon(zone),
            1,
            zone.color + "<bold>" + title(zone) + "</bold>",
            "<gray>" + this.describe(zone) + "</gray>",
            " ",
            "<#55FF88>" + here + "</#55FF88> <gray>joueur(s) sur place</gray>",
            "<dark_gray>Clic pour y aller</dark_gray>"
         );
      }

      private ItemStack soon(Zone zone) {
         return Util.item(
            Material.GRAY_DYE,
            1,
            "<dark_gray><bold>" + title(zone) + "</bold></dark_gray>",
            "<gray>" + this.describe(zone) + "</gray>",
            " ",
            "<#FFD25E>Bientot disponible !</#FFD25E>"
         );
      }

      private static Material icon(Zone zone) {
         return switch (zone) {
            case SURVIE -> Material.GRASS_BLOCK;
            case PARKOUR -> Material.FEATHER;
            case SKYHUB, SKYBLOCK -> Material.OAK_SAPLING;
            case PARCELLES -> Material.BRICKS;
            default -> Material.COMPASS;
         };
      }

      private String describe(Zone zone) {
         return switch (zone) {
            case SURVIE -> "La survie du serveur, avec tout ce qu'on y a construit.";
            case PARKOUR -> "Le parkour du mois : " + this.pl.parkour().mapName() + ".";
            case SKYHUB, SKYBLOCK -> "Ton ile, tes fermes, le marche commun.";
            case PARCELLES -> "Une parcelle en creatif, rien que pour toi.";
            default -> "";
         };
      }

      @Override
      public void click(Player p, int slot) {
         if (slot == 22) {
            p.closeInventory();
            this.pl.announcer().guide(p);
            return;
         }

         for (int i = 0; i < SLOTS.length && i < this.shown.size(); i++) {
            if (slot == SLOTS[i]) {
               Zone zone = this.shown.get(i);
               if (!this.pl.worlds().available(zone)) {
                  Msg.info(p, "Ce mode de jeu ouvrira bientot, patience !");
                  Util.sound(p, "block.note_block.bass", 0.6F, 0.8F);
                  return;
               }

               p.closeInventory();
               this.pl.worlds().send(p, zone);
               return;
            }
         }
      }
   }
}
