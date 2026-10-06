import fr.bdeimt.serveur.MapRepair;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.*;

public class TestRepair {
   static int fails = 0;
   static void check(boolean ok, String what) { System.out.println((ok ? "  OK   " : "  ECHEC ") + what); if (!ok) fails++; }

   static Map<String,String> digest(Path root) throws Exception {
      Map<String,String> out = new TreeMap<>();
      try (Stream<Path> w = Files.walk(root)) {
         for (Path p : w.filter(Files::isRegularFile).toList()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            out.put(root.relativize(p).toString(), HexFormat.of().formatHex(md.digest(Files.readAllBytes(p))));
         }
      }
      return out;
   }
   static void copy(Path src, Path dst) throws Exception {
      try (Stream<Path> w = Files.walk(src)) {
         for (Path p : w.toList()) {
            Path t = dst.resolve(src.relativize(p).toString());
            if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t);
         }
      }
   }
   static Path fresh(Path base, String name, Path map) throws Exception {
      Path c = base.resolve(name);
      copy(map, c.resolve("bdeimt_hub"));
      return c;
   }

   public static void main(String[] a) throws Exception {
      Path map = Paths.get(a[0]);
      Path base = Files.createTempDirectory(Paths.get(a[1]), "run");
      Map<String,String> original = digest(map);
      System.out.println("Carte d'origine : " + original.size() + " fichiers");

      // A. Paper a deplace la moitie des fichiers region, puis a echoue.
      System.out.println("\n[A] importation interrompue a mi-chemin");
      Path c = fresh(base, "A", map);
      Path legacy = c.resolve("bdeimt_hub"), migrated = c.resolve("world/dimensions/minecraft/bdeimt_hub");
      Files.createDirectories(migrated.resolve("region")); Files.createDirectories(migrated.resolve("data"));
      Files.writeString(migrated.resolve("data/partiel.dat"), "x");
      List<Path> regions; try (Stream<Path> s = Files.list(legacy.resolve("region"))) { regions = s.sorted().toList(); }
      int moved = 0;
      for (int i = 0; i < regions.size(); i += 2) { Files.move(regions.get(i), migrated.resolve("region").resolve(regions.get(i).getFileName())); moved++; }
      check(!digest(legacy).equals(original), "avant : la carte est bien coupee en deux (" + moved + " fichiers partis)");
      MapRepair.Result r = MapRepair.repair(legacy, migrated, c.resolve("bdeimt_hub_import-rate-1"));
      check(r != null && r.restored() == moved, "fichiers ramenes : " + (r == null ? "?" : r.restored()) + " / " + moved);
      check(r != null && r.duplicates() == 0, "aucun doublon");
      check(digest(legacy).equals(original), "la carte est identique a l'originale, octet pour octet");
      check(!Files.exists(migrated), "le dossier de la tentative ratee a disparu de world/");
      check(Files.exists(c.resolve("bdeimt_hub_import-rate-1/data/partiel.dat")), "ses restes sont gardes de cote");

      // B. Un monde vide a ete cree sous le meme nom : doublon de r.0.0.mca.
      System.out.println("\n[B] monde vide cree par erreur sous le meme nom");
      c = fresh(base, "B", map);
      legacy = c.resolve("bdeimt_hub"); migrated = c.resolve("world/dimensions/minecraft/bdeimt_hub");
      String any; try (Stream<Path> s = Files.list(legacy.resolve("region"))) { any = s.sorted().findFirst().get().getFileName().toString(); }
      Files.createDirectories(migrated.resolve("region"));
      Files.writeString(migrated.resolve("region").resolve(any), "terrain genere au hasard");
      Files.writeString(migrated.resolve("region/r.99.99.mca"), "chunk du monde vide");
      r = MapRepair.repair(legacy, migrated, c.resolve("aside"));
      check(r != null && r.duplicates() == 1, "doublon detecte : " + (r == null ? "?" : r.duplicates()));
      Map<String,String> after = digest(legacy);
      after.remove("region/r.99.99.mca");
      check(after.equals(original), "la version de la carte deposee est gardee pour le doublon");
      check(Files.readString(c.resolve("aside/region").resolve(any)).equals("terrain genere au hasard"), "le doublon du monde vide est garde de cote, pas perdu");

      // C. Rien a reparer.
      System.out.println("\n[C] aucune tentative precedente");
      c = fresh(base, "C", map);
      r = MapRepair.repair(c.resolve("bdeimt_hub"), c.resolve("world/dimensions/minecraft/bdeimt_hub"), c.resolve("aside"));
      check(r == null, "ne fait rien");
      check(digest(c.resolve("bdeimt_hub")).equals(original), "la carte n'a pas bouge");

      // D. Pas de level.dat : on ne touche a rien.
      System.out.println("\n[D] dossier sans level.dat");
      c = fresh(base, "D", map);
      Files.delete(c.resolve("bdeimt_hub/level.dat"));
      Path mig = c.resolve("world/dimensions/minecraft/bdeimt_hub/region");
      Files.createDirectories(mig); Files.writeString(mig.resolve("r.0.0.mca"), "x");
      boolean threw = false;
      try { MapRepair.repair(c.resolve("bdeimt_hub"), c.resolve("world/dimensions/minecraft/bdeimt_hub"), c.resolve("aside")); } catch (java.io.IOException e) { threw = true; }
      check(threw, "refuse d'agir");
      check(Files.exists(mig.resolve("r.0.0.mca")) && !Files.exists(c.resolve("aside")), "rien n'a ete deplace");

      System.out.println("\n" + (fails == 0 ? "TOUS LES TESTS PASSENT" : fails + " ECHEC(S)"));
      System.exit(fails == 0 ? 0 : 1);
   }
}
