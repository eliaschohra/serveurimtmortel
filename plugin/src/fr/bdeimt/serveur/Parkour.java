package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Le parkour du mois.
 *
 * <p>Une carte commune a tout le monde, sans stuff, sans casse et sans coups.
 * On avance de point de controle en point de controle ; si on tombe, on
 * repart du dernier atteint.
 *
 * <p>Le classement s'affiche en permanence sur le cote de l'ecran, et sur un
 * hologramme pose au depart : qui est alle le plus loin, et en combien de
 * temps pour ceux qui ont termine.
 *
 * <p>Les points de controle se posent en jeu avec {@code /parkour point},
 * dans l'ordre du parcours : le premier est le depart, le dernier l'arrivee.
 */
public final class Parkour implements Listener, CommandExecutor, TabCompleter {
   /** La performance d'un joueur sur la carte en cours. */
   public static final class Record {
      public final UUID uuid;
      public volatile String name;
      /** Nombre de points de controle atteints. */
      public volatile int reached;
      /** Meilleur temps complet en millisecondes, 0 si jamais termine. */
      public volatile long best;

      Record(UUID uuid, String name) {
         this.uuid = uuid;
         this.name = name;
      }
   }

   /** Une tentative en cours. */
   private static final class Run {
      int reached;
      long started;
      Location checkpoint;
   }

   private final BDEIMT pl;
   private final File file;
   private final NamespacedKey holoKey;
   private final List<String> pointNames = new ArrayList<>();
   private final List<Location> points = new ArrayList<>();
   private final Map<UUID, Parkour.Record> records = new ConcurrentHashMap<>();
   private final Map<UUID, Parkour.Run> runs = new ConcurrentHashMap<>();
   /** Les parties mises en pause (on a quitte le parkour ou le serveur) : point atteint, temps ecoule. */
   private final Map<UUID, long[]> paused = new ConcurrentHashMap<>();
   private volatile String mapName = "Warden Parkour";
   private volatile double floor = -64.0;
   /** De combien on peut descendre sous son dernier point avant d'y etre ramene. */
   private volatile double drop = 8.0;

   /**
    * Les plaques de pression en or du Warden Parkour, relevees dans les
    * fichiers de la carte : ce sont ses points de controle d'origine. Le
    * parcours est une tour qui monte, l'ordre du parcours est donc celui des
    * hauteurs — de la plaque du depart (y = 43) a celle du sommet (y = 231).
    */
   private static final int[][] WARDEN_PLATES = new int[][]{
      {0, 43, -1}, {-36, 50, 18}, {-38, 68, 20}, {-36, 87, 18}, {-36, 105, 18}, {-18, 120, 26},
      {-5, 135, -13}, {-3, 151, -4}, {0, 167, -24}, {-30, 197, 15}, {-21, 231, 1}
   };

   public Parkour(BDEIMT pl) {
      this.pl = pl;
      this.file = new File(pl.getDataFolder(), "parkour.yml");
      this.holoKey = new NamespacedKey(pl, "parkour_holo");
      this.stickKey = new NamespacedKey(pl, "parkour_baton");
   }

   // ----------------------------------------------------------------- baton

   /** La case du baton de retour, au milieu de la barre. */
   private static final int STICK_SLOT = 4;
   private final NamespacedKey stickKey;
   private final Map<UUID, Long> stickUse = new ConcurrentHashMap<>();

   /** Le baton qui ramene a la derniere plaque en or. */
   private org.bukkit.inventory.ItemStack stick() {
      org.bukkit.inventory.ItemStack item = Util.item(
         org.bukkit.Material.BLAZE_ROD,
         1,
         "<#FFD25E><bold>Retour au point de contrôle</bold></#FFD25E>",
         "<gray>Clic (droit ou gauche) : retour à ta</gray>",
         "<gray>dernière plaque en or.</gray>",
         "<dark_gray>Tu le gardes tant que tu es sur le parkour.</dark_gray>"
      );
      item.editMeta(meta -> meta.getPersistentDataContainer().set(this.stickKey, PersistentDataType.BYTE, (byte)1));
      return item;
   }

