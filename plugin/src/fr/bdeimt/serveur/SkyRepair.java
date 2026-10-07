package fr.bdeimt.serveur;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * La reparation du monde du skyblock.
 *
 * <p>Quand le monde a ete charge sans le generateur vide (par Multiverse, un
 * jour ou le skyblock etait ferme), les chunks neufs se sont remplis de terrain
 * normal : de l'herbe, de la pierre, de la mer, jusqu'a la bedrock tout en bas.
 * Un chunk du vrai skyblock n'a jamais de bedrock au fond du monde : c'est ce
 * qui permet de reconnaitre les chunks abimes sans se tromper.
 *
 * <p>Ce qu'on retire :
 * <ul>
 *   <li>loin de toute ile (la ou personne ne peut construire) : tout le chunk ;
 *   <li>dans la zone d'une ile : seulement les blocs naturels (pierre, terre,
 *       eau, minerais...) situes sous l'ile. Ce qu'un joueur a pu construire au
 *       niveau de son ile ou au-dessus n'est jamais touche.
 * </ul>
 * Rien n'est fait sans {@code /imt skyblock nettoyer confirmer}.
 */
public final class SkyRepair {
   /** Sous cette hauteur, dans la zone d'une ile, la terre naturelle est retiree. */
   private static final int BELOW = Skyblock.ISLAND_Y - 6;

   private static final Set<Material> NATURAL = EnumSet.noneOf(Material.class);

   static {
      for (Material m : Material.values()) {
         if (!m.isBlock() || m.isLegacy()) {
            continue;
         }

         String n = m.name();

         if (n.endsWith("_ORE")
            || n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("BEDROCK") || n.equals("DIRT") || n.equals("GRASS_BLOCK")
            || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT") || n.equals("MUD") || n.equals("CLAY") || n.equals("SAND")
            || n.equals("SANDSTONE") || n.equals("RED_SAND") || n.equals("GRAVEL") || n.equals("GRANITE") || n.equals("DIORITE")
            || n.equals("ANDESITE") || n.equals("TUFF") || n.equals("CALCITE") || n.equals("DRIPSTONE_BLOCK") || n.equals("POINTED_DRIPSTONE")
            || n.equals("WATER") || n.equals("LAVA") || n.equals("SEAGRASS") || n.equals("TALL_SEAGRASS") || n.equals("KELP")
            || n.equals("KELP_PLANT") || n.equals("MAGMA_BLOCK") || n.equals("SMOOTH_BASALT") || n.equals("GLOW_LICHEN")
            || n.equals("AMETHYST_BLOCK") || n.equals("BUDDING_AMETHYST") || n.equals("RAW_IRON_BLOCK") || n.equals("RAW_COPPER_BLOCK")
            || n.equals("MOSS_BLOCK") || n.equals("MOSS_CARPET") || n.equals("SCULK") || n.equals("SCULK_VEIN") || n.equals("SHORT_GRASS")
            || n.equals("TALL_GRASS") || n.equals("SEA_PICKLE") || n.equals("BUBBLE_COLUMN")) {
            NATURAL.add(m);
         }
      }
   }

   private final BDEIMT pl;
   private boolean running;

   public SkyRepair(BDEIMT pl) {
      this.pl = pl;
   }

   /** {@code /imt skyblock ...} */
   public void command(CommandSender sender, String[] args) {
      String sub = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "diag";

      switch (sub) {
         case "nettoyer" -> this.clean(sender, args.length >= 3 && args[2].equalsIgnoreCase("confirmer"));
         case "ile" -> this.island(sender, args);
         default -> this.diag(sender);
      }
   }

   // --------------------------------------------------------------- diagnostic

