package fr.bdeimt.serveur;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class Menu implements InventoryHolder {
   protected Inventory inv;

   protected Menu(int var1, Component var2) {
      this.inv = Bukkit.createInventory(this, var1 * 9, var2);
   }

   public Inventory getInventory() {
      return this.inv;
   }

   public void open(Player var1) {
      var1.openInventory(this.inv);
   }

   protected void fill() {
      ItemStack var1 = Util.item(Material.GRAY_STAINED_GLASS_PANE, 1, " ");

      for (int var2 = 0; var2 < this.inv.getSize(); var2++) {
         if (this.inv.getItem(var2) == null) {
            this.inv.setItem(var2, var1);
         }
      }
   }

   public abstract void click(Player var1, int var2);

   public static final class Listen implements Listener {
      @EventHandler
      public void onClick(InventoryClickEvent var1) {
         if (var1.getView().getTopInventory().getHolder() instanceof Menu var2) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player var5) {
               int var6 = var1.getRawSlot();
               if (var6 >= 0 && var6 < var1.getView().getTopInventory().getSize()) {
                  Bukkit.getScheduler().runTask(BDEIMT.get(), () -> {
                     if (var5.isOnline()) {
                        var2.click(var5, var6);
                     }
                  });
               }
            }
         }
      }

      @EventHandler
      public void onDrag(InventoryDragEvent var1) {
         if (var1.getView().getTopInventory().getHolder() instanceof Menu) {
            var1.setCancelled(true);
         }
      }
   }
}
