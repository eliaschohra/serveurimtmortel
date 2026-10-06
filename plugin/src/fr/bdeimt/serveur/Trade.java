package fr.bdeimt.serveur;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

public final class Trade implements CommandExecutor, TabCompleter, Listener {
   private static final long REQUEST_LIFE = 60000L;
   private static final long REQUEST_COOLDOWN = 10000L;
   private static final int[] LEFT = slots(0);
   private static final int[] RIGHT = slots(5);
   private static final int BTN_A = 46;
   private static final int BTN_B = 52;
   private static final int INFO = 49;
   private static final Set<String> TOP_OK = Set.of(
      "PICKUP_ALL",
      "PICKUP_SOME",
      "PICKUP_HALF",
      "PICKUP_ONE",
      "PLACE_ALL",
      "PLACE_SOME",
      "PLACE_ONE",
      "SWAP_WITH_CURSOR",
      "HOTBAR_SWAP",
      "MOVE_TO_OTHER_INVENTORY",
      "NOTHING"
   );
   private final BDEIMT pl;
   private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
   private final Map<UUID, Long> lastRequest = new HashMap<>();
   private final Map<UUID, Trade.Session> sessions = new HashMap<>();

   private static int[] slots(int var0) {
      int[] var1 = new int[16];
      int var2 = 0;

      for (int var3 = 1; var3 <= 4; var3++) {
         for (int var4 = var0; var4 < var0 + 4; var4++) {
            var1[var2++] = var3 * 9 + var4;
         }
      }

      return var1;
   }

   private static boolean in(int[] var0, int var1) {
      for (int var5 : var0) {
         if (var5 == var1) {
            return true;
         }
      }

      return false;
   }

   public Trade(BDEIMT var1) {
      this.pl = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (Msg.noConsole(var1)) {
         return true;
      } else {
         Player var5 = (Player)var1;
         if (this.pl.lobby().isLobby(var5.getWorld())) {
            Msg.err(var5, "Impossible depuis le lobby.");
            return true;
         } else if (var4.length == 0) {
            Msg.info(var5, "Utilisation : <white>/echange ‹joueur›</white> — puis l'autre accepte, vous posez vos objets et validez tous les deux.");
            return true;
         } else {
            String var6 = var4[0].toLowerCase(Locale.ROOT);
            if (var6.equals("accepter") || var6.equals("accept")) {
               this.accept(var5, var4.length > 1 ? var4[1] : null);
               return true;
            } else if (!var6.equals("refuser") && !var6.equals("deny")) {
               this.ask(var5, var4[0]);
               return true;
            } else {
               this.deny(var5, var4.length > 1 ? var4[1] : null);
               return true;
            }
         }
      }
   }

   private void ask(Player var1, String var2) {
      Player var3 = Bukkit.getPlayerExact(var2);
      if (var3 == null || !this.pl.auth().isLogged(var3) || !var1.canSee(var3) || this.pl.lobby().isLobby(var3.getWorld())) {
         Msg.err(var1, "Joueur introuvable.");
      } else if (var3.equals(var1)) {
         Msg.err(var1, "Échanger avec toi-même ? Pas besoin.");
      } else if (this.sessions.containsKey(var1.getUniqueId()) || this.sessions.containsKey(var3.getUniqueId())) {
         Msg.err(var1, "Un échange est déjà en cours.");
      } else if (this.pl.data().get(var3).ignores.contains(var1.getUniqueId())) {
         Msg.err(var1, "<white><n></white> n'accepte pas tes demandes.", Msg.p("n", var3.getName()));
      } else {
         long var4 = System.currentTimeMillis();
         Long var6 = this.lastRequest.get(var1.getUniqueId());
         if (var6 != null && var4 - var6 < 10000L) {
            Msg.err(var1, "Attends un peu avant une nouvelle demande.");
         } else {
            Map var7 = this.requests.get(var1.getUniqueId());
            if (var7 != null && var7.containsKey(var3.getUniqueId()) && var4 - (Long)var7.get(var3.getUniqueId()) < 60000L) {
               var7.remove(var3.getUniqueId());
               this.open(var3, var1);
            } else {
               this.lastRequest.put(var1.getUniqueId(), var4);
               this.requests.computeIfAbsent(var3.getUniqueId(), var0 -> new HashMap<>()).put(var1.getUniqueId(), var4);
               Msg.ok(var1, "Demande d'échange envoyée à <white><n></white>.", Msg.p("n", var3.getName()));
               Msg.raw(
                  var3,
                  "<#FFC93C>⇄</#FFC93C> <white><n></white> <gray>te propose un échange.</gray> <click:run_command:'/echange accepter "
                     + var1.getName()
                     + "'><hover:show_text:'<green>Ouvrir l’échange'><#55FF88><bold>[Accepter]</bold></#55FF88></hover></click> <click:run_command:'/echange refuser "
                     + var1.getName()
                     + "'><hover:show_text:'<red>Refuser'><#FF5555><bold>[Refuser]</bold></#FF5555></hover></click>",
                  Msg.p("n", var1.getName())
               );
               Msg.alert(
                  var3,
                  "<#FFC93C><bold>Échange</bold></#FFC93C>",
                  "<white>" + var1.getName() + "</white> <gray>te propose un échange</gray>"
               );
               Msg.bar(var3, "<gray>/echange accepter <white>" + var1.getName() + "</white></gray>");
            }
         }
      }
   }

