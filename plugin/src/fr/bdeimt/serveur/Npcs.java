package fr.bdeimt.serveur;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.UUID;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Zaza et le Mobutu : deux vrais modeles de joueur poses dans le hub.
 *
 * <p>Ce sont des mannequins, l'entite que Minecraft utilise pour afficher un
 * joueur : ils portent un vrai skin, ont une barre de vie et se frappent.
 *
 * <ul>
 *   <li><b>Zaza</b> encaisse, meurt, et revient au meme endroit.
 *   <li><b>Le Mobutu</b> a autant de vie qu'un Warden et renvoie cinq fois les
 *       degats recus. Le tuer declenche une annonce sur tout le serveur.
 * </ul>
 */
public final class Npcs implements Listener {
   /** Le Warden a 500 points de vie. */
   private static final double MOBUTU_HEALTH = 500.0;
   private static final double ZAZA_HEALTH = 20.0;
   private static final long RESPAWN_TICKS = 60L;

   private final BDEIMT pl;
   private final NamespacedKey key;

   public Npcs(BDEIMT pl) {
      this.pl = pl;
      this.key = new NamespacedKey(pl, "pnj");
   }

   // --------------------------------------------------------------- pose

   /** Enleve les anciens mannequins et repose les deux PNJ. */
   public void spawnAll() {
      World w = this.pl.worlds().world(Zone.HUB);
      if (w == null) {
         return;
      }

      for (Entity entity : w.getEntities()) {
         if (entity.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
            entity.remove();
         }
      }

      this.spawn("zaza");
      this.spawn("mobutu");
   }

   /** Position de pose d'un PNJ, reglee par {@code /imt pnj ‹nom› ici}. */
   public Location position(String id) {
      Location configured = Util.loc(this.pl.getConfig().getString("pnj." + id + ".position"));
      if (configured != null) {
         return configured;
      }

      Location spawn = this.pl.worlds().spawnOf(Zone.HUB, null);
      if (spawn == null) {
         return null;
      }

      // Par defaut : le Mobutu au milieu du spawn, Zaza quelques blocs a cote.
      return "mobutu".equals(id) ? spawn.clone() : spawn.clone().add(5.0, 0.0, 0.0);
   }

   public void setPosition(String id, Location at) {
      this.pl.getConfig().set("pnj." + id + ".position", Util.loc(at));
      this.pl.saveConfig();
   }

