package fr.bdeimt.serveur;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Les marchands du lobby skyblock.
 *
 * <p>Onze PNJ, chacun avec sa specialite et une vingtaine d'offres, decrits
 * dans {@code plugins/BDEIMT/marchands.yml} : les prix se changent par
 * FileZilla, sans toucher au plugin. On les pose a la main avec
 * {@code /imt marchand ‹nom›} ; comme Zaza et le Mobutu, ils restent la tant
 * que le serveur tourne et reviennent seuls s'ils disparaissent.
 *
 * <p>Un clic sur un marchand ouvre sa boutique : clic gauche pour acheter un
 * lot, clic droit pour en vendre un, avec Maj pour en faire beaucoup d'un coup.
 */
public final class Shops implements Listener {
   /** Une offre : un objet, un lot, et ce que coute ou rapporte un lot. */
   public record Offer(Material material, int amount, long buy, long sell, Enchantment book, int level) {
      ItemStack stack(int lots) {
         ItemStack item = new ItemStack(this.material, this.amount * Math.max(1, lots));

         if (this.book != null) {
            item.editMeta(meta -> {
               if (meta instanceof EnchantmentStorageMeta storage) {
                  storage.addStoredEnchant(this.book, this.level, true);
               }
            });
         }

         return item;
      }
   }

   /** Un marchand : son nom, sa couleur, son metier et ses offres. */
   public record Shop(String id, String name, String color, Villager.Profession profession, List<Offer> offers) {
   }

   private final BDEIMT pl;
   private final NamespacedKey key;
   private final Map<String, Shops.Shop> shops = new LinkedHashMap<>();

   public Shops(BDEIMT pl) {
      this.pl = pl;
      this.key = new NamespacedKey(pl, "marchand");
   }

   public Map<String, Shops.Shop> all() {
      return this.shops;
   }

   // ------------------------------------------------------------ catalogue

   /** Lit marchands.yml, en deposant celui du plugin la premiere fois. */
   public void load() {
      File file = new File(this.pl.getDataFolder(), "marchands.yml");

      if (!file.exists()) {
         this.pl.saveResource("marchands.yml", false);
      } else {
         this.upgrade(file);
      }

      YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
      this.shops.clear();
      int offers = 0;

      for (String id : yml.getKeys(false)) {
         ConfigurationSection s = yml.getConfigurationSection(id);

         if (s == null) {
            continue;
         }

         Villager.Profession profession = null;

         try {
            profession = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(s.getString("metier", "nitwit").toLowerCase(Locale.ROOT)));
         } catch (Throwable t) {
         }

         List<Offer> list = new ArrayList<>();

         for (String line : s.getStringList("offres")) {
            Offer offer = parse(line);

            if (offer == null) {
               this.pl.getLogger().warning("marchands.yml, " + id + " : offre illisible « " + line + " »");
            } else {
               list.add(offer);
            }
         }

         this.shops.put(id.toLowerCase(Locale.ROOT), new Shops.Shop(id.toLowerCase(Locale.ROOT), s.getString("nom", id), s.getString("couleur", "#FFFFFF"), profession, list));
         offers += list.size();
      }

