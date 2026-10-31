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
    private final Map<UUID, CreationSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, NationData> nations = new HashMap<>();
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
        Player p = (Player) e.getWhoClicked();
        if (e.getView().getTitle().equals("Выбор формирования:")) {
            e.setCancelled(true); if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
            CreationSession session = new CreationSession();
            session.type = e.getCurrentItem().getItemMeta().getDisplayName();
            sessions.put(p.getUniqueId(), session);
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + "Напишите НАЗВАНИЕ формирования в чат (без пробелов):");
        } 
        else if (e.getView().getTitle().equals("Выберите цвет Таба:")) {
            e.setCancelled(true); if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
            CreationSession session = sessions.get(p.getUniqueId()); if (session == null) return;
            
            try {
                String colorName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                session.color = ChatColor.valueOf(colorName);
                p.closeInventory();
                finaliseNation(p, session);
            } catch (Exception ex) {
                p.sendMessage(ChatColor.RED + "Ошибка выбора цвета.");
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (sessions.containsKey(p.getUniqueId())) {
            CreationSession session = sessions.get(p.getUniqueId());
            if (session.name == null) {
                e.setCancelled(true);
                session.name = e.getMessage().replace(" ", "_");
                Bukkit.getScheduler().runTask(this, () -> openColorMenu(p));
                return;
            }
        }
        if (nations.containsKey(p.getUniqueId())) {
            NationData data = nations.get(p.getUniqueId());
            e.setFormat(data.color + "[" + data.name + "] " + ChatColor.RESET + e.getFormat());
        }
    }

    private void openColorMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 18, "Выберите цвет Таба:");
        
        inv.setItem(0, createItem(Material.BLACK_WOOL, "BLACK"));
        inv.setItem(1, createItem(Material.BLUE_WOOL, "DARK_BLUE"));
        inv.setItem(2, createItem(Material.GREEN_WOOL, "DARK_GREEN"));
        inv.setItem(3, createItem(Material.CYAN_WOOL, "DARK_AQUA"));
        inv.setItem(4, createItem(Material.RED_WOOL, "DARK_RED"));
        inv.setItem(5, createItem(Material.PURPLE_WOOL, "DARK_PURPLE"));
        inv.setItem(6, createItem(Material.GOLD_BLOCK, "GOLD"));
        inv.setItem(7, createItem(Material.LIGHT_GRAY_WOOL, "GRAY"));
        inv.setItem(8, createItem(Material.GRAY_WOOL, "DARK_GRAY"));
        
        inv.setItem(9, createItem(Material.LAPIS_BLOCK, "BLUE"));
        inv.setItem(10, createItem(Material.LIME_WOOL, "GREEN"));
        inv.setItem(11, createItem(Material.LIGHT_BLUE_WOOL, "AQUA"));
        inv.setItem(12, createItem(Material.ORANGE_WOOL, "RED"));
        inv.setItem(13, createItem(Material.PINK_WOOL, "LIGHT_PURPLE"));
        inv.setItem(14, createItem(Material.YELLOW_WOOL, "YELLOW"));
        inv.setItem(15, createItem(Material.WHITE_WOOL, "WHITE"));
        
        p.openInventory(inv);
    }

    private void finaliseNation(Player p, CreationSession session) {
        NationData data = new NationData(session.name, session.color);
        nations.put(p.getUniqueId(), data);
        sessions.remove(p.getUniqueId());

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(session.name); if (team == null) team = sb.registerNewTeam(session.name);
        
        team.setColor(session.color);
        team.setPrefix(session.color + "[" + session.name + "] " + ChatColor.RESET);
        team.addEntry(p.getName());

        ItemStack mandate = new ItemStack(Material.NETHER_STAR); ItemMeta m = mandate.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.AQUA + "Мандат [" + session.name + "]");
            m.getPersistentDataContainer().set(mandateKey, PersistentDataType.STRING, session.name);
            mandate.setItemMeta(m);
        }
        p.getInventory().addItem(mandate);
        p.sendMessage(ChatColor.GREEN + "Формирование " + session.color + session.name + ChatColor.GREEN + " успешно зарегистрировано!");
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
            NationData data = nations.get(leader);
            if (data != null) {
                nations.put(p.getUniqueId(), data);
                Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(data.name);
                if (team != null) team.addEntry(p.getName());
                p.sendMessage(ChatColor.GREEN + "Вы успешно вступили в " + data.name);
                invites.remove(p.getUniqueId());
            }
        }
    }

    private static class CreationSession { String type; String name; ChatColor color; }
    private static class NationData { 
        String name; ChatColor color; 
        public NationData(String n, ChatColor c) { this.name = n; this.color = c; }
    }
}
