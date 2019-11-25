package fr.breakerland.breakerlandsleep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class BreakerLandSleep extends JavaPlugin implements CommandExecutor, Listener {
	final Map<UUID, Long> cooldown = new HashMap<>();
	final Random random = new Random();
	BukkitTask task = null;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		getCommand("cancel").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		if (task != null)
			task.cancel();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (! (sender instanceof Player) || !sender.hasPermission("breakerbed.cancel"))
			return false;

		if (task == null || task.isCancelled())
			sender.sendMessage(parseColor(getConfig().getString("canceledMessage", "Skipping night already canceled.")));
		else {
			getServer().broadcastMessage(parseColor(getConfig().getString("cancelMessage", "&eThe night skipping was canceled by %player%.").replaceFirst("%player%", sender.getName())));
			task.cancel();
		}

		return true;
	}

	@EventHandler(ignoreCancelled = true)
	public void PlayerEnterBed(PlayerBedEnterEvent event) {
		Player player = event.getPlayer();
		if (!player.hasPermission("breakerlandsleep.sleep"))
			return;

		Long time,
				now = System.currentTimeMillis();
		if ( (time = cooldown.get(player.getUniqueId())) != null && time < now) {
			player.sendMessage(parseColor(getConfig().getString("", "You need to wait before to use your bed again.")));
			return;
		} else
			cooldown.put(player.getUniqueId(), now);

		World world = player.getWorld();
		if ( (world.getTime() >= 13000 || world.isThundering()) && (task == null || task.isCancelled())) {
			task = getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
				if (world.getTime() >= 13000)
					world.setTime(0);

				if (world.isThundering())
					world.setThundering(false);

				if (world.hasStorm())
					world.setStorm(false);

				task = null;
			}, 20 * getConfig().getInt("sleepTime", 10));

			List<String> messages = getConfig().getStringList("sleepingMessages");
			String[] message = parseColor(messages.get(random.nextInt(messages.size())).replaceFirst("%player%", player.getName())).split("%cancel%");
			TextComponent component = new TextComponent(message[0]);
			if (message.length > 1) {
				TextComponent cancel = new TextComponent(getConfig().getString("cancel", "&f[&4CANCEL&f]"));
				cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cancel"));
				cancel.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(parseColor(getConfig().getString("cancelHover", "Click to cancel"))).create()));
				component.addExtra(cancel);
				component.addExtra(parseColor(message[1]));
			}

			for (Player players : Bukkit.getOnlinePlayers())
				if (!players.equals(player) && players.hasPermission("breakerbed.cancel"))
					players.spigot().sendMessage(component);
		}
	}

	private String parseColor(String input) {
		return ChatColor.translateAlternateColorCodes('&', input);
	}
}