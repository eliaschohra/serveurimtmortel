package fr.bdeimt.serveur;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class DataStore {
   private final File dir;
   private final Map<UUID, PlayerData> byId = new ConcurrentHashMap<>();
   private final Map<String, UUID> byName = new ConcurrentHashMap<>();

   public DataStore(File var1) {
      this.dir = new File(var1, "joueurs");
      if (!this.dir.exists() && !this.dir.mkdirs()) {
         throw new IllegalStateException("Dossier joueurs impossible a creer");
      }
   }

   public void loadAll() {
      File[] var1 = this.dir.listFiles((var0, var1x) -> var1x.endsWith(".yml"));
      if (var1 != null) {
         for (File var5 : var1) {
            try {
               PlayerData var6 = PlayerData.load(var5);
               this.byId.put(var6.uuid, var6);
               this.byName.put(var6.name.toLowerCase(Locale.ROOT), var6.uuid);
            } catch (Exception var7) {
               BDEIMT.get().getLogger().warning("Fiche illisible ignoree : " + var5.getName());
            }
         }
      }
   }

   public PlayerData get(Player var1) {
      PlayerData var2 = this.byId.computeIfAbsent(var1.getUniqueId(), var1x -> {
         PlayerData var2x = new PlayerData(var1x, var1.getName());
         var2x.firstJoin = System.currentTimeMillis();
         var2x.dirty = true;
         return var2x;
      });
      if (!var1.getName().equals(var2.name)) {
         this.byName.remove(var2.name.toLowerCase(Locale.ROOT));
         var2.name = var1.getName();
         var2.dirty = true;
      }

      this.byName.put(var2.name.toLowerCase(Locale.ROOT), var2.uuid);
      return var2;
   }

   public PlayerData get(UUID var1) {
      return this.byId.get(var1);
   }

   public PlayerData byName(String var1) {
      if (var1 == null) {
         return null;
      } else {
         UUID var2 = this.byName.get(var1.toLowerCase(Locale.ROOT));
         return var2 == null ? null : this.byId.get(var2);
      }
   }

   public Collection<PlayerData> all() {
      return this.byId.values();
   }

   public void save(PlayerData var1) {
      var1.save(this.dir);
   }

   public void saveDirty() {
      for (PlayerData var2 : this.byId.values()) {
         if (var2.dirty) {
            var2.save(this.dir);
         }
      }
   }

   public void saveAll() {
      for (PlayerData var2 : this.byId.values()) {
         var2.save(this.dir);
      }
   }

   public List<PlayerData> ranking() {
      ArrayList var1 = new ArrayList();

      for (PlayerData var3 : this.byId.values()) {
         if (var3.votes > 0) {
            var1.add(var3);
         }
      }

      var1.sort(Comparator.<PlayerData>comparingInt(var0 -> -var0.votes).thenComparing(var0 -> var0.name.toLowerCase(Locale.ROOT)));
      return var1;
   }

   public int rankOf(UUID var1) {
      List var2 = this.ranking();

      for (int var3 = 0; var3 < var2.size(); var3++) {
         if (((PlayerData)var2.get(var3)).uuid.equals(var1)) {
            return var3 + 1;
         }
      }

      return 0;
   }

   public List<PlayerData> auraRanking() {
      ArrayList var1 = new ArrayList();

      for (PlayerData var3 : this.byId.values()) {
         if (var3.aura > 0L) {
            var1.add(var3);
         }
      }

      var1.sort(Comparator.<PlayerData>comparingLong(var0 -> -var0.aura).thenComparing(var0 -> var0.name.toLowerCase(Locale.ROOT)));
      return var1;
   }

   public int auraRankOf(UUID var1) {
      List var2 = this.auraRanking();

      for (int var3 = 0; var3 < var2.size(); var3++) {
         if (((PlayerData)var2.get(var3)).uuid.equals(var1)) {
            return var3 + 1;
         }
      }

      return 0;
   }
}
