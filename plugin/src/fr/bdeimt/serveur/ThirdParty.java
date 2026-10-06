package fr.bdeimt.serveur;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Retouches sur les autres plugins installes a cote du notre.
 *
 * <p>Pour l'instant, une seule : faire taire le message que SkinsRestorer
 * envoie a chaque arrivee pour renvoyer les joueurs vers son site. Il n'existe
 * aucun moyen propre d'intercepter un message envoye par un autre plugin, donc
 * on va le chercher la ou il est ecrit — dans ses fichiers de traduction — et
 * on vide la ligne.
 *
 * <p>Le travail est refait a chaque demarrage : si SkinsRestorer est mis a
 * jour et reecrit ses fichiers, le message disparait a nouveau tout seul.
 */
public final class ThirdParty {
   /** On ne touche qu'aux lignes qui renvoient vers un site. */
   private static final String[] SUSPECTS = new String[]{"skinsrestorer.net", "skinsrestorer.net/skinsystem", "skinsrestorer.org"};

   private final BDEIMT pl;

   public ThirdParty(BDEIMT pl) {
      this.pl = pl;
   }

   public void run() {
      File folder = new File(this.pl.getDataFolder().getParentFile(), "SkinsRestorer");

      if (!folder.isDirectory()) {
         return;
      }

      List<File> files = new ArrayList<>();
      this.collect(folder, files, 0);
      int cleaned = 0;

      for (File file : files) {
         if (this.clean(file)) {
            cleaned++;
         }
      }

      if (cleaned > 0) {
         this.pl.getLogger().info("Message de bienvenue de SkinsRestorer retire de " + cleaned + " fichier(s).");
         this.pl.getLogger().info("Tape /sr reload en jeu, ou redemarre une fois, pour que SkinsRestorer le relise.");
      }
   }

   /** Ramasse les fichiers de messages, sans descendre trop profond. */
   private void collect(File folder, List<File> out, int depth) {
      File[] children = folder.listFiles();

      if (children == null || depth > 3) {
         return;
      }

      for (File child : children) {
         if (child.isDirectory()) {
            this.collect(child, out, depth + 1);
            continue;
         }

         String name = child.getName().toLowerCase(Locale.ROOT);

         if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties") || name.endsWith(".json")) {
            out.add(child);
         }
      }
   }

   /**
    * Vide la valeur des lignes qui citent le site de SkinsRestorer, en gardant
    * la cle : le plugin continue de trouver son message, il est simplement
    * vide, et plus rien ne s'affiche.
    */
   private boolean clean(File file) {
      String content;

      try {
         content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
      } catch (IOException | RuntimeException ex) {
         return false;
      }

      boolean touched = false;
      String[] lines = content.split("\n", -1);

      for (int i = 0; i < lines.length; i++) {
         String lower = lines[i].toLowerCase(Locale.ROOT);
         boolean suspect = false;

         for (String needle : SUSPECTS) {
            if (lower.contains(needle)) {
               suspect = true;
               break;
            }
         }

         if (!suspect || lines[i].trim().startsWith("#")) {
            continue;
         }

         int cut = lines[i].indexOf('=');

         if (cut < 0) {
            cut = lines[i].indexOf(':');
         }

         if (cut < 0) {
            continue;
         }

         String blanked = lines[i].substring(0, cut + 1) + (lines[i].charAt(cut) == ':' ? " \"\"" : "");

         if (!blanked.equals(lines[i])) {
            lines[i] = blanked;
            touched = true;
         }
      }

      if (!touched) {
         return false;
      }

      try {
         Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
         return true;
      } catch (IOException | RuntimeException ex) {
         this.pl.getLogger().warning("Fichier de SkinsRestorer non modifiable : " + file.getName());
         return false;
      }
   }
}
