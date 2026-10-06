package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Les listes de la survie.
 *
 * <p>Une liste, c'est une bande de 20 joueurs maximum. On ne se frappe pas
 * entre membres d'une meme liste, on se teleporte sans attendre, et le role
 * de chacun s'affiche a cote de son pseudo, dans la couleur de la liste.
 *
 * <p>A ne pas confondre avec les listes BDE du {@code /kit vote}, qui sont un
 * concours de votes et vivent dans {@link Kits}.
 */
public final class Lists implements Listener, CommandExecutor, TabCompleter {
   private static final int MAX_MEMBERS = 20;
   /** Des couleurs franches et bien distinctes, une par liste creee. */
   private static final String[] PALETTE = new String[]{
      "#FF5FAE", "#4FC3FF", "#55FF88", "#FFD25E", "#B66BFF", "#FF8A3D",
      "#00E5D0", "#FF4D4D", "#A3E635", "#7FA6FF", "#FF9AC8", "#D4A017"
   };

   /** Une liste : un nom, un proprietaire, des membres et leurs roles. */
   public static final class Band {
      public final String name;
      public volatile UUID owner;
      public volatile String color;
      public final Map<UUID, String> members = new LinkedHashMap<>();

      Band(String name, UUID owner, String color) {
         this.name = name;
         this.owner = owner;
         this.color = color;
      }
   }

   private final BDEIMT pl;
   private final File file;
   private final Map<String, Lists.Band> bands = new ConcurrentHashMap<>();
   /** Invitations en attente : invite -> nom de la liste. */
   private final Map<UUID, String> invites = new ConcurrentHashMap<>();

   public Lists(BDEIMT pl) {
      this.pl = pl;
      this.file = new File(pl.getDataFolder(), "listes.yml");
   }

   // ------------------------------------------------------------ chargement

   public void load() {
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection root = yml.getConfigurationSection("listes");
      if (root == null) {
         return;
      }

      for (String key : root.getKeys(false)) {
         ConfigurationSection s = root.getConfigurationSection(key);
         if (s == null) {
            continue;
         }

         try {
            Lists.Band band = new Lists.Band(s.getString("nom", key), UUID.fromString(s.getString("proprietaire")), s.getString("couleur", PALETTE[0]));
            ConfigurationSection mem = s.getConfigurationSection("membres");
            if (mem != null) {
               for (String uuid : mem.getKeys(false)) {
                  band.members.put(UUID.fromString(uuid), mem.getString(uuid, "membre"));
               }
            }

            this.bands.put(key.toLowerCase(Locale.ROOT), band);
         } catch (Exception ex) {
            this.pl.getLogger().warning("Liste illisible : " + key);
         }
      }

      this.pl.getLogger().info(this.bands.size() + " liste(s) chargee(s).");
   }

   public void save() {
      YamlConfiguration yml = new YamlConfiguration();

      for (Map.Entry<String, Lists.Band> entry : this.bands.entrySet()) {
         String path = "listes." + entry.getKey();
         Lists.Band band = entry.getValue();
         yml.set(path + ".nom", band.name);
         yml.set(path + ".proprietaire", band.owner.toString());
         yml.set(path + ".couleur", band.color);

         synchronized (band.members) {
            for (Map.Entry<UUID, String> member : band.members.entrySet()) {
               yml.set(path + ".membres." + member.getKey(), member.getValue());
            }
         }
      }

      try {
         yml.save(this.file);
      } catch (IOException e) {
         this.pl.getLogger().warning("listes.yml : " + e.getMessage());
      }
   }

   // -------------------------------------------------------------- lecture

   public Lists.Band bandOf(UUID uuid) {
      for (Lists.Band band : this.bands.values()) {
         if (band.members.containsKey(uuid)) {
            return band;
         }
      }

      return null;
   }

   public Lists.Band bandOf(Player p) {
      return this.bandOf(p.getUniqueId());
   }

   /** Deux joueurs de la meme liste ne se frappent pas et se teleportent vite. */
   public boolean sameList(Player a, Player b) {
      Lists.Band band = this.bandOf(a);
      return band != null && band.members.containsKey(b.getUniqueId());
   }

   /** Le role entre parentheses affiche a cote du pseudo, jamais en gras. */
   public String tabSuffix(Player p) {
      Lists.Band band = this.bandOf(p);
      if (band == null) {
         return "";
      }

      String role = band.members.get(p.getUniqueId());
      String shown = role == null || role.isBlank() ? band.name : role;
      return " <" + band.color + ">(" + shown + ")</" + band.color + ">";
   }

   private String nextColor() {
      return PALETTE[this.bands.size() % PALETTE.length];
   }

