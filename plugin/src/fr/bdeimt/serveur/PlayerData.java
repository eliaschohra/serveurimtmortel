package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerData {
   public final UUID uuid;
   public volatile String name;
   public volatile int votes;
   public volatile long aura;
   public volatile long lastVote;
   public final Map<String, Long> kitUses = new ConcurrentHashMap<>();
   public final Map<String, String> homes = new LinkedHashMap<>();
   public volatile int flySeconds;
   public volatile boolean guideSeen;
   public volatile String returnLoc;
   /** Le groupe de mondes ou se trouve son inventaire actuel. */
   public volatile String worldGroup;
   public volatile String savedGameMode;
   public volatile long mutedUntil;
   public volatile String muteReason;
   public final Set<UUID> ignores = ConcurrentHashMap.newKeySet();
   public volatile boolean tpaOff;
   public final List<String> pendingLoots = new ArrayList<>();
   public volatile long firstJoin;
   public volatile boolean dirty;

   public PlayerData(UUID var1, String var2) {
      this.uuid = var1;
      this.name = var2;
   }

   public boolean isMuted() {
      long var1 = this.mutedUntil;
      if (var1 == 0L) {
         return false;
      } else if (var1 == -1L) {
         return true;
      } else if (System.currentTimeMillis() >= var1) {
         this.mutedUntil = 0L;
         this.dirty = true;
         return false;
      } else {
         return true;
      }
   }

   public void touch() {
      this.dirty = true;
   }

   public static PlayerData load(File var0) {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(var0);
      UUID var2 = UUID.fromString(var0.getName().replace(".yml", ""));
      PlayerData var3 = new PlayerData(var2, var1.getString("nom", "?"));
      var3.votes = var1.getInt("votes");
      var3.aura = var1.getLong("aura");
      var3.lastVote = var1.getLong("dernier-vote");
      ConfigurationSection var4 = var1.getConfigurationSection("kits");
      if (var4 != null) {
         for (String var6 : var4.getKeys(false)) {
            var3.kitUses.put(var6, var4.getLong(var6));
         }
      }

      ConfigurationSection var10 = var1.getConfigurationSection("homes");
      if (var10 != null) {
         for (String var7 : var10.getKeys(false)) {
            var3.homes.put(var7, var10.getString(var7));
         }
      }

      var3.flySeconds = var1.getInt("fly-secondes");
      var3.guideSeen = var1.getBoolean("guide-vu");
      var3.returnLoc = var1.getString("position-retour");
      var3.worldGroup = var1.getString("groupe-de-mondes");
      var3.savedGameMode = var1.getString("mode-de-jeu");
      var3.mutedUntil = var1.getLong("mute-jusqua");
      var3.muteReason = var1.getString("mute-raison");

      for (String var13 : var1.getStringList("ignores")) {
         try {
            var3.ignores.add(UUID.fromString(var13));
         } catch (IllegalArgumentException var9) {
         }
      }

      var3.tpaOff = var1.getBoolean("tpa-off");
      var3.pendingLoots.addAll(var1.getStringList("lots-en-attente"));
      var3.firstJoin = var1.getLong("premiere-connexion");
      return var3;
   }

   public synchronized String serialize() {
      YamlConfiguration var1 = new YamlConfiguration();
      var1.set("nom", this.name);
      var1.set("votes", this.votes);
      var1.set("aura", this.aura);
      var1.set("dernier-vote", this.lastVote);

      for (Entry var3 : this.kitUses.entrySet()) {
         var1.set("kits." + (String)var3.getKey(), var3.getValue());
      }

      for (Entry var7 : this.homes.entrySet()) {
         var1.set("homes." + (String)var7.getKey(), var7.getValue());
      }

      var1.set("fly-secondes", this.flySeconds);
      var1.set("guide-vu", this.guideSeen);
      var1.set("position-retour", this.returnLoc);
      var1.set("groupe-de-mondes", this.worldGroup);
      var1.set("mode-de-jeu", this.savedGameMode);
      var1.set("mute-jusqua", this.mutedUntil);
      var1.set("mute-raison", this.muteReason);
      ArrayList var6 = new ArrayList();

      for (UUID var4 : this.ignores) {
         var6.add(var4.toString());
      }

      var1.set("ignores", var6);
      var1.set("tpa-off", this.tpaOff);
      var1.set("lots-en-attente", new ArrayList<>(this.pendingLoots));
      var1.set("premiere-connexion", this.firstJoin);
      return var1.saveToString();
   }

   public void save(File var1) {
      try {
         Files.writeString(new File(var1, this.uuid + ".yml").toPath(), this.serialize());
         this.dirty = false;
      } catch (IOException var3) {
         BDEIMT.get().getLogger().warning("Sauvegarde impossible pour " + this.name + " : " + var3.getMessage());
      }
   }
}
