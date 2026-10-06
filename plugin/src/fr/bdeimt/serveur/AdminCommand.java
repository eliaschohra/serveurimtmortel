package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AdminCommand implements CommandExecutor, TabCompleter {
   private final BDEIMT pl;

   public AdminCommand(BDEIMT var1) {
      this.pl = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var1 instanceof Player var5 && !this.pl.ranks().isAdmin(var5)) {
         Msg.err(var5, "Commande réservée au Mobutu.");
         return true;
      } else if (var4.length == 0) {
         this.help(var1);
         return true;
      } else {
         String var8 = var4[0].toLowerCase(Locale.ROOT);
         switch (var8) {
            case "reload":
               this.pl.reloadConfig();
               this.pl.lobby().decorate();
               this.pl.motd().loadIcon();
               this.pl.fun().loadStories();
               this.pl.votes().updateSidebar();
               Msg.ok(var1, "Config, images du lobby, icône et histoires du /tunnel rechargées.");
               break;
            case "modo":
               this.modo(var1, var4);
               break;
            case "monde":
            case "mondes":
               this.monde(var1, var4);
               break;
            case "portail":
            case "portails":
               this.portail(var1, var4);
               break;
            case "pnj":
               this.pnj(var1, var4);
               break;
            case "resetmdp":
               if (var4.length < 2) {
                  Msg.err(var1, "/imt resetmdp ‹pseudo›");
                  return true;
               }

               if (this.pl.auth().resetAccount(var4[1])) {
                  Msg.ok(var1, "Mot de passe de <white><n></white> effacé : il devra refaire /register.", Msg.p("n", var4[1]));
                  Player var9 = Bukkit.getPlayerExact(var4[1]);
                  if (var9 != null) {
                     var9.kick(Msg.mm("<#FFB020>Ton mot de passe a été réinitialisé.</#FFB020>\n<gray>Reconnecte-toi et fais /register."));
                  }
               } else {
                  Msg.err(var1, "Aucun compte à ce nom.");
               }
               break;
            case "votes":
               this.votes(var1, var4);
               break;
            case "dragon":
               Msg.info(var1, "Dragon : <white><r></white>", Msg.p("r", this.pl.dragon().respawn(true)));
               break;
            case "lobby":
               if (Msg.noConsole(var1)) {
                  return true;
               }

               ((Player)var1).teleport(this.pl.lobby().spawn());
               Msg.ok(var1, "Bienvenue dans le lobby. <gray>(tu peux le modifier en créatif ; /spawn pour repartir)");
               break;
            case "lot":
               this.lot(var1, var4);
               break;
            case "info":
               this.info(var1, var4);
               break;
            case "traq":
               String var7 = var4.length > 1 ? var4[1].toLowerCase(Locale.ROOT) : "";
               if (var7.equals("ouvrir") || var7.equals("open")) {
                  this.pl.fun().openTraq();
                  Msg.ok(var1, "Traq ouvert pour 1 h.");
               } else if (!var7.equals("fermer") && !var7.equals("close")) {
                  Msg.info(var1, "Le Traq est <white><e></white>. <gray>/imt traq ouvrir|fermer", Msg.p("e", this.pl.fun().traqOpen() ? "ouvert" : "fermé"));
               } else {
                  this.pl.fun().closeTraq();
                  Msg.ok(var1, "Traq fermé.");
               }
               break;
            case "listes":
               if (var4.length > 1 && var4[1].equalsIgnoreCase("reset")) {
                  this.pl.state().listVotes.clear();
                  this.pl.state().save();
                  this.pl.votes().updateSidebar();
                  Msg.ok(var1, "Voix des listes remises à zéro.");
               } else {
                  Msg.info(var1, "<gray>/imt listes reset</gray> — remettre les voix des listes à zéro");
               }
               break;
            default:
               this.help(var1);
         }

         return true;
      }
   }

   private void help(CommandSender var1) {
      Msg.raw(var1, "<#FF3B3B><bold>Commandes du Mobutu</bold></#FF3B3B>");
      Msg.raw(var1, " <white>/imt modo add|remove|liste ‹pseudo›</white> <gray>— gérer les modos");
      Msg.raw(var1, " <white>/imt resetmdp ‹pseudo›</white> <gray>— mot de passe oublié");
      Msg.raw(var1, " <white>/imt info ‹pseudo›</white> <gray>— fiche d'un joueur");
      Msg.raw(var1, " <white>/imt votes ‹pseudo› ‹nombre›</white> <gray>— corriger des votes · <white>/imt votes reset</white>");
      Msg.raw(var1, " <white>/imt lot ‹id› [pseudo]</white> <gray>— donner un lot du /vote (test)");
      Msg.raw(var1, " <white>/imt traq ouvrir|fermer</white> <gray>— ouvrir/fermer le Traq · <white>/imt listes reset</white>");
      Msg.raw(var1, " <white>/imt dragon</white> <gray>— faire renaître le dragon maintenant");
      Msg.raw(var1, " <white>/imt lobby</white> <gray>— aller voir le lobby · <white>/imt reload</white> <gray>— recharger images et config");
      Msg.raw(
         var1,
         " <gray>Kits cachés : <white>/kit mobutu</white>, <white>mobutu-chantier</white>, <white>mobutu-fete</white>, <white>mobutu-potions</white>, <white>mobutu-voyage</white>, <white>mobutu-farces</white>"
      );
   }

   void modo(CommandSender var1, String[] var2) {
      if (var2.length >= 2 && var2[1].equalsIgnoreCase("liste")) {
         if (this.pl.state().modos.isEmpty()) {
            Msg.info(var1, "Aucun modo.");
         } else {
            Msg.info(var1, "Modos : <white><l></white>", Msg.p("l", String.join(", ", this.pl.state().modos.values())));
         }
      } else if (var2.length < 3) {
         Msg.err(var1, "ajouter|retirer ‹pseudo›  ·  liste");
      } else {
         PlayerData var3 = this.pl.data().byName(var2[2]);
         if (var3 == null) {
            Msg.err(var1, "Joueur inconnu (il doit s'être connecté au moins une fois).");
         } else {
            String var4 = var2[1].toLowerCase(Locale.ROOT);
            if (!var4.equals("add") && !var4.equals("ajouter")) {
               if (!var4.equals("remove") && !var4.equals("retirer")) {
                  Msg.err(var1, "ajouter|retirer ‹pseudo›");
                  return;
               }

               if (this.pl.state().modos.remove(var3.uuid) == null) {
                  Msg.err(var1, "Ce joueur n'est pas modérateur.");
                  return;
               }

               Msg.ok(var1, "<white><n></white> n'est plus modérateur.", Msg.p("n", var3.name));
            } else {
               this.pl.state().modos.put(var3.uuid, var3.name);
               Msg.ok(var1, "<white><n></white> est maintenant <#FFB020><bold>[Modérateur]</bold></#FFB020>.", Msg.p("n", var3.name));
            }

            this.pl.state().save();
            Player var5 = Bukkit.getPlayer(var3.uuid);
            if (var5 != null && this.pl.auth().isLogged(var5)) {
               this.pl.ranks().apply(var5);
            }
         }
      }
   }

   private void votes(CommandSender var1, String[] var2) {
      if (var2.length >= 2 && var2[1].equalsIgnoreCase("reset")) {
         for (PlayerData var4 : this.pl.data().all()) {
            var4.votes = 0;
            var4.touch();
         }

         this.pl.data().saveDirty();
         this.pl.votes().updateSidebar();
         Msg.ok(var1, "Classement des votes remis à zéro.");
      } else if (var2.length < 3) {
         Msg.err(var1, "/imt votes ‹pseudo› ‹nombre›  ·  /imt votes reset");
      } else {
         PlayerData var3 = this.pl.data().byName(var2[1]);
         if (var3 == null) {
            Msg.err(var1, "Joueur inconnu.");
         } else {
            try {
               var3.votes = Math.max(0, Integer.parseInt(var2[2]));
            } catch (NumberFormatException var5) {
               Msg.err(var1, "Nombre invalide.");
               return;
            }

            var3.touch();
            this.pl.data().save(var3);
            this.pl.votes().updateSidebar();
            Msg.ok(var1, "<white><n></white> a maintenant <white><v></white> votes.", Msg.p("n", var3.name), Msg.p("v", var3.votes));
         }
      }
   }

   private void lot(CommandSender var1, String[] var2) {
      if (var2.length >= 2) {
         Loots.Loot var6 = this.pl.votes().loots().get(var2[1]);
         if (var6 == null) {
            Msg.err(var1, "Lot inconnu.");
         } else {
            Player var7 = var2.length >= 3 ? Bukkit.getPlayerExact(var2[2]) : (var1 instanceof Player var8 ? var8 : null);
            if (var7 == null) {
               Msg.err(var1, "Joueur introuvable.");
            } else {
               this.pl.votes().grant(var7, var6);
               Msg.ok(var1, "Lot <white><l></white> donné à <white><n></white>.", Msg.p("l", var6.name), Msg.p("n", var7.getName()));
            }
         }
      } else {
         ArrayList var3 = new ArrayList();

         for (Loots.Loot var5 : this.pl.votes().loots().all()) {
            var3.add(var5.id);
         }

         Msg.info(var1, "Lots : <white><l></white>", Msg.p("l", String.join(", ", var3)));
      }
   }

   private void info(CommandSender var1, String[] var2) {
      if (var2.length < 2) {
         Msg.err(var1, "/imt info ‹pseudo›");
      } else {
         PlayerData var3 = this.pl.data().byName(var2[1]);
         if (var3 == null) {
            Msg.err(var1, "Joueur inconnu.");
         } else {
            Player var4 = Bukkit.getPlayer(var3.uuid);
            Msg.raw(
               var1,
               "<#4FC3FF><bold>"
                  + var3.name
                  + "</bold></#4FC3FF> <gray>· rang "
                  + this.pl.ranks().compute(var3.uuid, var3.name).name().toLowerCase(Locale.ROOT)
            );
            Msg.raw(var1, " <gray>Connecté : <white>" + (var4 == null ? "non" : (this.pl.auth().isLogged(var4) ? "oui" : "oui, pas identifié")));
            Msg.raw(var1, " <gray>Compte : <white>" + (this.pl.auth().hasAccount(var3.name) ? "oui" : "non"));
            Msg.raw(var1, " <gray>Votes : <white>" + var3.votes + "</white> (n°" + this.pl.data().rankOf(var3.uuid) + ")");
            Msg.raw(var1, " <gray>Fly restant : <white>" + Util.duration(var3.flySeconds * 1000L));
            Msg.raw(
               var1,
               " <gray>Muet : <white>"
                  + (
                     var3.isMuted()
                        ? (var3.mutedUntil == -1L ? "oui (permanent)" : "encore " + Util.duration(var3.mutedUntil - System.currentTimeMillis()))
                        : "non"
                  )
            );
            Msg.raw(var1, " <gray>Homes : <white>" + String.join(", ", var3.homes.keySet()));
         }
      }
   }

   /** {@code /imt monde} : la liste des univers, et reglage du point d'arrivee. */
   private void monde(CommandSender sender, String[] args) {
      if (args.length < 2) {
         Msg.raw(sender, "<dark_gray>———— <#4FC3FF>Les univers</#4FC3FF> ————</dark_gray>");

         for (Zone zone : Zone.values()) {
            boolean open = this.pl.worlds().available(zone);
            Msg.raw(
               sender,
               " " + (open ? "<#55FF88>●</#55FF88>" : "<#FF5555>●</#FF5555>") + " " + zone.color + zone.shortLabel()
                  + "</" + zone.color.substring(1, zone.color.length() - 1) + "> <dark_gray>(" + (zone.world == null ? "monde principal" : zone.world)
                  + ")</dark_gray> <gray>" + (open ? this.pl.worlds().count(zone) + " joueur(s)" : "monde absent") + "</gray>"
            );

            if (!open) {
               Msg.raw(sender, "    <#FFB3B3>" + this.pl.worlds().diagnose(zone).replace("<", "‹").replace(">", "›") + "</#FFB3B3>");
            }
         }

         Msg.info(sender, "<white>/imt monde spawn ‹zone›</white> pour fixer le point d'arrivee la ou tu es.");
         Msg.info(sender, "<white>/imt monde charger ‹zone›</white> pour reessayer de charger un monde depose.");
         return;
      }

      // Charger un monde sans redemarrer, en reparant au passage l'erreur
      // classique du dossier depose dans un autre dossier.
      if (args[1].equalsIgnoreCase("charger") || args[1].equalsIgnoreCase("load")) {
         if (args.length < 3) {
            Msg.err(sender, "/imt monde charger ‹zone›");
            return;
         }

         Zone wanted = parseZone(args[2]);

         if (wanted == null || wanted.world == null) {
            Msg.err(sender, "Zone inconnue.");
            return;
         }

         if (this.pl.worlds().available(wanted)) {
            Msg.info(sender, "Ce monde est deja charge.");
            return;
         }

         if (this.pl.worlds().unnest(wanted)) {
            Msg.ok(sender, "Le dossier etait emboite dans un autre : remis a plat.");
         }

         if (this.pl.worlds().loadNow(wanted)) {
            Msg.ok(sender, "Monde <white>" + wanted.world + "</white> charge. Il apparait dans la boussole.");

            if (wanted == Zone.HUB) {
               this.pl.hub().decorate();
               this.pl.npcs().spawnAll();
            } else if (wanted == Zone.PARKOUR) {
               this.pl.parkour().refreshHologram();
            }
         } else {
            Msg.err(sender, this.pl.worlds().diagnose(wanted).replace("<", "‹").replace(">", "›"));
         }

         return;
      }

      if (!args[1].equalsIgnoreCase("spawn") || args.length < 3) {
         Msg.err(sender, "/imt monde spawn ‹zone›");
         return;
      }

      if (Msg.noConsole(sender)) {
         return;
      }

      Zone zone = parseZone(args[2]);
      if (zone == null) {
         Msg.err(sender, "Zone inconnue. Essaie : hub, parkour, skyhub, skyblock, parcelles, survie.");
         return;
      }

      Player p = (Player)sender;
      this.pl.worlds().setSpawn(zone, p.getLocation());
      Msg.ok(sender, "Point d'arrivee de " + zone.color + zone.shortLabel() + "</gray> fixe ici <gray>(" + Util.coords(p.getLocation()) + ")</gray>.");
   }

   /** {@code /imt portail} : poser un passage physique vers un mode de jeu. */
   private void portail(CommandSender sender, String[] args) {
      if (Msg.noConsole(sender)) {
         return;
      }

      Player p = (Player)sender;
      if (args.length >= 2 && args[1].equalsIgnoreCase("effacer")) {
         int removed = this.pl.hub().clearPortals(p.getWorld());
         Msg.ok(sender, removed + " portail(s) retire(s) de ce monde.");
         return;
      }

      if (args.length < 2) {
         Msg.err(sender, "/imt portail ‹zone› [rayon]  —  ou  /imt portail effacer");
         return;
      }

      Zone zone = parseZone(args[1]);
      if (zone == null) {
         Msg.err(sender, "Zone inconnue.");
         return;
      }

      double radius = 2.0;
      if (args.length >= 3) {
         try {
            radius = Math.max(1.0, Math.min(8.0, Double.parseDouble(args[2])));
         } catch (NumberFormatException ex) {
            Msg.err(sender, "Rayon invalide.");
            return;
         }
      }

      this.pl.hub().addPortal(zone, p.getLocation(), radius);
      Msg.ok(sender, "Portail vers " + zone.color + zone.shortLabel() + "</gray> pose ici, rayon <white>" + radius + "</white>.");
   }

   /** {@code /imt pnj} : poser Zaza et le Mobutu, et leur donner un skin. */
   private void pnj(CommandSender sender, String[] args) {
      if (args.length < 2) {
         Msg.err(sender, "/imt pnj ‹zaza|mobutu› ici   —   /imt pnj skin ‹zaza|mobutu› ‹pseudo›");
         return;
      }

      if (args[1].equalsIgnoreCase("skin")) {
         if (args.length < 4) {
            Msg.err(sender, "/imt pnj skin ‹zaza|mobutu› ‹pseudo›   —   ou   /imt pnj skin ‹zaza|mobutu› fichier");
            return;
         }

         String id = args[2].toLowerCase(Locale.ROOT);
         if (!id.equals("zaza") && !id.equals("mobutu")) {
            Msg.err(sender, "Il n'y a que zaza et mobutu.");
            return;
         }

         // « fichier » : on prend le PNG depose dans plugins/BDEIMT/skins/.
         if (args[3].equalsIgnoreCase("fichier") || args[3].equalsIgnoreCase("png")) {
            this.pl.skins().upload(id, sender);
            return;
         }

         this.pl.getConfig().set("pnj." + id + ".pseudo", args[3]);
         this.pl.getConfig().set("pnj." + id + ".texture", "");
         this.pl.saveConfig();
         this.pl.npcs().spawn(id);
         Msg.ok(sender, "Skin de <white>" + id + "</white> copie sur le compte <white>" + args[3] + "</white> <gray>(quelques secondes)</gray>.");
         return;
      }

      String id = args[1].toLowerCase(Locale.ROOT);
      if (!id.equals("zaza") && !id.equals("mobutu")) {
         Msg.err(sender, "Il n'y a que zaza et mobutu.");
         return;
      }

      if (Msg.noConsole(sender)) {
         return;
      }

      Player p = (Player)sender;
      if (this.pl.worlds().zoneOf(p) != Zone.HUB) {
         Msg.err(sender, "Les PNJ vivent dans le hub : place-toi la-bas d'abord.");
         return;
      }

      this.pl.npcs().setPosition(id, p.getLocation());
      this.pl.npcs().spawn(id);
      Msg.ok(sender, "<white>" + id + "</white> est pose ici.");
   }

   private static Zone parseZone(String name) {
      String wanted = name.toLowerCase(Locale.ROOT);

      for (Zone zone : Zone.values()) {
         if (zone.name().toLowerCase(Locale.ROOT).equals(wanted) || zone.shortLabel().toLowerCase(Locale.ROOT).equals(wanted)) {
            return zone;
         }
      }

      return null;
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (var1 instanceof Player var6 && !this.pl.ranks().isAdmin(var6)) {
         return var5;
      } else {
         if (var4.length == 1) {
            var5.addAll(List.of("modo", "resetmdp", "info", "votes", "lot", "dragon", "lobby", "reload", "traq", "listes", "monde", "portail", "pnj"));
         } else if (var4.length == 2) {
            String var10 = var4[0].toLowerCase(Locale.ROOT);
            switch (var10) {
               case "modo":
                  var5.addAll(List.of("add", "remove", "liste"));
                  break;
               case "traq":
                  var5.addAll(List.of("ouvrir", "fermer"));
                  break;
               case "listes":
                  var5.add("reset");
                  break;
               case "lot":
                  for (Loots.Loot var19 : this.pl.votes().loots().all()) {
                     var5.add(var19.id);
                  }
                  break;
               case "votes":
                  var5.add("reset");

                  for (Player var18 : Bukkit.getOnlinePlayers()) {
                     var5.add(var18.getName());
                  }
                  break;
               default:
                  for (Player var9 : Bukkit.getOnlinePlayers()) {
                     var5.add(var9.getName());
                  }
            }
         } else if (var4.length == 3) {
            if (var4[0].equalsIgnoreCase("modo") && var4[1].equalsIgnoreCase("remove")) {
               for (Entry var15 : this.pl.state().modos.entrySet()) {
                  var5.add((String)var15.getValue());
               }
            } else {
               for (Player var14 : Bukkit.getOnlinePlayers()) {
                  var5.add(var14.getName());
               }
            }
         }

         String var13 = var4[var4.length - 1].toLowerCase(Locale.ROOT);
         var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var13));
         return var5;
      }
   }
}