   private void diag(CommandSender sender) {
      Skyblock sky = this.pl.skyblock();
      World w = this.pl.worlds().world(Zone.SKYBLOCK);
      Msg.raw(sender, "<#7FE3FF><bold>Skyblock — état</bold></#7FE3FF>");
      Msg.raw(sender, " <gray>Ouvert : <white>" + (this.pl.worlds().enabled(Zone.SKYBLOCK) ? "oui" : "non") + "</white> · monde chargé : <white>"
         + (w == null ? "non" : "oui") + "</white> · îles lues : <white>" + (sky.isLoaded() ? sky.all().size() : 0) + "</white></gray>");

      if (w != null) {
         boolean good = w.getGenerator() instanceof Skyblock.VoidGenerator;
         Msg.raw(sender, " <gray>Générateur : " + (good ? "<#55FF88>vide (bon)</#55FF88>" : "<#FF5555>PAS le générateur vide</#FF5555> <white>— /stop pour corriger</white>") + "</gray>");
      }

      Map<UUID, Integer> owned = new HashMap<>();

      for (Skyblock.Island island : sky.all()) {
         owned.merge(island.owner, 1, Integer::sum);
      }

      List<Skyblock.Island> sorted = new ArrayList<>(sky.all());
      sorted.sort((a, b) -> a.ownerName.compareToIgnoreCase(b.ownerName));

      for (Skyblock.Island island : sorted) {
         StringBuilder line = new StringBuilder(" <white>" + island.ownerName + "</white> <dark_gray>case " + island.key()
            + " (x " + island.x * Skyblock.SPACING + ", z " + island.z * Skyblock.SPACING + ")</dark_gray>");

         if (!island.members.isEmpty()) {
            List<String> names = new ArrayList<>();

            for (UUID member : island.members) {
               PlayerData data = this.pl.data().get(member);
               names.add(data == null ? member.toString().substring(0, 8) : data.name);
            }

            line.append(" <gray>+ ").append(String.join(", ", names)).append("</gray>");
         }

         if (owned.get(island.owner) > 1) {
            line.append(" <#FF5555>(a plusieurs îles !)</#FF5555>");
         }

         Msg.raw(sender, line.toString());
      }

      Msg.raw(sender, " <gray>Chercher le terrain parasite : <white>/imt skyblock nettoyer</white> · aller sur une île : <white>/imt skyblock ile ‹pseudo›</white></gray>");
   }

   private void island(CommandSender sender, String[] args) {
      if (!(sender instanceof Player p) || args.length < 3) {
         Msg.err(sender, "/imt skyblock ile ‹pseudo›");
         return;
      }

      for (Skyblock.Island island : this.pl.skyblock().all()) {
         if (island.ownerName.equalsIgnoreCase(args[2])) {
            org.bukkit.Location home = this.pl.skyblock().home(island);

            if (home == null) {
               Msg.err(p, "Le monde du skyblock n'est pas chargé.");
               return;
            }

            if (this.pl.worlds().zoneOf(p) != Zone.SKYBLOCK && !this.pl.worlds().send(p, Zone.SKYBLOCK)) {
               return;
            }

            p.teleport(home);
            Msg.ok(p, "Île de <white>" + island.ownerName + "</white> (case " + island.key() + ").");
            return;
         }
      }

      Msg.err(sender, "Aucune île à ce nom.");
   }

   // --------------------------------------------------------------- nettoyage

   private record ChunkPos(int x, int z) {}

   private void clean(CommandSender sender, boolean confirmed) {
      World w = this.pl.worlds().world(Zone.SKYBLOCK);

      if (w == null || !this.pl.skyblock().isLoaded()) {
         Msg.err(sender, "Le skyblock doit être ouvert et chargé (/imt mode skyblock on, puis /stop).");
         return;
      }

      if (!(w.getGenerator() instanceof Skyblock.VoidGenerator)) {
         Msg.err(sender, "Le monde n'a pas encore le générateur vide : fais d'abord /stop, sinon le terrain reviendrait.");
         return;
      }

      if (this.running) {
         Msg.err(sender, "Un nettoyage est déjà en cours.");
         return;
      }

      List<ChunkPos> chunks;

      try {
         chunks = this.existingChunks(w);
      } catch (IOException ex) {
         Msg.err(sender, "Lecture des fichiers du monde impossible : " + ex.getMessage());
         return;
      }

      Msg.info(sender, "Analyse de <white>" + chunks.size() + "</white> chunks du skyblock" + (confirmed ? " et nettoyage" : "") + "... <gray>(ça peut prendre une minute)</gray>");
      this.running = true;
      Deque<ChunkPos> queue = new ArrayDeque<>(chunks);
      int[] stats = new int[3]; // naturels loin des iles, naturels dans une zone d'ile, blocs retires

      Bukkit.getScheduler().runTaskTimer(this.pl, task -> {
         long until = System.nanoTime() + 20_000_000L;

         while (!queue.isEmpty() && System.nanoTime() < until) {
            ChunkPos pos = queue.poll();

            try {
               this.handle(w, pos, confirmed, stats);
            } catch (Throwable t) {
               this.pl.getLogger().warning("Chunk " + pos + " : " + t);
            }
         }

         if (queue.isEmpty()) {
            task.cancel();
            this.running = false;

            if (stats[0] + stats[1] == 0) {
               Msg.ok(sender, "Aucun terrain parasite : le monde du skyblock est propre.");
            } else if (confirmed) {
               Msg.ok(sender, "Nettoyage terminé : <white>" + stats[2] + "</white> blocs retirés dans <white>" + (stats[0] + stats[1]) + "</white> chunks.");
            } else {
               Msg.raw(sender, "<#FFD25E>Terrain parasite trouvé :</#FFD25E> <white>" + stats[0] + "</white> <gray>chunks loin des îles (effacés entièrement),</gray> <white>"
                  + stats[1] + "</white> <gray>chunks dans la zone d'une île (seule la terre naturelle SOUS l'île est retirée).</gray>");
               Msg.raw(sender, "<gray>Rien n'a été touché. Pour nettoyer : <white>/imt skyblock nettoyer confirmer</white></gray>");
            }
         }
      }, 1L, 1L);
   }

