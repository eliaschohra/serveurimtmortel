package fr.bdeimt.serveur;

import java.net.URI;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

/**
 * Les sons faits maison : un extrait joue quand on prend le kit d'une liste.
 *
 * <p>Minecraft ne sait pas jouer un fichier mp3 envoye par un serveur. Il faut
 * passer par un pack de ressources : une archive qui contient les sons au
 * format .ogg et un fichier sounds.json qui leur donne un nom. Le serveur
 * donne l'adresse du pack au joueur a la connexion ; son jeu le telecharge
 * une fois et le garde en cache.
 *
 * <p>Les sons s'appellent {@code bdeimt:liste.‹nom de la liste›} — par exemple
 * {@code bdeimt:liste.imtmortel}. Ils ne sont joues qu'en survie.
 *
 * <p>Tant qu'aucun pack n'est configure, rien n'est envoye et rien n'est joue.
 */
public final class Sounds {
   /** Un identifiant fixe : un nouveau pack remplace l'ancien chez le joueur. */
   private static final UUID PACK_ID = UUID.nameUUIDFromBytes("bdeimt-sons".getBytes(java.nio.charset.StandardCharsets.UTF_8));

   private final BDEIMT pl;

   public Sounds(BDEIMT pl) {
      this.pl = pl;
   }

   private String url() {
      String url = this.pl.getConfig().getString("sons.pack-url", "");
      return url == null ? "" : url.trim();
   }

   /** Propose le pack de sons au joueur, a la connexion. Il peut le refuser. */
   public void offer(Player p) {
      String url = this.url();

      if (url.isEmpty()) {
         return;
      }

      try {
         String sha1 = this.pl.getConfig().getString("sons.pack-sha1", "").trim().toLowerCase(java.util.Locale.ROOT);
         p.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
               .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sha1))
               .required(false)
               .replace(false)
               .prompt(Msg.mm("<gradient:#4FC3FF:#FF5FAE>Les sons du BDE</gradient>\n<gray>Quelques extraits pour les listes. Rien d'autre ne change.</gray>"))
         );
      } catch (Throwable t) {
         this.pl.getLogger().warning("Pack de sons non envoye a " + p.getName() + " : " + t.getMessage());
      }
   }

   /** L'extrait d'une liste, quand on prend son kit. En survie seulement. */
   public void playList(Player p, String list) {
      if (this.url().isEmpty() || this.pl.worlds().zoneOf(p) != Zone.SURVIE) {
         return;
      }

      try {
         p.playSound(Sound.sound(Key.key("bdeimt", "liste." + list), Sound.Source.MASTER, 1.0F, 1.0F), Sound.Emitter.self());
      } catch (Throwable t) {
      }
   }
}