   private UUID pick(Player var1, String var2) {
      Map<UUID, Long> var3 = this.requests.get(var1.getUniqueId());
      if (var3 == null) {
         return null;
      } else {
         long var4 = System.currentTimeMillis();
         var3.values().removeIf(var2x -> var4 - var2x > 60000L);
         if (var2 == null) {
            UUID var11 = null;
            long var7 = 0L;

            for (Entry<UUID, Long> var10 : var3.entrySet()) {
               if ((Long)var10.getValue() > var7) {
                  var7 = (Long)var10.getValue();
                  var11 = (UUID)var10.getKey();
               }
            }

            return var11;
         } else {
            Player var6 = Bukkit.getPlayerExact(var2);
            return var6 != null && var3.containsKey(var6.getUniqueId()) ? var6.getUniqueId() : null;
         }
      }
   }

   private void accept(Player var1, String var2) {
      UUID var3 = this.pick(var1, var2);
      Player var4 = var3 == null ? null : Bukkit.getPlayer(var3);
      if (var4 == null) {
         Msg.err(var1, "Aucune demande d'échange en attente.");
      } else {
         this.requests.get(var1.getUniqueId()).remove(var3);
         if (this.sessions.containsKey(var4.getUniqueId()) || this.sessions.containsKey(var1.getUniqueId())) {
            Msg.err(var1, "Un échange est déjà en cours.");
         } else if (this.pl.lobby().isLobby(var4.getWorld())) {
            Msg.err(var1, "Ce joueur n'est pas disponible.");
         } else {
            this.open(var4, var1);
         }
      }
   }

   private void deny(Player var1, String var2) {
      UUID var3 = this.pick(var1, var2);
      if (var3 == null) {
         Msg.err(var1, "Aucune demande d'échange en attente.");
      } else {
         this.requests.get(var1.getUniqueId()).remove(var3);
         Player var4 = Bukkit.getPlayer(var3);
         Msg.info(var1, "Échange refusé.");
         if (var4 != null) {
            Msg.err(var4, "<white><n></white> a refusé l'échange.", Msg.p("n", var1.getName()));
         }
      }
   }

   private void open(Player var1, Player var2) {
      Trade.Session var3 = new Trade.Session(var1, var2);
      this.sessions.put(var1.getUniqueId(), var3);
      this.sessions.put(var2.getUniqueId(), var3);
      var1.openInventory(var3.inv);
      var2.openInventory(var3.inv);
      Util.sound(var1, "block.chest.open", 0.7F, 1.2F);
      Util.sound(var2, "block.chest.open", 0.7F, 1.2F);
   }

