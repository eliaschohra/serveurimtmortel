package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Les parcelles en creatif.
 *
 * <p>Un monde plat de terre tres profonde, decoupe en parcelles carrees
 * separees par des chemins en bois borde de dalles de pierre, avec un
 * lampadaire a chaque croisement. La nuit y est expediee en quelques
 * secondes.
 *
 * <p>Chacun prend une parcelle avec {@code /parcelle creer}, y construit ce
 * qu'il veut, et peut inviter jusqu'a quatre autres joueurs. On circule
 * librement partout, mais on ne modifie que chez soi.
 *
 * <p>Une fois par jour, on peut voter pour la parcelle de quelqu'un ; le
 * classement s'affiche sur le cote de l'ecran.
 */
public final class Plots implements Listener, CommandExecutor, TabCompleter, Worlds.BuildRule {
   /** Cote d'une parcelle, en blocs. */
   public static final int SIZE = 32;
   /** Largeur du chemin entre deux parcelles. */
   public static final int ROAD = 8;
   private static final int PERIOD = SIZE + ROAD;
   /** Hauteur du sol : tout ce qui est en dessous est de la terre. */
   public static final int GROUND = 63;
   private static final int MAX_MEMBERS = 5;
   private static final long VOTE_DELAY = 86400000L;

   /** Une parcelle : ses coordonnees de grille, son proprietaire, ses invites. */
   public static final class Plot {
      public final int x;
      public final int z;
      public volatile UUID owner;
      public volatile String ownerName;
      public volatile int votes;
      public final Set<UUID> members = new LinkedHashSet<>();
      public final Set<UUID> banned = new LinkedHashSet<>();