      this.pl.getLogger().info(this.shops.size() + " marchand(s), " + offers + " offre(s).");
   }

   /**
    * Quand le plugin apporte un catalogue plus recent (numero « version »),
    * il remplace celui du serveur ; l'ancien est garde a cote, au cas ou.
    */
   private void upgrade(File file) {
      try (java.io.InputStream in = this.pl.getResource("marchands.yml")) {
         if (in == null) {
            return;
         }

         YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
         int wanted = bundled.getInt("version", 0);
         int current = YamlConfiguration.loadConfiguration(file).getInt("version", 0);

         if (wanted > current) {
            File old = new File(this.pl.getDataFolder(), "marchands-ancien.yml");
            java.nio.file.Files.copy(file.toPath(), old.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            this.pl.saveResource("marchands.yml", true);
            this.pl.getLogger().info("marchands.yml mis a jour (version " + wanted + ") ; l'ancien est dans marchands-ancien.yml.");
         }
      } catch (Exception ex) {
         this.pl.getLogger().warning("marchands.yml : mise a jour impossible (" + ex.getMessage() + ")");
      }
   }

   /** « MATERIAU LOT achat=X vente=Y livre=enchant:niveau » */
   static Offer parse(String line) {
      try {
         String[] p = line.trim().split("\\s+");
         Material material = Material.valueOf(p[0].toUpperCase(Locale.ROOT));
         int amount = Integer.parseInt(p[1]);
         long buy = 0L;
         long sell = 0L;
         Enchantment book = null;
         int level = 0;

         for (int i = 2; i < p.length; i++) {
            String[] kv = p[i].split("=", 2);

            switch (kv[0].toLowerCase(Locale.ROOT)) {
               case "achat" -> buy = Long.parseLong(kv[1]);
               case "vente" -> sell = Long.parseLong(kv[1]);
               case "livre" -> {
                  String[] e = kv[1].split(":");
                  book = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e[0].toLowerCase(Locale.ROOT)));
                  level = e.length > 1 ? Integer.parseInt(e[1]) : 1;

                  if (book == null) {
                     return null;
                  }
               }
               default -> {
                  return null;
               }
            }
         }

         if (amount < 1 || amount > 64 * 9 || buy < 0L || sell < 0L || buy == 0L && sell == 0L) {
            return null;
         }

         // Un livre enchante ne se revend pas : on ne saurait pas l'evaluer.
         return new Offer(material, amount, buy, book != null ? 0L : sell, book, level);
      } catch (Exception ex) {
         return null;
      }
   }

   // ------------------------------------------------------------- les PNJ

   public Location position(String id) {
      return Util.loc(this.pl.getConfig().getString("marchands." + id + ".position"));
   }

   public void place(String id, Location at) {
      this.pl.getConfig().set("marchands." + id + ".position", Util.loc(at));
      this.pl.saveConfig();
      this.spawn(id);
   }

   public void unplace(String id) {
      this.remove(id);
      this.pl.getConfig().set("marchands." + id, null);
      this.pl.saveConfig();
   }

   private void remove(String id) {
      for (World w : Bukkit.getWorlds()) {
         for (Entity entity : w.getEntities()) {
            if (id.equals(entity.getPersistentDataContainer().get(this.key, PersistentDataType.STRING))) {
               entity.remove();
            }
         }
      }
   }

   private Villager find(String id) {
      Location at = this.position(id);

      if (at == null || at.getWorld() == null) {
         return null;
      }

      for (Entity entity : at.getWorld().getEntities()) {
         if (entity instanceof Villager v && !v.isDead() && id.equals(entity.getPersistentDataContainer().get(this.key, PersistentDataType.STRING))) {
            return v;
         }
      }

      return null;
   }

   public void spawn(String id) {
      Shops.Shop shop = this.shops.get(id);
      Location at = this.position(id);

      if (shop == null || at == null || at.getWorld() == null) {
         return;
      }

      this.remove(id);
      at.getWorld().addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, this.pl);

      try {
         at.getWorld().spawn(at, Villager.class, v -> {
            v.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
            v.customName(Msg.mm("<" + shop.color() + "><bold>" + shop.name() + "</bold></" + shop.color() + ">"));
            v.setCustomNameVisible(true);

            if (shop.profession() != null) {
               v.setProfession(shop.profession());
            }

            v.setVillagerLevel(5);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(false);
            v.setRemoveWhenFarAway(false);
         });
      } catch (Throwable t) {
         this.pl.getLogger().warning("Marchand " + id + " : " + t.getMessage());
      }
   }

   /** Pose tous les marchands places, au demarrage. */
   public void spawnAll() {
      for (String id : this.shops.keySet()) {
         this.spawn(id);
      }
   }

   /** Toutes les dix secondes : un marchand qui manque revient. */
   public void watch() {
      for (String id : this.shops.keySet()) {
         Location at = this.position(id);

         if (at != null && at.getWorld() != null) {
            at.getWorld().addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, this.pl);

            if (this.find(id) == null) {
               this.spawn(id);
            }
         }
      }
   }

   public void clearAll() {
      for (String id : this.shops.keySet()) {
         this.remove(id);
      }
   }

   // --------------------------------------------------------- evenements

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTalk(PlayerInteractEntityEvent e) {
      String id = e.getRightClicked().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);

      if (id == null) {
         return;
      }

      // Pas l'ecran de troc des villageois : notre boutique.
      e.setCancelled(true);
      Shops.Shop shop = this.shops.get(id);

      if (shop != null && this.pl.auth().isLogged(e.getPlayer())) {
         Player p = e.getPlayer();
         Bukkit.getScheduler().runTask(this.pl, () -> new Shops.ShopMenu(this.pl, shop, p).open(p));
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDamage(EntityDamageEvent e) {
      if (e.getEntity().getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
         e.setCancelled(true);
      }
   }

   // ------------------------------------------------------------ la boutique

   /** Un objet « ordinaire » : ni renomme, ni enchante. Seuls ceux-la se revendent. */
   static boolean plain(ItemStack item, Material material) {
      return item != null
         && item.getType() == material
         && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName() && !item.getItemMeta().hasEnchants() && !item.getItemMeta().hasLore());
   }

   static int count(Player p, Material material) {
      int n = 0;

      for (ItemStack item : p.getInventory().getStorageContents()) {
         if (plain(item, material)) {
            n += item.getAmount();
         }
      }

      return n;
   }

   static void removeItems(Player p, Material material, int amount) {
      ItemStack[] contents = p.getInventory().getStorageContents();

      for (int i = 0; i < contents.length && amount > 0; i++) {
         ItemStack item = contents[i];

         if (plain(item, material)) {
            int take = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - take);
            amount -= take;

            if (item.getAmount() <= 0) {
               contents[i] = null;
            }
         }
      }

      p.getInventory().setStorageContents(contents);
   }

   public static final class ShopMenu extends Menu {
      private final BDEIMT pl;
      private final Shops.Shop shop;

      ShopMenu(BDEIMT pl, Shops.Shop shop, Player viewer) {
         super(6, Msg.mm("<dark_gray>» </dark_gray><" + shop.color() + ">" + shop.name()));
         this.pl = pl;
         this.shop = shop;
         this.draw(viewer);
      }

      private void draw(Player viewer) {
         this.inv.clear();

         for (int i = 0; i < this.shop.offers().size() && i < 45; i++) {
            Offer offer = this.shop.offers().get(i);
            ItemStack icon = offer.stack(1);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Par lot de <white>" + offer.amount() + "</white></gray>");
            lore.add(" ");

            if (offer.buy() > 0L) {
               lore.add("<#55FF88>Acheter : " + Economy.format(offer.buy()) + "</#55FF88>");
               lore.add("<dark_gray>  clic gauche · Maj : 8 lots</dark_gray>");
            } else {
               lore.add("<dark_gray>Ne se vend pas ici</dark_gray>");
            }

            if (offer.sell() > 0L) {
               lore.add("<#FFD25E>Revendre : " + Economy.format(offer.sell()) + "</#FFD25E>");
               lore.add("<dark_gray>  clic droit · Maj : tout</dark_gray>");
               int have = count(viewer, offer.material());

               if (have > 0) {
                  lore.add("<gray>  Tu en as <white>" + have + "</white></gray>");
               }
            }

            this.inv.setItem(i, Util.lore(icon, lore));
         }

         this.inv.setItem(
            49,
            Util.item(
               Material.GOLD_NUGGET,
               1,
               "<#FFD25E><bold>Ton solde</bold></#FFD25E>",
               "<white>" + Economy.format(this.pl.economy().balance(viewer)) + "</white>",
               " ",
               "<gray>Vends aux marchands ou sur /marche</gray>"
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
         if (slot < 0 || slot >= this.shop.offers().size() || slot >= 45) {
            return;
         }

         Offer offer = this.shop.offers().get(slot);

         if (type.isLeftClick()) {
            this.buy(p, offer, type.isShiftClick() ? 8 : 1);
         } else if (type.isRightClick()) {
            this.sell(p, offer, type.isShiftClick());
         }

         this.draw(p);
      }

      private void buy(Player p, Offer offer, int lots) {
         if (offer.buy() <= 0L) {
            Msg.err(p, "Ce marchand ne vend pas ça.");
            return;
         }

         long price = offer.buy() * lots;

         if (!this.pl.economy().take(p.getUniqueId(), price)) {
            Msg.err(p, "Il te faut <white>" + Economy.format(price) + "</white>, tu as <white>" + Economy.format(this.pl.economy().balance(p)) + "</white>.");
            Util.sound(p, "entity.villager.no", 0.7F, 1.0F);
            return;
         }

         Util.give(p, List.of(offer.stack(lots)));
         Util.sound(p, "entity.villager.yes", 0.7F, 1.0F);
         Msg.bar(p, "<#55FF88>-" + Economy.format(price) + "</#55FF88> <gray>· solde</gray> <white>" + Economy.format(this.pl.economy().balance(p)) + "</white>");
      }

      private void sell(Player p, Offer offer, boolean all) {
         if (offer.sell() <= 0L) {
            Msg.err(p, "Ce marchand n'achète pas ça.");
            return;
         }

         int have = count(p, offer.material());
         int lots = all ? have / offer.amount() : (have >= offer.amount() ? 1 : 0);

         if (lots <= 0) {
            Msg.err(p, "Il te faut au moins <white>" + offer.amount() + "</white> de cet objet, ordinaire (ni renommé ni enchanté).");
            Util.sound(p, "entity.villager.no", 0.7F, 1.0F);
            return;
         }

         removeItems(p, offer.material(), lots * offer.amount());
         long gain = offer.sell() * lots;
         this.pl.economy().give(p.getUniqueId(), gain);
         Util.sound(p, "block.note_block.chime", 0.7F, 1.4F);
         Msg.bar(p, "<#FFD25E>+" + Economy.format(gain) + "</#FFD25E> <gray>· solde</gray> <white>" + Economy.format(this.pl.economy().balance(p)) + "</white>");
      }
   }
}
