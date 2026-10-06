package fr.bdeimt.serveur;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

public final class Motd implements Listener {
   private static final String LINE1 = "<gradient:#4FC3FF:#B66BFF:#FF5FAE:#4FC3FF:<phase>><bold>✦ SERVEUR DU BDE IMT ✦</bold></gradient>";
   private static final List<String> LINE2 = List.of(
      "<#FFC93C>★</#FFC93C> <gray>Survie entre étudiants · votes · kits · bonne ambiance</gray>",
      "<#FF9AC8>⚄</#FF9AC8> <gray>Un</gray> <white>/vote</white> <gray>par heure, 100 lots à gagner !</gray>",
      "<#C77DFF>☠</#C77DFF> <gray>L'Ender Dragon renaît toutes les 48 h...</gray>",
      "<rainbow:<phase>>Rejoins ta promo, on t'attend !</rainbow>",
      "<#4FC3FF>⚒</#4FC3FF> <gray>Tombes, homes, tpa, échanges sécurisés</gray>"
   );
   private final BDEIMT pl;
   private CachedServerIcon icon;

   public Motd(BDEIMT var1) {
      this.pl = var1;
   }

   public void loadIcon() {
      this.icon = null;
      File var1 = new File(this.pl.getDataFolder(), "images");
      File[] var2 = var1.listFiles();
      if (var2 != null) {
         File var3 = null;

         for (File var7 : var2) {
            if (var7.getName().equalsIgnoreCase("serveur.png")) {
               var3 = var7;
            }
         }

         if (var3 == null) {
            this.pl.getLogger().info("Icone absente : depose serveur.png dans plugins/BDEIMT/images pour l'afficher.");
         } else {
            try {
               BufferedImage var10 = ImageIO.read(var3);
               if (var10 == null) {
                  throw new IllegalArgumentException("pas un PNG");
               }

               int var11 = Math.min(var10.getWidth(), var10.getHeight());
               BufferedImage var12 = var10.getSubimage((var10.getWidth() - var11) / 2, (var10.getHeight() - var11) / 2, var11, var11);
               BufferedImage var13 = new BufferedImage(64, 64, 2);
               Graphics2D var8 = var13.createGraphics();
               var8.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
               var8.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
               var8.drawImage(var12, 0, 0, 64, 64, null);
               var8.dispose();
               this.icon = Bukkit.loadServerIcon(var13);
               this.pl.getLogger().info("Icone du serveur chargee.");
            } catch (Exception var9) {
               this.pl.getLogger().warning("serveur.png illisible : " + var9.getMessage());
            }
         }
      }
   }

   @EventHandler
   public void onPing(ServerListPingEvent var1) {
      String var2 = String.format(Locale.ROOT, "%.2f", ThreadLocalRandom.current().nextDouble(-1.0, 1.0));
      String var3 = LINE2.get(ThreadLocalRandom.current().nextInt(LINE2.size()));
      var1.motd(
         Msg.mm(
            "<gradient:#4FC3FF:#B66BFF:#FF5FAE:#4FC3FF:<phase>><bold>✦ SERVEUR DU BDE IMT ✦</bold></gradient>".replace("<phase>", var2)
               + "\n"
               + var3.replace("<phase>", String.valueOf(ThreadLocalRandom.current().nextInt(0, 10)))
         )
      );
      if (this.icon != null) {
         try {
            var1.setServerIcon(this.icon);
         } catch (Throwable var5) {
         }
      }
   }
}