   private static String describe(List<ItemStack> var0) {
      if (var0.isEmpty()) {
         return "rien";
      } else {
         StringBuilder var1 = new StringBuilder();

         for (ItemStack var3 : var0) {
            if (var1.length() > 0) {
               var1.append(", ");
            }

            var1.append(var3.getAmount()).append("x ").append(var3.getType().name().toLowerCase(Locale.ROOT));
         }

         return var1.toString();
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof Trade.Session var2) {
         if (var1.getWhoClicked() instanceof Player var16) {
            if (var2.closed) {
               var1.setCancelled(true);
            } else {
               int var17 = var1.getRawSlot();
               int var5 = var1.getView().getTopInventory().getSize();
               InventoryAction var6 = var1.getAction();
               if (var17 >= 0 && var17 < var5) {
                  if ((var17 != 46 || !var2.isA(var16)) && (var17 != 52 || var2.isA(var16))) {
                     if (in(var2.own(var16), var17) && TOP_OK.contains(var6.name())) {
                        var2.changed();
                        Bukkit.getScheduler().runTask(this.pl, var2::refresh);
                     } else {
                        var1.setCancelled(true);
                     }
                  } else {
                     var1.setCancelled(true);
                     var2.toggle(var16);
                  }
               } else if (var6 == InventoryAction.COLLECT_TO_CURSOR) {
                  var1.setCancelled(true);
               } else {
                  if (var6 == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                     var1.setCancelled(true);
                     ItemStack var7 = var1.getCurrentItem();
                     if (var7 == null || var7.getType().isAir() || var1.getClickedInventory() == null) {
                        return;
                     }

                     ItemStack var8 = var7.clone();
                     int[] var9 = var2.own(var16);

                     for (int var13 : var9) {
                        if (var8.getAmount() <= 0) {
                           break;
                        }

                        ItemStack var14 = var2.inv.getItem(var13);
                        if (var14 != null && var14.isSimilar(var8) && var14.getAmount() < var14.getMaxStackSize()) {
                           int var15 = Math.min(var8.getAmount(), var14.getMaxStackSize() - var14.getAmount());
                           var14.setAmount(var14.getAmount() + var15);
                           var2.inv.setItem(var13, var14);
                           var8.setAmount(var8.getAmount() - var15);
                        }
                     }

                     for (int var21 : var9) {
                        if (var8.getAmount() <= 0) {
                           break;
                        }

                        ItemStack var22 = var2.inv.getItem(var21);
                        if (var22 == null || var22.getType().isAir()) {
                           var2.inv.setItem(var21, var8.clone());
                           var8.setAmount(0);
                        }
                     }

                     var1.getClickedInventory().setItem(var1.getSlot(), var8.getAmount() > 0 ? var8 : null);
                     var2.changed();
                  }
               }
            }
         } else {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof Trade.Session var2) {
         if (var1.getWhoClicked() instanceof Player var8 && !var2.closed) {
            int var9 = var1.getView().getTopInventory().getSize();
            boolean var5 = false;

            for (int var7 : var1.getRawSlots()) {
               if (var7 < var9) {
                  var5 = true;
                  if (!in(var2.own(var8), var7)) {
                     var1.setCancelled(true);
                     return;
                  }
               }
            }

            if (var5) {
               var2.changed();
               Bukkit.getScheduler().runTask(this.pl, var2::refresh);
            }
         } else {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      if (var1.getInventory().getHolder() instanceof Trade.Session var2 && !var2.closed) {
         if (var1.getPlayer() instanceof Player var5) {
            var2.cancel(var5, var5.getName() + " a fermé l'échange", true);
         }
      }
   }

   private void cancelFor(Player var1, String var2) {
      Trade.Session var3 = this.sessions.get(var1.getUniqueId());
      if (var3 != null) {
         var3.cancel(var1, var2, false);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onQuit(PlayerQuitEvent var1) {
      this.cancelFor(var1.getPlayer(), var1.getPlayer().getName() + " s'est déconnecté");
      this.requests.remove(var1.getPlayer().getUniqueId());

      for (Map var3 : this.requests.values()) {
         var3.remove(var1.getPlayer().getUniqueId());
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDeath(PlayerDeathEvent var1) {
      Trade.Session var2 = this.sessions.get(var1.getEntity().getUniqueId());
      if (var2 != null) {
         var2.cancel(var1.getEntity(), var1.getEntity().getName() + " est mort", false, var1.getDrops());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent var1) {
      this.cancelFor(var1.getPlayer(), var1.getPlayer().getName() + " s'est téléporté");
   }

   @EventHandler
   public void onWorld(PlayerChangedWorldEvent var1) {
      this.cancelFor(var1.getPlayer(), var1.getPlayer().getName() + " a changé de monde");
   }

   public void cancelAll() {
      for (Trade.Session var2 : new ArrayList<>(this.sessions.values())) {
         var2.cancel(null, "arrêt du serveur", false);
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      ArrayList<String> var5 = new ArrayList<>();
      if (!(var1 instanceof Player var6)) {
         return var5;
      } else {
         if (var4.length == 1) {
            var5.add("accepter");
            var5.add("refuser");

            for (Player var8 : Bukkit.getOnlinePlayers()) {
               if (!var8.equals(var6) && var6.canSee(var8) && this.pl.auth().isLogged(var8)) {
                  var5.add(var8.getName());
               }
            }
         }

         String var9 = var4.length > 0 ? var4[var4.length - 1].toLowerCase(Locale.ROOT) : "";
         var5.removeIf(var1x -> !var1x.toLowerCase(Locale.ROOT).startsWith(var9));
         return var5;
      }
   }

   private final class Session implements InventoryHolder {
      final Player a;
      final Player b;
      final Inventory inv;
      boolean readyA;
      boolean readyB;
      boolean closed;
      BukkitTask countdown;
      int count;

      Session(Player nullx, Player nullxx) {
         this.a = nullx;
         this.b = nullxx;
         this.inv = Bukkit.createInventory(
            this,
            54,
            Msg.mm("<#FFC93C><bold>⇄ Échange</bold></#FFC93C> <dark_gray><x> ⇄ <y></dark_gray>", Msg.p("x", nullx.getName()), Msg.p("y", nullxx.getName()))
         );
         ItemStack var4 = Util.item(Material.BLACK_STAINED_GLASS_PANE, 1, " ");

         for (int var5 = 0; var5 < 54; var5++) {
            if (!Trade.in(Trade.LEFT, var5) && !Trade.in(Trade.RIGHT, var5)) {
               this.inv.setItem(var5, var4);
            }
         }

         this.inv.setItem(0, this.head(nullx, "<#4FC3FF>Offre de <white>" + nullx.getName()));
         this.inv.setItem(8, this.head(nullxx, "<#FF9AC8>Offre de <white>" + nullxx.getName()));
         this.refresh();
      }

      public Inventory getInventory() {
         return this.inv;
      }

      boolean isA(Player var1) {
         return var1.getUniqueId().equals(this.a.getUniqueId());
      }

      int[] own(Player var1) {
         return this.isA(var1) ? Trade.LEFT : Trade.RIGHT;
      }

      Player other(Player var1) {
         return this.isA(var1) ? this.b : this.a;
      }

      private ItemStack head(Player var1, String var2) {
         ItemStack var3 = new ItemStack(Material.PLAYER_HEAD);
         if (var3.getItemMeta() instanceof SkullMeta var4) {
            var4.setOwningPlayer(var1);
            var4.displayName(Util.noItalic(Msg.mm(var2)));
            var3.setItemMeta(var4);
         }

         return var3;
      }

      void refresh() {
         if (!this.closed) {
            ItemStack var1 = Util.item(
               this.readyA ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
               1,
               this.readyA ? "<#55FF88>✔ " + this.a.getName() + " a validé" : "<#FF5555>✖ " + this.a.getName() + " n'a pas validé"
            );
            ItemStack var2 = Util.item(
               this.readyB ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
               1,
               this.readyB ? "<#55FF88>✔ " + this.b.getName() + " a validé" : "<#FF5555>✖ " + this.b.getName() + " n'a pas validé"
            );

            for (int var3 = 1; var3 <= 3; var3++) {
               this.inv.setItem(var3, var1);
            }

            for (int var4 = 5; var4 <= 7; var4++) {
               this.inv.setItem(var4, var2);
            }

            this.inv.setItem(46, this.button(this.readyA, this.a));
            this.inv.setItem(52, this.button(this.readyB, this.b));
            if (this.countdown != null) {
               this.inv.setItem(49, Util.item(Material.CLOCK, Math.max(1, this.count), "<#FFC93C><bold>Échange dans " + this.count + "...</bold>"));
            } else {
               this.inv
                  .setItem(
                     49,
                     Util.item(
                        Material.OAK_SIGN,
                        1,
                        "<#FFC93C><bold>Comment ça marche ?</bold>",
                        "<gray>Pose ton offre de <white>ton côté</white><gray>.",
                        "<gray>Quand c'est bon, clique sur <green>Valider</green><gray>.",
                        "<gray>Si quelque chose change, les",
                        "<gray>validations sont annulées.",
                        "<gray>Fermer le coffre annule tout."
                     )
                  );
            }
         }
      }

      private ItemStack button(boolean var1, Player var2) {
         return var1
            ? Util.item(
               Material.LIME_CONCRETE, 1, "<#55FF88><bold>Validé ✔</bold>", "<gray>Clique pour annuler ta validation.", "<dark_gray>(" + var2.getName() + ")"
            )
            : Util.item(
               Material.ORANGE_CONCRETE,
               1,
               "<#FFC93C><bold>Valider l'échange</bold>",
               "<gray>Clique quand ton offre est prête.",
               "<dark_gray>(" + var2.getName() + ")"
            );
      }

      void changed() {
         if (!this.closed) {
            if (this.readyA || this.readyB || this.countdown != null) {
               this.readyA = this.readyB = false;
               this.stopCountdown();
            }

            this.refresh();
         }
      }

      void stopCountdown() {
         if (this.countdown != null) {
            this.countdown.cancel();
            this.countdown = null;
         }
      }

      void toggle(Player var1) {
         if (this.isA(var1)) {
            this.readyA = !this.readyA;
         } else {
            this.readyB = !this.readyB;
         }

         Util.sound(var1, "ui.button.click", 0.7F, 1.2F);
         if (this.readyA && this.readyB) {
            this.count = 3;
            this.countdown = Bukkit.getScheduler().runTaskTimer(Trade.this.pl, () -> {
               if (this.closed) {
                  this.stopCountdown();
               } else if (this.count <= 0) {
                  this.stopCountdown();
                  this.complete();
               } else {
                  Util.sound(this.a, "block.note_block.hat", 0.7F, 1.5F);
                  Util.sound(this.b, "block.note_block.hat", 0.7F, 1.5F);
                  this.refresh();
                  this.count--;
               }
            }, 0L, 20L);
         } else {
            this.stopCountdown();
         }

         this.refresh();
      }

      List<ItemStack> take(int[] var1) {
         ArrayList var2 = new ArrayList();

         for (int var6 : var1) {
            ItemStack var7 = this.inv.getItem(var6);
            if (var7 != null && !var7.getType().isAir()) {
               var2.add(var7);
            }

            this.inv.setItem(var6, null);
         }

         return var2;
      }

      void complete() {
         if (!this.closed) {
            this.closed = true;
            List var1 = this.take(Trade.LEFT);
            List var2 = this.take(Trade.RIGHT);
            this.end(null);
            Util.give(this.a, var2);
            Util.give(this.b, var1);
            Msg.ok(this.a, "Échange terminé avec <white><n></white> !", Msg.p("n", this.b.getName()));
            Msg.ok(this.b, "Échange terminé avec <white><n></white> !", Msg.p("n", this.a.getName()));
            Util.sound(this.a, "entity.villager.yes", 0.8F, 1.1F);
            Util.sound(this.b, "entity.villager.yes", 0.8F, 1.1F);
            Trade.this.pl
               .getLogger()
               .info(
                  "Echange "
                     + this.a.getName()
                     + " -> "
                     + this.b.getName()
                     + " : "
                     + Trade.describe(var1)
                     + " | "
                     + this.b.getName()
                     + " -> "
                     + this.a.getName()
                     + " : "
                     + Trade.describe(var2)
               );
         }
      }

      void cancel(Player var1, String var2, boolean var3) {
         this.cancel(var1, var2, var3, null);
      }

      void cancel(Player var1, String var2, boolean var3, List<ItemStack> var4) {
         if (!this.closed) {
            this.closed = true;
            this.stopCountdown();
            List var5 = this.take(Trade.LEFT);
            List var6 = this.take(Trade.RIGHT);
            this.end(var3 ? var1 : null);
            this.giveBack(this.a, var5, var1, var4);
            this.giveBack(this.b, var6, var1, var4);
            Player var7 = var1 == null ? null : this.other(var1);
            if (var7 != null && var7.isOnline()) {
               Msg.err(var7, "Échange annulé : <r>.", Msg.p("r", var2));
            }

            if (var1 != null && var1.isOnline()) {
               Msg.info(var1, "Échange annulé, tes objets te sont rendus.");
            }
         }
      }

      private void giveBack(Player var1, List<ItemStack> var2, Player var3, List<ItemStack> var4) {
         if (var4 != null && var1.equals(var3)) {
            var4.addAll(var2);
         } else if (var1.isOnline()) {
            Util.give(var1, var2);
         } else {
            for (ItemStack var6 : var2) {
               var1.getWorld().dropItemNaturally(var1.getLocation(), var6);
            }
         }
      }

      private void end(Player var1) {
         Trade.this.sessions.remove(this.a.getUniqueId());
         Trade.this.sessions.remove(this.b.getUniqueId());

         for (Player var5 : new Player[]{this.a, this.b}) {
            if (!var5.equals(var1) && var5.isOnline() && var5.getOpenInventory().getTopInventory().getHolder() == this) {
               Bukkit.getScheduler().runTask(Trade.this.pl, () -> {
                  if (var5.isOnline() && var5.getOpenInventory().getTopInventory().getHolder() == this) {
                     var5.closeInventory();
                  }
               });
            }
         }
      }
   }
}
