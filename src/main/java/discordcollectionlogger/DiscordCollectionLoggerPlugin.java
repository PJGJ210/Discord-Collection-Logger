package discordcollectionlogger;

import com.google.common.base.Strings;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.runelite.api.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;

import static net.runelite.http.api.RuneLiteAPI.GSON;

import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
        name = "Discord Collection Logger"
)
public class DiscordCollectionLoggerPlugin extends Plugin {
    @Inject
    private DiscordCollectionLoggerConfig config;
    @Inject
    private ItemManager itemManager;
    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private DrawManager drawManager;

    private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern.compile("New item added to your collection log:.*");
    private static final String COLLECTION_LOG_TEXT = "New item added to your collection log: ";

    private static final Pattern Pet_LOG_ITEM_REGEX = Pattern.compile("You have a funny feeling like you.*");

    private static String itemImageUrl(int itemId)
    {
        return "https://static.runelite.net/cache/item/icon/" + itemId + ".png";
    }

    @Inject
    private Client client;

    private boolean delayScreenshot;

    @Override
    protected void startUp()
    {
    }

    @Override
    protected void shutDown()
    {
        delayScreenshot = false;
    }

    @Provides
    DiscordCollectionLoggerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DiscordCollectionLoggerConfig.class);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM) {
            return;
        }
        String inputMessage = chatMessage.getMessage();
        String outputMessage = Text.removeTags(inputMessage);
        String item;
        if (config.includeCollectionLog()
                && COLLECTION_LOG_ITEM_REGEX.matcher(outputMessage).matches()
                && client.getVarbitValue(Varbits.COLLECTION_LOG_NOTIFICATION) == 1) {
            item = outputMessage.substring(COLLECTION_LOG_TEXT.length());
            outputMessage = COLLECTION_LOG_TEXT + "**" + item + "**";
            processCollection(item, outputMessage);

        }
        if (config.includePets() && Pet_LOG_ITEM_REGEX.matcher(outputMessage).matches()) {
            processCollection(outputMessage);
        }
    }

    private String getPlayerName()
    {
        return client.getLocalPlayer().getName();
    }
    private void processCollection(String itemName, String outputText){
        WebhookBody webhookBody = new WebhookBody();
        StringBuilder stringBuilder = new StringBuilder();
        if (config.includeUsername())
        {
            stringBuilder.append("\n**").append(getPlayerName()).append("**").append("\n");
        }
        stringBuilder.append(outputText).append("\n");

        if(config.includeCollectionImage()) {
            int itemId = getItemID(itemName);
            if(itemId < 0) {
                List<ItemPrice> items = itemManager.search(itemName);
                if (items.size() == 1) {
                    itemId = items.get(0).getId();
                    webhookBody.getEmbeds().add(new WebhookBody.Embed(new WebhookBody.UrlEmbed(itemImageUrl(itemId))));
                }
            }
            else {
                webhookBody.getEmbeds().add(new WebhookBody.Embed(new WebhookBody.UrlEmbed(itemImageUrl(itemId))));
            }
        }
        webhookBody.setContent(stringBuilder.toString());
        sendWebhook(webhookBody);
    }
    private void processCollection(String outputText){
        WebhookBody webhookBody = new WebhookBody();
        StringBuilder stringBuilder = new StringBuilder();
        if (config.includeUsername())
        {
            stringBuilder.append("\n**").append(getPlayerName()).append("**").append("\n");
        }
        stringBuilder.append(outputText).append("\n");
        webhookBody.setContent(stringBuilder.toString());
        sendWebhook(webhookBody);
    }

    private int getItemID(String itemName)
    {
        String workingName = itemName.replace(" ","_");
        workingName = workingName.replace("'","");
        workingName = workingName.replace("(","");
        workingName = workingName.replace(")","");
        workingName = workingName.toUpperCase();
        if(Character.isDigit(workingName.charAt(0)))
        {
            workingName = "_" + workingName;
        }
        Class<?> c = net.runelite.api.ItemID.class;
        try {
            Field F = c.getDeclaredField(workingName);
            int itemID = (int) F.get(null);
            return itemID;
        }
        catch(Exception e){
            System.out.println("DCL Error: " + e.getMessage());
            return -1;
        }
    }
    private void sendWebhook(WebhookBody webhookBody)
    {
        String configUrl = config.webhook();
        if (Strings.isNullOrEmpty(configUrl))
        {
            return;
        }

        HttpUrl url = HttpUrl.parse(configUrl);
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", GSON.toJson(webhookBody));

        if (config.sendScreenshot())
        {
            sendWebhookWithScreenshot(url, requestBodyBuilder);
        }
        else
        {
            buildRequestAndSend(url, requestBodyBuilder);
        }
    }

    private void sendWebhookWithScreenshot(HttpUrl url, MultipartBody.Builder requestBodyBuilder)
    {
        drawManager.requestNextFrameListener(image ->
        {
            BufferedImage bufferedImage = (BufferedImage) image;
            byte[] imageBytes;
            try
            {
                imageBytes = convertImageToByteArray(bufferedImage);
            }
            catch (IOException e)
            {
                log.warn("Error converting image to byte array", e);
                return;
            }

            requestBodyBuilder.addFormDataPart("file", "image.png",
                    RequestBody.create(MediaType.parse("image/png"), imageBytes));
            buildRequestAndSend(url, requestBodyBuilder);
        });
    }

    private void buildRequestAndSend(HttpUrl url, MultipartBody.Builder requestBodyBuilder)
    {
        RequestBody requestBody = requestBodyBuilder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        sendRequest(request);
    }

    private void sendRequest(Request request)
    {
        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Error submitting webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                response.close();
            }
        });
    }

    private static byte[] convertImageToByteArray(BufferedImage bufferedImage) throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        switch (scriptPreFired.getScriptId())
        {
            case ScriptID.NOTIFICATION_START:
                delayScreenshot = true;
                break;
            case ScriptID.NOTIFICATION_DELAY:
                if (!delayScreenshot)
                {
                    return;
                }
                String notificationTopText = client.getVarcStrValue(VarClientStr.NOTIFICATION_TOP_TEXT);
                String notificationBottomText = client.getVarcStrValue(VarClientStr.NOTIFICATION_BOTTOM_TEXT);
                if (notificationTopText.equalsIgnoreCase("Collection log") && config.includeCollectionLog())
                {
                    String item = Text.removeTags(notificationBottomText).substring("New item:".length());
                    String outputText = COLLECTION_LOG_TEXT + "**" + item + "**";
                    processCollection(item,outputText);
                }
                delayScreenshot = false;
                break;
        }
    }
}
