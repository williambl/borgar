package io.github.shaksternano.mediamanipulator.command;

import com.google.common.collect.ListMultimap;
import io.github.shaksternano.mediamanipulator.graphics.GraphicsUtil;
import io.github.shaksternano.mediamanipulator.graphics.TextAlignment;
import io.github.shaksternano.mediamanipulator.graphics.drawable.Drawable;
import io.github.shaksternano.mediamanipulator.graphics.drawable.ParagraphCompositeDrawable;
import io.github.shaksternano.mediamanipulator.image.util.ImageUtil;
import io.github.shaksternano.mediamanipulator.image.FrameData;
import io.github.shaksternano.mediamanipulator.image.ImageProcessor;
import io.github.shaksternano.mediamanipulator.io.MediaUtil;
import io.github.shaksternano.mediamanipulator.util.MessageUtil;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A command that adds a captions media.
 */
public class CaptionCommand extends FileCommand {

    private final boolean CAPTION_2;

    /**
     * Creates a new command object.
     *
     * @param name        The name of the command. When a user sends a message starting with {@link Command#PREFIX}
     *                    followed by this name, the command will be executed.
     * @param description The description of the command. This is displayed in the help command.
     * @param caption2    Whether to put text on the bottom of the image instead of the top.
     */
    public CaptionCommand(String name, String description, boolean caption2) {
        super(name, description);
        CAPTION_2 = caption2;
    }

    @Override
    public File modifyFile(File file, String fileFormat, List<String> arguments, ListMultimap<String, String> extraArguments, MessageReceivedEvent event) throws IOException {
        Map<String, Drawable> nonTextParts = MessageUtil.getEmojiImages(event.getMessage());
        try (ImageProcessor<?> processor = new CaptionProcessor(CAPTION_2, arguments, nonTextParts)) {
            return MediaUtil.processMedia(file, fileFormat, "captioned", processor);
        }
    }

    private record CaptionProcessor(boolean caption2, List<String> words, Map<String, Drawable> nonTextParts) implements ImageProcessor<CaptionData> {

        @Override
        public BufferedImage transformImage(BufferedImage image, FrameData frameData, CaptionData globalData) throws IOException {
            return drawCaption(image, globalData, frameData.timestamp());
        }

        @Override
        public CaptionData globalData(BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();

            int averageDimension = (width + height) / 2;

            String fontName = caption2 ? "Helvetica Neue" : "Futura-CondensedExtraBold";
            float fontRatio = caption2 ? 9 : 7;
            Font font = new Font(fontName, Font.PLAIN, (int) (averageDimension / fontRatio));
            int padding = (int) (averageDimension * 0.04F);
            Graphics2D graphics = image.createGraphics();

            graphics.setFont(font);
            ImageUtil.configureTextDrawQuality(graphics);

            int maxWidth = width - (padding * 2);

            TextAlignment textAlignment;
            if (caption2) {
                textAlignment = TextAlignment.LEFT;
            } else {
                textAlignment = TextAlignment.CENTER;
            }

            Drawable paragraph = new ParagraphCompositeDrawable.Builder(nonTextParts)
                .addWords(null, words)
                .build(textAlignment, maxWidth);

            GraphicsUtil.fontFitWidth(maxWidth, paragraph, graphics);
            font = graphics.getFont();
            int fillHeight = paragraph.getHeight(graphics) + (padding * 2);
            graphics.dispose();
            return new CaptionData(font, fillHeight, padding, paragraph);
        }

        @Override
        public void close() throws IOException {
            IOException exception = null;
            for (Drawable drawable : nonTextParts.values()) {
                try {
                    drawable.close();
                } catch (IOException e) {
                    exception = e;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static BufferedImage drawCaption(BufferedImage image, CaptionData data, long timestamp) throws IOException {
        BufferedImage captionedImage = new BufferedImage(image.getWidth(), image.getHeight() + data.fillHeight(), ImageUtil.getType(image));
        Graphics2D graphics = captionedImage.createGraphics();
        ImageUtil.configureTextDrawQuality(graphics);

        graphics.drawImage(image, 0, data.fillHeight(), null);

        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, captionedImage.getWidth(), data.fillHeight());

        graphics.setFont(data.font());
        graphics.setColor(Color.BLACK);
        data.paragraph.draw(graphics, data.padding(), data.padding(), timestamp);

        graphics.dispose();

        return captionedImage;
    }


    private record CaptionData(Font font, int fillHeight, int padding, Drawable paragraph) {}
}
