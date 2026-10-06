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
      ItemStack item = Util.item(
         Material.COMPASS,
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
         && item.getType() == Material.COMPASS
         && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(this.compassKey, PersistentDataType.BYTE);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onUse(PlayerInteractEvent e) {
      if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
         if (this.isCompass(e.getItem()) && this.pl.auth().isLogged(e.getPlayer())) {
            e.setCancelled(true);
            new Hub.Chooser(this.pl).open(e.getPlayer());
            Util.sound(e.getPlayer(), "ui.button.click", 0.5F, 1.6F);
         }
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
      this.portals.add(new Hub.Portal(zone, at, radius));
      List<String> lines = new ArrayList<>(this.pl.getConfig().getStringList("portails"));
      lines.add(zone.name() + "@" + Util.loc(at) + "|" + radius);
      this.pl.getConfig().set("portails", lines);
      this.pl.saveConfig();
   }

   public int clearPortals(World world) {
      int before = this.portals.size();
      this.portals.removeIf(portal -> portal.at().getWorld() != null && portal.at().getWorld().equals(world));
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

   /** Pose ou replace un hologramme du hub (panneaux d'accueil). */
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

      Location spawn = this.pl.worlds().spawnOf(Zone.HUB, null);
      if (spawn == null) {
         return;
      }

      this.hologram(
         spawn.clone().add(0.0, 3.2, 0.0),
         1.5F,
         "<gradient:#4FC3FF:#B66BFF:#FF5FAE><bold>✦ Lobby du BDE de l'IMT ✦</bold></gradient>\n"
            + "<#E8E8E8>Clic droit sur la <#4FC3FF>boussole</#4FC3FF> pour choisir ton mode de jeu.</#E8E8E8>"
      );
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
