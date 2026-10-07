package fr.bdeimt.serveur;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /modo} : les outils des moderateurs.
 *
 * <p>Un moderateur peut garder le serveur propre et le depanner sans pouvoir
 * changer la partie : il sanctionne (dans des limites), se deplace, ferme un
 * mode qui deraille, replace un point d'arrivee. Il ne peut ni donner
 * d'objets, ni toucher aux votes, a l'argent ou a l'aura, ni bannir pour
 * toujours, ni nommer d'autres moderateurs. Tout ce qu'il fait est annonce aux
 * autres membres du staff.
 */
public final class ModoCommand implements CommandExecutor, TabCompleter {
   /** Les limites d'un moderateur (le Mobutu n'en a pas). */
   public static final long MAX_MUTE = 24L * 3600000L;
   public static final long MAX_TEMPBAN = 7L * 24L * 3600000L;

   private static final List<String> SPAWN_ZONES = List.of("survie", "hub", "parkour", "skyblock", "parcelles");
   private static final List<String> MODES = List.of("survie", "parkour", "skyblock", "parcelles");

   private final BDEIMT pl;

   public ModoCommand(BDEIMT pl) {
      this.pl = pl;
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player p)) {
         Msg.err(sender, "Commande à utiliser en jeu.");
         return true;
      }

      if (!this.pl.ranks().isStaff(p)) {
         Msg.err(p, "Réservé aux modérateurs.");
         return true;
      }

      String sub = args.length == 0 ? "aide" : args[0].toLowerCase(Locale.ROOT);

      switch (sub) {
         case "spawn" -> this.spawn(p, args);
         case "mode" -> this.mode(p, args);
         case "tp" -> this.tp(p, args);
         case "ile" -> this.pl.skyRepair().command(p, new String[]{"skyblock", "ile", args.length >= 2 ? args[1] : ""});
         case "annonce" -> this.announce(p, args);
         default -> this.help(p);
      }

      return true;
   }

   // ------------------------------------------------------------------ aide

   /** La liste complete, montree a la nomination et sur /modo. */
   public void help(Player p) {
      String line = "<dark_gray>" + "─".repeat(40) + "</dark_gray>";
      Msg.raw(p, line);
      Msg.raw(p, Ranks.Rank.MODO.prefix + " <white>Tes commandes de modérateur</white>");
      Msg.raw(p, "<#4FA8FF>Sanctions</#4FA8FF>");
      Msg.raw(p, " <white>/mute ‹pseudo› ‹durée› ‹raison›</white> <gray>— le faire taire (24 h max)</gray>");
      Msg.raw(p, " <white>/unmute ‹pseudo›</white> <gray>— lui rendre la parole</gray>");
      Msg.raw(p, " <white>/kick ‹pseudo› ‹raison›</white> <gray>— l'expulser (il peut revenir)</gray>");
      Msg.raw(p, " <white>/tempban ‹pseudo› ‹durée› ‹raison›</white> <gray>— le bannir (7 jours max)</gray>");
      Msg.raw(p, " <white>/unban ‹pseudo›</white> <gray>— lever un bannissement</gray>");
      Msg.raw(p, " <dark_gray>Durées : 30m, 2h, 3j, 1sem</dark_gray>");
      Msg.raw(p, "<#4FA8FF>Surveiller</#4FA8FF>");
      Msg.raw(p, " <white>/panel</white> <gray>— les joueurs en ligne : aller vers eux, les amener, voir leur inventaire</gray>");
      Msg.raw(p, " <white>/modo tp ‹pseudo›</white> <gray>— aller voir un joueur, dans n'importe quel mode</gray>");
      Msg.raw(p, " <white>/modo ile ‹pseudo›</white> <gray>— aller sur l'île de quelqu'un</gray>");
      Msg.raw(p, " <white>/fly</white> <gray>— voler (staff)</gray>");
      Msg.raw(p, "<#4FA8FF>Dépanner</#4FA8FF>");
      Msg.raw(p, " <white>/modo mode ‹mode› off|on</white> <gray>— fermer un mode qui bugue (les joueurs vont au lobby)</gray>");
      Msg.raw(p, " <white>/modo spawn ‹survie|hub|parkour|skyblock|parcelles›</white> <gray>— le point d'arrivée, là où tu es</gray>");
      Msg.raw(p, " <white>/modo annonce ‹message›</white> <gray>— un message à tout le serveur</gray>");
      Msg.raw(p, "<dark_gray>Tout ce que tu fais est signalé au staff. Pas d'abus : en cas de doute, demande au Mobutu.</dark_gray>");
      Msg.raw(p, line);
   }

   /** Ce que voit un joueur au moment ou il devient moderateur. */
   public void welcome(Player p) {
      p.showTitle(
         Title.title(
            Msg.mm(Ranks.Rank.MODO.prefix),
            Msg.mm("<white>Tu es maintenant modérateur du serveur</white>"),
            Title.Times.times(Duration.ofMillis(300L), Duration.ofSeconds(4L), Duration.ofSeconds(1L))
         )
      );
      Util.sound(p, "ui.toast.challenge_complete", 1.0F, 1.2F);
      this.help(p);
   }

   // -------------------------------------------------------------- commandes

   private void spawn(Player p, String[] args) {
      if (args.length < 2 || !SPAWN_ZONES.contains(args[1].toLowerCase(Locale.ROOT))) {
         Msg.err(p, "/modo spawn ‹survie|hub|parkour|skyblock|parcelles› <gray>(place-toi d'abord au bon endroit)</gray>");
         return;
      }

      String which = args[1].toLowerCase(Locale.ROOT);
      Zone zone = switch (which) {
         case "survie" -> Zone.SURVIE;
         case "hub" -> Zone.HUB;
         case "parkour" -> Zone.PARKOUR;
         case "skyblock" -> Zone.SKYHUB;
         default -> Zone.PARCELLES;
      };

      if (this.pl.worlds().zoneOf(p) != zone) {
         Msg.err(p, "Va d'abord dans " + zone.colored() + "<#FF5555>, à l'endroit voulu.");
         return;
      }

      Location here = p.getLocation();

      if (zone == Zone.SURVIE) {
         // La survie : c'est le point d'apparition du monde lui-meme.
         here.getWorld().setSpawnLocation(here);
      } else {
         this.pl.worlds().setSpawn(zone, here);
      }

      Msg.ok(p, "Point d'arrivée de " + zone.colored() + " <#55FF88>placé ici.");
      Msg.staff("<white><a></white> a déplacé le point d'arrivée de <white><z></white>.", Msg.p("a", p.getName()), Msg.p("z", zone.shortLabel()));
   }

   private void mode(Player p, String[] args) {
      if (args.length < 3 || !MODES.contains(args[1].toLowerCase(Locale.ROOT))) {
         for (String group : MODES) {
            boolean on = this.pl.getConfig().getBoolean("modes." + group, true);
            Msg.raw(p, " " + (on ? "<#55FF88>●</#55FF88>" : "<#FF5555>●</#FF5555>") + " <white>" + group + "</white> <gray>" + (on ? "ouvert" : "fermé") + "</gray>");
         }

         Msg.info(p, "<white>/modo mode ‹mode› off</white> pour fermer, <white>on</white> pour rouvrir.");
         return;
      }

      String group = args[1].toLowerCase(Locale.ROOT);
      boolean on = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("ouvrir");
      this.pl.getConfig().set("modes." + group, on);
      this.pl.saveConfig();

      if (!on) {
         // Fermer tout de suite : ceux qui y sont repartent au lobby.
         for (Player other : Bukkit.getOnlinePlayers()) {
            if (this.pl.auth().isLogged(other) && this.pl.worlds().zoneOf(other).group.equals(group)) {
               this.pl.worlds().send(other, Zone.HUB);
               Msg.info(other, "Ce mode de jeu est fermé un moment pour réparation. Ton stuff t'attend, rien n'est perdu.");
            }
         }

         Msg.ok(p, "Mode <white>" + group + "</white> fermé.");
      } else {
         boolean ready = false;

         for (Zone zone : Zone.values()) {
            if (zone.group.equals(group) && zone.isGameMode()) {
               ready = this.pl.worlds().available(zone);
            }
         }

         Msg.ok(p, "Mode <white>" + group + "</white> ouvert." + (ready ? "" : " <#FFD25E>Son monde n'est pas chargé : il sera disponible au prochain redémarrage (demande au Mobutu).</#FFD25E>"));
      }

      Msg.staff("<white><a></white> a " + (on ? "ouvert" : "fermé") + " le mode <white><m></white>.", Msg.p("a", p.getName()), Msg.p("m", group));
   }

   private void tp(Player p, String[] args) {
      Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;

      if (target == null || !this.pl.auth().isLogged(target)) {
         Msg.err(p, "/modo tp ‹pseudo› <gray>(joueur connecté)</gray>");
         return;
      }

      Zone zone = this.pl.worlds().zoneOf(target);

      if (this.pl.worlds().zoneOf(p).group.equals(zone.group) || this.pl.worlds().send(p, zone)) {
         p.teleport(target.getLocation());
         Msg.ok(p, "Te voilà près de <white>" + target.getName() + "</white>.");
      }
   }

   private void announce(Player p, String[] args) {
      if (args.length < 2) {
         Msg.err(p, "/modo annonce ‹message›");
         return;
      }

      String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).replace("<", "‹");
      Msg.broadcast(Msg.mm("<dark_gray>[</dark_gray><#4FA8FF><bold>Annonce</bold></#4FA8FF><dark_gray>]</dark_gray> <white>" + text + "</white> <dark_gray>— " + p.getName() + "</dark_gray>"));

      for (Player other : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(other)) {
            Util.sound(other, "block.note_block.bell", 0.8F, 1.2F);
         }
      }
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();

      if (args.length == 1) {
         out.addAll(List.of("aide", "tp", "ile", "mode", "spawn", "annonce"));
      } else if (args.length == 2) {
         switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mode" -> out.addAll(MODES);
            case "spawn" -> out.addAll(SPAWN_ZONES);
            case "tp", "ile" -> {
               for (Player other : Bukkit.getOnlinePlayers()) {
                  out.add(other.getName());
               }
            }
            default -> {
            }
         }
      } else if (args.length == 3 && args[0].equalsIgnoreCase("mode")) {
         out.addAll(List.of("off", "on"));
      }

      String start = args[args.length - 1].toLowerCase(Locale.ROOT);
      out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(start));
      return out;
   }
}
