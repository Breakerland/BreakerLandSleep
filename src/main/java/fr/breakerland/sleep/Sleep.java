package fr.breakerland.sleep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Sleep extends JavaPlugin implements CommandExecutor, Listener {
	final Map<UUID, Long> cooldown = new HashMap<>();
	final Map<UUID, BukkitTask> tasks = new HashMap<>();
	final Map<UUID, Set<UUID>> sleeping = new HashMap<>();
	final Random random = new Random();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		getCommand("cancel").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		tasks.values().forEach((task) -> task.cancel());
		tasks.clear();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (! (sender instanceof Player) || !sender.hasPermission("breakerlandsleep.cancel"))
			return false;

		BukkitTask task = tasks.get( ((Player) sender).getWorld().getUID());
		if (task == null)
			sender.sendMessage(parseColor(getConfig().getString("noCancel", "&cNobody is sleeping.")));
		else if (task.isCancelled())
			sender.sendMessage(parseColor(getConfig().getString("alreadyCancel", "&cSkipping night already canceled.")));
		else {
			getServer().broadcastMessage(parseColor(getConfig().getString("cancelMessage", "&eThe night skipping was canceled by %player%.").replaceFirst("%player%", sender.getName())));
			task.cancel();
		}

		return true;
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerEnterBed(PlayerBedEnterEvent event) {
		Player player = event.getPlayer();
		if (!player.hasPermission("breakerlandsleep.sleep") || ! (getServer().getOnlinePlayers().size() > 1))
			return;

		Long time = cooldown.get(player.getUniqueId()),
				now = System.currentTimeMillis();
		if (time != null && time < now) {
			player.sendMessage(parseColor(getConfig().getString("cooldownMessage", "&cYou need to wait before to use your bed again.")));
			return;
		} else
			cooldown.put(player.getUniqueId(), now + 20 * getConfig().getInt("sleepCooldown", 60));

		World world = player.getWorld();
		BukkitTask task = null;
		if (world.getTime() >= 13000 || world.isThundering() || world.hasStorm()) {
			UUID worldId = world.getUID();
			if ( (task = tasks.get(worldId)) == null || task.isCancelled()) {
				tasks.put(worldId, getServer().getScheduler().runTaskLater(this, () -> {
					if (world.getTime() >= 13000)
						world.setTime(getConfig().getInt("skipTime", 0));

					world.setThundering(false);
					world.setStorm(false);
					cooldown.clear();
					tasks.remove(worldId);
				}, 20 * getConfig().getInt("sleepTime", 10)));

				List<String> messages = getConfig().getStringList("sleepingMessages");
				String[] message = parseColor(messages.get(random.nextInt(messages.size())).replaceFirst("%player%", player.getName())).split("%cancel%");
				TextComponent component = new TextComponent(message[0]);
				TextComponent cancel = new TextComponent(parseColor(getConfig().getString("cancel", "&f[&4CANCEL&f]")));
				cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cancel"));
				cancel.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(parseColor(getConfig().getString("cancelHover", "Click to cancel"))).create()));
				component.addExtra(cancel);
				if (message.length > 1)
					component.addExtra(parseColor(message[1]));

				for (Player players : getServer().getOnlinePlayers())
					if (!players.equals(player) && players.hasPermission("breakerlandsleep.cancel"))
						players.spigot().sendMessage(component);
			} else if (task != null && !task.isCancelled())
				sleeping.getOrDefault(worldId, new HashSet<>()).add(player.getUniqueId());
		}
	}

	@EventHandler
	public void onPlayerLeaveBed(PlayerBedLeaveEvent event) {
		stopSleeping(event.getPlayer());
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		stopSleeping(event.getPlayer());
	}

	private void stopSleeping(Player player) {
		BukkitTask task;
		Set<UUID> sleeping = this.sleeping.getOrDefault(player.getWorld().getUID(), new HashSet<>());
		if (sleeping.remove(player.getUniqueId()) && ! (sleeping.size() > 0) && (task = tasks.get(player.getWorld().getUID())) != null)
			task.cancel();
	}

	private String parseColor(String input) {
		return ChatColor.translateAlternateColorCodes('&', input);
	}
}
