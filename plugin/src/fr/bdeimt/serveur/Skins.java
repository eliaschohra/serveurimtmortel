package fr.bdeimt.serveur;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

/**
 * Donner un skin aux PNJ a partir d'un simple fichier PNG.
 *
 * <p>Minecraft n'accepte pas n'importe quelle image : le client ne va chercher
 * les skins que sur les serveurs de Mojang, et il faut une signature de Mojang
 * pour qu'il accepte de l'afficher. On ne peut donc pas se contenter de poser
 * un PNG dans un dossier.
 *
 * <p>On passe par <b>MineSkin</b>, le service qui fait ce travail : on lui
 * envoie le PNG, il le depose chez Mojang et renvoie les deux chaines que
 * Minecraft attend — la texture et sa signature. On les garde dans
 * {@code config.yml}, donc l'envoi n'a lieu qu'une fois par image.
 *
 * <p>Si le service ne repond pas, rien n'est casse : le PNJ garde son skin
 * precedent et la console explique comment faire a la main.
 */
public final class Skins {
   private static final String ENDPOINT = "https://api.mineskin.org/v2/generate";
   private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
   private static final Pattern SIGNATURE = Pattern.compile("\"signature\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
   private static final Pattern MESSAGE = Pattern.compile("\"(?:message|error|detail)\"\\s*:\\s*\"([^\"]+)\"");

   private final BDEIMT pl;
   private final File folder;

   public Skins(BDEIMT pl) {
      this.pl = pl;
      this.folder = new File(pl.getDataFolder(), "skins");

      if (!this.folder.exists() && !this.folder.mkdirs()) {
         pl.getLogger().warning("Dossier skins impossible a creer");
      }
   }

   public File folder() {
      return this.folder;
   }

   /** Le PNG d'un PNJ, quelle que soit la casse du nom de fichier. */
   public File imageOf(String id) {
      File[] children = this.folder.listFiles();

      if (children == null) {
         return null;
      }

      for (File child : children) {
         if (child.isFile() && child.getName().equalsIgnoreCase(id + ".png")) {
            return child;
         }
      }

      return null;
   }

   /**
    * Envoie les PNG qui ont change depuis la derniere fois. Appele au
    * demarrage : deposer un fichier par FileZilla puis redemarrer suffit.
    */
   public void syncAll() {
      for (String id : List.of("zaza", "mobutu")) {
         File image = this.imageOf(id);

         if (image == null) {
            continue;
         }

         String fingerprint = fingerprint(image);

         if (fingerprint != null && fingerprint.equals(this.pl.getConfig().getString("pnj." + id + ".empreinte", ""))) {
            continue;
         }

         this.upload(id, null);
      }
   }

   /**
    * Envoie le PNG d'un PNJ a MineSkin, puis applique le skin obtenu.
    *
    * @param who a qui rendre compte en jeu, ou null pour ne parler qu'a la console
    */
   public void upload(String id, CommandSender who) {
      File image = this.imageOf(id);

      if (image == null) {
         this.say(who, "Aucun fichier " + id + ".png dans plugins/BDEIMT/skins/.");
         return;
      }

      byte[] bytes;

      try {
         bytes = Files.readAllBytes(image.toPath());
      } catch (Exception ex) {
         this.say(who, "Fichier " + image.getName() + " illisible : " + ex.getMessage());
         return;
      }

      String size = this.checkSize(image);

      if (size != null) {
         this.say(who, size);
         return;
      }

      String variant = this.pl.getConfig().getString("pnj." + id + ".modele", "classic");
      String apiKey = this.pl.getConfig().getString("mineskin-cle", "");
      this.say(who, "Envoi de " + image.getName() + " a MineSkin, patiente quelques secondes...");

      Bukkit.getScheduler().runTaskAsynchronously(this.pl, () -> {
         String body;

         try {
            body = send(bytes, image.getName(), id, variant, apiKey);
         } catch (Exception ex) {
            this.report(who, id, "MineSkin injoignable (" + ex.getClass().getSimpleName() + ") : " + ex.getMessage());
            return;
         }

         Matcher value = VALUE.matcher(body);
         Matcher signature = SIGNATURE.matcher(body);

         if (!value.find() || !signature.find()) {
            Matcher message = MESSAGE.matcher(body);
            this.report(who, id, "MineSkin a refuse l'image" + (message.find() ? " : " + message.group(1) : "."));
            return;
         }

         String texture = value.group(1);
         String signed = signature.group(1);
         Bukkit.getScheduler().runTask(this.pl, () -> {
            this.pl.getConfig().set("pnj." + id + ".texture", texture);
            this.pl.getConfig().set("pnj." + id + ".signature", signed);
            this.pl.getConfig().set("pnj." + id + ".pseudo", "");
            this.pl.getConfig().set("pnj." + id + ".empreinte", fingerprint(image));
            this.pl.saveConfig();
            this.pl.npcs().spawn(id);

            if ("mobutu".equals(id)) {
               for (Player online : Bukkit.getOnlinePlayers()) {
                  this.applyToAdmin(online);
               }
            }

            this.say(who, "Skin de " + id + " applique depuis " + image.getName() + ".");
         });
      });
   }

   /** Un vrai skin fait 64 x 64 (ou 64 x 32 pour les tres anciens). */
   private String checkSize(File image) {
      try {
         BufferedImage picture = ImageIO.read(image);

         if (picture == null) {
            return image.getName() + " n'est pas un vrai PNG.";
         }

         if (picture.getWidth() != 64 || picture.getHeight() != 64 && picture.getHeight() != 32) {
            return image.getName() + " fait " + picture.getWidth() + " x " + picture.getHeight()
               + " : un skin Minecraft fait 64 x 64.";
         }

         return null;
      } catch (Exception ex) {
         return image.getName() + " illisible : " + ex.getMessage();
      }
   }

   /** L'envoi lui-meme : un formulaire multipart, construit a la main. */
   private static String send(byte[] png, String fileName, String name, String variant, String apiKey) throws Exception {
      String boundary = "----bdeimt" + System.nanoTime();
      var out = new java.io.ByteArrayOutputStream();
      var writer = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8);
      writer.write("--" + boundary + "\r\n");
      writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
      writer.write("Content-Type: image/png\r\n\r\n");
      writer.flush();
      out.write(png);
      writer.write("\r\n--" + boundary + "\r\n");
      writer.write("Content-Disposition: form-data; name=\"variant\"\r\n\r\n" + variant + "\r\n");
      writer.write("--" + boundary + "\r\n");
      writer.write("Content-Disposition: form-data; name=\"name\"\r\n\r\n" + name + "\r\n");
      writer.write("--" + boundary + "--\r\n");
      writer.flush();

      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ENDPOINT))
         .timeout(Duration.ofSeconds(60))
         .header("Content-Type", "multipart/form-data; boundary=" + boundary)
         .header("User-Agent", "BDEIMT/2.1 (serveur du BDE de l'IMT Atlantique)")
         .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));

      if (apiKey != null && !apiKey.isBlank()) {
         request.header("Authorization", "Bearer " + apiKey.trim());
      }

      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
      HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return response.body() == null ? "" : response.body();
   }

   private static String fingerprint(File file) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file.toPath())));
      } catch (Exception ex) {
         return null;
      }
   }

   private void report(CommandSender who, String id, String what) {
      Bukkit.getScheduler().runTask(this.pl, () -> {
         this.say(who, what);
         this.say(who, "Le PNJ " + id + " garde son skin precedent.");
         this.pl.getLogger().warning("Pour poser le skin a la main : va sur mineskin.org, envoie le PNG,");
         this.pl.getLogger().warning("puis colle « value » et « signature » dans config.yml sous pnj." + id + ".");
      });
   }

   private void say(CommandSender who, String what) {
      if (who != null) {
         Msg.info(who, what.replace("<", "‹").replace(">", "›"));
      }

      this.pl.getLogger().info(what);
   }

   /**
    * Donne au Mobutu — le vrai, le joueur — le skin de son PNJ, dans tous les
    * mondes.
    *
    * <p>Changer soi-meme le profil d'un joueur connecte oblige son client a
    * tout recharger : c'est l'ecran « Loading terrain » qui revenait a chaque
    * changement de monde. On confie donc le skin a SkinsRestorer, qui le pose
    * une fois pour toutes et le reapplique a chaque connexion, avant meme que
    * le joueur n'apparaisse, sans aucun rechargement.
    *
    * <p>Ce n'est fait qu'une fois par image : l'URL deja confiee est retenue.
    * Sans SkinsRestorer, on change le profil nous-memes, une seule fois a la
    * connexion.
    */
   public void applyToAdmin(Player p) {
      if (p == null || !p.getName().equalsIgnoreCase(this.pl.adminName())) {
         return;
      }

      if (!this.pl.getConfig().getBoolean("pnj.mobutu.aussi-pour-l-admin", true)) {
         return;
      }

      String texture = this.pl.getConfig().getString("pnj.mobutu.texture", "");
      String signature = this.pl.getConfig().getString("pnj.mobutu.signature", "");

      if (texture == null || texture.isBlank()) {
         return;
      }

      String url = textureUrl(texture.trim());

      if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null && url != null) {
         if (url.equals(this.pl.getConfig().getString("pnj.mobutu.confie-a-skinsrestorer", ""))) {
            return;
         }

         // Syntaxe relevee dans le code de SkinsRestorer :
         //    skin set <skinName> <selector>   (SkinCommand#onSkinSetOther)
         // une URL de texture y est acceptee comme nom de skin.
         String command = "skin set \"" + url + "\" " + p.getName();
         Bukkit.getScheduler().runTaskLater(this.pl, () -> {
            if (p.isOnline() && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
               this.pl.getConfig().set("pnj.mobutu.confie-a-skinsrestorer", url);
               this.pl.saveConfig();
               this.pl.getLogger().info("Skin du Mobutu confie a SkinsRestorer pour " + p.getName() + ".");
            }
         }, 40L);
         return;
      }

      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (!p.isOnline()) {
            return;
         }

         try {
            PlayerProfile profile = p.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty("textures", texture.trim(), signature == null || signature.isBlank() ? null : signature.trim()));
            p.setPlayerProfile(profile);
         } catch (Throwable t) {
            this.pl.getLogger().warning("Skin du Mobutu non applique a " + p.getName() + " : " + t.getMessage());
         }
      }, 20L);
   }

   /** L'adresse de l'image cachee dans la valeur « textures » (du JSON en base64). */
   static String textureUrl(String texture) {
      try {
         String json = new String(java.util.Base64.getDecoder().decode(texture), StandardCharsets.UTF_8);
         Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(json);
         return m.find() ? m.group(1) : null;
      } catch (Exception ex) {
         return null;
      }
   }

   /** Le nom du PNJ correspondant a un fichier, ou null. */
   public static String idOfFile(String fileName) {
      String lower = fileName.toLowerCase(Locale.ROOT);

      for (String id : List.of("zaza", "mobutu")) {
         if (lower.equals(id + ".png")) {
            return id;
         }
      }

      return null;
   }
}
