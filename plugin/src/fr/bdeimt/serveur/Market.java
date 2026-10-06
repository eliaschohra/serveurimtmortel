package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Le marche du skyblock : les joueurs y vendent ce qu'ils veulent, au prix
 * qu'ils veulent.
 *
 * <p>{@code /marche vendre ‹prix›} met en vente l'objet qu'on tient en main ;
 * {@code /marche} ouvre les etals. Le vendeur est paye a la vente, meme s'il
 * n'est pas connecte. Une annonce peut etre retiree a tout moment par son
 * vendeur, qui recupere l'objet.
 *
 * <p>Les objets sont gardes dans {@code marche.yml}, au format binaire de
 * Paper, qui survit aux changements de version de Minecraft.
 */
public final class Market implements CommandExecutor {
   private static final int MAX_LISTINGS = 10;
   private static final long MAX_PRICE = 1_000_000_000L;
   private static final int PAGE = 45;

   /** Une annonce du marche. */
   public static final class Listing {
      final UUID id;
      final UUID seller;
      final String sellerName;
      final ItemStack item;
      final long price;
      final long created;

      Listing(UUID id, UUID seller, String sellerName, ItemStack item, long price, long created) {
         this.id = id;
         this.seller = seller;
         this.sellerName = sellerName;
         this.item = item;
         this.price = price;
         this.created = created;
      }
   }

   private final BDEIMT pl;
   private final File file;
   private final Map<UUID, Market.Listing> listings = new ConcurrentHashMap<>();

   public Market(BDEIMT pl) {
      this.pl = pl;
      this.file = new File(pl.getDataFolder(), "marche.yml");
   }

   public int size() {
      return this.listings.size();
   }

   // ------------------------------------------------------------ stockage