   private boolean isStick(org.bukkit.inventory.ItemStack item) {
      return item != null
         && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(this.stickKey, PersistentDataType.BYTE);
   }

   /** Remet le baton s'il n'est plus dans l'inventaire. */
   private void ensureStick(Player p) {
      for (org.bukkit.inventory.ItemStack item : p.getInventory().getContents()) {
         if (this.isStick(item)) {
            return;
         }
      }

      p.getInventory().setItem(STICK_SLOT, this.stick());
   }

   /** Le baton disparait des qu'on quitte le parkour. */
   private void removeStick(Player p) {
      org.bukkit.inventory.ItemStack[] contents = p.getInventory().getContents();

      for (int i = 0; i < contents.length; i++) {
         if (this.isStick(contents[i])) {
            p.getInventory().setItem(i, null);
         }
      }
   }

   /** Retour a la derniere plaque en or. */
   public void backToCheckpoint(Player p) {
      Location back = this.checkpointOf(p);

      if (back != null) {
         p.setFallDistance(0.0F);
         p.setVelocity(new org.bukkit.util.Vector());
         p.teleport(back);
         Util.sound(p, "entity.enderman.teleport", 0.5F, 1.6F);
         p.sendActionBar(Msg.mm("<#FFD25E>Retour au dernier point de contrôle</#FFD25E>"));
      }
   }

   @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
   public void onStick(org.bukkit.event.player.PlayerInteractEvent e) {
      // Marcher sur une plaque (PHYSICAL) avec le baton en main ne compte pas.
      if (!this.isStick(e.getItem()) || e.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
         return;
      }

      e.setCancelled(true);
      Player p = e.getPlayer();

      if (this.pl.worlds().zoneOf(p) != Zone.PARKOUR) {
         return;
      }

      long now = System.currentTimeMillis();
      Long last = this.stickUse.get(p.getUniqueId());

      if (last != null && now - last < 800L) {
         return;
      }

      this.stickUse.put(p.getUniqueId(), now);
      this.backToCheckpoint(p);
   }