      Plot(int x, int z, UUID owner, String ownerName) {
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
   private final Map<String, Plots.Plot> plots = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastVote = new ConcurrentHashMap<>();

   public Plots(BDEIMT pl) {
      this.pl = pl;
      this.file = new File(pl.getDataFolder(), "parcelles.yml");
   }

   // ----------------------------------------------------------------- monde

   /** Cree le monde des parcelles au demarrage s'il n'existe pas encore. */
   public void init() {
      if (!this.pl.worlds().enabled(Zone.PARCELLES)) {
         this.pl.getLogger().info("Parcelles fermees (modes.parcelles dans config.yml) : leur monde n'est pas charge.");
         return;
      }

      if (Bukkit.getWorld(Zone.PARCELLES.world) == null) {
         try {
            Bukkit.createWorld(
               new WorldCreator(Zone.PARCELLES.world)
                  .environment(Environment.NORMAL)
                  .generator(new Plots.Generator())
                  .generateStructures(false)
            );
         } catch (Throwable t) {
            this.pl.getLogger().warning("Monde des parcelles : " + t.getMessage());
            return;
         }
      }

      this.pl.worlds().tune(Zone.PARCELLES);
      this.pl.worlds().registerBuildRule(Zone.PARCELLES, this);
      this.pl.worlds().registerSpawn(Zone.PARCELLES, this::arrival);
      this.load();
   }

   /** Ou atterrit le joueur : sur sa parcelle s'il en a une, sinon a l'entree. */
   private Location arrival(Player p) {
      Plots.Plot mine = this.plotOf(p.getUniqueId());
      return mine != null ? this.center(mine) : null;
   }

   public Location center(Plots.Plot plot) {
      World w = this.pl.worlds().world(Zone.PARCELLES);
      if (w == null) {
         return null;
      }

      return new Location(w, plot.x * PERIOD + SIZE / 2.0, GROUND + 1.0, plot.z * PERIOD + SIZE / 2.0, 0.0F, 0.0F);
   }

   /** La parcelle qui contient ce point, ou null si on est sur un chemin. */
   public Plots.Plot at(Location l) {
      int localX = Math.floorMod(l.getBlockX(), PERIOD);
      int localZ = Math.floorMod(l.getBlockZ(), PERIOD);
      if (localX >= SIZE || localZ >= SIZE) {
         return null;
      }

      return this.plots.get(Math.floorDiv(l.getBlockX(), PERIOD) + "," + Math.floorDiv(l.getBlockZ(), PERIOD));
   }

   // ------------------------------------------------------------ chargement

   public void load() {
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection root = yml.getConfigurationSection("parcelles");
      this.plots.clear();

      if (root != null) {
         for (String key : root.getKeys(false)) {
            try {
               String[] parts = key.split(",");
               Plots.Plot plot = new Plots.Plot(
                  Integer.parseInt(parts[0]),
                  Integer.parseInt(parts[1]),
                  UUID.fromString(root.getString(key + ".proprietaire")),
                  root.getString(key + ".nom", "?")
               );
               plot.votes = root.getInt(key + ".votes");

               for (String uuid : root.getStringList(key + ".membres")) {
                  plot.members.add(UUID.fromString(uuid));
               }

               for (String uuid : root.getStringList(key + ".bannis")) {
                  plot.banned.add(UUID.fromString(uuid));
               }

               this.plots.put(plot.key(), plot);
            } catch (Exception ex) {
               this.pl.getLogger().warning("Parcelle illisible : " + key);
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

      this.pl.getLogger().info(this.plots.size() + " parcelle(s) chargee(s).");
   }

   public void save() {
      YamlConfiguration yml = new YamlConfiguration();

      for (Plots.Plot plot : this.plots.values()) {
         String path = "parcelles." + plot.key();
         yml.set(path + ".proprietaire", plot.owner.toString());
         yml.set(path + ".nom", plot.ownerName);
         yml.set(path + ".votes", plot.votes);
         yml.set(path + ".membres", toStrings(plot.members));
         yml.set(path + ".bannis", toStrings(plot.banned));
      }

      for (Map.Entry<UUID, Long> entry : this.lastVote.entrySet()) {
         yml.set("derniers-votes." + entry.getKey(), entry.getValue());
      }

      try {
         yml.save(this.file);
      } catch (IOException e) {
         this.pl.getLogger().warning("parcelles.yml : " + e.getMessage());
      }
   }

   private static List<String> toStrings(Set<UUID> uuids) {
      List<String> out = new ArrayList<>();

      for (UUID uuid : uuids) {
         out.add(uuid.toString());
      }

      return out;
   }

   // ------------------------------------------------------------ proprietes

   public Plots.Plot plotOf(UUID uuid) {
      for (Plots.Plot plot : this.plots.values()) {
         if (plot.owner.equals(uuid)) {
            return plot;
         }
      }

      return null;
   }

   /** On ne construit que chez soi, ou chez quelqu'un qui nous a invite. */
   @Override
   public boolean canBuild(Player p, Location at) {
      Plots.Plot plot = this.at(at);
      if (plot == null) {
         return false;
      }

      if (plot.banned.contains(p.getUniqueId())) {
         return false;
      }

      return plot.owner.equals(p.getUniqueId()) || plot.members.contains(p.getUniqueId());
   }

   // -------------------------------------------------------------- la nuit

   /** La nuit des parcelles dure quelques secondes : on la saute. */
   public void tick() {
      World w = this.pl.worlds().world(Zone.PARCELLES);
      if (w != null) {
         long time = w.getTime();
         if (time > 13000L && time < 23000L) {
            w.setTime(23000L);
         }
      }

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p) && this.pl.worlds().zoneOf(p) == Zone.PARCELLES) {
            try {
               this.sidebar(p);
            } catch (Throwable t) {
            }
         }
      }
   }

   // ----------------------------------------------------------- attribution

   /** Trouve la premiere place libre, en spirale depuis le centre. */
   private int[] freeSlot() {
      int x = 0;
      int z = 0;
      int dx = 0;
      int dz = -1;

      for (int i = 0; i < 40000; i++) {
         if (!this.plots.containsKey(x + "," + z)) {
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

      String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);

      switch (sub) {
         case "creer", "create", "créer", "parcel" -> this.create(p);
         case "tp", "aller", "home" -> this.goHome(p, args);
         case "invite", "inviter" -> this.invite(p, args);
         case "retirer", "kick" -> this.uninvite(p, args);
         case "ban", "bannir" -> this.ban(p, args, true);
         case "unban", "debannir" -> this.ban(p, args, false);
         case "supprimer", "delete" -> this.delete(p);
         case "vote", "voter" -> this.vote(p, args);
         case "top", "classement" -> this.top(p);
         case "info" -> this.info(p);
         default -> this.help(p);
      }

      return true;
   }

   private void help(Player p) {
      Msg.raw(p, "<dark_gray>———— <#C48BFF>Les parcelles</#C48BFF> ————</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle creer</#55FF88> <dark_gray>— prendre ta parcelle</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle tp</#55FF88> <gray>[joueur]</gray> <dark_gray>— aller chez toi ou chez quelqu'un</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle invite</#55FF88> <gray>‹joueur›</gray> <dark_gray>— l'autoriser a construire</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle retirer</#55FF88> <gray>‹joueur›</gray> <dark_gray>— lui retirer le droit</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle ban</#55FF88> <gray>‹joueur›</gray> <dark_gray>— le bannir de chez toi</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle vote</#55FF88> <gray>‹joueur›</gray> <dark_gray>— une voix par jour</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle top</#55FF88> <dark_gray>— les parcelles les plus aimees</dark_gray>");
      Msg.raw(p, " <#55FF88>/parcelle supprimer</#55FF88> <dark_gray>— rendre ta parcelle</dark_gray>");
   }

   private void create(Player p) {
      if (this.pl.worlds().zoneOf(p) != Zone.PARCELLES) {
         Msg.err(p, "Il faut être dans le monde des parcelles (boussole du lobby).");
         return;
      }

      if (this.plotOf(p.getUniqueId()) != null) {
         Msg.err(p, "Tu as déjà une parcelle. Une seule par personne !");
         Msg.info(p, "Tu peux la rendre avec <white>/parcelle supprimer</white> et en reprendre une.");
         return;
      }

      int[] slot = this.freeSlot();
      Plots.Plot plot = new Plots.Plot(slot[0], slot[1], p.getUniqueId(), p.getName());
      this.plots.put(plot.key(), plot);
      this.save();
      Location center = this.center(plot);

      if (center != null) {
         p.teleport(center);
      }

      Msg.ok(p, "Ta parcelle est à toi ! <gray>(" + SIZE + " × " + SIZE + " blocs, parcelle " + plot.key() + ")</gray>");
      Msg.info(p, "Tu peux inviter jusqu'à " + (MAX_MEMBERS - 1) + " personnes avec <white>/parcelle invite ‹joueur›</white>.");
      Util.sound(p, "entity.player.levelup", 0.7F, 1.4F);
   }

   private void goHome(Player p, String[] args) {
      if (this.pl.worlds().zoneOf(p) != Zone.PARCELLES) {
         this.pl.worlds().send(p, Zone.PARCELLES);
         return;
      }

      Plots.Plot target;

      if (args.length >= 2) {
         Player other = Bukkit.getPlayerExact(args[1]);
         UUID uuid = other != null ? other.getUniqueId() : this.byName(args[1]);
         target = uuid == null ? null : this.plotOf(uuid);

         if (target == null) {
            Msg.err(p, "Ce joueur n'a pas de parcelle.");
            return;
         }

         if (target.banned.contains(p.getUniqueId())) {
            Msg.err(p, "Tu es banni de cette parcelle.");
            return;
         }
      } else {
         target = this.plotOf(p.getUniqueId());

         if (target == null) {
            Msg.err(p, "Tu n'as pas encore de parcelle. <white>/parcelle creer</white>");
            return;
         }
      }

      Location center = this.center(target);
      if (center != null) {
         p.teleport(center);
         Msg.ok(p, "Te voilà sur la parcelle de <white>" + target.ownerName + "</white>.");
      }
   }

   private UUID byName(String name) {
      for (Plots.Plot plot : this.plots.values()) {
         if (plot.ownerName.equalsIgnoreCase(name)) {
            return plot.owner;
         }
      }

      return null;
   }

   private void invite(Player p, String[] args) {
      Plots.Plot plot = this.plotOf(p.getUniqueId());
      if (plot == null) {
         Msg.err(p, "Tu n'as pas de parcelle.");
         return;
      }

      if (args.length < 2) {
         Msg.err(p, "/parcelle invite ‹joueur›");
         return;
      }

      Player target = Bukkit.getPlayerExact(args[1]);
      if (target == null || !this.pl.auth().isLogged(target)) {
         Msg.err(p, "Ce joueur n'est pas connecté.");
         return;
      }

      if (plot.members.size() + 1 >= MAX_MEMBERS) {
         Msg.err(p, "Ta parcelle est pleine (" + MAX_MEMBERS + " personnes au total).");
         return;
      }

      plot.banned.remove(target.getUniqueId());
      plot.members.add(target.getUniqueId());
      this.save();
      Msg.ok(p, "<white>" + target.getName() + "</white> peut maintenant construire chez toi.");
      Msg.poulpy(target, "<white>" + p.getName() + "</white> t'invite à construire sur sa parcelle. <gray>(/parcelle tp " + p.getName() + ")</gray>");
   }

   private void uninvite(Player p, String[] args) {
      Plots.Plot plot = this.plotOf(p.getUniqueId());
      if (plot == null || args.length < 2) {
         Msg.err(p, "/parcelle retirer ‹joueur›");
         return;
      }

      UUID uuid = this.resolve(args[1]);
      if (uuid == null || !plot.members.remove(uuid)) {
         Msg.err(p, "Ce joueur n'est pas invité chez toi.");
         return;
      }

      this.save();
      Msg.ok(p, "<white>" + args[1] + "</white> ne peut plus construire chez toi.");
   }

   private void ban(Player p, String[] args, boolean banning) {
      Plots.Plot plot = this.plotOf(p.getUniqueId());
      if (plot == null) {
         Msg.err(p, "Tu n'as pas de parcelle.");
         return;
      }

      if (args.length < 2) {
         Msg.err(p, "/parcelle " + (banning ? "ban" : "unban") + " ‹joueur›");
         return;
      }

      UUID uuid = this.resolve(args[1]);
      if (uuid == null) {
         Msg.err(p, "Joueur inconnu.");
         return;
      }

      if (uuid.equals(p.getUniqueId())) {
         Msg.err(p, "Tu ne peux pas te bannir de chez toi.");
         return;
      }

      if (banning) {
         plot.members.remove(uuid);
         plot.banned.add(uuid);
         Player online = Bukkit.getPlayer(uuid);

         if (online != null && this.at(online.getLocation()) == plot) {
            Location spawn = this.pl.worlds().spawnOf(Zone.PARCELLES, online);
            if (spawn != null) {
               online.teleport(spawn);
            }

            Msg.err(online, "Tu as été banni de la parcelle de <white>" + p.getName() + "</white>.");
         }
      } else {
         plot.banned.remove(uuid);
      }

      this.save();
      Msg.ok(p, "<white>" + args[1] + "</white> est " + (banning ? "banni" : "de nouveau autorisé") + ".");
   }

   private UUID resolve(String name) {
      Player online = Bukkit.getPlayerExact(name);
      if (online != null) {
         return online.getUniqueId();
      }

      for (PlayerData data : this.pl.data().all()) {
         if (data.name != null && data.name.equalsIgnoreCase(name)) {
            return data.uuid;
         }
      }

      return null;
   }

   private void delete(Player p) {
      Plots.Plot plot = this.plotOf(p.getUniqueId());
      if (plot == null) {
         Msg.err(p, "Tu n'as pas de parcelle.");
         return;
      }

      this.plots.remove(plot.key());
      this.save();
      Msg.ok(p, "Parcelle rendue. Tu peux en reprendre une neuve avec <white>/parcelle creer</white>.");
      Msg.info(p, "<gray>Les constructions restent : pense à les casser avant si tu veux repartir de zéro.</gray>");
   }

   private void vote(Player p, String[] args) {
      if (args.length < 2) {
         Msg.err(p, "/parcelle vote ‹joueur›");
         return;
      }

      long now = System.currentTimeMillis();
      Long last = this.lastVote.get(p.getUniqueId());

      if (last != null && now - last < VOTE_DELAY) {
         Msg.err(p, "Un vote par jour. Reviens dans <white>" + Util.duration(VOTE_DELAY - (now - last)) + "</white>.");
         return;
      }

      UUID uuid = this.resolve(args[1]);
      Plots.Plot plot = uuid == null ? null : this.plotOf(uuid);

      if (plot == null) {
         Msg.err(p, "Ce joueur n'a pas de parcelle.");
         return;
      }

      if (plot.owner.equals(p.getUniqueId())) {
         Msg.err(p, "On ne vote pas pour soi-même.");
         return;
      }

      plot.votes++;
      this.lastVote.put(p.getUniqueId(), now);
      this.save();
      Msg.ok(p, "Ta voix du jour va à la parcelle de <white>" + plot.ownerName + "</white>.");
      Player owner = Bukkit.getPlayer(plot.owner);

      if (owner != null) {
         Msg.panthere(owner, "<white>" + p.getName() + "</white> a voté pour ta parcelle ! Tu en es à <#55FF88>" + plot.votes + "</#55FF88> voix.");
      }
   }

   public List<Plots.Plot> ranking() {
      List<Plots.Plot> all = new ArrayList<>(this.plots.values());
      all.sort(Comparator.<Plots.Plot>comparingInt(plot -> -plot.votes).thenComparing(plot -> plot.ownerName.toLowerCase(Locale.ROOT)));
      return all;
   }

   private void top(Player p) {
      List<Plots.Plot> top = this.ranking();
      Msg.raw(p, "<dark_gray>———— <#C48BFF>Les parcelles les plus aimées</#C48BFF> ————</dark_gray>");

      if (top.isEmpty()) {
         Msg.raw(p, "<dark_gray>Aucune parcelle pour l'instant.</dark_gray>");
         return;
      }

      for (int i = 0; i < top.size() && i < 10; i++) {
         Plots.Plot plot = top.get(i);
         Msg.raw(p, " <gray>" + (i + 1) + ". <white>" + plot.ownerName + "</white> <#C48BFF>" + plot.votes + "</#C48BFF> <dark_gray>voix</dark_gray></gray>");
      }
   }

   private void info(Player p) {
      Plots.Plot here = this.at(p.getLocation());

      if (this.pl.worlds().zoneOf(p) == Zone.PARCELLES && here != null) {
         Msg.raw(p, "<dark_gray>———— <#C48BFF>Parcelle de " + here.ownerName + "</#C48BFF> ————</dark_gray>");
         Msg.raw(p, "<gray>Voix : <white>" + here.votes + "</white>   ·   Invités : <white>" + here.members.size() + "</white> / " + (MAX_MEMBERS - 1) + "</gray>");
         Msg.raw(p, "<gray>Tu " + (this.canBuild(p, p.getLocation()) ? "<#55FF88>peux</#55FF88>" : "<#FF5555>ne peux pas</#FF5555>") + " <gray>construire ici.</gray>");
      }

      Plots.Plot mine = this.plotOf(p.getUniqueId());

      if (mine == null) {
         Msg.info(p, "Tu n'as pas encore de parcelle : <white>/parcelle creer</white>.");
      } else {
         Msg.info(p, "Ta parcelle : <white>" + mine.key() + "</white>, <white>" + mine.votes + "</white> voix. <gray>(/parcelle tp)</gray>");
      }

      this.help(p);
   }

   // ------------------------------------------------------------- affichage

   private void sidebar(Player p) {
      Scoreboard board = p.getScoreboard();

      if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
         board = Bukkit.getScoreboardManager().getNewScoreboard();
         p.setScoreboard(board);
      }

      Objective obj = board.getObjective("parcelles");

      if (obj == null) {
         obj = board.registerNewObjective("parcelles", Criteria.DUMMY, Msg.mm("<gradient:#C48BFF:#FF9AC8><bold>Les parcelles</bold></gradient>"));
         obj.setDisplaySlot(DisplaySlot.SIDEBAR);
      }

      for (String entry : new ArrayList<>(board.getEntries())) {
         if (entry.startsWith("~c")) {
            board.resetScores(entry);
         }
      }

      List<Plots.Plot> top = this.ranking();
      int line = 15;
      Plots.Plot mine = this.plotOf(p.getUniqueId());
      this.line(obj, "~c0", line--, "<gray>Ta parcelle : <white>" + (mine == null ? "aucune" : mine.key()) + "</white></gray>");
      this.line(obj, "~c1", line--, " ");
      this.line(obj, "~c2", line--, "<#C48BFF>Les plus aimées</#C48BFF>");

      for (int i = 0; i < top.size() && i < 8; i++) {
         Plots.Plot plot = top.get(i);
         this.line(obj, "~c" + (3 + i), line--, "<gray>" + (i + 1) + ". <white>" + plot.ownerName + "</white> <#C48BFF>" + plot.votes + "</#C48BFF></gray>");
      }
   }

   private void line(Objective obj, String entry, int order, String text) {
      Score score = obj.getScore(entry);
      score.setScore(order);

      try {
         score.customName(Msg.mm(text));
      } catch (Throwable t) {
      }
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();

      if (args.length == 1) {
         out.addAll(List.of("creer", "tp", "invite", "retirer", "ban", "unban", "vote", "top", "supprimer", "info"));
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

   // ----------------------------------------------------------- generation

   /**
    * Le terrain des parcelles : de la terre jusqu'au fond, des chemins en
    * planches bordes de dalles, et un lampadaire a chaque croisement.
    */
   public static final class Generator extends ChunkGenerator {
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
         // Au milieu du premier croisement, a l'entree du quartier.
         return new Location(world, SIZE + ROAD / 2.0, GROUND + 1.0, SIZE + ROAD / 2.0);
      }

      @Override
      public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
         return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
               return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
               return List.of(Biome.PLAINS);
            }
         };
      }

