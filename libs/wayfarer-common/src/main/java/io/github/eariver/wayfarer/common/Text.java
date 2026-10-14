package io.github.eariver.wayfarer.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Small text helpers (MiniMessage is bundled with Paper). */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage == null ? "" : miniMessage);
    }

    public static Component mm(String miniMessage, String... placeholders) {
        if (placeholders.length == 0) return mm(miniMessage);
        var resolvers = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[placeholders.length / 2];
        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = Placeholder.unparsed(placeholders[i * 2], placeholders[i * 2 + 1]);
        }
        return MM.deserialize(miniMessage, resolvers);
    }

    public static String plain(Component component) {
        return MM.serialize(component);
    }
}
