package fr.bdeimt.serveur;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.inventory.Book;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Announcer implements CommandExecutor {
   private final BDEIMT pl;
   private int tipIndex;
   private static final List<String> TIPS = List.of(
      "Un <white>/vote</white> par heure = un lot au hasard parmi 200. Les plus rares sont annoncés à tout le serveur !",
      "Envie de voir tes chances ? <click:run_command:'/probavote'><#FF9AC8><u>/probavote</u></#FF9AC8></click> affiche les 200 lots et leurs probabilités.",
      "Mort ? Pas de panique : ta tombe garde ton stuff <white>24 h</white> et la barre en haut de l'écran t'y guide. Fonce : n'importe qui peut la piller !",
      "Chaque <white>/vote ‹liste›</white> donne une voix à la liste. Qui gagne ? <white>/listes</white> (et à droite de l'écran).",
      "Le Traq ouvre au hasard plusieurs fois par jour, pendant 1 h. Quand LaPanthère l'annonce : <white>/traq</white> !",
      "Commandes de soirée : <white>/ghoule</white>, <white>/oniris</white>, <white>/sugardaddimt</white>, <white>/wei</white>, <white>/tunnel</white>... à tes risques et périls.",
      "L'enclume dépasse les limites : deux livres Tranchant V donnent Tranchant VI, et ainsi de suite jusqu'à <white>X</white> !",
      "<white>/sethome</white> enregistre l'endroit où tu es, <white>/home</white> t'y ramène (un seul home, choisis bien !).",
      "Pour rejoindre un ami : <white>/tpa ‹pseudo›</white>. Il doit accepter, et tu dois rester immobile 3 s.",
      "Un kit de départ t'attend toutes les 2 h : <white>/kit</white>. Tous les kits : <white>/help kit</white>.",
      "Le Top 10 des votes a droit au <white>/kit topvote</white>, et le n°1 au <white>/kit top1</white> !",
      "Échanger sans arnaque : <white>/echange ‹pseudo›</white>. Les deux doivent valider, et rien ne bouge au dernier moment.",
      "Un message privé ? <white>/msg ‹pseudo› ‹message›</white>, et <white>/r</white> pour répondre.",
      "Quelqu'un t'embête ? <white>/ignore ‹pseudo›</white> et tu ne vois plus ses messages ni ses demandes.",
      "L'Ender Dragon renaît toutes les <white>48 h</white> dans l'End. Organisez-vous en groupe !",
      "L'aura se gagne en enchaînant les <white>coups critiques</white> : +100, +1 000, +100 000... Le top 3 s'affiche à droite. Mourir = tout perdre !",
      "Respect, entraide et pas de grief : c'est ce qui fait un bon serveur. Merci à vous !"
   );

   /** Les astuces de chaque mode de jeu, repetees dans le chat de ce mode seulement. */
   private static final java.util.Map<String, List<String>> MODE_TIPS = java.util.Map.of(
      "lobby", List.of(
         "Clic droit sur la <white>boussole</white> (au milieu de ta barre) pour choisir un mode de jeu. Tu peux aussi passer dans un portail.",
         "<white>/hub</white> te ramène ici depuis n'importe quel mode de jeu.",
         "La musique du lobby te gêne ? <white>/musique</white> la coupe (et la remet).",
         "Perdu ? <white>/guide</white> ouvre le guide complet du serveur."
      ),
      "parkour", List.of(
         "Les <#FFD25E>plaques en or</#FFD25E> sont les points de contrôle : passe dessus pour enregistrer ta progression.",
         "Tu tombes de plus de 8 blocs ? Retour automatique à ta dernière plaque en or.",
         "Le <#FFD25E>bâton</#FFD25E> au milieu de ta barre te ramène à ta dernière plaque d'un clic. <white>/parkour retour</white> fait pareil.",
         "<white>/parkour recommencer</white> pour repartir du début et relancer le chrono.",
         "Le classement est à droite de l'écran : d'abord le plus loin, puis le plus rapide.",
         "<white>/hub</white> pour revenir au lobby."
      ),
      "skyblock", List.of(
         "Pour aller au <#7FE3FF>lobby du skyblock</#7FE3FF> (marchands, marché) : <white>/ile hub</white>. Pour rentrer sur ton île : <white>/ile</white>.",
         "Pas encore d'île ? <white>/ile creer</white>. Une seule par personne.",
         "Jouer à plusieurs : <white>/ile invite ‹pseudo›</white>. <#FF5555>Attention : celui qui accepte perd sa propre île, effacée pour toujours.</#FF5555>",
         "<#FF5555>Tomber dans le vide = mort.</#FF5555> Ton stuff est perdu, tu réapparais sur ton île. Pose des blocs au bord !",
         "La lave du coffre et l'eau de la glace font un générateur de pierre. Ne fais surtout pas tomber le seau de lave !",
         "Les marchands du lobby skyblock (<white>/ile hub</white>) achètent tes récoltes. Ton argent : <white>/solde</white>.",
         "<white>/marche</white> pour acheter aux autres joueurs, <white>/marche vendre ‹prix›</white> pour vendre l'objet que tu tiens.",
         "Une voix par jour pour l'île de quelqu'un : <white>/vote ‹pseudo›</white>. Le classement est à droite de l'écran.",
         "Chaque île a sa bordure : impossible d'aller chez les autres à pied. <white>/ile tp ‹pseudo›</white> pour visiter.",
         "<white>/tpa ‹pseudo›</white> pour rejoindre un ami, <white>/echange ‹pseudo›</white> pour troquer sans arnaque.",
         "Ton île a disparu ou tu veux repartir de zéro ? <white>/ile supprimer confirmer</white> puis <white>/ile creer</white>."
      ),
      "parcelles", List.of(
         "<white>/parcelle creer</white> pour prendre ta parcelle, puis <white>/parcelle</white> pour y retourner à tout moment.",
         "Construire à plusieurs : <white>/parcelle invite ‹pseudo›</white>. Il pourra construire chez toi, et garde sa propre parcelle.",
         "Tu es en créatif, mais seulement chez toi et chez ceux qui t'ont invité. Les chemins et les autres parcelles sont protégés.",
         "Visiter la parcelle de quelqu'un : <white>/parcelle tp ‹pseudo›</white>.",
         "Une voix par jour : <white>/parcelle vote ‹pseudo›</white>. Le classement : <white>/parcelle top</white>.",
         "Ce que tu as ici reste ici : ton inventaire créatif ne te suit pas dans les autres modes."
      )
   );
   private final java.util.Map<String, Integer> modeTipIndex = new java.util.concurrent.ConcurrentHashMap<>();

   /** Une astuce par mode de jeu, dans le chat de ce mode seulement. */
   public void modeTip() {
      for (java.util.Map.Entry<String, List<String>> entry : MODE_TIPS.entrySet()) {
         boolean someone = false;

         for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.pl.auth().isLogged(p) && this.pl.worlds().zoneOf(p).group.equals(entry.getKey())) {
               someone = true;
               break;
            }
         }

         if (someone) {
            int i = this.modeTipIndex.merge(entry.getKey(), 1, Integer::sum) - 1;
            Msg.poulpyGroup(entry.getKey(), "<#FFC93C>Astuce :</#FFC93C> " + entry.getValue().get(i % entry.getValue().size()));
         }
      }
   }

   /** Les astuces du moment pour un mode, pour les hologrammes qui tournent. */
   public static List<String> tipsOf(String group) {
      return MODE_TIPS.getOrDefault(group, List.of());
   }

   /** Le petit mot d'accueil en arrivant dans un mode de jeu. */
   public void welcome(Player p, Zone zone) {
      String line = "<dark_gray>" + "─".repeat(38) + "</dark_gray>";

      switch (zone) {
         case SKYHUB, SKYBLOCK -> {
            boolean hasIsland = this.pl.skyblock().homeIslandOf(p.getUniqueId()) != null;
            Msg.raw(p, line);
            Msg.raw(p, "<#7FE3FF><bold>Bienvenue dans le Skyblock !</bold></#7FE3FF>");
            Msg.raw(p, hasIsland
               ? "<gray>• Rentrer sur ton île : <white>/ile</white></gray>"
               : "<gray>• Fabrique ton île : <click:run_command:'/ile creer'><#55FF88><u>/ile creer</u></#55FF88></click> <dark_gray>(une seule par personne)</dark_gray></gray>");
            Msg.raw(p, "<gray>• Revenir au <#7FE3FF>lobby du skyblock</#7FE3FF> (marchands, marché) : <white>/ile hub</white></gray>");
            Msg.raw(p, "<gray>• Jouer à plusieurs : <white>/ile invite ‹pseudo›</white> <#FF5555>(rejoindre efface ta propre île !)</#FF5555></gray>");
            Msg.raw(p, "<gray>• <#FF5555>Tomber dans le vide = mort</#FF5555> : stuff perdu, retour sur ton île.</gray>");
            Msg.raw(p, "<gray>• Toutes les commandes : <white>/ile aide</white>  ·  le lobby : <white>/hub</white></gray>");
            Msg.raw(p, line);
         }
         case PARCELLES -> {
            boolean hasPlot = this.pl.plots().plotOf(p.getUniqueId()) != null;
            Msg.raw(p, line);
            Msg.raw(p, "<#C48BFF><bold>Bienvenue dans les Parcelles !</bold></#C48BFF> <gray>(créatif)</gray>");
            Msg.raw(p, hasPlot
               ? "<gray>• Aller sur ta parcelle : <white>/parcelle</white></gray>"
               : "<gray>• Prends ta parcelle : <click:run_command:'/parcelle creer'><#55FF88><u>/parcelle creer</u></#55FF88></click></gray>");
            Msg.raw(p, "<gray>• Construire à plusieurs : <white>/parcelle invite ‹pseudo›</white></gray>");
            Msg.raw(p, "<gray>• On ne construit que chez soi et chez ceux qui nous invitent.</gray>");
            Msg.raw(p, "<gray>• Toutes les commandes : <white>/parcelle aide</white>  ·  le lobby : <white>/hub</white></gray>");
            Msg.raw(p, line);
         }
         case PARKOUR -> {
            Msg.raw(p, line);
            Msg.raw(p, "<#FFD25E><bold>Bienvenue sur le Parkour du mois !</bold></#FFD25E>");
            Msg.raw(p, "<gray>• Les <#FFD25E>plaques en or</#FFD25E> = points de contrôle.</gray>");
            Msg.raw(p, "<gray>• Tu tombes ? Retour à la dernière plaque. Le <#FFD25E>bâton</#FFD25E> t'y ramène aussi.</gray>");
            Msg.raw(p, "<gray>• <white>/parkour recommencer</white> pour repartir du début  ·  <white>/hub</white> pour sortir</gray>");
            Msg.raw(p, line);
         }
         default -> {
         }
      }
   }

   public Announcer(BDEIMT var1) {
      this.pl = var1;
   }

   private String whatsapp() {
      String var1 = this.pl.getConfig().getString("whatsapp.numero", "+33 6 75 15 77 40");
      String var2 = this.pl.getConfig().getString("whatsapp.lien", "https://wa.me/33675157740");
      return "Vous souhaitez créer un monde pour votre survie <white>solo</white>, <white>duo</white> ou même à plusieurs, uniquement pour vous ? Demandez à <white>Elias</white> au <click:open_url:'"
         + var2
         + "'><hover:show_text:'<green>Ouvrir WhatsApp'><#25D366><u>"
         + var1
         + "</u></#25D366></hover></click> sur WhatsApp, en précisant bien votre pseudo Minecraft.";
   }

   /**
    * Le guide du serveur : un livre de plusieurs pages, qui s'ouvre a l'ecran.
    *
    * <p>Couleurs sombres uniquement : le parchemin rend illisibles le jaune,
    * le cyan et les pastels.
    */
   public void guide(Player var1) {
      List<String> var2 = new ArrayList<>();
      var2.add(
         "<dark_purple><bold>Le guide du serveur</bold></dark_purple>\n<dark_gray>BDE IMT Atlantique</dark_gray>\n\n"
            + "<black>Ce guide a <bold>plusieurs pages</bold> : tourne-les avec la fleche en bas a droite.</black>\n\n"
            + "<dark_blue>2</dark_blue> Le lobby\n<dark_blue>3</dark_blue> Survie : bases\n<dark_blue>4</dark_blue> Survie : votes, kits\n"
            + "<dark_blue>5</dark_blue> Survie : listes\n<dark_blue>6</dark_blue> Survie : delires\n<dark_blue>7</dark_blue> Parkour\n"
            + "<dark_blue>8</dark_blue> Parcelles\n<dark_blue>9</dark_blue> Skyblock\n<dark_blue>10</dark_blue> Secrets\n<dark_blue>11</dark_blue> Regles"
      );
      var2.add(
         "<dark_blue><bold>Le lobby</bold></dark_blue>\n\n"
            + "<black>Clic droit sur la <dark_blue>boussole</dark_blue> pour choisir un monde, ou marche dans un portail.</black>\n\n"
            + "<dark_green>/hub</dark_green> <black>revenir ici</black>\n<dark_green>/musique</dark_green> <black>couper la musique</black>\n\n"
            + "<black>Chaque monde a son inventaire et son chat. Rien ne passe de l'un a l'autre.</black>\n\n"
            + "<dark_gray>Zaza se frappe. Le Mobutu, non.</dark_gray>"
      );
      var2.add(
         "<dark_green><bold>Survie : les bases</bold></dark_green>\n\n"
            + "<dark_green>/sethome</dark_green> <dark_green>/home</dark_green>\n<black>ton point de retour</black>\n\n"
            + "<dark_green>/tpa</dark_green> <black>‹pseudo›</black>\n<black>rejoindre un ami</black>\n\n"
            + "<dark_green>/msg</dark_green> <dark_green>/r</dark_green> <black>chuchoter</black>\n"
            + "<dark_green>/echange</dark_green> <black>‹pseudo›</black>\n<black>echange securise</black>\n\n"
            + "<black>Mort ? Ta <dark_red>tombe</dark_red> garde ton stuff 24 h.</black>"
      );
      var2.add(
         "<dark_green><bold>Survie : votes, kits</bold></dark_green>\n\n"
            + "<dark_green>/vote</dark_green> <black>un lot au hasard, toutes les heures</black>\n"
            + "<dark_green>/probavote</dark_green> <black>les chances</black>\n\n"
            + "<dark_green>/kit</dark_green> <black>kit de depart, toutes les 2 h</black>\n\n"
            + "<dark_green>/vote</dark_green> <black>‹liste› : le kit d'une liste du BDE, et une voix pour elle</black>\n"
            + "<dark_green>/listes</dark_green> <black>le classement</black>\n\n"
            + "<dark_purple>/aura</dark_purple> <black>enchaine les coups critiques</black>"
      );
      var2.add(
         "<dark_green><bold>Survie : les listes</bold></dark_green>\n\n"
            + "<black>Une bande de 20 max. Entre membres : pas de coups, et /tpa sans attente.</black>\n\n"
            + "<dark_green>/liste create</dark_green> <black>‹nom›</black>\n"
            + "<dark_green>/liste invite</dark_green> <black>‹joueur› ‹role›</black>\n"
            + "<dark_green>/liste accept</dark_green>\n<dark_green>/liste role</dark_green>\n"
            + "<dark_green>/liste kick</dark_green>\n<dark_green>/liste quitter</dark_green>"
      );
      var2.add(
         "<gold><bold>Survie : la vie de l'IMT</bold></gold>\n\n"
            + "<gold>/traq</gold> <black>s'il est ouvert...</black>\n"
            + "<gold>/ghoule</gold> <black>a vos risques</black>\n"
            + "<gold>/oniris</gold> <black>miaou</black>\n"
            + "<gold>/sugardaddimt</gold> <black>gourmandise</black>\n"
            + "<gold>/wei</gold> <black>un feu d'artifice</black>\n"
            + "<gold>/tunnel</gold> <black>une histoire du campus</black>\n\n"
            + "<dark_gray>Le Traq ouvre au hasard, une heure. Guette l'annonce.</dark_gray>"
      );
      var2.add(
         "<gold><bold>Le parkour du mois</bold></gold>\n\n"
            + "<black>Une carte commune, sans stuff ni coups.</black>\n\n"
            + "<black>Les plaques en or sont les points de controle : si tu tombes, tu y reviens. Le baton aussi.</black>\n\n"
            + "<dark_green>/parkour</dark_green> <black>le classement</black>\n"
            + "<dark_green>/parkour retour</dark_green>\n"
            + "<dark_green>/parkour recommencer</dark_green>\n\n"
            + "<black>Le plus loin gagne, puis le plus rapide.</black>"
      );
      var2.add(
         "<dark_purple><bold>Les parcelles</bold></dark_purple>\n\n"
            + "<black>En creatif, mais on ne construit que chez soi et chez ceux qui invitent.</black>\n\n"
            + "<dark_green>/parcelle creer</dark_green>\n<dark_green>/parcelle</dark_green> <black>y aller</black>\n<dark_green>/parcelle tp</dark_green> <black>‹joueur›</black>\n"
            + "<dark_green>/parcelle invite</dark_green>\n<dark_green>/parcelle ban</dark_green>\n"
            + "<dark_green>/parcelle vote</dark_green> <black>une voix par jour</black>\n<dark_green>/parcelle top</dark_green>"
      );
      var2.add(
         "<dark_aqua><bold>Le skyblock</bold></dark_aqua>\n\n"
            + "<dark_green>/ile creer</dark_green> <black>ton ile</black>\n<dark_green>/ile</dark_green> <black>rentrer</black>\n"
            + "<dark_green>/ile hub</dark_green> <black>le lobby skyblock</black>\n"
            + "<dark_green>/ile invite</dark_green> <black>‹joueur›</black>\n"
            + "<dark_red>Rejoindre l'ile d'un ami efface la tienne. Le vide tue : stuff perdu.</dark_red>\n"
            + "<dark_green>/vote</dark_green> <black>‹pseudo› une voix pour son ile</black>\n"
            + "<dark_green>/marche</dark_green> <black>vendre et acheter</black>\n"
            + "<dark_green>/solde</dark_green> <black>ton argent</black>"
      );
      var2.add(
         "<dark_red><bold>Les secrets</bold></dark_red>\n\n"
            + "<black>- A l'enclume, deux livres pareils montent plus haut que d'habitude. Bien plus haut.</black>\n\n"
            + "<black>- Il existe un /vote pour une liste qui n'existe pas. Elle mene a Brest.</black>\n\n"
            + "<black>- Certains succes ne s'annoncent pas.</black>\n\n"
            + "<dark_gray>- On dit que le Mobutu peut tomber. On dit aussi ce qui arrive apres.</dark_gray>"
      );
      var2.add(
         "<dark_blue><bold>Les regles</bold></dark_blue>\n\n"
            + "<black>Respect et bonne humeur.</black>\n\n"
            + "<black>Pas de grief, pas d'insultes, pas de triche.</black>\n\n"
            + "<black>Un souci ? Ecris au staff avec /msg.</black>\n\n"
            + "<dark_gray>Bon jeu !</dark_gray>"
      );
      List<Component> var3 = new ArrayList<>();

      for (String var5 : var2) {
         var3.add(Msg.mm(var5));
      }

      try {
         var1.openBook(Book.book(Msg.mm("Guide du serveur"), Msg.mm("Poulpy"), var3));
      } catch (Throwable var7) {
         Msg.poulpy(var1, "Ton jeu n'a pas pu ouvrir le livre du guide.");
      }

      PlayerData var8 = this.pl.data().get(var1);
      if (!var8.guideSeen) {
         var8.guideSeen = true;
         var8.touch();
      }
   }

   public void onLogin(Player var1, boolean var2) {
      if (var2) {
         this.guide(var1);
      } else {
         Msg.poulpy(
            var1,
            "Content de te revoir, <white><n></white> ! <gray>Les commandes :</gray> <click:run_command:'/guide'><#4FC3FF><u>/guide</u></#4FC3FF></click>",
            Msg.p("n", var1.getName())
         );
         PlayerData var3 = this.pl.data().get(var1);
         long var4 = var3.lastVote + 3600000L - System.currentTimeMillis();
         if (var4 <= 0L) {
            Msg.panthere(var1, "Ton <white>/vote</white> est disponible, tente ta chance !");
         }
      }

      Msg.poulpy(var1, this.whatsapp());
   }

   public void tip() {
      if (this.onlineLogged() != 0) {
         // Les astuces de la survie ne concernent que la survie.
         String var1 = TIPS.get(this.tipIndex++ % TIPS.size());
         Msg.poulpySurvie("<#FFC93C>Astuce :</#FFC93C> " + var1);
      }
   }

   public void guideReminder() {
      if (this.onlineLogged() != 0) {
         Msg.poulpyAll("Perdu ? Tapez <click:run_command:'/guide'><#4FC3FF><u>/guide</u></#4FC3FF></click> pour revoir toutes les commandes du serveur.");
      }
   }

   private int onlineLogged() {
      int var1 = 0;

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(var3)) {
            var1++;
         }
      }

      return var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         this.guide((Player)var1);
         return true;
      }
   }
}
