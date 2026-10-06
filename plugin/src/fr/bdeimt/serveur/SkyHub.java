package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.TextDisplay.TextAlignment;
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
 * Le lobby du skyblock : la carte SkySpawn, deposee comme le lobby principal.
 *
 * <p>On y arrive, on y lit les regles sur un grand hologramme, on y trouve les
 * marchands et le marche. On n'y casse rien et on ne s'y bat pas.
 *
 * <p>Le panneau lateral du skyblock vit aussi ici : le solde du joueur, les
 * iles les plus aimees et le nombre d'annonces du marche.
 */
public final class SkyHub {
   private final BDEIMT pl;
   private final NamespacedKey key;

   public SkyHub(BDEIMT pl) {
      this.pl = pl;
      this.key = new NamespacedKey(pl, "skyhub");
   }

   // ----------------------------------------------------------- hologramme

   /** Le grand panneau des regles, devant le point d'arrivee. */
   public void decorate() {
      World w = this.pl.worlds().world(Zone.SKYHUB);

      if (w == null || !this.pl.worlds().available(Zone.SKYHUB)) {
         return;
      }

      for (Entity entity : w.getEntities()) {
         if (entity.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            entity.remove();
         }
      }

      Location spawn = this.pl.worlds().spawnOf(Zone.SKYHUB, null);

      if (spawn == null) {
         return;
      }

      Location configured = Util.loc(this.pl.getConfig().getString("mondes.skyhub.hologramme"));
      Location at;

      if (configured != null && configured.getWorld() == w) {
         at = configured;
      } else {
         double yaw = Math.toRadians(spawn.getYaw());
         at = spawn.clone().add(-Math.sin(yaw) * 8.0, 3.5, Math.cos(yaw) * 8.0);
      }

      String text = "<gradient:#7FE3FF:#4FC3FF:#B66BFF><bold>✦ SKYBLOCK ✦</bold></gradient>\n"
         + "<#E8E8E8>Une île, le vide, et tout à construire.</#E8E8E8>\n \n"
         + "<#55FF88>/ile creer</#55FF88> <gray>ta propre île — une seule par personne</gray>\n"
         + "<#55FF88>/ile</#55FF88> <gray>rentrer chez toi  ·  </gray><#55FF88>/ile hub</#55FF88> <gray>revenir ici</gray>\n"
         + "<#55FF88>/ile invite</#55FF88> <gray>‹joueur›  ·  </gray><#FF5555>rejoindre un ami efface ton île</#FF5555>\n \n"
         + "<#FFD25E>/solde</#FFD25E> <gray>ton argent  ·  </gray><#FFD25E>/payer</#FFD25E> <gray>‹joueur› ‹montant›</gray>\n"
         + "<#FFD25E>/marche</#FFD25E> <gray>acheter aux autres  ·  </gray><#FFD25E>/marche vendre</#FFD25E> <gray>‹prix›</gray>\n"
         + "<gray>Les marchands achètent tes récoltes et vendent ce qui manque.</gray>\n \n"
         + "<#B66BFF>/vote</#B66BFF> <gray>‹pseudo› une voix par jour pour une île</gray>\n"
         + "<#B66BFF>/tpa</#B66BFF> <gray>rejoindre un ami  ·  </gray><#B66BFF>/echange</#B66BFF> <gray>troquer en sécurité</gray>\n \n"
         + "<dark_gray>Pas de combat entre joueurs. Tomber dans le vide te ramène chez toi.</dark_gray>";

      w.spawn(at, TextDisplay.class, d -> {
         d.text(Msg.mm(text));
         d.setBillboard(Billboard.VERTICAL);
         d.setAlignment(TextAlignment.CENTER);
         d.setLineWidth(420);
         d.setShadowed(true);
         d.setBackgroundColor(Color.fromARGB(150, 8, 12, 28));
         d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.9F, 1.9F, 1.9F), new AxisAngle4f()));
         d.setPersistent(false);
         d.getPersistentDataContainer().set(this.key, PersistentDataType.BYTE, (byte)1);
      });
   }

   /** {@code /imt monde holo skyhub} : deplacer le panneau la ou l'on est. */
   public void moveHologram(Location at) {
      this.pl.getConfig().set("mondes.skyhub.hologramme", Util.loc(at.clone().add(0.0, 2.5, 0.0)));
      this.pl.saveConfig();
      this.decorate();
   }

   // ------------------------------------------------------- panneau lateral

   /** Chaque seconde, pour ceux qui sont dans le skyblock. */
   public void tick() {
      List<Skyblock.Island> top = this.pl.skyblock().ranking();

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p) && this.pl.worlds().zoneOf(p).group.equals(Zone.SKYBLOCK.group)) {
            try {
               this.sidebar(p, top);
            } catch (Throwable t) {
            }
         }
      }
   }

   private void sidebar(Player p, List<Skyblock.Island> top) {
      Scoreboard board = p.getScoreboard();

      if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
         board = Bukkit.getScoreboardManager().getNewScoreboard();
         p.setScoreboard(board);
      }

      Objective obj = board.getObjective("skyblock");

      if (obj == null) {
         obj = board.registerNewObjective("skyblock", Criteria.DUMMY, Msg.mm("<gradient:#7FE3FF:#B66BFF><bold>Skyblock</bold></gradient>"));
         obj.setDisplaySlot(DisplaySlot.SIDEBAR);
      }

      for (String entry : new ArrayList<>(board.getEntries())) {
         if (entry.startsWith("~s")) {
            board.resetScores(entry);
         }
      }

      int line = 15;
      line(obj, "~s0", line--, "<#FFD25E>Solde</#FFD25E> <white>" + Economy.format(this.pl.economy().balance(p)) + "</white>");
      line(obj, "~s1", line--, "<gray>Marché :</gray> <white>" + this.pl.market().size() + "</white> <gray>annonce(s)</gray>");
      line(obj, "~s2", line--, " ");
      line(obj, "~s3", line--, "<#B66BFF>Îles les plus aimées</#B66BFF>");
      int shown = 0;

      for (Skyblock.Island island : top) {
         if (shown >= 5 || island.votes <= 0) {
            break;
         }

         line(obj, "~s" + (4 + shown), line--, "<gray>" + (shown + 1) + ". <white>" + island.ownerName + "</white> <#B66BFF>" + island.votes + "</#B66BFF></gray>");
         shown++;
      }

      if (shown == 0) {
         line(obj, "~s4", line--, "<dark_gray>/vote ‹pseudo›</dark_gray>");
      }
   }

   private static void line(Objective obj, String entry, int order, String text) {
      Score score = obj.getScore(entry);
      score.setScore(order);

      try {
         score.customName(Msg.mm(text));
      } catch (Throwable t) {
      }
   }
}
