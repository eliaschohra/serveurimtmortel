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

   /**
    * {@code /imt sons ‹lien›} : enregistre le pack sans toucher a config.yml.
    *
    * <p>Le serveur telecharge lui-meme le pack pour en calculer l'empreinte
    * SHA-1 (sans elle, les joueurs retelechargeraient le pack a chaque
    * connexion), l'enregistre, puis le propose a tous ceux qui sont en ligne.
    * {@code /imt sons retirer} enleve le pack.
    */
   public void configure(org.bukkit.command.CommandSender sender, String[] args) {
      if (args.length < 2) {
         String url = this.url();
         Msg.raw(sender, "<#FF9AC8><bold>Pack de sons</bold></#FF9AC8> <gray>— " + (url.isEmpty() ? "<#FF5555>aucun</#FF5555>" : "<white>" + url + "</white>") + "</gray>");
         Msg.raw(sender, " <white>/imt sons ‹lien›</white> <gray>— colle le lien donné par mc-packs.net</gray>");
         Msg.raw(sender, " <white>/imt sons retirer</white> <gray>— ne plus envoyer de pack</gray>");
         return;
      }

      if (args[1].equalsIgnoreCase("retirer") || args[1].equalsIgnoreCase("aucun")) {
         this.pl.getConfig().set("sons.pack-url", "");
         this.pl.getConfig().set("sons.pack-sha1", "");
         this.pl.saveConfig();
         Msg.ok(sender, "Pack de sons retiré.");
         return;
      }

      String url = args[1].trim();

      if (!url.startsWith("http://") && !url.startsWith("https://")) {
         Msg.err(sender, "Ce n'est pas un lien. Exemple : <white>/imt sons https://download.mc-packs.net/pack/....zip</white>");
         return;
      }

      Msg.info(sender, "Téléchargement du pack pour vérification...");
      org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(this.pl, () -> {
         String sha1;
         long size;

         try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
               .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
               .connectTimeout(java.time.Duration.ofSeconds(15))
               .build();
            java.net.http.HttpResponse<byte[]> response = client.send(
               java.net.http.HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(60)).GET().build(),
               java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );

            if (response.statusCode() != 200) {
               throw new IllegalStateException("le site répond " + response.statusCode());
            }

            byte[] body = response.body();

            if (body.length < 4 || body[0] != 'P' || body[1] != 'K') {
               throw new IllegalStateException("ce lien ne donne pas un fichier .zip (c'est sans doute la page du site, pas le lien de téléchargement)");
            }

            size = body.length;
            StringBuilder hex = new StringBuilder();

            for (byte b : java.security.MessageDigest.getInstance("SHA-1").digest(body)) {
               hex.append(String.format("%02x", b));
            }

            sha1 = hex.toString();
         } catch (Exception ex) {
            String why = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            org.bukkit.Bukkit.getScheduler().runTask(this.pl, () -> Msg.err(sender, "Pack refusé : " + why.replace("<", "")));
            return;
         }

         org.bukkit.Bukkit.getScheduler().runTask(this.pl, () -> {
            this.pl.getConfig().set("sons.pack-url", url);
            this.pl.getConfig().set("sons.pack-sha1", sha1);
            this.pl.saveConfig();
            Msg.ok(sender, "Pack de sons enregistré <gray>(" + (size / 1024) + " ko, SHA-1 " + sha1 + ")</gray>.");
            Msg.info(sender, "Il est proposé dès maintenant aux joueurs connectés, et à chaque connexion.");

            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
               if (this.pl.auth().isLogged(p)) {
                  this.offer(p);
               }
            }
         });
      });
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
