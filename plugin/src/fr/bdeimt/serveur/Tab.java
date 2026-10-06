package fr.bdeimt.serveur;

import java.util.EnumMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * La liste des joueurs (touche TAB).
 *
 * <p>En-tete : le nom du serveur et le nombre de connectes. Pied de page : le
 * detail par univers, pour savoir ou sont les gens avant de choisir son mode
 * de jeu. A cote de chaque pseudo on affiche le rang et, en survie, le role
 * dans sa liste.
 */
public final class Tab {
   private final BDEIMT pl;

   public Tab(BDEIMT pl) {
      this.pl = pl;
   }

   /** Rafraichit tout le monde, appele chaque seconde. */
   public void tick() {
      Map<Zone, Integer> counts = new EnumMap<>(Zone.class);
      int total = 0;

      for (Zone zone : Zone.values()) {
         if (zone.isGameMode() && this.pl.worlds().available(zone)) {
            counts.put(zone, 0);
         }
      }

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p)) {
            total++;
            Zone zone = this.pl.worlds().zoneOf(p);

            for (Zone key : counts.keySet()) {
               if (key.group.equals(zone.group)) {
                  counts.merge(key, 1, Integer::sum);
               }
            }
         }
      }

      StringBuilder detail = new StringBuilder();

      for (Map.Entry<Zone, Integer> entry : counts.entrySet()) {
         if (!detail.isEmpty()) {
            detail.append("<dark_gray> | </dark_gray>");
         }

         detail.append(entry.getKey().color)
            .append(entry.getKey().shortLabel())
            .append("</")
            .append(entry.getKey().color, 1, entry.getKey().color.length() - 1)
            .append("> <white>")
            .append(entry.getValue())
            .append("</white>");
      }

      int lobby = 0;

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p) && this.pl.worlds().zoneOf(p).group.equals("lobby")) {
            lobby++;
         }
      }

      if (lobby > 0) {
         if (!detail.isEmpty()) {
            detail.append("<dark_gray> | </dark_gray>");
         }

         detail.append("<#4FC3FF>Lobby</#4FC3FF> <white>").append(lobby).append("</white>");
      }

      Component header = Msg.mm(
         "\n<gradient:#4FC3FF:#B66BFF:#FF5FAE><bold>  Serveur du BDE de l'IMT  </bold></gradient>\n"
            + "<gray>imtmortel.com</gray>   <dark_gray>·</dark_gray>   <white>" + total + "</white> <gray>en ligne</gray>\n"
      );
      Component footer = Msg.mm("\n" + detail + "\n<dark_gray>/hub ou la boussole du lobby pour changer de monde</dark_gray>\n");

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p)) {
            try {
               p.sendPlayerListHeaderAndFooter(header, footer);
            } catch (Throwable t) {
            }
         }
      }
   }

   /** Met a jour la ligne d'un joueur dans le TAB. */
   public void refresh(Player p) {
      try {
         Zone zone = this.pl.worlds().zoneOf(p);
         String role = this.pl.lists().tabSuffix(p);
         p.playerListName(Msg.mm(this.pl.ranks().prefix(p) + " <white>" + p.getName() + "</white>" + role + " <dark_gray>·</dark_gray> " + zone.color + zone.shortLabel()));
      } catch (Throwable t) {
      }
   }

   /** Rafraichit la ligne de tous les joueurs identifies. */
   public void refreshAll() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p)) {
            this.refresh(p);
         }
      }
   }
}
