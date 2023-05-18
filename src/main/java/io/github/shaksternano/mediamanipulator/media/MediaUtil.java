package io.github.shaksternano.mediamanipulator.media;

import io.github.shaksternano.mediamanipulator.io.FileUtil;
import io.github.shaksternano.mediamanipulator.media.io.Imageprocessor.BasicImageProcessor;
import io.github.shaksternano.mediamanipulator.media.io.Imageprocessor.DualImageProcessor;
import io.github.shaksternano.mediamanipulator.media.io.Imageprocessor.SingleImageProcessor;
import io.github.shaksternano.mediamanipulator.media.io.MediaReaders;
import io.github.shaksternano.mediamanipulator.media.io.MediaWriters;
import io.github.shaksternano.mediamanipulator.media.io.reader.MediaReader;
import io.github.shaksternano.mediamanipulator.media.io.reader.ZippedMediaReader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class MediaUtil {

    public static File processMedia(
        File media,
        String outputFormat,
        String resultName,
        UnaryOperator<BufferedImage> imageMapper
    ) throws IOException {
        return processMedia(
            media,
            outputFormat,
            resultName,
            new BasicImageProcessor(imageMapper)
        );
    }

    public static <T> File processMedia(
        File media,
        String outputFormat,
        String resultName,
        SingleImageProcessor<T> processor
    ) throws IOException {
        var output = FileUtil.createTempFile(resultName, outputFormat);
        return processMedia(media, outputFormat, output, processor);
    }

    public static <T> File processMedia(
        File media,
        String outputFormat,
        File output,
        SingleImageProcessor<T> processor
    ) throws IOException {
        var imageReader = MediaReaders.createImageReader(media, outputFormat);
        var audioReader = MediaReaders.createAudioReader(media, outputFormat);
        return processMedia(imageReader, audioReader, output, outputFormat, processor);
    }

    public static <T> File processMedia(
        MediaReader<ImageFrame> imageReader,
        MediaReader<AudioFrame> audioReader,
        String outputFormat,
        String resultName,
        SingleImageProcessor<T> processor
    ) throws IOException {
        var output = FileUtil.createTempFile(resultName, outputFormat);
        return processMedia(imageReader, audioReader, output, outputFormat, processor);
    }

    public static <T> File processMedia(
        MediaReader<ImageFrame> imageReader,
        MediaReader<AudioFrame> audioReader,
        File output,
        String outputFormat,
        SingleImageProcessor<T> processor
    ) throws IOException {
        try (
            imageReader;
            audioReader;
            processor;
            var writer = MediaWriters.createWriter(
                output,
                outputFormat,
                audioReader.audioChannels()
            )
        ) {
            T constantFrameDataValue = null;
            for (var imageFrame : imageReader) {
                if (constantFrameDataValue == null) {
                    constantFrameDataValue = processor.constantData(imageFrame.content());
                }
                writer.recordImageFrame(imageFrame.transform(
                    processor.transformImage(imageFrame, constantFrameDataValue),
                    processor.speed()
                ));
            }
            for (var audioFrame : audioReader) {
                writer.recordAudioFrame(audioFrame.transform(processor.speed()));
            }
            return output;
        }
    }

    public static <T> File processMedia(
        MediaReader<ImageFrame> imageReader1,
        MediaReader<AudioFrame> audioReader1,
        MediaReader<ImageFrame> imageReader2,
        String outputFormat,
        String resultName,
        DualImageProcessor<T> processor
    ) throws IOException {
        var output = FileUtil.createTempFile(resultName, outputFormat);
        try (
            processor;
            var zippedImageReader = new ZippedMediaReader<>(imageReader1, imageReader2);
            var zippedAudioReader = new ZippedMediaReader<>(audioReader1, imageReader2, true);
            var writer = MediaWriters.createWriter(
                output,
                outputFormat,
                audioReader1.audioChannels()
            )
        ) {
            T constantFrameDataValue = null;
            for (var framePair : zippedImageReader) {
                var firstFrame = framePair.first();
                var secondFrame = framePair.second();
                if (constantFrameDataValue == null) {
                    constantFrameDataValue = processor.constantData(firstFrame.content(), secondFrame.content());
                }
                var toTransform = zippedImageReader.isFirstControlling()
                    ? firstFrame
                    : secondFrame;
                writer.recordImageFrame(toTransform.transform(
                    processor.transformImage(firstFrame, secondFrame, constantFrameDataValue),
                    processor.speed()
                ));
            }
            for (var framePair : zippedAudioReader) {
                var audioFrame = framePair.first();
                writer.recordAudioFrame(audioFrame.transform(processor.speed()));
            }
            return output;
        }
    }

    public static File cropMedia(
        File media,
        String outputFormat,
        String resultName,
        Function<BufferedImage, Rectangle> cropKeepAreaFinder
    ) throws IOException {
        try (var reader = MediaReaders.createImageReader(media, outputFormat)) {
            Rectangle toKeep = null;
            var width = -1;
            var height = -1;

            for (var frame : reader) {
                var image = frame.content();
                if (width < 0) {
                    width = image.getWidth();
                    height = image.getHeight();
                }

                var mayKeepArea = cropKeepAreaFinder.apply(image);
                if ((mayKeepArea.getX() != 0
                    || mayKeepArea.getY() != 0
                    || mayKeepArea.getWidth() != width
                    || mayKeepArea.getHeight() != height)
                    && mayKeepArea.getWidth() > 0
                    && mayKeepArea.getHeight() > 0
                ) {
                    if (toKeep == null) {
                        toKeep = mayKeepArea;
                    } else {
                        toKeep = toKeep.union(mayKeepArea);
                    }
                }
            }

            if (toKeep == null
                || (toKeep.getX() == 0
                && toKeep.getY() == 0
                && toKeep.getWidth() == width
                && toKeep.getHeight() == height
            )) {
                return media;
            } else {
                final Rectangle finalToKeep = toKeep;
                return processMedia(
                    media,
                    outputFormat,
                    resultName,
                    image -> image.getSubimage(
                        finalToKeep.x,
                        finalToKeep.y,
                        finalToKeep.width,
                        finalToKeep.height
                    )
                );
            }
        }
    }

    public static String equivalentTransparentFormat(String format) {
        if (isJpg(format)) {
            return "png";
        } else {
            return format;
        }
    }

    private static boolean isJpg(String format) {
        return format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg");
    }

    public static boolean supportsTransparency(String format) {
        return format.equalsIgnoreCase("png") || format.equalsIgnoreCase("gif");
    }
}
