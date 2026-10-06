package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class State {
   private final File file;
   public final Map<UUID, String> modos = new ConcurrentHashMap<>();
   public final List<Integer> mapIds = new ArrayList<>();
   public long nextDragon;
   public boolean dragonWarned;
   public int lobbyVersion;
   public long lastGuideReminder;
   public volatile boolean maintenance;
   public final Map<String, Integer> listVotes = new LinkedHashMap<>();

   public State(File var1) {
      this.file = new File(var1, "etat.yml");
   }

   public void load() {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection var2 = var1.getConfigurationSection("modos");
      if (var2 != null) {
         for (String var4 : var2.getKeys(false)) {
            try {
               this.modos.put(UUID.fromString(var4), var2.getString(var4));
            } catch (IllegalArgumentException var6) {
            }
         }
      }

      this.mapIds.addAll(var1.getIntegerList("cartes-image"));
      this.nextDragon = var1.getLong("prochain-dragon");
      this.dragonWarned = var1.getBoolean("dragon-averti");
      this.lobbyVersion = var1.getInt("lobby-version");
      this.maintenance = var1.getBoolean("maintenance");
      ConfigurationSection var7 = var1.getConfigurationSection("votes-listes");
      if (var7 != null) {
         for (String var5 : var7.getKeys(false)) {
            this.listVotes.put(var5, var7.getInt(var5));
         }
      }
   }

   public void save() {
      YamlConfiguration var1 = new YamlConfiguration();

      for (Entry var3 : this.modos.entrySet()) {
         var1.set("modos." + var3.getKey(), var3.getValue());
      }

      var1.set("cartes-image", this.mapIds);
      var1.set("prochain-dragon", this.nextDragon);
      var1.set("dragon-averti", this.dragonWarned);
      var1.set("lobby-version", this.lobbyVersion);
      var1.set("maintenance", this.maintenance);

      for (Entry var6 : this.listVotes.entrySet()) {
         var1.set("votes-listes." + (String)var6.getKey(), var6.getValue());
      }

      try {
         var1.save(this.file);
      } catch (IOException var4) {
         BDEIMT.get().getLogger().warning("etat.yml : " + var4.getMessage());
      }
   }
}
