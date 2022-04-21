package io.github.shaksternano.mediamanipulator.util;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import io.github.shaksternano.mediamanipulator.Main;
import io.github.shaksternano.mediamanipulator.util.tenor.TenorMediaType;
import io.github.shaksternano.mediamanipulator.util.tenor.TenorUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains static methods for dealing with {@link Message}s.
 */
public class MessageUtil {

    /**
     * The maximum number of messages to retrieve from the channel history.
     */
    private static final int MAX_PAST_MESSAGES_TO_CHECK = 50;

    /**
     * A pattern to extract web URLs from a string.
     */
    private static final Pattern WEB_URL_PATTERN = Pattern.compile("\\b((?:https?|ftp|file)://[-a-zA-Z\\d+&@#/%?=~_|!:, .;]*[-a-zA-Z\\d+&@#/%=~_|])", Pattern.CASE_INSENSITIVE);

    /**
     * Downloads an image.
     *
     * @param message   The message to download the image from.
     * @param directory The directory to download the image to.
     * @return An {@link Optional} describing the image file.
     */
    public static Optional<File> downloadImage(Message message, File directory) {
        return downloadImage(message, directory, true);
    }

    /**
     * Downloads an image.
     *
     * @param message      The message to download the image from.
     * @param directory    The directory to download the image to.
     * @param checkReplies Whether to check the message the given message is responding to.
     * @return An {@link Optional} describing the image file.
     */
    private static Optional<File> downloadImage(Message message, File directory, boolean checkReplies) {
        Optional<File> imageFileOptional = downloadAttachmentImage(message, directory);
        if (imageFileOptional.isPresent()) {
            return imageFileOptional;
        }

        imageFileOptional = downloadUrlImage(message.getContentRaw(), directory, false);
        if (imageFileOptional.isPresent()) {
            return imageFileOptional;
        }

        imageFileOptional = downloadEmbedImage(message, directory);
        if (imageFileOptional.isPresent()) {
            return imageFileOptional;
        }

        if (checkReplies) {
            Message referencedMessage = message.getReferencedMessage();

            if (referencedMessage != null) {
                imageFileOptional = downloadImage(referencedMessage, directory, false);

                if (imageFileOptional.isPresent()) {
                    return imageFileOptional;
                }
            }

            MessageHistory history = message.getChannel().getHistory();
            try {
                List<Message> previousMessages = history.retrievePast(MAX_PAST_MESSAGES_TO_CHECK).submit().get(10, TimeUnit.SECONDS);
                for (Message previousMessage : previousMessages) {
                    Optional<File> previousImageFileOptional = downloadImage(previousMessage, directory, false);

                    if (previousImageFileOptional.isPresent()) {
                        return previousImageFileOptional;
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                Main.LOGGER.error("Error while retrieving previous messages", e);
            } catch (TimeoutException e) {
                Main.LOGGER.error("Timeout while retrieving previous messages!", e);
            }
        }

        return Optional.empty();
    }

    /**
     * Downloads an image from an attachment.
     *
     * @param message   The message to download the image from.
     * @param directory The directory to download the image to.
     * @return An {@link Optional} describing the image file.
     */
    private static Optional<File> downloadAttachmentImage(Message message, File directory) {
        List<Message.Attachment> attachments = message.getAttachments();

        for (Message.Attachment attachment : attachments) {
            if (attachment.isImage()) {
                File imageFile = FileUtil.getUniqueFile(directory, attachment.getFileName());

                try {
                    return Optional.of(attachment.downloadToFile(imageFile).get(10, TimeUnit.SECONDS));
                } catch (ExecutionException | InterruptedException e) {
                    Main.LOGGER.error("Error downloading image!", e);
                } catch (TimeoutException e) {
                    Main.LOGGER.error("Image took too long to download!", e);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Downloads an image from a URL.
     *
     * @param text         The text to download the image from.
     * @param directory    The directory to download the image to.
     * @param isMessageUrl Whether the text is a URL.
     * @return An {@link Optional} describing the image file.
     */
    private static Optional<File> downloadUrlImage(String text, File directory, boolean isMessageUrl) {
        List<String> urls;

        if (isMessageUrl) {
            urls = ImmutableList.of(text);
        } else {
            urls = extractUrls(text);
        }

        for (String url : urls) {
            try {
                Optional<String> tenorMediaUrlOptional = TenorUtil.getTenorMediaUrl(url, TenorMediaType.GIF_NORMAL, Main.getTenorApiKey());
                if (tenorMediaUrlOptional.isPresent()) {
                    url = tenorMediaUrlOptional.orElseThrow();
                }

                String fileNameWithoutExtension = Files.getNameWithoutExtension(url);
                String extension = Files.getFileExtension(url);

                if (extension.isEmpty()) {
                    extension = "png";
                } else {
                    int index = extension.indexOf("?");
                    if (index != -1) {
                        extension = extension.substring(0, index);
                    }
                }

                BufferedImage image;

                String fileName = fileNameWithoutExtension + "." + extension;
                File imageFile = FileUtil.getUniqueFile(directory, fileName);

                if (extension.equals("gif")) {
                    FileUtil.downloadFile(url, imageFile);
                    return Optional.of(imageFile);
                } else {
                    image = ImageIO.read(new URL(url));

                    if (image != null) {
                        ImageIO.write(image, extension, imageFile);
                        return Optional.of(imageFile);
                    }
                }
            } catch (IOException ignored) {
            }
        }

        return Optional.empty();
    }

    /**
     * Downloads an image file from an embed.
     *
     * @param message   The message containing the embed to download the image from.
     * @param directory The directory to download the image to.
     * @return An {@link Optional} describing the image file.
     */
    private static Optional<File> downloadEmbedImage(Message message, File directory) {
        List<MessageEmbed> embeds = message.getEmbeds();

        for (MessageEmbed embed : embeds) {
            MessageEmbed.ImageInfo imageInfo = embed.getImage();

            if (imageInfo != null) {
                Optional<File> imageFileOptional = downloadUrlImage(imageInfo.getUrl(), directory, true);
                if (imageFileOptional.isPresent()) {
                    return imageFileOptional;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Extracts all web URLs from a string.
     *
     * @param text The text to extract the URLs from.
     * @return A list of all URLs in the text.
     */
    private static List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();

        Matcher matcher = WEB_URL_PATTERN.matcher(text);

        while (matcher.find()) {
            urls.add(text.substring(matcher.start(0), matcher.end(0)));
        }

        return urls;
    }
}
