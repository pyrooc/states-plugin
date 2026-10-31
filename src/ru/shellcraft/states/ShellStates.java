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
    private final Map<String, NationData> nations = new HashMap<>();
    private final Map<UUID, String> playerNation = new HashMap<>();
    private NamespacedKey patentKey;
    private NamespacedKey mandateKey;

    @Override
    public void onEnable() {
        this.patentKey = new NamespacedKey(this, "patent");
        this.mandateKey = new NamespacedKey(this, "mandate");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("nationadmin")).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (p.isOp()) {
                p.getInventory().addItem(createPatentItem());
                p.sendMessage(ChatColor.GREEN + "Вы получили Государственный Патент!");
            } else {
                p.sendMessage(ChatColor.RED + "У вас нет прав (нужна опка).");
            }
        }
        return true;
    }

    public ItemStack createPatentItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Государственный Патент");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Нажмите ПКМ, чтобы начать", ChatColor.GRAY + "регистрацию великого формирования."));
            meta.getPersistentDataContainer().set(patentKey, PersistentDataType.STRING, "true");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createMandateItem(String nationName) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Дипломатический Мандат [" + nationName + "]");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Shift + ПКМ по игроку, чтобы", ChatColor.GRAY + "пригласить его в ваше формирование."));
            meta.getPersistentDataContainer().set(mandateKey, PersistentDataType.STRING, nationName);
            item.setItemMeta(meta);
        }
        return item;
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getItemMeta() == null) return;
        if (item.getItemMeta().getPersistentDataContainer().has(patentKey, PersistentDataType.STRING)) {
            e.setCancelled(true);
            openTypeMenu(p);
        }
    }

    private void openTypeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "Тип формирования:");
        ItemStack cwc = new ItemStack(Material.IRON_SWORD);
        ItemMeta m1 = cwc.getItemMeta(); 
        if (m1 != null) { m1.setDisplayName(ChatColor.RED + "Частная Военная Компания (ЧВК)"); cwc.setItemMeta(m1); }
        ItemStack state = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta m2 = state.getItemMeta(); 
        if (m2 != null) { m2.setDisplayName(ChatColor.GOLD + "Независимое Государство"); state.setItemMeta(m2); }
        inv.setItem(3, cwc); inv.setItem(5, state);
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals("Тип формирования:")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
            Player p = (Player) e.getWhoClicked();
            CreationSession session = new CreationSession();
            session.type = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            sessions.put(p.getUniqueId(), session);
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + "Введите НАЗВАНИЕ вашего формирования в чат (без пробелов):");
        } 
        else if (e.getView().getTitle().equals("Выберите цвет Таба:")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
            Player p = (Player) e.getWhoClicked();
            CreationSession session = sessions.get(p.getUniqueId());
            if (session == null) return;
            String colorName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            session.color = ChatColor.valueOf(colorName);
            if (session.type != null && session.type.contains("Государство")) { openIdeologyMenu(p); } 
            else { finaliseNation(p, session); }
        } 
        else if (e.getView().getTitle().equals("Выберите идеологию:")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;
            Player p = (Player) e.getWhoClicked();
            CreationSession session = sessions.get(p.getUniqueId());
            if (session == null) return;
            session.ideology = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            finaliseNation(p, session);
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
        if (playerNation.containsKey(p.getUniqueId())) {
            String natName = playerNation.get(p.getUniqueId());
            NationData data = nations.get(natName);
            if (data != null) { e.setFormat(data.color + "[" + natName + "] " + ChatColor.RESET + e.getFormat()); }
        }
    }

    private void openColorMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "Выберите цвет Таба:");
        inv.setItem(2, createColorItem(Material.RED_WOOL, "RED"));
        inv.setItem(3, createColorItem(Material.BLUE_WOOL, "BLUE"));
        inv.setItem(4, createColorItem(Material.GREEN_WOOL, "GREEN"));
        inv.setItem(5, createColorItem(Material.YELLOW_WOOL, "YELLOW"));
        inv.setItem(6, createColorItem(Material.GOLD_BLOCK, "GOLD"));
        p.openInventory(inv);
    }

    private ItemStack createColorItem(Material mat, String name) {
        ItemStack i = new ItemStack(mat); ItemMeta m = i.getItemMeta(); 
        if (m != null) { m.setDisplayName(name); i.setItemMeta(m); } return i;
    }

    private void openIdeologyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "Выберите идеологию:");
        inv.setItem(2, createColorItem(Material.BOOK, "Демократия"));
        inv.setItem(4, createColorItem(Material.ANVIL, "Монархия"));
        inv.setItem(6, createColorItem(Material.TNT, "Диктатура"));
        p.openInventory(inv);
    }

    private void finaliseNation(Player p, CreationSession session) {
        p.closeInventory();
        NationData data = new NationData(session.name, session.type, session.color, session.ideology, p.getUniqueId());
        nations.put(session.name, data);
        playerNation.put(p.getUniqueId(), session.name);
        sessions.remove(p.getUniqueId());
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(session.name);
        if (team == null) { team = sb.registerNewTeam(session.name); }
        if (session.color != null) {
            team.setColor(session.color);
            team.setPrefix(session.color + "[" + session.name + "] " + ChatColor.RESET);
        }
        team.addEntry(p.getName());
        p.sendMessage(ChatColor.GREEN + "Формирование " + session.color + session.name + ChatColor.GREEN + " успешно создано!");
        p.getInventory().addItem(createMandateItem(session.name));
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        Player leader = e.getPlayer();
        if (e.getRightClicked() instanceof Player && leader.isSneaking()) {
            Player target = (Player) e.getRightClicked();
            ItemStack item = leader.getInventory().getItemInMainHand();
            if (item.getItemMeta() != null && item.getItemMeta().getPersistentDataContainer().has(mandateKey, PersistentDataType.STRING)) {
                String nationName = item.getItemMeta().getPersistentDataContainer().get(mandateKey, PersistentDataType.STRING);
                if (nationName != null) {
                    invites.put(target.getUniqueId(), leader.getUniqueId());
                    target.sendMessage(ChatColor.AQUA + "Лидер " + leader.getName() + " приглашает вас в " + nationName);
                    target.sendMessage(ChatColor.YELLOW + "Напишите в чат " + ChatColor.GREEN + "вступление" + ChatColor.YELLOW + " для подтверждения.");
                    leader.sendMessage(ChatColor.GREEN + "Приглашение отправлено игроку " + target.getName());
                }
            }
        }
    }

    @EventHandler
    public void onAcceptInvite(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (e.getMessage().equalsIgnoreCase("вступление") && invites.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            UUID leaderUUID = invites.get(p.getUniqueId());
            if (leaderUUID == null) return;
            String natName = playerNation.get(leaderUUID);
            if (natName != null) {
                playerNation.put(p.getUniqueId(), natName);
                Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                Team team = sb.getTeam(natName);
                if (team != null) { team.addEntry(p.getName()); }
                p.sendMessage(ChatColor.GREEN + "Вы успешно вступили в формирование " + natName);
                invites.remove(p.getUniqueId());
            }
        }
    }

    private static class CreationSession { String type; String name; String ideology; ChatColor color; }
    private static class NationData {
        String name; String type; String ideology; ChatColor color; UUID leader; List<UUID> members = new ArrayList<>();
        public NationData(String n, String t, ChatColor c, String i, UUID l) {
            this.name = n; this.type = t; this.color = c; this.ideology = i; this.leader = l; this.members.add(l);
        }
    }
}
