package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Un inventaire par univers.
 *
 * <p>Le stuff de la survie ne doit pas suivre le joueur dans le lobby, le
 * parkour, le skyblock ou les parcelles : chaque groupe de zones garde son
 * propre inventaire, son experience, sa vie et sa faim, dans
 * {@code plugins/BDEIMT/inventaires/<uuid>.yml}.
 *
 * <p>Les objets sont ranges en base64 du format binaire de Paper, le seul qui
 * se met a jour tout seul quand Minecraft change de version.
 */
public final class Inventories {
   private final BDEIMT pl;
   private final File folder;
   /** Le groupe dans lequel se trouve chaque joueur connecte. */
   private final Map<UUID, String> current = new ConcurrentHashMap<>();

   public Inventories(BDEIMT pl) {
      this.pl = pl;
      this.folder = new File(pl.getDataFolder(), "inventaires");
      if (!this.folder.exists() && !this.folder.mkdirs()) {
         pl.getLogger().warning("Dossier inventaires impossible a creer");
      }
   }

   private File file(UUID uuid) {
      return new File(this.folder, uuid + ".yml");
   }

   /**
    * Le groupe ou se trouve l'inventaire que le joueur porte en ce moment.
    *
    * <p>On ne peut pas le deduire du monde ou il est : quelqu'un qui se
    * deconnecte avant de s'etre identifie a deja ete pose dans le hub, mais
    * porte encore l'inventaire de la survie. Le groupe est donc ecrit dans sa
    * fiche, et c'est elle qui fait foi.
    */
   public String currentGroup(Player p) {
      String g = this.current.get(p.getUniqueId());

      if (g != null) {
         return g;
      }

      PlayerData data = this.pl.data().get(p);

      if (data != null && data.worldGroup != null && !data.worldGroup.isBlank()) {
         return data.worldGroup;
      }

      return this.pl.worlds().zoneOf(p).group;
   }

   public void setCurrentGroup(Player p, String group) {
      this.current.put(p.getUniqueId(), group);
      PlayerData data = this.pl.data().get(p);

      if (data != null) {
         data.worldGroup = group;
         data.touch();
      }
   }

   public void forget(Player p) {
      this.current.remove(p.getUniqueId());
   }

   /**
    * Passe le joueur du groupe ou il se trouve a un autre : range l'inventaire
    * courant, sort celui de la destination. Sans effet si c'est le meme groupe.
    */
   public void switchTo(Player p, String group) {
      String from = this.currentGroup(p);
      if (from.equals(group)) {
         this.setCurrentGroup(p, group);
         return;
      }

      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file(p.getUniqueId()));
      this.store(p, yml.createSection(from));
      this.restore(p, yml.getConfigurationSection(group));
      this.setCurrentGroup(p, group);

      try {
         yml.save(this.file(p.getUniqueId()));
      } catch (IOException e) {
         this.pl.getLogger().warning("Inventaire de " + p.getName() + " non sauvegarde : " + e.getMessage());
      }
   }

   /** Range l'inventaire courant du joueur sans rien changer a l'ecran. */
   public boolean save(Player p) {
      UUID uuid = p.getUniqueId();
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file(uuid));
      this.store(p, yml.createSection(this.currentGroup(p)));

      try {
         yml.save(this.file(uuid));
         return true;
      } catch (IOException e) {
         this.pl.getLogger().warning("Inventaire de " + p.getName() + " non sauvegarde : " + e.getMessage());
         return false;
      }
   }

   private void store(Player p, ConfigurationSection s) {
      PlayerInventory inv = p.getInventory();
      s.set("contenu", encode(inv.getContents()));
      s.set("enderchest", encode(p.getEnderChest().getContents()));
      s.set("slot", inv.getHeldItemSlot());
      s.set("niveau", p.getLevel());
      s.set("exp", p.getExp());
      s.set("faim", p.getFoodLevel());
      s.set("saturation", p.getSaturation());
      s.set("vie", p.getHealth());
      s.set("mode", p.getGameMode().name());
   }

   private void restore(Player p, ConfigurationSection s) {
      PlayerInventory inv = p.getInventory();
      p.closeInventory();

      for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
         p.removePotionEffect(effect.getType());
      }

      if (s == null) {
         inv.clear();
         p.getEnderChest().clear();
         inv.setHeldItemSlot(0);
         p.setLevel(0);
         p.setExp(0.0F);
         p.setFoodLevel(20);
         p.setSaturation(5.0F);
         p.setFireTicks(0);
         safeHeal(p, maxHealth(p));
         return;
      }

      inv.setContents(decode(s.getStringList("contenu"), inv.getSize()));
      p.getEnderChest().setContents(decode(s.getStringList("enderchest"), p.getEnderChest().getSize()));
      inv.setHeldItemSlot(Math.max(0, Math.min(8, s.getInt("slot"))));
      p.setLevel(Math.max(0, s.getInt("niveau")));
      p.setExp(Math.max(0.0F, Math.min(0.999F, (float)s.getDouble("exp"))));
      p.setFoodLevel(Math.max(0, Math.min(20, s.getInt("faim", 20))));
      p.setSaturation((float)s.getDouble("saturation", 5.0));
      p.setFireTicks(0);
      safeHeal(p, s.getDouble("vie", maxHealth(p)));
   }

   private static double maxHealth(Player p) {
      try {
         return p.getAttribute(Attribute.MAX_HEALTH).getValue();
      } catch (Throwable t) {
         return 20.0;
      }
   }

   private static void safeHeal(Player p, double health) {
      double max = maxHealth(p);
      p.setHealth(Math.max(0.5, Math.min(max, health <= 0.0 ? max : health)));
   }

   private static List<String> encode(ItemStack[] items) {
      List<String> out = new ArrayList<>(items.length);

      for (ItemStack item : items) {
         if (item == null || item.getType().isAir()) {
            out.add("");
         } else {
            try {
               out.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            } catch (Throwable t) {
               out.add("");
            }
         }
      }

      return out;
   }

   private static ItemStack[] decode(List<String> raw, int size) {
      ItemStack[] items = new ItemStack[size];

      for (int i = 0; i < size && i < raw.size(); i++) {
         String line = raw.get(i);
         if (line != null && !line.isEmpty()) {
            try {
               items[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(line));
            } catch (Throwable t) {
               items[i] = null;
            }
         }
      }

      return items;
   }

   /** Vide completement le joueur : utilise a l'arrivee dans une zone sans stuff. */
   public static void clear(Player p) {
      p.getInventory().clear();
      p.getInventory().setHeldItemSlot(4);
      p.setLevel(0);
      p.setExp(0.0F);
      p.setFoodLevel(20);
      p.setSaturation(5.0F);
      p.setFireTicks(0);
      p.setFallDistance(0.0F);

      for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
         p.removePotionEffect(effect.getType());
      }

      safeHeal(p, maxHealth(p));
      if (p.getGameMode() != GameMode.CREATIVE) {
         p.setAllowFlight(false);
         p.setFlying(false);
      }
   }

   /** Sauvegarde tout le monde, au /stop. */
   public void saveAll() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         try {
            if (this.pl.auth().isLogged(p)) {
               this.save(p);
            }
         } catch (Throwable t) {
            this.pl.getLogger().warning("Inventaire de " + p.getName() + " : " + t.getMessage());
         }
      }
   }
}
