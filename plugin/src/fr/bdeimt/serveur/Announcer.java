package fr.bdeimt.serveur;

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
      "Chaque <white>/kit vote ‹liste›</white> donne une voix à la liste. Qui gagne ? <white>/listes</white> (et à droite de l'écran).",
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

   public void guide(Player var1) {
      Msg.raw(var1, " ");
      Msg.raw(var1, "<gradient:#4FC3FF:#B66BFF:#FF5FAE><bold>━━━━━━━  Bienvenue sur le serveur du BDE  ━━━━━━━</bold></gradient>");
      Msg.poulpy(var1, "Salut <white><n></white>, moi c'est Poulpy, l'IA du serveur. Voici l'essentiel :", Msg.p("n", var1.getName()));
      Msg.raw(
         var1,
         " <#4FC3FF>⌂</#4FC3FF> <white>/sethome</white> <gray>et</gray> <white>/home</white> <dark_gray>—</dark_gray> <gray>ton point de retour (1 seul)</gray>"
      );
      Msg.raw(var1, " <#4FC3FF>✈</#4FC3FF> <white>/tpa ‹pseudo›</white> <dark_gray>—</dark_gray> <gray>rejoindre un ami (il doit accepter)</gray>");
      Msg.raw(
         var1,
         " <#FF9AC8>★</#FF9AC8> <white>/vote</white> <dark_gray>—</dark_gray> <gray>toutes les heures, un lot au hasard · </gray><white>/probavote</white>"
      );
      Msg.raw(
         var1,
         " <#FFC93C>⚒</#FFC93C> <white>/kit</white> <dark_gray>—</dark_gray> <gray>kit de départ toutes les 2 h · tous les kits :</gray> <white>/help kit</white>"
      );
      Msg.raw(
         var1,
         " <#C77DFF>✦</#C77DFF> <white>Aura</white> <dark_gray>—</dark_gray> <gray>enchaîne les coups critiques pour grimper au top 3 · </gray><white>/aura</white>"
      );
      Msg.raw(var1, " <#C77DFF>⚰</#C77DFF> <gray>Mort ? Ta tombe garde ton stuff 24 h (et les autres peuvent la piller)</gray>");
      Msg.raw(
         var1,
         " <#55FF88>\ud83d\uddf3</#55FF88> <white>/kit vote ‹liste›</white> <dark_gray>—</dark_gray> <gray>1 voix pour la liste · classement :</gray> <white>/listes</white>"
      );
      Msg.raw(
         var1,
         " <#FFC93C>\ud83c\udf7a</#FFC93C> <white>/traq</white> <white>/ghoule</white> <white>/oniris</white> <white>/sugardaddimt</white> <white>/wei</white> <white>/tunnel</white> <dark_gray>—</dark_gray> <gray>la vie de l'IMT</gray>"
      );
      Msg.raw(var1, " <#C77DFF>✉</#C77DFF> <white>/msg</white> <gray>pour chuchoter ·</gray> <white>/echange ‹pseudo›</white> <gray>pour échanger</gray>");
      Msg.raw(var1, " <#55FF88>❤</#55FF88> <gray>Respect et bonne humeur : pas de grief, pas d'insultes.</gray>");
      Msg.raw(var1, "<dark_gray>Revois ce guide à tout moment avec</dark_gray> <click:run_command:'/guide'><#4FC3FF><u>/guide</u></#4FC3FF></click>");
      Msg.raw(var1, " ");
      PlayerData var2 = this.pl.data().get(var1);
      if (!var2.guideSeen) {
         var2.guideSeen = true;
         var2.touch();
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
         String var1 = TIPS.get(this.tipIndex++ % TIPS.size());
         Msg.poulpyAll("<#FFC93C>Astuce :</#FFC93C> " + var1);
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
