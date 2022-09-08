package discordcollectionlogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DiscordCollectionLoggerConfig.GROUP)
public interface DiscordCollectionLoggerConfig extends Config{
    String GROUP = "discordcollectionlogger";

    @ConfigItem(
            keyName = "webhook",
            name = "Webhook URL",
            description = "The Discord Webhook URL to send messages to"
    )
    String webhook();

    @ConfigItem(
            keyName = "sendScreenshot",
            name = "Send Screenshot",
            description = "Includes a screenshot when receiving the loot"
    )
    default boolean sendScreenshot()
    {
        return false;
    }


    @ConfigItem(
            keyName = "includeusername",
            name = "Include Username",
            description = "Include your RSN in the post."
    )
    default boolean includeUsername()
    {
        return false;
    }

    @ConfigItem(
            keyName = "includepets",
            name = "Include Pets",
            description = "Log pet drops."
    )
    default boolean includePets()
    {
        return false;
    }

    @ConfigItem(
            keyName = "includecollectionlog",
            name = "Include Collection Log",
            description = "Log collection log drops. (Must enable Settings>Chat>Collection log - New addition notification)"
    )
    default boolean includeCollectionLog()
    {
        return false;
    }

    @ConfigItem(
            keyName = "includecollectionimage",
            name = "Include Collection Image",
            description = "For collection log drops include an image of the item if found"
    )
    default boolean includeCollectionImage()
    {
        return true;
    }
}