   // ------------------------------------------------------------- commande

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;
      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      if (this.pl.worlds().zoneOf(p) != Zone.SURVIE) {
         Msg.err(p, "Les listes, c'est en survie uniquement.");
         return true;
      }

      if (args.length == 0) {
         this.info(p, this.bandOf(p));
         return true;
      }

      String sub = args[0].toLowerCase(Locale.ROOT);

      switch (sub) {
         case "create", "creer", "créer" -> this.create(p, args);
         case "invite", "inviter" -> this.invite(p, args);
         case "accept", "accepter", "oui" -> this.accept(p);
         case "refuse", "refuser", "non" -> this.refuse(p);
         case "kick", "exclure", "virer" -> this.kick(p, args);
         case "role", "rôle" -> this.role(p, args);
         case "quit", "quitter", "leave" -> this.quit(p);
         case "supprimer", "delete", "dissoudre" -> this.delete(p);
         case "info", "liste" -> this.info(p, args.length >= 2 ? this.bands.get(args[1].toLowerCase(Locale.ROOT)) : this.bandOf(p));
         default -> this.help(p);
      }

      return true;
   }

   private void help(Player p) {
      Msg.raw(p, "<dark_gray>———— <#FFD25E>Les listes</#FFD25E> ————</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste create</#55FF88> <gray>‹nom›</gray> <dark_gray>— fonder ta liste</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste invite</#55FF88> <gray>‹joueur› [role]</gray> <dark_gray>— inviter quelqu'un</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste accept</#55FF88> <dark_gray>— accepter une invitation</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste role</#55FF88> <gray>‹joueur› ‹role›</gray> <dark_gray>— changer un role</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste kick</#55FF88> <gray>‹joueur›</gray> <dark_gray>— exclure</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste quitter</#55FF88> <dark_gray>— partir de ta liste</dark_gray>");
      Msg.raw(p, " <#55FF88>/liste supprimer</#55FF88> <dark_gray>— dissoudre (proprietaire)</dark_gray>");
      Msg.raw(p, "<gray>Entre membres : pas de coups, et /tpa sans attente.</gray>");
   }

   private void create(Player p, String[] args) {
      if (args.length < 2) {
         Msg.err(p, "Il faut un nom : /liste create ‹nom›");
         return;
      }

      if (this.bandOf(p) != null) {
         Msg.err(p, "Tu es deja dans une liste. Quitte-la d'abord (/liste quitter).");
         return;
      }

      String name = args[1].trim();
      if (!name.matches("[A-Za-z0-9_\\-]{3,16}")) {
         Msg.err(p, "Nom invalide : 3 a 16 caracteres, lettres, chiffres, _ et - seulement.");
         return;
      }

      String key = name.toLowerCase(Locale.ROOT);
      if (this.bands.containsKey(key)) {
         Msg.err(p, "Ce nom est deja pris.");
         return;
      }

      Lists.Band band = new Lists.Band(name, p.getUniqueId(), this.nextColor());
      band.members.put(p.getUniqueId(), "chef");
      this.bands.put(key, band);
      this.save();
      Msg.ok(p, "La liste <" + band.color + ">" + name + "</" + band.color + "> est fondee. Invite du monde avec /liste invite ‹joueur›.");
      this.pl.tab().refresh(p);
      Util.sound(p, "entity.player.levelup", 0.7F, 1.4F);
   }

   private void invite(Player p, String[] args) {
      Lists.Band band = this.bandOf(p);
      if (band == null) {
         Msg.err(p, "Tu n'as pas de liste. Cree-la avec /liste create ‹nom›.");
         return;
      }

      if (!band.owner.equals(p.getUniqueId())) {
         Msg.err(p, "Seul le proprietaire de la liste peut inviter.");
         return;
      }

      if (args.length < 2) {
         Msg.err(p, "Qui invites-tu ? /liste invite ‹joueur› [role]");
         return;
      }

      Player target = Bukkit.getPlayerExact(args[1]);
      if (target == null || !this.pl.auth().isLogged(target)) {
         Msg.err(p, "Ce joueur n'est pas connecte.");
         return;
      }

      if (target.equals(p)) {
         Msg.err(p, "Tu es deja dedans.");
         return;
      }

      if (band.members.size() >= MAX_MEMBERS) {
         Msg.err(p, "Ta liste est pleine (" + MAX_MEMBERS + " membres maximum).");
         return;
      }

      if (this.bandOf(target) != null) {
         Msg.err(p, "<white><n></white> est deja dans une liste.", Msg.p("n", target.getName()));
         return;
      }

      String role = args.length >= 3 ? joinFrom(args, "as".equalsIgnoreCase(args[2]) ? 3 : 2) : "membre";
      if (role.isBlank()) {
         role = "membre";
      }

      if (role.length() > 16) {
         role = role.substring(0, 16);
      }

      this.invites.put(target.getUniqueId(), band.name.toLowerCase(Locale.ROOT) + "|" + role);
      Msg.ok(p, "Invitation envoyee a <white><n></white>.", Msg.p("n", target.getName()));
      Msg.raw(
         target,
         "<dark_gray>» </dark_gray><white>" + p.getName() + "</white> <gray>t'invite dans la liste </gray><" + band.color + ">" + band.name
            + "</" + band.color + "> <gray>comme </gray><white>" + role + "</white><gray>.</gray>"
      );
      Msg.raw(target, "<gray>   <click:run_command:'/liste accept'><hover:show_text:'<gray>Rejoindre'><#55FF88>[Accepter]</#55FF88></hover></click>   <click:run_command:'/liste refuse'><#FF5555>[Refuser]</#FF5555></click>");
      Util.sound(target, "entity.experience_orb.pickup", 0.8F, 1.2F);
   }

   private static String joinFrom(String[] args, int from) {
      StringBuilder sb = new StringBuilder();

      for (int i = from; i < args.length; i++) {
         if (!sb.isEmpty()) {
            sb.append(' ');
         }

         sb.append(args[i]);
      }

      return sb.toString().replaceAll("[^A-Za-z0-9 _\\-]", "").trim();
   }

   private void accept(Player p) {
      String pending = this.invites.remove(p.getUniqueId());
      if (pending == null) {
         Msg.err(p, "Aucune invitation en attente.");
         return;
      }

      String[] parts = pending.split("\\|", 2);
      Lists.Band band = this.bands.get(parts[0]);
      if (band == null) {
         Msg.err(p, "Cette liste n'existe plus.");
         return;
      }

      if (this.bandOf(p) != null) {
         Msg.err(p, "Tu es deja dans une liste.");
         return;
      }

      if (band.members.size() >= MAX_MEMBERS) {
         Msg.err(p, "Cette liste est pleine.");
         return;
      }

      band.members.put(p.getUniqueId(), parts.length > 1 ? parts[1] : "membre");
      this.save();
      this.announce(band, "<white>" + p.getName() + "</white> <gray>rejoint la liste.</gray>");
      this.pl.tab().refresh(p);
      Util.sound(p, "entity.player.levelup", 0.7F, 1.4F);
   }

   private void refuse(Player p) {
      if (this.invites.remove(p.getUniqueId()) == null) {
         Msg.err(p, "Aucune invitation en attente.");
      } else {
         Msg.info(p, "Invitation refusee.");
      }
   }

   private void kick(Player p, String[] args) {
      Lists.Band band = this.bandOf(p);
      if (band == null || !band.owner.equals(p.getUniqueId())) {
         Msg.err(p, "Seul le proprietaire de la liste peut exclure.");
         return;
      }

      if (args.length < 2) {
         Msg.err(p, "Qui exclus-tu ? /liste kick ‹joueur›");
         return;
      }

      UUID target = this.find(band, args[1]);
      if (target == null) {
         Msg.err(p, "Ce joueur n'est pas dans ta liste.");
         return;
      }

      if (target.equals(p.getUniqueId())) {
         Msg.err(p, "Pour partir, utilise /liste supprimer.");
         return;
      }

      band.members.remove(target);
      this.save();
      this.announce(band, "<white>" + nameOf(target) + "</white> <gray>a ete exclu de la liste.</gray>");
      Player online = Bukkit.getPlayer(target);
      if (online != null) {
         Msg.err(online, "Tu as ete exclu de la liste <" + band.color + ">" + band.name + "</" + band.color + ">.");
         this.pl.tab().refresh(online);
      }
   }

   private void role(Player p, String[] args) {
      Lists.Band band = this.bandOf(p);
      if (band == null || !band.owner.equals(p.getUniqueId())) {
         Msg.err(p, "Seul le proprietaire de la liste peut changer les roles.");
         return;
      }

      if (args.length < 3) {
         Msg.err(p, "/liste role ‹joueur› ‹role›");
         return;
      }

      UUID target = this.find(band, args[1]);
      if (target == null) {
         Msg.err(p, "Ce joueur n'est pas dans ta liste.");
         return;
      }

      String role = joinFrom(args, 2);
      if (role.isBlank()) {
         role = "membre";
      }

      if (role.length() > 16) {
         role = role.substring(0, 16);
      }

      band.members.put(target, role);
      this.save();
      this.announce(band, "<white>" + nameOf(target) + "</white> <gray>est maintenant </gray><white>" + role + "</white><gray>.</gray>");
      Player online = Bukkit.getPlayer(target);
      if (online != null) {
         this.pl.tab().refresh(online);
      }
   }

   private void quit(Player p) {
      Lists.Band band = this.bandOf(p);
      if (band == null) {
         Msg.err(p, "Tu n'es dans aucune liste.");
         return;
      }

      if (band.owner.equals(p.getUniqueId())) {
         Msg.err(p, "Tu es le proprietaire : utilise /liste supprimer, ou passe la main avec /liste role.");
         return;
      }

      band.members.remove(p.getUniqueId());
      this.save();
      this.announce(band, "<white>" + p.getName() + "</white> <gray>a quitte la liste.</gray>");
      Msg.info(p, "Tu as quitte la liste.");
      this.pl.tab().refresh(p);
   }

   private void delete(Player p) {
      Lists.Band band = this.bandOf(p);
      if (band == null || !band.owner.equals(p.getUniqueId())) {
         Msg.err(p, "Seul le proprietaire peut dissoudre la liste.");
         return;
      }

      this.announce(band, "<gray>La liste </gray><" + band.color + ">" + band.name + "</" + band.color + "> <gray>est dissoute.</gray>");
      List<UUID> members = new ArrayList<>(band.members.keySet());
      this.bands.remove(band.name.toLowerCase(Locale.ROOT));
      this.save();

      for (UUID uuid : members) {
         Player online = Bukkit.getPlayer(uuid);
         if (online != null) {
            this.pl.tab().refresh(online);
         }
      }
   }

   private void info(Player p, Lists.Band band) {
      if (band == null) {
         Msg.info(p, "Tu n'es dans aucune liste. /liste create ‹nom› pour en fonder une.");
         return;
      }

      Msg.raw(p, "<dark_gray>———— <" + band.color + ">" + band.name + "</" + band.color + "> <dark_gray>————</dark_gray>");
      Msg.raw(p, "<gray>Membres : <white>" + band.members.size() + "</white> / " + MAX_MEMBERS + "</gray>");

      for (Map.Entry<UUID, String> entry : band.members.entrySet()) {
         boolean online = Bukkit.getPlayer(entry.getKey()) != null;
         Msg.raw(
            p,
            " " + (online ? "<#55FF88>●</#55FF88>" : "<dark_gray>●</dark_gray>") + " <white>" + nameOf(entry.getKey()) + "</white> <"
               + band.color + ">(" + entry.getValue() + ")</" + band.color + ">" + (band.owner.equals(entry.getKey()) ? " <#FFD25E>★</#FFD25E>" : "")
         );
      }
   }

   private UUID find(Lists.Band band, String name) {
      for (UUID uuid : band.members.keySet()) {
         if (nameOf(uuid).equalsIgnoreCase(name)) {
            return uuid;
         }
      }

      return null;
   }

   private static String nameOf(UUID uuid) {
      PlayerData data = BDEIMT.get().data().get(uuid);
      if (data != null && data.name != null) {
         return data.name;
      }

      Player online = Bukkit.getPlayer(uuid);
      return online != null ? online.getName() : "?";
   }

   private void announce(Lists.Band band, String text) {
      for (UUID uuid : band.members.keySet()) {
         Player online = Bukkit.getPlayer(uuid);
         if (online != null) {
            Msg.raw(online, "<dark_gray>[</dark_gray><" + band.color + ">" + band.name + "</" + band.color + "><dark_gray>] </dark_gray>" + text);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      this.invites.remove(e.getPlayer().getUniqueId());
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      List<String> out = new ArrayList<>();
      if (args.length == 1) {
         out.addAll(List.of("create", "invite", "accept", "refuse", "role", "kick", "quitter", "supprimer", "info"));
         String start = args[0].toLowerCase(Locale.ROOT);
         out.removeIf(s -> !s.startsWith(start));
      } else if (args.length == 2 && sender instanceof Player p) {
         String sub = args[0].toLowerCase(Locale.ROOT);
         if (sub.equals("invite") || sub.equals("inviter")) {
            for (Player other : Bukkit.getOnlinePlayers()) {
               if (!other.equals(p) && this.bandOf(other) == null) {
                  out.add(other.getName());
               }
            }
         } else if (sub.equals("kick") || sub.equals("role")) {
            Lists.Band band = this.bandOf(p);
            if (band != null) {
               for (UUID uuid : band.members.keySet()) {
                  out.add(nameOf(uuid));
               }
            }
         }

         String start = args[1].toLowerCase(Locale.ROOT);
         out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(start));
      }

      return out;
   }
}
