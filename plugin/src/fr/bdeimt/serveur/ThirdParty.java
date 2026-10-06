package fr.bdeimt.serveur;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Retouches sur les autres plugins installes a cote du notre.
 *
 * <p>Pour l'instant, une seule : faire taire l'avertissement que SkinsRestorer
 * envoie a chaque arrivee sur un serveur en mode hors ligne — « Some
 * third-party launchers override SkinsRestorer skins… », avec un lien vers
 * son site.
 *
 * <p>Ce message a un interrupteur dans la configuration de SkinsRestorer :
 * {@code login.offlineModeWarning.enabled}, releve dans son code source
 * ({@code LoginConfig.OFFLINE_MODE_WARNING_ENABLED}). On le met a false au
 * demarrage. SkinsRestorer le relit a son prochain lancement, ou tout de
 * suite avec {@code /sr reload}.
 */
public final class ThirdParty {
   private static final String SWITCH = "login.offlineModeWarning.enabled";

   private final BDEIMT pl;

   public ThirdParty(BDEIMT pl) {
      this.pl = pl;
   }

   public void run() {
      File config = new File(new File(this.pl.getDataFolder().getParentFile(), "SkinsRestorer"), "config.yml");

      if (!config.isFile()) {
         return;
      }

      try {
         YamlConfiguration yml = YamlConfiguration.loadConfiguration(config);

         if (yml.contains(SWITCH) && !yml.getBoolean(SWITCH, true)) {
            return;
         }

         yml.set(SWITCH, false);
         yml.save(config);
         this.pl.getLogger().info("Avertissement « third-party launchers » de SkinsRestorer desactive (" + SWITCH + ": false).");
         this.pl.getLogger().info("Il disparait au prochain redemarrage, ou tout de suite avec /sr reload.");
      } catch (Exception ex) {
         this.pl.getLogger().warning(
            "Impossible de modifier plugins/SkinsRestorer/config.yml : mets " + SWITCH + " a false a la main (" + ex.getMessage() + ")."
         );
      }
   }
}
