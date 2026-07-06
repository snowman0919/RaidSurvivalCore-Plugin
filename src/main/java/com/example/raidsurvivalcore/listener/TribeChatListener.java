package com.example.raidsurvivalcore.listener;

import com.example.raidsurvivalcore.chat.ChatObfuscator;
import com.example.raidsurvivalcore.chat.TribeChatState;
import com.example.raidsurvivalcore.tribe.TribeService;
import com.example.raidsurvivalcore.tribe.TribeSnapshot;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class TribeChatListener implements Listener {
    private final TribeService tribes;
    private final TribeChatState chatState;
    private final ChatObfuscator obfuscator = new ChatObfuscator();
    private final Logger logger;

    public TribeChatListener(TribeService tribes, TribeChatState chatState, Logger logger) {
        this.tribes = tribes;
        this.chatState = chatState;
        this.logger = logger;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String plain = obfuscator.sanitizePlain(PlainTextComponentSerializer.plainText().serialize(event.message()));
        UUID messageId = UUID.randomUUID();
        TribeSnapshot snapshot = tribes.snapshot();
        if (chatState.enabled(sender.getUniqueId())) {
            event.viewers().removeIf(viewer -> viewer instanceof Player player && !canReadPlain(sender, player, snapshot));
            logger.info("[TribeChat] " + sender.getName() + ": " + plain);
            event.renderer(tribeRenderer(plain));
            return;
        }
        logger.info("[Chat] " + sender.getName() + ": " + plain);
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            if (viewer instanceof ConsoleCommandSender) return format(sourceDisplayName, Component.text(plain));
            if (viewer instanceof Player target && canReadPlain(sender, target, snapshot)) {
                return format(sourceDisplayName, Component.text(plain));
            }
            UUID viewerId = viewer instanceof Player p ? p.getUniqueId() : new UUID(0L, 0L);
            return format(sourceDisplayName, Component.text(obfuscator.obfuscate(plain, messageId, viewerId, true, true, true)));
        });
    }

    private ChatRenderer tribeRenderer(String plain) {
        return (source, sourceDisplayName, message, viewer) -> Component.text("[부족] ").append(sourceDisplayName).append(Component.text(": ")).append(Component.text(plain));
    }

    private boolean canReadPlain(Player sender, Player viewer, TribeSnapshot snapshot) {
        return sender.getUniqueId().equals(viewer.getUniqueId())
            || viewer.hasPermission("raidcore.chat.spy")
            || snapshot.sameTribe(sender.getUniqueId(), viewer.getUniqueId());
    }

    private Component format(Component sourceDisplayName, Component message) {
        return sourceDisplayName.append(Component.text(": ")).append(message);
    }
}
