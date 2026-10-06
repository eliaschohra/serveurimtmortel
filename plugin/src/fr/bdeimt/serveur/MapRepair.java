package fr.bdeimt.serveur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Remet en etat une importation de carte ratee par Paper.
 *
 * <p>Isole de {@link Worlds} et sans aucune dependance a Bukkit, pour pouvoir
 * etre teste sur une copie de vraies cartes : c'est du code qui deplace des
 * fichiers sur le serveur, il ne doit jamais rien perdre.
 *
 * <p>Paper importe une carte en deplacant ses dossiers {@code region/},
 * {@code entities/} et {@code poi/} fichier par fichier vers le nouveau
 * dossier de dimension, et refuse d'ecraser un fichier deja present. Une
 * tentative interrompue laisse donc la carte coupee en deux, et toutes les
 * suivantes echouent. On recolle les morceaux :
 *
 * <ul>
 *   <li>un fichier present seulement dans le nouveau dossier y avait ete
 *       deplace par la tentative ratee : il revient dans la carte ;
 *   <li>un fichier present des deux cotes est un doublon — un monde vide cree
 *       par erreur sous le meme nom, par exemple : on garde celui de la carte
 *       deposee, l'autre part de cote avec tout le reste du nouveau dossier.
 * </ul>
 *
 * <p>Rien n'est jamais supprime.
 */
public final class MapRepair {
   private static final String[] DIRECTORIES = new String[]{"region", "entities", "poi"};

   /** Ce qu'a fait la reparation, pour le dire dans la console. */
   public record Result(int restored, int duplicates, Path aside) {
   }

   private MapRepair() {
   }

   /**
    * @param legacy   la carte deposee a l'ancienne, a la racine du serveur
    * @param migrated le dossier de dimension laisse par la tentative ratee
    * @param aside    ou ranger ce qui ne revient pas dans la carte ; ne doit pas exister
    * @return ce qui a ete fait, ou null s'il n'y avait rien a reparer
    */
   public static Result repair(Path legacy, Path migrated, Path aside) throws IOException {
      if (!Files.isDirectory(migrated)) {
         return null;
      }

      if (!Files.isRegularFile(legacy.resolve("level.dat"))) {
         throw new IOException("Pas de level.dat dans " + legacy + " : rien a reparer, on ne touche a rien.");
      }

      if (Files.exists(aside)) {
         throw new IOException(aside + " existe deja.");
      }

      int restored = 0;
      int duplicates = 0;

      for (String dir : DIRECTORIES) {
         Path from = migrated.resolve(dir);

         if (!Files.isDirectory(from)) {
            continue;
         }

         List<Path> files;

         try (Stream<Path> walk = Files.walk(from)) {
            files = walk.filter(Files::isRegularFile).toList();
         }

         for (Path file : files) {
            Path back = legacy.resolve(migrated.relativize(file).toString());

            if (Files.exists(back)) {
               duplicates++;
            } else {
               Files.createDirectories(back.getParent());
               Files.move(file, back);
               restored++;
            }
         }
      }

      // Tout ce qui reste — doublons, donnees de la tentative ratee — part de
      // cote d'un seul bloc, hors du monde principal.
      Files.createDirectories(aside.getParent());
      Files.move(migrated, aside);
      return new Result(restored, duplicates, aside);
   }
}