   /** Ni dans l'autre main, ni par terre. */
   @EventHandler(ignoreCancelled = true)
   public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
      if (this.isStick(e.getMainHandItem()) || this.isStick(e.getOffHandItem())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDropStick(org.bukkit.event.player.PlayerDropItemEvent e) {
      if (this.isStick(e.getItemDrop().getItemStack())) {
         e.setCancelled(true);
      }
   }

   public String mapName() {
      return this.mapName;
   }

   // ----------------------------------------------------------- chargement

   public void load() {
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
      this.mapName = yml.getString("nom", "Warden Parkour");
      this.floor = yml.getDouble("plancher", 10.0);
      this.drop = yml.getDouble("chute", 8.0);
      this.pointNames.clear();
      this.points.clear();

      for (String line : yml.getStringList("points")) {
         String[] parts = line.split("\\|", 2);
         Location at = Util.loc(parts.length > 1 ? parts[1] : null);
         if (at != null) {
            this.pointNames.add(parts[0]);
            this.points.add(at);
         }
      }

      // Aucun point pose a la main : on prend les plaques en or de la carte.
      World w = Bukkit.getWorld(Zone.PARKOUR.world);
      boolean importedPlates = false;

      if (this.points.isEmpty() && w != null) {
         for (int i = 0; i < WARDEN_PLATES.length; i++) {
            int[] at = WARDEN_PLATES[i];
            Location here = new Location(w, at[0] + 0.5, at[1], at[2] + 0.5);

            // On regarde vers la plaque suivante en arrivant.
            int[] next = WARDEN_PLATES[Math.min(i + 1, WARDEN_PLATES.length - 1)];
            if (next != at) {
               here.setDirection(new org.bukkit.util.Vector(next[0] - at[0], 0, next[2] - at[2]));
               here.setPitch(0.0F);
            }

            this.points.add(here);
            this.pointNames.add(i == 0 ? "depart" : i == WARDEN_PLATES.length - 1 ? "sommet" : "etape " + i);
         }

         this.pl.getLogger().info("Parkour : les " + WARDEN_PLATES.length + " plaques en or de la carte servent de points de controle.");
         importedPlates = true;
      }

      this.records.clear();
      ConfigurationSection root = yml.getConfigurationSection("records");
      if (root != null) {
         for (String key : root.getKeys(false)) {
            try {
               UUID uuid = UUID.fromString(key);
               Parkour.Record record = new Parkour.Record(uuid, root.getString(key + ".nom", "?"));
               record.reached = root.getInt(key + ".points");
               record.best = root.getLong(key + ".temps");
               this.records.put(uuid, record);
            } catch (IllegalArgumentException ex) {
            }
         }
      }

      this.paused.clear();
      ConfigurationSection progress = yml.getConfigurationSection("en-cours");
      if (progress != null) {
         for (String key : progress.getKeys(false)) {
            try {
               String[] parts = progress.getString(key, "").split(";");
               this.paused.put(UUID.fromString(key), new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])});
            } catch (RuntimeException ex) {
            }
         }
      }

      // Seulement maintenant : une sauvegarde plus tot aurait ecrit le fichier
      // sans les records ni les parties en cours.
      if (importedPlates) {
         this.save();
      }

      if (!this.points.isEmpty()) {
         this.pl.getLogger().info("Parkour « " + this.mapName + " » : " + this.points.size() + " point(s) de controle.");
      }
   }

   public void save() {
      YamlConfiguration yml = new YamlConfiguration();
      yml.set("nom", this.mapName);
      yml.set("plancher", this.floor);
      yml.set("chute", this.drop);
      List<String> lines = new ArrayList<>();

      for (int i = 0; i < this.points.size(); i++) {
         lines.add(this.pointNames.get(i) + "|" + Util.loc(this.points.get(i)));
      }

      yml.set("points", lines);

      // Les parties en cours de ceux qui sont partis : point atteint ; temps ecoule.
      for (Map.Entry<UUID, long[]> entry : this.paused.entrySet()) {
         yml.set("en-cours." + entry.getKey(), entry.getValue()[0] + ";" + entry.getValue()[1]);
      }

      for (Parkour.Record record : this.records.values()) {
         yml.set("records." + record.uuid + ".nom", record.name);
         yml.set("records." + record.uuid + ".points", record.reached);
         yml.set("records." + record.uuid + ".temps", record.best);
      }

      try {
         yml.save(this.file);
      } catch (IOException e) {
         this.pl.getLogger().warning("parkour.yml : " + e.getMessage());
      }
   }

   // ------------------------------------------------------------ classement

   /** Les meilleurs : d'abord le plus loin, puis le plus rapide. */
   public List<Parkour.Record> ranking() {
      List<Parkour.Record> all = new ArrayList<>(this.records.values());
      all.removeIf(record -> record.reached <= 0);
      all.sort(
         Comparator.<Parkour.Record>comparingInt(record -> -record.reached)
            .thenComparingLong(record -> record.best == 0L ? Long.MAX_VALUE : record.best)
            .thenComparing(record -> record.name.toLowerCase(Locale.ROOT))
      );
      return all;
   }

   private Parkour.Record record(Player p) {
      return this.records.computeIfAbsent(p.getUniqueId(), uuid -> new Parkour.Record(uuid, p.getName()));
   }

   // --------------------------------------------------------------- partie

   /**
    * En arrivant sur le parkour : on reprend la ou on s'etait arrete (le
    * dernier point de controle et le chrono sont gardes, meme apres une
    * deconnexion), sinon on part du debut.
    */
   public void start(Player p) {
      long[] saved = this.paused.remove(p.getUniqueId());

      if (saved != null && saved[0] > 0 && saved[0] < this.points.size()) {
         Parkour.Run run = new Parkour.Run();
         run.reached = (int)saved[0];
         run.started = System.currentTimeMillis() - Math.max(0L, saved[1]);
         run.checkpoint = this.points.get(run.reached - 1).clone();
         this.runs.put(p.getUniqueId(), run);
         p.teleport(run.checkpoint);
         p.setFallDistance(0.0F);
         this.ensureStick(p);
         p.getInventory().setHeldItemSlot(STICK_SLOT);
         Msg.poulpy(p, "Tu reprends à ton dernier point de contrôle (<white>" + run.reached + "</white> / " + this.points.size() + "), chrono compris. <gray>Repartir de zéro : /parkour recommencer</gray>");
         Util.sound(p, "block.note_block.pling", 0.8F, 1.6F);
         this.save();
         return;
      }

      this.restart(p);
   }

   /** Met la partie en pause : on la retrouvera en revenant. */
   private void pause(UUID uuid) {
      Parkour.Run run = this.runs.remove(uuid);

      if (run != null && run.reached > 0 && run.reached < this.points.size()) {
         this.paused.put(uuid, new long[]{run.reached, System.currentTimeMillis() - run.started});
         this.save();
      }
   }

   /** Remet le joueur au depart et relance son chrono. */
   public void restart(Player p) {
      this.paused.remove(p.getUniqueId());
      Parkour.Run run = new Parkour.Run();
      run.started = System.currentTimeMillis();
      run.reached = 0;
      run.checkpoint = this.points.isEmpty() ? null : this.points.get(0).clone();
      this.runs.put(p.getUniqueId(), run);

      if (run.checkpoint != null) {
         p.teleport(run.checkpoint);
      }

      p.setFallDistance(0.0F);
      this.ensureStick(p);
      p.getInventory().setHeldItemSlot(STICK_SLOT);
      Msg.poulpy(p, "C'est parti pour <white>" + this.mapName + "</white> ! Le chrono tourne.");
      Msg.poulpy(p, "Les <#FFD25E>plaques en or</#FFD25E> sont les points de contrôle. Tu tombes ? Retour à la dernière. Le <#FFD25E>bâton</#FFD25E> en main t'y ramène aussi d'un clic.");
      Util.sound(p, "block.note_block.pling", 0.8F, 1.6F);
   }

   /** Le point de reprise : le dernier point de controle atteint. */
   public Location checkpointOf(Player p) {
      Parkour.Run run = this.runs.get(p.getUniqueId());
      if (run != null && run.checkpoint != null) {
         return run.checkpoint.clone();
      }

      if (!this.points.isEmpty()) {
         return this.points.get(0).clone();
      }

      // Toujours un endroit ou revenir, meme sans aucun point de controle.
      return this.pl.worlds().spawnOf(Zone.PARKOUR, p);
   }

   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent e) {
      Player p = e.getPlayer();
      if (this.pl.worlds().zoneOf(p) != Zone.PARKOUR || !this.pl.auth().isLogged(p)) {
         return;
      }

      Location to = e.getTo();

      // Tombe : on remonte au dernier point de controle. Les degats etant
      // coupes dans le parkour, on ne meurt jamais : sans ce retour, on
      // atterrissait au pied de la tour sans que rien ne se passe.
      Location last = this.checkpointOf(p);

      if (to.getY() < this.floor || last != null && last.getWorld() == to.getWorld() && to.getY() < last.getY() - this.drop) {
         Location back = last;
         if (back != null) {
            p.setFallDistance(0.0F);
            p.teleport(back);
            Msg.err(p, "Raté ! Retour au dernier point de contrôle.");
            Util.sound(p, "entity.item.break", 0.6F, 0.8F);
         }

         return;
      }

      if (this.points.isEmpty()) {
         return;
      }

      Parkour.Run run = this.runs.computeIfAbsent(p.getUniqueId(), uuid -> {
         Parkour.Run fresh = new Parkour.Run();
         fresh.started = System.currentTimeMillis();
         fresh.checkpoint = this.points.get(0).clone();
         return fresh;
      });

      for (int i = run.reached; i < this.points.size(); i++) {
         Location point = this.points.get(i);
         if (point.getWorld() != null && point.getWorld().equals(to.getWorld()) && point.distanceSquared(to) <= 6.25) {
            this.reach(p, run, i);
            return;
         }
      }
   }

   private void reach(Player p, Parkour.Run run, int index) {
      if (index < run.reached) {
         return;
      }

      run.reached = index + 1;
      run.checkpoint = this.points.get(index).clone();
      Parkour.Record record = this.record(p);
      record.name = p.getName();
      boolean progress = run.reached > record.reached;
      if (progress) {
         record.reached = run.reached;
      }

      boolean finish = run.reached >= this.points.size();
      if (finish) {
         long time = System.currentTimeMillis() - run.started;
         boolean better = record.best == 0L || time < record.best;
         if (better) {
            record.best = time;
         }

         this.save();
         this.zoneBroadcast(
            "<#FFD25E>✦</#FFD25E> <white>" + p.getName() + "</white> <gray>termine </gray><white>" + this.mapName
               + "</white> <gray>en </gray><#55FF88>" + chrono(time) + "</#55FF88>" + (better ? " <gray>(record personnel)</gray>" : "")
         );
         Util.sound(p, "ui.toast.challenge_complete", 1.0F, 1.0F);
         run.started = System.currentTimeMillis();
         run.reached = 0;
         run.checkpoint = this.points.get(0).clone();
         this.refreshHologram();
         return;
      }

      if (progress) {
         this.save();
         this.refreshHologram();
      }

      Msg.ok(p, "Point de contrôle <white>" + run.reached + "</white> / " + this.points.size() + " <gray>— " + chrono(System.currentTimeMillis() - run.started) + "</gray>");
      Util.sound(p, "entity.experience_orb.pickup", 0.7F, 1.5F);
   }

   private void zoneBroadcast(String text) {
      Component line = Msg.mm(text);

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p) && this.pl.worlds().zoneOf(p) == Zone.PARKOUR) {
            p.sendMessage(line);
         }
      }
   }

   public static String chrono(long ms) {
      long total = Math.max(0L, ms) / 1000L;
      long min = total / 60L;
      long sec = total % 60L;
      return min + " min " + (sec < 10L ? "0" : "") + sec + " s";
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      this.pause(e.getPlayer().getUniqueId());
      this.stickUse.remove(e.getPlayer().getUniqueId());
   }

   // ------------------------------------------------------------- affichage

   /** Le panneau lateral, rafraichi chaque seconde pour les gens du parkour. */
   public void tick() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (!this.pl.auth().isLogged(p)) {
            continue;
         }

         if (this.pl.worlds().zoneOf(p) == Zone.PARKOUR) {
            try {
               this.ensureStick(p);
               this.sidebar(p);
            } catch (Throwable t) {
            }
         } else {
            // On ne repart pas avec le baton dans un autre monde, et la
            // partie attend qu'on revienne.
            this.removeStick(p);

            if (this.runs.containsKey(p.getUniqueId())) {
               this.pause(p.getUniqueId());
            }
         }
      }
   }

   private void sidebar(Player p) {
      Scoreboard board = p.getScoreboard();
      if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
         board = Bukkit.getScoreboardManager().getNewScoreboard();
         p.setScoreboard(board);
      }

      Objective obj = board.getObjective("parkour");
      if (obj == null) {
         obj = board.registerNewObjective("parkour", Criteria.DUMMY, Msg.mm("<gradient:#FFD25E:#FF8A3D><bold>" + this.mapName + "</bold></gradient>"));
         obj.setDisplaySlot(DisplaySlot.SIDEBAR);
      }

      for (String entry : new ArrayList<>(board.getEntries())) {
         if (entry.startsWith("~p")) {
            board.resetScores(entry);
         }
      }

      List<Parkour.Record> top = this.ranking();
      int line = 15;
      Parkour.Run run = this.runs.get(p.getUniqueId());
      int reached = run == null ? 0 : run.reached;
      this.line(board, obj, "~p0", line--, "<gray>Toi : <white>" + reached + "</white> / " + Math.max(1, this.points.size()) + "</gray>");
      this.line(board, obj, "~p1", line--, "<gray>Chrono : <white>" + (run == null ? "—" : chrono(System.currentTimeMillis() - run.started)) + "</white></gray>");
      this.line(board, obj, "~p2", line--, " ");
      this.line(board, obj, "~p3", line--, "<#FFD25E>Les plus loin</#FFD25E>");

      for (int i = 0; i < top.size() && i < 5; i++) {
         Parkour.Record record = top.get(i);
         String time = record.best == 0L ? "" : " <dark_gray>" + chrono(record.best) + "</dark_gray>";
         this.line(board, obj, "~p" + (4 + i), line--, "<gray>" + (i + 1) + ". <white>" + record.name + "</white> <#55FF88>" + record.reached + "</#55FF88>" + time);
      }
   }

   private void line(Scoreboard board, Objective obj, String entry, int order, String text) {
      Score score = obj.getScore(entry);
      score.setScore(order);

      try {
         score.customName(Msg.mm(text));
      } catch (Throwable t) {
      }
   }

   /** L'hologramme du classement, pose au depart du parcours. */
   public void refreshHologram() {
      World w = this.pl.worlds().world(Zone.PARKOUR);
      if (w == null) {
         return;
      }

      for (Entity entity : w.getEntities()) {
         if (entity.getPersistentDataContainer().has(this.holoKey, PersistentDataType.BYTE)) {
            entity.remove();
         }
      }

      Location at = this.points.isEmpty() ? this.pl.worlds().spawnOf(Zone.PARKOUR, null) : this.points.get(0).clone();
      if (at == null) {
         return;
      }

      StringBuilder text = new StringBuilder("<gradient:#FFD25E:#FF8A3D><bold>" + this.mapName + "</bold></gradient>\n<gray>Le parkour du mois</gray>\n ");
      List<Parkour.Record> top = this.ranking();

      if (top.isEmpty()) {
         text.append("\n<dark_gray>Personne n'est encore parti.</dark_gray>");
      } else {
         for (int i = 0; i < top.size() && i < 10; i++) {
            Parkour.Record record = top.get(i);
            text.append("\n<gray>")
               .append(i + 1)
               .append(". <white>")
               .append(record.name)
               .append("</white> <#55FF88>")
               .append(record.reached)
               .append("</#55FF88><gray>/")
               .append(Math.max(1, this.points.size()))
               .append("</gray>");

            if (record.best > 0L) {
               text.append(" <dark_gray>").append(chrono(record.best)).append("</dark_gray>");
            }
         }
      }

      Location holo = at.clone().add(0.0, 3.0, 0.0);
      String body = text.toString();
      w.spawn(holo, TextDisplay.class, display -> {
         display.text(Msg.mm(body));
         display.setBillboard(Billboard.VERTICAL);
         display.setAlignment(TextAlignment.CENTER);
         display.setLineWidth(400);
         display.setShadowed(true);
         display.setBackgroundColor(Color.fromARGB(120, 10, 10, 25));
         display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.1F, 1.1F, 1.1F), new AxisAngle4f()));
         display.setPersistent(false);
         display.getPersistentDataContainer().set(this.holoKey, PersistentDataType.BYTE, (byte)1);
      });
   }

   // -------------------------------------------------------------- commande

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;
      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      if (args.length >= 1 && this.pl.ranks().isAdmin(p)) {
         String sub = args[0].toLowerCase(Locale.ROOT);

         switch (sub) {
            case "point" -> {
               String name = args.length >= 2 ? args[1] : "point " + (this.points.size() + 1);
               this.pointNames.add(name);
               this.points.add(p.getLocation());
               this.save();
               this.refreshHologram();
               Msg.ok(p, "Point de contrôle <white>" + this.points.size() + "</white> posé ici (" + name + ").");
               return true;
            }
            case "annuler" -> {
               if (this.points.isEmpty()) {
                  Msg.err(p, "Aucun point à retirer.");
               } else {
                  this.points.remove(this.points.size() - 1);
                  this.pointNames.remove(this.pointNames.size() - 1);
                  this.save();
                  this.refreshHologram();
                  Msg.ok(p, "Dernier point retiré. Il en reste <white>" + this.points.size() + "</white>.");
               }

               return true;
            }
            case "nom" -> {
               if (args.length < 2) {
                  Msg.err(p, "/parkour nom ‹nom de la carte›");
               } else {
                  this.mapName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                  this.save();
                  this.refreshHologram();
                  Msg.ok(p, "Carte du mois : <white>" + this.mapName + "</white>.");
               }

               return true;
            }
            case "plancher" -> {
               this.floor = p.getLocation().getY() - 3.0;
               this.save();
               Msg.ok(p, "En dessous de <white>" + Math.round(this.floor) + "</white>, on repart du dernier point.");
               return true;
            }
            case "chute" -> {
               if (args.length >= 2) {
                  try {
                     this.drop = Math.max(2.0, Double.parseDouble(args[1]));
                     this.save();
                  } catch (NumberFormatException ex) {
                     Msg.err(p, "/parkour chute ‹blocs›");
                     return true;
                  }
               }

               Msg.ok(p, "On est ramené au dernier point quand on tombe de plus de <white>" + Math.round(this.drop) + "</white> blocs sous lui.");
               return true;
            }
            case "reset" -> {
               this.records.clear();
               this.paused.clear();
               this.save();
               this.refreshHologram();
               Msg.ok(p, "Classement du parkour remis à zéro.");
               return true;
            }
            default -> {
            }
         }
      }

      if (args.length >= 1 && (args[0].equalsIgnoreCase("retour") || args[0].equalsIgnoreCase("checkpoint"))) {
         if (this.pl.worlds().zoneOf(p) != Zone.PARKOUR) {
            Msg.err(p, "Il faut être sur le parkour.");
         } else {
            this.backToCheckpoint(p);
         }

         return true;
      }

      if (args.length >= 1 && (args[0].equalsIgnoreCase("recommencer") || args[0].equalsIgnoreCase("restart"))) {
         if (this.pl.worlds().zoneOf(p) != Zone.PARKOUR) {
            Msg.err(p, "Il faut être sur le parkour.");
         } else {
            this.restart(p);
         }

         return true;
      }

      Msg.raw(p, "<dark_gray>———— <#FFD25E>" + this.mapName + "</#FFD25E> ————</dark_gray>");
      Msg.raw(p, "<gray>Points de contrôle : <white>" + this.points.size() + "</white></gray>");
      List<Parkour.Record> top = this.ranking();

      if (top.isEmpty()) {
         Msg.raw(p, "<dark_gray>Personne n'est encore parti. À toi l'honneur.</dark_gray>");
      } else {
         for (int i = 0; i < top.size() && i < 10; i++) {
            Parkour.Record record = top.get(i);
            Msg.raw(
               p,
               " <gray>" + (i + 1) + ". <white>" + record.name + "</white> <#55FF88>" + record.reached + "</#55FF88><gray>/"
                  + Math.max(1, this.points.size()) + (record.best > 0L ? " <dark_gray>" + chrono(record.best) + "</dark_gray>" : "") + "</gray>"
            );
         }
      }

      Msg.raw(p, "<gray>/parkour recommencer <dark_gray>— repartir du début</dark_gray></gray>");
      Msg.raw(p, "<gray>/parkour retour <dark_gray>— revenir à ta dernière plaque en or (comme le bâton)</dark_gray></gray>");
      return true;
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();
      if (args.length == 1) {
         out.add("recommencer");
         out.add("retour");
         if (sender instanceof Player p && this.pl.ranks().isAdmin(p)) {
            out.addAll(List.of("point", "annuler", "nom", "plancher", "chute", "reset"));
         }

         String start = args[0].toLowerCase(Locale.ROOT);
         out.removeIf(s -> !s.startsWith(start));
      }

      return out;
   }
}
