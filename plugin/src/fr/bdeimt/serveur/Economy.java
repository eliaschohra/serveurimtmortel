package fr.bdeimt.serveur;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * L'argent du skyblock : le solde de chacun, en pieces.
 *
 * <p>On en gagne en vendant aux marchands et aux autres joueurs, on en
 * depense chez les marchands et sur le marche. Le solde vit dans la fiche du
 * joueur ; toutes les operations passent par ici, sur le fil principal, pour
 * qu'une piece ne puisse jamais etre comptee deux fois.
 */
public final class Economy implements CommandExecutor, TabCompleter {
   /** Ce qu'on trouve sur son compte en creant sa premiere ile. */
   public static final long STARTING_MONEY = 50L;

   private final BDEIMT pl;

   public Economy(BDEIMT pl) {
      this.pl = pl;
   }

   public static String format(long amount) {
      return NumberFormat.getIntegerInstance(Locale.FRANCE).format(amount).replace(' ', ' ').replace(' ', ' ') + " pièce" + (Math.abs(amount) > 1L ? "s" : "");
   }

   public long balance(UUID uuid) {
      PlayerData data = this.pl.data().get(uuid);
      return data == null ? 0L : data.money;
   }

   public long balance(Player p) {
      return this.pl.data().get(p).money;
   }

   /** Ajoute de l'argent, meme a un joueur hors ligne. */
   public void give(UUID uuid, long amount) {
      PlayerData data = this.pl.data().get(uuid);

      if (data == null || amount <= 0L) {
         return;
      }

      data.money = data.money > Long.MAX_VALUE - amount ? Long.MAX_VALUE : data.money + amount;
      data.touch();
   }

   /** Retire de l'argent s'il y en a assez. */
   public boolean take(UUID uuid, long amount) {
      PlayerData data = this.pl.data().get(uuid);

      if (data == null || amount < 0L || data.money < amount) {
         return false;
      }

      data.money -= amount;
      data.touch();
      return true;
   }

   public void set(UUID uuid, long amount) {
      PlayerData data = this.pl.data().get(uuid);

      if (data != null) {
         data.money = Math.max(0L, amount);
         data.touch();
      }
   }

   public static long parseAmount(String raw) {
      try {
         long value = Long.parseLong(raw.replace(" ", "").replace("_", ""));
         return value;
      } catch (NumberFormatException ex) {
         return -1L;
      }
   }

   // ------------------------------------------------------------- commandes

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;

      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      if (command.getName().equalsIgnoreCase("payer")) {
         this.pay(p, args);
         return true;
      }

      if (args.length >= 1 && this.pl.ranks().isStaff(p)) {
         PlayerData other = this.pl.data().byName(args[0]);

         if (other != null) {
            Msg.info(p, "Solde de <white>" + other.name + "</white> : <#FFD25E>" + format(other.money) + "</#FFD25E>.");
            return true;
         }
      }

      Msg.info(p, "Ton solde : <#FFD25E>" + format(this.balance(p)) + "</#FFD25E>.");
      Msg.raw(p, "<gray>Gagne-en en vendant aux marchands du lobby skyblock, ou sur <white>/marche</white>.</gray>");
      return true;
   }

   private void pay(Player p, String[] args) {
      if (args.length < 2) {
         Msg.err(p, "/payer ‹joueur› ‹montant›");
         return;
      }

      PlayerData target = this.pl.data().byName(args[0]);

      if (target == null) {
         Msg.err(p, "Joueur inconnu.");
         return;
      }

      if (target.uuid.equals(p.getUniqueId())) {
         Msg.err(p, "Tu ne peux pas te payer toi-même.");
         return;
      }

      long amount = parseAmount(args[1]);

      if (amount <= 0L) {
         Msg.err(p, "Montant invalide.");
         return;
      }

      if (!this.take(p.getUniqueId(), amount)) {
         Msg.err(p, "Tu n'as que <white>" + format(this.balance(p)) + "</white>.");
         return;
      }

      this.give(target.uuid, amount);
      this.pl.data().save(this.pl.data().get(p));
      this.pl.data().save(target);
      Msg.ok(p, "Tu as envoyé <#FFD25E>" + format(amount) + "</#FFD25E> à <white>" + target.name + "</white>.");
      Player online = Bukkit.getPlayer(target.uuid);

      if (online != null) {
         Msg.alert(online, "<#FFD25E><bold>+ " + format(amount) + "</bold></#FFD25E>", "<gray>de la part de</gray> <white>" + p.getName() + "</white>");
      }
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();

      if (args.length == 1) {
         for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
               out.add(other.getName());
            }
         }
      }

      return out;
   }
}