   private void handle(World w, ChunkPos pos, boolean confirmed, int[] stats) {
      Chunk chunk = w.getChunkAt(pos.x(), pos.z());
      ChunkSnapshot snap = chunk.getChunkSnapshot(true, false, false);
      int min = w.getMinHeight();

      if (snap.getBlockType(0, min, 0) != Material.BEDROCK
         && snap.getBlockType(8, min, 8) != Material.BEDROCK
         && snap.getBlockType(15, min, 15) != Material.BEDROCK) {
         return;
      }

      boolean nearIsland = this.nearIsland(pos.x() * 16 + 8, pos.z() * 16 + 8);
      stats[nearIsland ? 1 : 0]++;

      if (!confirmed) {
         return;
      }

      int max = w.getMaxHeight();

      for (int x = 0; x < 16; x++) {
         for (int z = 0; z < 16; z++) {
            int top = Math.min(max - 1, snap.getHighestBlockYAt(x, z) + 1);

            for (int y = top; y >= min; y--) {
               Material type = snap.getBlockType(x, y, z);

               if (type.isAir()) {
                  continue;
               }

               if (nearIsland && (y >= BELOW || !NATURAL.contains(type))) {
                  continue;
               }

               chunk.getBlock(x, y, z).setType(Material.AIR, false);
               stats[2]++;
            }
         }
      }

      if (!nearIsland) {
         for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
               entity.remove();
            }
         }
      }
   }

   /** Ce point est-il dans la zone d'une ile (ou tout pres) ? */
   private boolean nearIsland(int x, int z) {
      for (Skyblock.Island island : this.pl.skyblock().all()) {
         int dx = Math.abs(x - island.x * Skyblock.SPACING);
         int dz = Math.abs(z - island.z * Skyblock.SPACING);

         if (dx <= Skyblock.RADIUS + 24 && dz <= Skyblock.RADIUS + 24) {
            return true;
         }
      }

      return false;
   }

   /** Les chunks qui existent sur le disque, lus dans l'en-tete des fichiers .mca. */
   private List<ChunkPos> existingChunks(World w) throws IOException {
      List<ChunkPos> out = new ArrayList<>();
      Path region = w.getWorldPath().resolve("region");

      if (!Files.isDirectory(region)) {
         return out;
      }

      List<Path> files;

      try (Stream<Path> list = Files.list(region)) {
         files = list.filter(p -> p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).toList();
      }

      for (Path file : files) {
         String[] parts = file.getFileName().toString().split("\\.");
         int rx = Integer.parseInt(parts[1]);
         int rz = Integer.parseInt(parts[2]);

         try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            if (raf.length() < 4096) {
               continue;
            }

            byte[] header = new byte[4096];
            raf.readFully(header);

            for (int i = 0; i < 1024; i++) {
               int offset = (header[i * 4] & 0xFF) << 16 | (header[i * 4 + 1] & 0xFF) << 8 | header[i * 4 + 2] & 0xFF;

               if (offset != 0) {
                  out.add(new ChunkPos(rx * 32 + (i & 31), rz * 32 + (i >> 5)));
               }
            }
         }
      }

      return out;
   }
}
