package ru.theabyss.chatrelay;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ChatRelay — captures player chat and hands it to a poller (a bot) on demand.
 *
 * How it works:
 *  - Every player chat message is stored in an in-memory buffer (nick + plain text,
 *    with all color/format codes already stripped by the plain-text serializer).
 *  - The bot polls over RCON by running /chatpull. That command DRAINS the buffer
 *    and returns every message as one text block, each line "[Nick]: message".
 *    The reply is sent synchronously to the command sender, so RCON captures it.
 *  - Messages older than 15 seconds are dropped automatically (a prune task), so if
 *    the bot stops polling the buffer cannot grow without bound.
 *
 * No open ports, no incoming HTTP: the bot pulls through the existing RCON channel.
 */
public final class ChatRelay extends JavaPlugin implements Listener {

    /** Messages live at most this long if the bot does not pull them. */
    private static final long TTL_MS = 15_000L;

    /** Thread-safe: chat events fire on async threads, /chatpull runs on the main thread. */
    private final Queue<Msg> buffer = new ConcurrentLinkedQueue<>();

    private record Msg(String nick, String text, long at) {}

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        // Prune expired messages every 5 seconds (100 ticks).
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::prune, 100L, 100L);
        getLogger().info("ChatRelay enabled (TTL " + (TTL_MS / 1000) + "s).");
    }

    @Override
    public void onDisable() {
        buffer.clear();
        getLogger().info("ChatRelay disabled.");
    }

    /** Runs on an async thread. AsyncChatEvent is Paper's modern chat event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String nick = stripCodes(event.getPlayer().getName());
        // Plain text drops component formatting; stripCodes also removes any literal
        // § the player typed, so the buffered text is guaranteed code-free.
        String text = stripCodes(PlainTextComponentSerializer.plainText().serialize(event.message()));
        buffer.add(new Msg(nick, text, System.currentTimeMillis()));
    }

    /** Removes every § color/format code (legacy and §x hex), plus any stray §. */
    private static String stripCodes(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§.", "").replace("§", "");
    }

    private void prune() {
        long now = System.currentTimeMillis();
        buffer.removeIf(m -> now - m.at() > TTL_MS);
    }

    /**
     * /chatpull — drains the buffer and returns the messages.
     * Called by the bot over RCON. Reply is synchronous so RCON captures it.
     * Empty buffer -> empty reply (the bot just sees no messages).
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("chatpull")) {
            return false;
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        Msg m;
        while ((m = buffer.poll()) != null) {
            if (now - m.at() > TTL_MS) {
                continue; // expired, skip
            }
            sb.append('[').append(m.nick()).append("]: ").append(m.text()).append('\n');
        }
        String out = sb.length() == 0 ? "" : sb.toString().stripTrailing();
        sender.sendMessage(Component.text(out));
        return true;
    }
}
