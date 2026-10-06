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

   /** Le grand panneau et la ligne d'astuces qui tourne en dessous. */
   private volatile TextDisplay board;
   private volatile TextDisplay tipLine;
   private int tipIndex;
   private int seconds;

   private static final String TEXT = "<gradient:#7FE3FF:#4FC3FF:#B66BFF><bold>✦ SKYBLOCK ✦</bold></gradient>\n \n"
      + "<#55FF88>/ile creer</#55FF88> <white>ta propre île (une par personne)</white>\n"
      + "<#55FF88>/ile</#55FF88> <white>rentrer chez toi</white>  <dark_gray>·</dark_gray>  <#55FF88>/ile hub</#55FF88> <white>revenir ici</white>\n"
      + "<#55FF88>/ile invite ‹pseudo›</#55FF88> <white>jouer à plusieurs</white>\n"
      + "<#FF6B6B><bold>⚠ Rejoindre une île EFFACE la tienne</bold></#FF6B6B>\n"
      + "<#55FF88>/ile tp ‹pseudo›</#55FF88> <white>visiter une île</white>\n \n"
      + "<#FFD25E>Les marchands d'ici achètent et vendent tout</#FFD25E>\n"
      + "<#FFD25E>/solde  /payer  /marche  /echange</#FFD25E>\n"
      + "<#C9A6FF>/vote ‹pseudo›</#C9A6FF> <white>une voix par jour pour une île</white>\n \n"
      + "<#FF6B6B>Le vide tue : stuff perdu, retour sur ton île</#FF6B6B>\n"
      + "<#55FF88>/ile aide</#55FF88> <white>toutes les commandes</white>";

   /**
    * La place du panneau, par rapport au point d'arrivee : derriere l'arbre
    * aux champignons, a gauche en arrivant, quelques blocs au-dessus des
    * tetes. L'endroit a ete choisi en lisant la carte SkySpawn bloc par
    * bloc : rien ne le traverse, et on le voit du point d'arrivee.
    */
   private static final double[] OFFSET = new double[]{-7.7, 6.0, -6.4};

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

      Location configured = Util.loc(this.pl.getConfig().getString("mondes.skyhub.panneau"));
      Location at;

      if (configured != null && configured.getWorld() == w) {
         at = configured;
      } else {
         at = spawn.clone().add(OFFSET[0], OFFSET[1], OFFSET[2]);
      }

      // Sans ce ticket, le chunk se decharge des que personne n'est a cote,
      // et le panneau (non sauvegarde) disparait avec lui : c'est pour ca
      // qu'on ne le voyait jamais.
      w.addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, this.pl);

      this.board = w.spawn(at, TextDisplay.class, d -> {
         d.text(Msg.mm(TEXT));
         d.setBillboard(Billboard.VERTICAL);
         d.setAlignment(TextAlignment.CENTER);
         d.setLineWidth(300);
         d.setShadowed(true);
         // Fond presque opaque : sur le ciel et les feuilles, le texte se lit.
         d.setBackgroundColor(Color.fromARGB(215, 10, 14, 30));
         d.setViewRange(4.0F);
         d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.75F, 0.75F, 0.75F), new AxisAngle4f()));
         d.setPersistent(false);
         d.getPersistentDataContainer().set(this.key, PersistentDataType.BYTE, (byte)1);
      });

      this.tipLine = w.spawn(at.clone().add(0.0, -0.75, 0.0), TextDisplay.class, d -> {
         d.text(Msg.mm(this.tipText()));
         d.setBillboard(Billboard.VERTICAL);
         d.setAlignment(TextAlignment.CENTER);
         d.setLineWidth(300);
         d.setShadowed(true);
         d.setBackgroundColor(Color.fromARGB(215, 48, 34, 4));
         d.setViewRange(4.0F);
         d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6F, 0.6F, 0.6F), new AxisAngle4f()));
         d.setPersistent(false);
         d.getPersistentDataContainer().set(this.key, PersistentDataType.BYTE, (byte)1);
      });
   }

   private String tipText() {
      List<String> tips = Announcer.tipsOf("skyblock");

      if (tips.isEmpty()) {
         return " ";
      }

      return "<#FFC93C><bold>Astuce</bold></#FFC93C>\n<#F2F2F2>" + tips.get(this.tipIndex % tips.size()) + "</#F2F2F2>";
   }

   /** {@code /imt monde holo skyhub} : deplacer le panneau la ou l'on est. */
   public void moveHologram(Location at) {
      this.pl.getConfig().set("mondes.skyhub.panneau", Util.loc(at.clone().add(0.0, 2.5, 0.0)));
      this.pl.saveConfig();
      this.decorate();
   }

   // ------------------------------------------------------- panneau lateral

   /** Chaque seconde, pour ceux qui sont dans le skyblock. */
   public void tick() {
      this.seconds++;

      // Le panneau a disparu (chunk recharge, /kill...) : on le remet.
      if (this.seconds % 10 == 0 && (this.board == null || !this.board.isValid() || this.tipLine == null || !this.tipLine.isValid())) {
         try {
            this.decorate();
         } catch (Throwable t) {
         }
      }

      // Une nouvelle astuce toutes les 8 secondes sous le panneau.
      if (this.seconds % 8 == 0 && this.tipLine != null && this.tipLine.isValid()) {
         this.tipIndex++;
         this.tipLine.text(Msg.mm(this.tipText()));
      }

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