   /** Pose un PNJ, en remplacant celui qui s'y trouve deja. */
   public void spawn(String id) {
      Location at = this.position(id);
      if (at == null || at.getWorld() == null) {
         return;
      }

      this.remove(id);
      boolean mobutu = "mobutu".equals(id);

      try {
         at.getWorld().spawn(at, Mannequin.class, npc -> {
            npc.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, id);
            npc.customName(Msg.mm(mobutu ? "<#8B0000><bold>Mobutu</bold></#8B0000>" : "<#FFD25E>zaza</#FFD25E>"));
            npc.setCustomNameVisible(true);
            npc.setImmovable(true);
            npc.setInvulnerable(false);
            npc.setPersistent(false);
            npc.setRemoveWhenFarAway(false);
            npc.setDescription(Msg.mm(mobutu ? "<gray>Ne le reveille pas.</gray>" : "<gray>Frappe, elle revient.</gray>"));
            AttributeInstance health = npc.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
               health.setBaseValue(mobutu ? MOBUTU_HEALTH : ZAZA_HEALTH);
            }

            npc.setHealth(mobutu ? MOBUTU_HEALTH : ZAZA_HEALTH);
            this.applySkin(npc, id);
         });
      } catch (Throwable t) {
         this.pl.getLogger().warning("PNJ " + id + " impossible a poser : " + t.getMessage());
      }
   }

   public void remove(String id) {
      World w = this.pl.worlds().world(Zone.HUB);
      if (w != null) {
         for (Entity entity : w.getEntities()) {
            if (id.equals(entity.getPersistentDataContainer().get(this.key, PersistentDataType.STRING))) {
               entity.remove();
            }
         }
      }
   }

   private Mannequin find(String id) {
      World w = this.pl.worlds().world(Zone.HUB);
      if (w != null) {
         for (Entity entity : w.getEntities()) {
            if (entity instanceof Mannequin npc && id.equals(entity.getPersistentDataContainer().get(this.key, PersistentDataType.STRING))) {
               return npc;
            }
         }
      }

      return null;
   }

   private String idOf(Entity entity) {
      return entity.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
   }

   // --------------------------------------------------------------- skins

   /**
    * Applique le skin du PNJ : soit la valeur « textures » collee dans
    * config.yml, soit le skin d'un compte Minecraft existant, cherche sur le
    * reseau en dehors du fil principal.
    */
   private void applySkin(Mannequin npc, String id) {
      String texture = this.pl.getConfig().getString("pnj." + id + ".texture", "");
      String signature = this.pl.getConfig().getString("pnj." + id + ".signature", "");
      String pseudo = this.pl.getConfig().getString("pnj." + id + ".pseudo", "");

      if (texture != null && !texture.isBlank()) {
         PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), id);
         profile.setProperty(new ProfileProperty("textures", texture.trim(), signature == null || signature.isBlank() ? null : signature.trim()));
         npc.setProfile(ResolvableProfile.resolvableProfile(profile));
         return;
      }

      if (pseudo == null || pseudo.isBlank()) {
         return;
      }

      String wanted = pseudo.trim();
      Bukkit.getScheduler().runTaskAsynchronously(this.pl, () -> {
         PlayerProfile profile = Bukkit.createProfile(wanted);

         try {
            profile.complete(true);
         } catch (Throwable t) {
            this.pl.getLogger().warning("Skin de " + id + " introuvable pour le pseudo " + wanted + " : " + t.getMessage());
            return;
         }

         if (!profile.hasTextures()) {
            this.pl.getLogger().warning("Le compte " + wanted + " n'a pas de skin : PNJ " + id + " laisse par defaut.");
            return;
         }

         Bukkit.getScheduler().runTask(this.pl, () -> {
            Mannequin live = this.find(id);
            if (live != null) {
               live.setProfile(ResolvableProfile.resolvableProfile(profile));
            }
         });
      });
   }

   // ------------------------------------------------------------- combat

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHit(EntityDamageByEntityEvent e) {
      String id = this.idOf(e.getEntity());
      if (!"mobutu".equals(id) || !(e.getDamager() instanceof Player p)) {
         return;
      }

      // La colere du Mobutu : cinq fois ce qu'on lui met, applique a la main
      // parce que le hub annule normalement tous les degats entre entites.
      double back = e.getFinalDamage() * 5.0;
      Bukkit.getScheduler().runTask(this.pl, () -> {
         if (!p.isOnline()) {
            return;
         }

         double left = p.getHealth() - back;
         Util.sound(p, "entity.warden.heartbeat", 1.0F, 0.6F);

         if (left <= 0.0) {
            p.setHealth(0.0);
            Msg.panthereAll(
               "<white><n></white> a voulu toucher au Mobutu. Le Mobutu n'a pas apprecie.",
               Msg.p("n", p.getName())
            );
         } else {
            p.setHealth(left);
            Msg.err(p, "Le Mobutu te rend <white>" + Math.round(back) + "</white> points de degats.");
         }
      });
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(EntityDeathEvent e) {
      String id = this.idOf(e.getEntity());
      if (id == null) {
         return;
      }

      e.getDrops().clear();
      e.setDroppedExp(0);

      if ("zaza".equals(id)) {
         Bukkit.getScheduler().runTaskLater(this.pl, () -> this.spawn("zaza"), RESPAWN_TICKS);
         return;
      }

      if ("mobutu".equals(id)) {
         this.mobutuFalls(e.getEntity().getKiller());
         Bukkit.getScheduler().runTaskLater(this.pl, () -> this.spawn("mobutu"), RESPAWN_TICKS * 10L);
      }
   }

   /** Le seul evenement qui traverse tous les mondes a la fois. */
   private void mobutuFalls(Player killer) {
      String name = killer == null ? "Quelqu'un" : killer.getName();
      Msg.broadcast(Msg.mm("<dark_gray>" + "─".repeat(42) + "</dark_gray>"));
      Msg.broadcast(
         Msg.mm(
            "<#8B0000><bold>  LA COLERE DU MOBUTU</bold></#8B0000>\n"
               + "<white>  <n></white> <#FFB3B3>a terrasse le Mobutu.</#FFB3B3>\n"
               + "<#FF5555>  Sa colere s'abattra sur tout le monde,</#FF5555>\n"
               + "<#FF5555>  et jusqu'en dehors de toutes ces dimensions.</#FF5555>",
            Msg.p("n", name)
         )
      );
      Msg.broadcast(Msg.mm("<dark_gray>" + "─".repeat(42) + "</dark_gray>"));

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.pl.auth().isLogged(p)) {
            Util.sound(p, "entity.wither.spawn", 1.0F, 0.6F);
         }
      }
   }

   /** Retire les PNJ au /stop, pour ne pas les laisser en double. */
   public void clearAll() {
      this.remove("zaza");
      this.remove("mobutu");
   }
}