   public void load() {
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection root = yml.getConfigurationSection("annonces");
      this.listings.clear();

      if (root != null) {
         for (String key : root.getKeys(false)) {
            try {
               ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(root.getString(key + ".objet")));
               Market.Listing listing = new Market.Listing(
                  UUID.fromString(key),
                  UUID.fromString(root.getString(key + ".vendeur")),
                  root.getString(key + ".nom", "?"),
                  item,
                  root.getLong(key + ".prix"),
                  root.getLong(key + ".depuis")
               );
               this.listings.put(listing.id, listing);
            } catch (Exception ex) {
               this.pl.getLogger().warning("Annonce illisible dans marche.yml : " + key);
            }
         }
      }
   }

   public void save() {
      YamlConfiguration yml = new YamlConfiguration();

      for (Market.Listing listing : this.listings.values()) {
         String path = "annonces." + listing.id;
         yml.set(path + ".vendeur", listing.seller.toString());
         yml.set(path + ".nom", listing.sellerName);
         yml.set(path + ".objet", Base64.getEncoder().encodeToString(listing.item.serializeAsBytes()));
         yml.set(path + ".prix", listing.price);
         yml.set(path + ".depuis", listing.created);
      }

      try {
         yml.save(this.file);
      } catch (IOException e) {
         this.pl.getLogger().warning("marche.yml : " + e.getMessage());
      }
   }

   private List<Market.Listing> sorted() {
      List<Market.Listing> all = new ArrayList<>(this.listings.values());
      all.sort(Comparator.comparingLong((Market.Listing l) -> -l.created));
      return all;
   }

   // ------------------------------------------------------------ commande

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (Msg.noConsole(sender)) {
         return true;
      }

      Player p = (Player)sender;

      if (!this.pl.auth().isLogged(p)) {
         return true;
      }

      if (!this.pl.worlds().zoneOf(p).group.equals(Zone.SKYBLOCK.group)) {
         Msg.err(p, "Le marché est dans le skyblock.");
         return true;
      }

      if (args.length >= 1 && (args[0].equalsIgnoreCase("vendre") || args[0].equalsIgnoreCase("sell"))) {
         this.sell(p, args);
         return true;
      }

      if (args.length >= 1 && (args[0].equalsIgnoreCase("aide") || args[0].equalsIgnoreCase("help"))) {
         this.help(p);
         return true;
      }

      new Market.MarketMenu(this, p, 0).open(p);
      return true;
   }

   private void help(Player p) {
      Msg.raw(p, "<dark_gray>———— <#FFD25E>Le marché</#FFD25E> ————</dark_gray>");
      Msg.raw(p, " <#55FF88>/marche</#55FF88> <dark_gray>— voir les étals, acheter</dark_gray>");
      Msg.raw(p, " <#55FF88>/marche vendre</#55FF88> <gray>‹prix›</gray> <dark_gray>— vendre l'objet en main</dark_gray>");
      Msg.raw(p, " <gray>Clic sur sa propre annonce pour la retirer. " + MAX_LISTINGS + " annonces maximum par joueur.</gray>");
   }

   private void sell(Player p, String[] args) {
      if (args.length < 2) {
         Msg.err(p, "/marche vendre ‹prix›  <gray>(avec l'objet à vendre en main)</gray>");
         return;
      }

      long price = Economy.parseAmount(args[1]);

      if (price <= 0L || price > MAX_PRICE) {
         Msg.err(p, "Prix invalide : entre 1 et " + Economy.format(MAX_PRICE) + ".");
         return;
      }

      ItemStack hand = p.getInventory().getItemInMainHand();

      if (hand.getType().isAir()) {
         Msg.err(p, "Prends en main l'objet à vendre.");
         return;
      }

      long mine = this.listings.values().stream().filter(l -> l.seller.equals(p.getUniqueId())).count();

      if (mine >= MAX_LISTINGS) {
         Msg.err(p, "Tu as déjà " + MAX_LISTINGS + " annonces. Retire-en une depuis /marche.");
         return;
      }

      ItemStack item = hand.clone();
      p.getInventory().setItemInMainHand(null);
      Market.Listing listing = new Market.Listing(UUID.randomUUID(), p.getUniqueId(), p.getName(), item, price, System.currentTimeMillis());
      this.listings.put(listing.id, listing);
      this.save();
      Msg.ok(p, "En vente : <white>" + item.getAmount() + " × " + name(item) + "</white> pour <#FFD25E>" + Economy.format(price) + "</#FFD25E>.");

      for (Player other : Bukkit.getOnlinePlayers()) {
         if (!other.equals(p) && this.pl.auth().isLogged(other) && this.pl.worlds().zoneOf(other).group.equals(Zone.SKYBLOCK.group)) {
            Msg.raw(
               other,
               "<dark_gray>[</dark_gray><#FFD25E>Marché</#FFD25E><dark_gray>]</dark_gray> <white>" + p.getName() + "</white> <gray>vend</gray> <white>"
                  + item.getAmount() + " × " + name(item) + "</white> <gray>pour</gray> <#FFD25E>" + Economy.format(price) + "</#FFD25E> <click:run_command:'/marche'><#55FF88>[voir]</#55FF88></click>"
            );
         }
      }
   }

   static String name(ItemStack item) {
      String raw = item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
      return raw.replace("<", "");
   }

   // ------------------------------------------------------------ rappel

   /** Toutes les quinze minutes, dans le chat du skyblock seulement. */
   public void remind() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p) && this.pl.worlds().zoneOf(p).group.equals(Zone.SKYBLOCK.group)) {
            Msg.raw(
               p,
               "<dark_gray>[</dark_gray><#FFD25E>Marché</#FFD25E><dark_gray>]</dark_gray> <gray>Tu peux mettre en vente ce que tu veux : "
                  + "prends l'objet en main et tape</gray> <white>/marche vendre ‹prix›</white><gray>.</gray> <white>" + this.listings.size()
                  + "</white> <gray>annonce(s) en ce moment —</gray> <click:run_command:'/marche'><#55FF88>[voir les étals]</#55FF88></click>"
            );
         }
      }
   }

   // -------------------------------------------------------------- le menu

   public static final class MarketMenu extends Menu {
      private final Market market;
      private final int page;
      private final List<Market.Listing> shown;

      MarketMenu(Market market, Player viewer, int page) {
         super(6, Msg.mm("<dark_gray>» </dark_gray><#FFD25E>Le marché</#FFD25E> <dark_gray>— page " + (page + 1) + "</dark_gray>"));
         this.market = market;
         this.page = page;
         this.shown = market.sorted();
         this.draw(viewer);
      }

      private void draw(Player viewer) {
         int from = this.page * PAGE;

         for (int i = 0; i < PAGE && from + i < this.shown.size(); i++) {
            Market.Listing listing = this.shown.get(from + i);
            ItemStack icon = listing.item.clone();
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("<#FFD25E>Prix : " + Economy.format(listing.price) + "</#FFD25E>");
            lore.add("<gray>Vendeur : <white>" + listing.sellerName + "</white></gray>");
            lore.add(" ");
            lore.add(listing.seller.equals(viewer.getUniqueId()) ? "<#FF8A3D>Clic : retirer ton annonce</#FF8A3D>" : "<#55FF88>Clic : acheter</#55FF88>");
            this.inv.setItem(i, Util.lore(icon, lore));
         }

         if (this.page > 0) {
            this.inv.setItem(45, Util.item(Material.ARROW, 1, "<white>Page précédente</white>"));
         }

         if (from + PAGE < this.shown.size()) {
            this.inv.setItem(53, Util.item(Material.ARROW, 1, "<white>Page suivante</white>"));
         }

         this.inv.setItem(
            49,
            Util.item(
               Material.GOLD_NUGGET,
               1,
               "<#FFD25E><bold>Ton solde</bold></#FFD25E>",
               "<white>" + Economy.format(this.market.pl.economy().balance(viewer)) + "</white>",
               " ",
               "<gray>Vendre : objet en main, puis</gray>",
               "<white>/marche vendre ‹prix›</white>"
            )
         );
         this.fill();
      }

      @Override
      public void click(Player p, int slot) {
         this.click(p, slot, ClickType.LEFT);
      }

      @Override
      public void click(Player p, int slot, ClickType type) {
         if (slot == 45 && this.page > 0) {
            new Market.MarketMenu(this.market, p, this.page - 1).open(p);
            return;
         }

         if (slot == 53 && (this.page + 1) * PAGE < this.shown.size()) {
            new Market.MarketMenu(this.market, p, this.page + 1).open(p);
            return;
         }

         int index = this.page * PAGE + slot;

         if (slot >= PAGE || index >= this.shown.size()) {
            return;
         }

         Market.Listing listing = this.market.listings.get(this.shown.get(index).id);

         if (listing == null) {
            Msg.err(p, "Cette annonce n'est plus là.");
         } else if (listing.seller.equals(p.getUniqueId())) {
            this.market.listings.remove(listing.id);
            this.market.save();
            Util.give(p, List.of(listing.item.clone()));
            Msg.ok(p, "Annonce retirée, l'objet est revenu dans ton inventaire.");
         } else {
            this.market.buy(p, listing);
         }

         new Market.MarketMenu(this.market, p, this.page).open(p);
      }
   }

   private void buy(Player p, Market.Listing listing) {
      // L'annonce est retiree AVANT tout paiement : deux acheteurs au meme
      // instant ne peuvent pas obtenir le meme objet.
      if (this.listings.remove(listing.id) == null) {
         Msg.err(p, "Quelqu'un a été plus rapide.");
         return;
      }

      if (!this.pl.economy().take(p.getUniqueId(), listing.price)) {
         this.listings.put(listing.id, listing);
         Msg.err(p, "Il te faut <white>" + Economy.format(listing.price) + "</white>, tu as <white>" + Economy.format(this.pl.economy().balance(p)) + "</white>.");
         return;
      }

      this.pl.economy().give(listing.seller, listing.price);
      this.save();
      this.pl.data().save(this.pl.data().get(p));
      PlayerData seller = this.pl.data().get(listing.seller);

      if (seller != null) {
         this.pl.data().save(seller);
      }

      Util.give(p, List.of(listing.item.clone()));
      Msg.ok(p, "Acheté : <white>" + listing.item.getAmount() + " × " + name(listing.item) + "</white> pour <#FFD25E>" + Economy.format(listing.price) + "</#FFD25E>.");
      Util.sound(p, "entity.villager.yes", 0.7F, 1.0F);
      Player online = Bukkit.getPlayer(listing.seller);

      if (online != null) {
         Msg.alert(online, "<#FFD25E><bold>Vendu !</bold></#FFD25E>", "<white>" + p.getName() + "</white> <gray>t'a acheté</gray> <white>" + name(listing.item) + "</white> <gray>· +" + Economy.format(listing.price) + "</gray>");
      }
   }
}
