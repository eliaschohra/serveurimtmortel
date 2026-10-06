package fr.bdeimt.serveur;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Fun implements CommandExecutor, Listener {
   private static final long MIN = 60000L;
   private static final long H = 3600000L;
   private static final int MAX_LEVEL = 10;
   private final BDEIMT pl;
   private final Set<UUID> brest = new HashSet<>();
   private final Map<String, List<String>> stories = new HashMap<>();
   private long traqOpenUntil;
   private static final String[] CAT_SOUNDS = new String[]{
      "entity.cat.ambient", "entity.cat.purreow", "entity.cat.hiss", "entity.cat.beg_for_food", "entity.cat.purr"
   };

   public Fun(BDEIMT var1) {
      this.pl = var1;
   }

   public void init() {
      this.loadStories();
      Bukkit.getScheduler().runTaskTimer(this.pl, this::traqTick, 1200L, 1200L);
   }

   public void loadStories() {
      File var1 = new File(this.pl.getDataFolder(), "histoires.yml");
      if (!var1.exists()) {
         this.pl.saveResource("histoires.yml", false);
      }

      YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
      this.stories.clear();
      ConfigurationSection var3 = var2.getConfigurationSection("histoires");
      if (var3 != null) {
         for (String var5 : var3.getKeys(false)) {
            List var6 = var3.getStringList(var5);
            if (!var6.isEmpty()) {
               this.stories.put(var5, var6);
            }
         }
      }

      this.pl.getLogger().info(this.stories.size() + " histoires chargees pour /tunnel.");
   }

   private long use(Player var1, String var2, long var3) {
      if (this.pl.ranks().isAdmin(var1)) {
         return 0L;
      } else {
         PlayerData var5 = this.pl.data().get(var1);
         long var6 = System.currentTimeMillis();
         Long var8 = var5.kitUses.get("cmd-" + var2);
         if (var8 != null && var6 - var8 < var3) {
            return var8 + var3 - var6;
         } else {
            var5.kitUses.put("cmd-" + var2, var6);
            var5.touch();
            return 0L;
         }
      }
   }

   private boolean wait(Player var1, long var2) {
      if (var2 <= 0L) {
         return false;
      } else {
         Msg.err(var1, "Pas si vite ! Encore <white><t></white> à attendre.", Msg.p("t", Util.duration(var2)));
         return true;
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         if (this.pl.lobby().isLobby(var5.getWorld())) {
            Msg.err(var5, "Pas dans le lobby.");
            return true;
         } else {
            String var6 = var2.getName();
            switch (var6) {
               case "ghoule":
                  if (!this.wait(var5, this.use(var5, "ghoule", 18000000L))) {
                     this.ghoule(var5);
                  }
                  break;
               case "traq":
                  this.traq(var5);
                  break;
               case "oniris":
                  if (!this.wait(var5, this.use(var5, "oniris", 7200000L))) {
                     this.oniris(var5);
                  }
                  break;
               case "sugardaddimt":
                  if (!this.wait(var5, this.use(var5, "sugardaddimt", 7200000L))) {
                     this.sugar(var5);
                  }
                  break;
               case "wei":
                  if (!this.wait(var5, this.use(var5, "wei", 1800000L))) {
                     this.wei(var5);
                  }
                  break;
               case "tunnel":
                  if (this.pl.data().get(var5).isMuted()) {
                     Msg.err(var5, "Tu es muet.");
                     return true;
                  }

                  if (this.stories.isEmpty()) {
                     Msg.err(var5, "Aucune histoire en stock.");
                     return true;
                  }

                  if (!this.wait(var5, this.use(var5, "tunnel", 3600000L))) {
                     this.tunnel(var5);
                  }
            }

            return true;
         }
      }
   }

   private void ghoule(Player var1) {
      Util.sound(var1, "entity.generic.drink", 1.0F, 0.8F);
      Bukkit.getScheduler().runTaskLater(this.pl, () -> Util.sound(var1, "entity.generic.drink", 1.0F, 0.7F), 6L);
      Bukkit.getScheduler().runTaskLater(this.pl, () -> Util.sound(var1, "entity.player.burp", 1.0F, 0.6F), 16L);
      var1.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 4, false, false, false));
      var1.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 4, false, false, false));
      var1.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 9, false, false, false));
      var1.showTitle(
         Title.title(
            Msg.mm("<#7FB800><bold>TU DEVIENS UNE GHOULE</bold></#7FB800>"),
            Msg.mm("<gray>*glou glou glou*</gray>"),
            Times.times(Duration.ZERO, Duration.ofSeconds(3L), Duration.ofSeconds(1L))
         )
      );
      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (var1.isOnline()) {
            var1.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 2, false, false, false));
            var1.setFoodLevel(6);
            var1.setSaturation(0.0F);
            var1.sendActionBar(Msg.mm("<#7FB800>La gueule de bois commence...</#7FB800>"));
         }
      }, 200L);

      for (Player var3 : var1.getWorld().getPlayers()) {
         if (!var3.equals(var1) && this.pl.auth().isLogged(var3) && !(var3.getLocation().distanceSquared(var1.getLocation()) > 2500.0)) {
            var3.showTitle(
               Title.title(
                  Msg.mm("<#FF5555><bold>⚠ ATTENTION ⚠</bold></#FF5555>"),
                  Msg.mm("<#FFD25E><white><n></white> se transforme en ghoule !</#FFD25E>", Msg.p("n", var1.getName())),
                  Times.times(Duration.ZERO, Duration.ofSeconds(4L), Duration.ofSeconds(1L))
               )
            );
            Msg.raw(
               var3,
               "<#FF5555><bold>⚠ Attention</bold></#FF5555> <white><n></white> <#FFD25E>se transforme en ghoule, enfuis-toi et va chercher un respo VSS ou un respo BR !</#FFD25E>",
               Msg.p("n", var1.getName())
            );
            var3.sendActionBar(Msg.mm("<#FF5555><bold>Enfuis-toi ! Va chercher un respo VSS ou un respo BR !</bold></#FF5555>"));
            var3.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
            var3.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, false, false));
            var3.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 1, false, false, false));
            var3.damage(1.0);
            Util.sound(var3, "entity.zombie.ambient", 1.0F, 0.6F);
         }
      }

      Msg.broadcastSurvie(Msg.mm("<#7FB800>☠</#7FB800> ").append(this.pl.ranks().display(var1)).append(Msg.mm(" <#7FB800>se transforme en ghoule...</#7FB800>")));
   }

   public boolean traqOpen() {
      return System.currentTimeMillis() < this.traqOpenUntil;
   }

   private void traqTick() {
      long var1 = System.currentTimeMillis();
      if (this.traqOpenUntil > 0L && var1 >= this.traqOpenUntil) {
         this.traqOpenUntil = 0L;
         Msg.panthereSurvie("<#FF5555><bold>Le Traq ferme ses portes.</bold></#FF5555> Rentrez bien, et buvez de l'eau.");
      } else {
         if (!this.traqOpen() && ThreadLocalRandom.current().nextDouble() < 0.012) {
            this.openTraq();
         }
      }
   }

   public void openTraq() {
      this.traqOpenUntil = System.currentTimeMillis() + 3600000L;
      Msg.panthereSurvie("<#FFC93C><bold>\ud83c\udf7a LE TRAQ EST OUVERT pendant 1 h !</bold></#FFC93C> Tapez <white>/traq</white> pour y foncer.");

      for (Player var2 : Bukkit.getOnlinePlayers()) {
         // Le Traq n'existe que dans la survie : on n'en parle pas ailleurs.
         if (this.pl.auth().isLogged(var2) && this.pl.worlds().zoneOf(var2) == Zone.SURVIE) {
            Util.sound(var2, "block.note_block.bell", 1.0F, 1.4F);
            Msg.big(var2, "<#FFC93C><bold>LE TRAQ EST OUVERT</bold></#FFC93C>", "<gray>une heure, pas une de plus — <white>/traq</white></gray>", 5000L);
         }
      }
   }

   public void closeTraq() {
      this.traqOpenUntil = System.currentTimeMillis() - 1L;
      this.traqTick();
   }

   private void traq(Player var1) {
      if (!this.traqOpen() && !this.pl.ranks().isAdmin(var1)) {
         var1.showTitle(
            Title.title(
               Msg.mm("<#FF5555><bold>LE TRAQ EST FERMÉ</bold></#FF5555>"),
               Msg.mm("<gray>Le Pôle Log se bave dessus...</gray>"),
               Times.times(Duration.ofMillis(200L), Duration.ofSeconds(4L), Duration.ofSeconds(1L))
            )
         );
         var1.sendActionBar(Msg.mm("<#FFC93C>Envoie-leur un message sur IMTA1 !</#FFC93C>"));
         Msg.panthere(
            var1,
            "Le Traq est fermé : le Pôle Log se bave dessus... Envoie-leur un message sur <white>IMTA1</white> ! <gray>(Il ouvre au hasard plusieurs fois par jour, je l'annoncerai.)</gray>"
         );
         Util.sound(var1, "entity.villager.no", 1.0F, 0.8F);
      } else {
         long var2 = this.use(var1, "traq", 60000L);
         if (var2 > 0L) {
            Msg.err(var1, "Tu sors à peine du Traq. Attends <white><t></white>.", Msg.p("t", Util.duration(var2)));
         } else {
            var1.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 9, false, false, false));
            var1.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 5, false, false, false));
            Bukkit.getScheduler().runTaskLater(this.pl, () -> {
               if (var1.isOnline()) {
                  var1.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, false));
               }
            }, 95L);
            var1.showTitle(
               Title.title(
                  Msg.mm("<#FFC93C><bold>On remet le facteur sur le vélo ?</bold></#FFC93C>"),
                  Msg.mm("<#7FB800><bold>Rejoins les ghoules !</bold></#7FB800>"),
                  Times.times(Duration.ZERO, Duration.ofSeconds(3L), Duration.ofSeconds(1L))
               )
            );
            Util.sound(var1, "entity.firework_rocket.launch", 1.0F, 0.8F);
            Msg.broadcastSurvie(
               Msg.mm("<#FFC93C>\ud83c\udf7a</#FFC93C> ")
                  .append(this.pl.ranks().display(var1))
                  .append(Msg.mm(" <#FFD25E>file au Traq à toute vitesse !</#FFD25E>"))
            );
         }
      }
   }

   private void oniris(Player var1) {
      Location var2 = var1.getLocation();
      ArrayList<Cat> var3 = new ArrayList<>();

      for (int var4 = 0; var4 < 10; var4++) {
         double var5 = (Math.PI * 2) * var4 / 10.0;
         Location var7 = var2.clone().add(Math.cos(var5) * 2.5, 0.2, Math.sin(var5) * 2.5);
         var3.add((Cat)var2.getWorld().spawn(var7, Cat.class, var0 -> {
            var0.setPersistent(false);
            var0.customName(Msg.mm("<#FF9AC8>Chat d'Oniris"));
         }));
      }

      Msg.broadcastSurvie(
         Msg.mm("<#FF9AC8>\ud83d\udc31</#FF9AC8> ")
            .append(this.pl.ranks().display(var1))
            .append(Msg.mm(" <#FF9AC8>a ouvert la chatterie d'Oniris !</#FF9AC8>"))
      );
      ArrayList<Player> var8 = new ArrayList<>();

      for (Player var6 : var2.getWorld().getPlayers()) {
         if (var6.getLocation().distanceSquared(var2) < 2304.0) {
            var8.add(var6);
         }
      }

      for (Player var12 : var8) {
         Util.sound(var12, "music_disc.pigstep", 2.0F, 1.0F);
      }

      int[] var11 = new int[]{0};
      Bukkit.getScheduler().runTaskTimer(this.pl, var3x -> {
         if (var11[0]++ >= 50) {
            var3x.cancel();

            for (Player var14 : var8) {
               if (var14.isOnline()) {
                  var14.stopSound(SoundStop.named(Key.key("music_disc.pigstep")));
               }
            }

            for (Cat var15 : var3) {
               if (var15.isValid()) {
                  try {
                     var15.getWorld().spawnParticle(Particle.POOF, var15.getLocation(), 8, 0.2, 0.2, 0.2, 0.02);
                  } catch (Throwable var9) {
                  }

                  var15.remove();
               }
            }
         } else {
            for (Cat var5x : var3) {
               if (var5x.isValid() && ThreadLocalRandom.current().nextInt(3) == 0) {
                  String var6x = CAT_SOUNDS[ThreadLocalRandom.current().nextInt(CAT_SOUNDS.length)];

                  for (Player var8x : var8) {
                     if (var8x.isOnline()) {
                        Util.sound(var8x, var6x, 1.5F, 0.8F + ThreadLocalRandom.current().nextFloat() * 0.6F);
                     }
                  }
               }
            }

            for (Player var13 : var8) {
               if (var13.isOnline() && var11[0] % 2 == 0) {
                  Util.sound(var13, "block.note_block.basedrum", 1.2F, 1.0F);
               }
            }
         }
      }, 0L, 4L);
   }

   private void sugar(Player var1) {
      Util.give(
         var1,
         List.of(
            Util.named(new ItemStack(Material.CAKE), "<#FF9AC8>Gâteau offert par ton sugar daddy"),
            Util.named(new ItemStack(Material.COOKIE), "<#FF9AC8>Cookie offert par ton sugar daddy"),
            Util.named(new ItemStack(Material.CAKE), "<#FF9AC8>Gâteau offert par ton sugar daddy")
         )
      );
      Msg.poulpy(var1, "Ton sugar daddy de l'IMT t'offre deux gâteaux et un cookie. Ne lui demande pas d'où vient l'argent.");
      Util.sound(var1, "entity.player.levelup", 0.6F, 1.6F);
   }

   private void wei(Player var1) {
      ItemStack var2 = new ItemStack(Material.FIREWORK_ROCKET);
      if (var2.getItemMeta() instanceof FireworkMeta var3) {
         Color[] var5 = new Color[]{Color.fromRGB(5227519), Color.fromRGB(16736174), Color.fromRGB(16763196), Color.fromRGB(5635976)};
         var3.setPower(1);
         var3.addEffect(
            FireworkEffect.builder()
               .with(Type.BALL_LARGE)
               .withColor(var5[ThreadLocalRandom.current().nextInt(var5.length)])
               .withFade(Color.WHITE)
               .trail(true)
               .build()
         );
         var3.displayName(Util.noItalic(Msg.mm("<#FFC93C>Feu d'artifice du WEI")));
         var2.setItemMeta(var3);
      }

      Util.give(var1, List.of(var2));
      Msg.poulpy(var1, "Un feu d'artifice du WEI, rien que pour toi. (Un seul. C'est le budget.)");
   }

   private void tunnel(Player var1) {
      ArrayList var2 = new ArrayList<>(this.stories.keySet());
      List var3 = this.stories.get(var2.get(ThreadLocalRandom.current().nextInt(var2.size())));
      Component var4 = Msg.mm(this.pl.ranks().prefix(var1) + " <white><n></white> <dark_gray>»</dark_gray> ", Msg.p("n", var1.getName()));

      for (int var5 = 0; var5 < var3.size(); var5++) {
         String var6 = (String)var3.get(var5);
         Bukkit.getScheduler().runTaskLater(this.pl, () -> {
            if (var1.isOnline()) {
               Msg.broadcastSurvie(var4.append(Component.text(var6, NamedTextColor.WHITE)));
            }
         }, 10L + var5 * 50L);
      }

      Bukkit.getScheduler().runTaskLater(this.pl, () -> {
         if (var1.isOnline()) {
            Msg.poulpySurvie("<gray><i>" + var1.getName() + " jure qu'il n'a jamais écrit ça.</i></gray>");
         }
      }, 10L + var3.size() * 50L);
   }

   public void brest(Player var1) {
      this.brest.add(var1.getUniqueId());
      Util.sound(var1, "entity.minecart.riding", 1.0F, 1.0F);
      var1.setHealth(0.0);
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDeath(PlayerDeathEvent var1) {
      if (this.brest.remove(var1.getEntity().getUniqueId())) {
         var1.deathMessage(
            Msg.mm("<gray>")
               .append(this.pl.ranks().display(var1.getEntity()))
               .append(Msg.mm(" <gray>a pris le TER pour Brest. Il n'en est jamais revenu.</gray>"))
         );
      }
   }

   @EventHandler
   public void onAdvancement(PlayerAdvancementDoneEvent var1) {
      var1.message(null);
   }

   private static Map<Enchantment, Integer> enchants(ItemStack var0) {
      return var0.getItemMeta() instanceof EnchantmentStorageMeta var1 ? new HashMap<>(var1.getStoredEnchants()) : new HashMap<>(var0.getEnchantments());
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onAnvil(PrepareAnvilEvent var1) {
      ItemStack var2 = var1.getInventory().getItem(0);
      ItemStack var3 = var1.getInventory().getItem(1);
      if (var2 != null && var3 != null && !var2.getType().isAir() && !var3.getType().isAir()) {
         boolean var4 = var3.getType() == Material.ENCHANTED_BOOK;
         if (var4 || var3.getType() == var2.getType()) {
            Map<Enchantment, Integer> var5 = enchants(var2);
            Map<Enchantment, Integer> var6 = enchants(var3);
            ItemStack var7 = var1.getResult() != null && !var1.getResult().getType().isAir() ? var1.getResult().clone() : null;
            boolean var8 = var2.getType() == Material.ENCHANTED_BOOK;
            HashSet var9 = new HashSet(var5.keySet());
            var9.addAll(var6.keySet());
            int var10 = 0;
            Iterator var11 = var9.iterator();

            while (true) {
               Enchantment var12;
               int var14;
               int var28;
               while (true) {
                  if (!var11.hasNext()) {
                     if (var7 != null && var10 == 0 && var1.getResult() == null) {
                        var7 = null;
                     }

                     if (var7 != null && !var7.equals(var1.getResult())) {
                        if (var10 == 0) {
                           var10 = 5;
                        }

                        String var23 = null;

                        try {
                           var23 = var1.getView().getRenameText();
                        } catch (Throwable var22) {
                        }

                        if (var23 != null && !var23.isBlank()) {
                           ItemMeta var24 = var7.getItemMeta();
                           var24.displayName(Component.text(var23));
                           var7.setItemMeta(var24);
                        }

                        var1.setResult(var7);
                        int var25 = 0;

                        try {
                           var25 = var1.getView().getRepairCost();
                        } catch (Throwable var21) {
                        }

                        int var26 = Math.max(1, Math.min(39, Math.max(var25, var10)));

                        try {
                           AnvilView var27 = var1.getView();
                           var27.setMaximumRepairCost(1000);
                           var27.setRepairCost(var26);
                           Bukkit.getScheduler().runTask(this.pl, () -> {
                              try {
                                 if (var27.getTopInventory().getResult() != null) {
                                    var27.setMaximumRepairCost(1000);
                                    var27.setRepairCost(var26);
                                 }
                              } catch (Throwable var3x) {
                              }
                           });
                        } catch (Throwable var20) {
                        }

                        return;
                     }

                     return;
                  }

                  var12 = (Enchantment)var11.next();
                  int var13 = var12.getMaxLevel();
                  if (var13 >= 2) {
                     var14 = var5.getOrDefault(var12, 0);
                     int var15 = var6.getOrDefault(var12, 0);
                     var28 = var14 == var15 && var14 >= var13 ? var14 + 1 : Math.max(var14, var15);
                     var28 = Math.min(10, var28);
                     if (var28 > var13) {
                        if (var14 != 0) {
                           break;
                        }

                        if (var8 || var12.canEnchantItem(var2)) {
                           boolean var17 = false;

                           for (Enchantment var19 : var5.keySet()) {
                              if (!var19.equals(var12) && var19.conflictsWith(var12)) {
                                 var17 = true;
                              }
                           }

                           if (!var17) {
                              break;
                           }
                        }
                     }
                  }
               }

               if (var7 == null) {
                  var7 = var2.clone();
               }

               if (var7.getItemMeta() instanceof EnchantmentStorageMeta var29) {
                  var29.addStoredEnchant(var12, var28, true);
                  var7.setItemMeta(var29);
               } else {
                  var7.addUnsafeEnchantment(var12, var28);
               }

               if (var28 > var14) {
                  var10 += var28 * 4;
               }
            }
         }
      }
   }
}
