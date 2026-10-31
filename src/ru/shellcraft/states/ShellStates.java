package ru.shellcraft.states;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import java.util.*;

public class ShellStates extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, String> sessions = new HashMap<>(); // Игрок -> Тип
    private final Map<UUID, UUID> invites = new HashMap<>(); // Цель -> Лидер
    private final Map<UUID, String> playerNation = new HashMap<>(); // Игрок -> Фракция
    private NamespacedKey patentKey; private NamespacedKey mandateKey;

    @Override
    public void onEnable() {
        this.patentKey = new NamespacedKey(this, "patent");
        this.mandateKey = new NamespacedKey(this, "mandate");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("nationadmin")).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player && s.isOp()) {
            ItemStack item = new ItemStack(Material.PAPER); ItemMeta m = item.getItemMeta();
            if (m != null) {
                m.setDisplayName(ChatColor.GOLD + "Государственный Патент");
                m.getPersistentDataContainer().set(patentKey, PersistentDataType.STRING, "true");
                item.setItemMeta(m);
            }
            ((Player) s).getInventory().addItem(item);
            s.sendMessage(ChatColor.GREEN + "Вы получили Патент!");
        }
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack i = e.getItem(); if (i == null || i.getItemMeta() == null) return;
        if (i.getItemMeta().getPersistentDataContainer().has(patentKey, PersistentDataType.STRING)) {
            e.setCancelled(true);
            Inventory inv = Bukkit.createInventory(null, 9, "Выбор формирования:");
            inv.setItem(3, createItem(Material.IRON_SWORD, "ЧВК"));
            inv.setItem(5, createItem(Material.GOLDEN_HELMET, "Государство"));
            e.getPlayer().openInventory(inv);
        }
    }

    private ItemStack createItem(Material m, String name) {
        ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); i.setItemMeta(meta); } return i;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals("Выбор формирования:")) {
            e.setCancelled(true); if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
            Player p = (Player) e.getWhoClicked();
            sessions.put(p.getUniqueId(), e.getCurrentItem().getItemMeta().getDisplayName());
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + "Напишите НАЗВАНИЕ формирования в чат:");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (sessions.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            String name = e.getMessage().replace(" ", "_");
            playerNation.put(p.getUniqueId(), name);
            sessions.remove(p.getUniqueId());

            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = sb.getTeam(name); if (team == null) team = sb.registerNewTeam(name);
            team.setColor(ChatColor.GOLD);
            team.setPrefix(ChatColor.GOLD + "[" + name + "] " + ChatColor.RESET);
            team.addEntry(p.getName());

            ItemStack mandate = new ItemStack(Material.NETHER_STAR); ItemMeta m = mandate.getItemMeta();
            if (m != null) {
                m.setDisplayName(ChatColor.AQUA + "Мандат [" + name + "]");
                m.getPersistentDataContainer().set(mandateKey, PersistentDataType.STRING, name);
                mandate.setItemMeta(m);
            }
            p.getInventory().addItem(mandate);
            p.sendMessage(ChatColor.GREEN + "Формирование создано! Вы получили Мандат.");
            return;
        }
        if (playerNation.containsKey(p.getUniqueId())) {
            e.setFormat(ChatColor.GOLD + "[" + playerNation.get(p.getUniqueId()) + "] " + ChatColor.RESET + e.getFormat());
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player leader = e.getPlayer();
        if (e.getRightClicked() instanceof Player && leader.isSneaking()) {
            Player target = (Player) e.getRightClicked();
            ItemStack item = leader.getInventory().getItemInMainHand();
            if (item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(mandateKey, PersistentDataType.STRING)) {
                String natName = item.getItemMeta().getPersistentDataContainer().get(mandateKey, PersistentDataType.STRING);
                invites.put(target.getUniqueId(), leader.getUniqueId());
                target.sendMessage(ChatColor.AQUA + "Вас приглашают в " + natName + ". Напишите 'вступление'.");
            }
        }
    }

    @EventHandler
    public void onAccept(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (e.getMessage().equalsIgnoreCase("вступление") && invites.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            UUID leader = invites.get(p.getUniqueId());
            String natName = playerNation.get(leader);
            if (natName != null) {
                playerNation.put(p.getUniqueId(), natName);
                Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(natName);
                if (team != null) team.addEntry(p.getName());
                p.sendMessage(ChatColor.GREEN + "Вы вступили в " + natName);
                invites.remove(p.getUniqueId());
            }
        }
    }
}