      @Override
      public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
         int bottom = data.getMinHeight();
         // En un seul appel plutot qu'en cent vingt-sept mille : le remplissage
         // par region est beaucoup plus rapide que bloc par bloc, et c'est ce
         // qui rend la generation du monde supportable quand on l'explore.
         data.setRegion(0, bottom, 0, 16, bottom + 1, 16, Material.BEDROCK);
         data.setRegion(0, bottom + 1, 0, 16, GROUND, 16, Material.DIRT);

         for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
               int worldX = chunkX * 16 + lx;
               int worldZ = chunkZ * 16 + lz;
               int inX = Math.floorMod(worldX, PERIOD);
               int inZ = Math.floorMod(worldZ, PERIOD);
               boolean roadX = inX >= SIZE;
               boolean roadZ = inZ >= SIZE;

               if (!roadX && !roadZ) {
                  // Dans la parcelle : de l'herbe par-dessus la terre.
                  data.setBlock(lx, GROUND, lz, Material.GRASS_BLOCK);
                  continue;
               }

               // Bordure : la premiere et la derniere case du chemin sont des
               // dalles, ce qui marque la limite de chaque parcelle.
               boolean edge = roadX && (inX == SIZE || inX == PERIOD - 1) || roadZ && (inZ == SIZE || inZ == PERIOD - 1);
               data.setBlock(lx, GROUND, lz, edge ? Material.SMOOTH_STONE_SLAB : Material.OAK_PLANKS);

               // Lampadaire au centre de chaque croisement.
               if (roadX && roadZ && inX == SIZE + ROAD / 2 && inZ == SIZE + ROAD / 2) {
                  data.setBlock(lx, GROUND, lz, Material.STONE_BRICKS);
                  data.setBlock(lx, GROUND + 1, lz, Material.OAK_FENCE);
                  data.setBlock(lx, GROUND + 2, lz, Material.OAK_FENCE);
                  data.setBlock(lx, GROUND + 3, lz, Material.LANTERN);
               }
            }
         }
      }
   }
}
