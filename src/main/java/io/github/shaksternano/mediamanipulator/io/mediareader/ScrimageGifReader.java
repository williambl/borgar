package io.github.shaksternano.mediamanipulator.io.mediareader;

import com.sksamuel.scrimage.nio.AnimatedGifReader;
import com.sksamuel.scrimage.nio.ImageSource;
import io.github.shaksternano.mediamanipulator.image.ImageFrame;
import io.github.shaksternano.mediamanipulator.io.MediaReaderFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ScrimageGifReader extends BaseMediaReader<ImageFrame> {

    private final List<ImageFrame> frames;

    public ScrimageGifReader(File input, String format) throws IOException {
        this(ImageSource.of(input), format);
    }

    public ScrimageGifReader(InputStream input, String format) throws IOException {
        this(ImageSource.of(input), format);
        input.close();
    }

    private ScrimageGifReader(ImageSource imageSource, String format) throws IOException {
        super(format);
        var gif = AnimatedGifReader.read(imageSource);
        frameCount = gif.getFrameCount();
        if (frameCount <= 0) {
            throw new IOException("Could not read any frames!");
        }
        List<ImageFrame> framesBuilder = new ArrayList<>();
        var totalDuration = 0;
        for (var i = 0; i < frameCount; i++) {
            var image = gif.getFrame(i).awt();
            var frameDuration = gif.getDelay(i).toMillis() * 1000;
            framesBuilder.add(new ImageFrame(image, frameDuration, totalDuration));
            totalDuration += frameDuration;
        }
        frames = Collections.unmodifiableList(framesBuilder);
        duration = totalDuration;
        frameRate = (1_000_000.0 * frameCount) / duration;
        frameDuration = 1_000_000 / frameRate;
        var dimension = gif.getDimensions();
        width = dimension.width;
        height = dimension.height;
    }

    @Override
    public ImageFrame frame(long timestamp) {
        var circularTimestamp = timestamp % Math.max(duration(), 1);
        var index = findIndex(circularTimestamp, frames);
        return frames.get(index);
    }

    /**
     * Finds the index of the frame with the given timestamp.
     * If there is no frame with the given timestamp, the index of the frame
     * with the highest timestamp smaller than the given timestamp is returned.
     *
     * @param timeStamp The timestamp in microseconds.
     * @param frames    The frames.
     * @return The index of the frame with the given timestamp.
     */
    private static int findIndex(long timeStamp, List<ImageFrame> frames) {
        if (frames.size() == 0) {
            throw new IllegalArgumentException("Frames list is empty");
        } else if (timeStamp < 0) {
            throw new IllegalArgumentException("Timestamp must not be negative");
        } else if (timeStamp < frames.get(0).timestamp()) {
            throw new IllegalArgumentException("Timestamp must not be smaller than the first timestamp");
        } else if (timeStamp == frames.get(0).timestamp()) {
            return 0;
        } else if (timeStamp < frames.get(frames.size() - 1).timestamp()) {
            return findIndexBinarySearch(timeStamp, frames);
        } else {
            // If the timestamp is equal to or greater than the last timestamp.
            return frames.size() - 1;
        }
    }

    private static int findIndexBinarySearch(long timeStamp, List<ImageFrame> frames) {
        var low = 0;
        var high = frames.size() - 1;
        while (low <= high) {
            var mid = low + ((high - low) / 2);
            if (frames.get(mid).timestamp() == timeStamp
                || (frames.get(mid).timestamp() < timeStamp
                && frames.get(mid + 1).timestamp() > timeStamp)) {
                return mid;
            } else if (frames.get(mid).timestamp() < timeStamp) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        throw new IllegalStateException("This should never be reached. Timestamp: " + timeStamp + ", Frames: " + frames);
    }

    @Override
    public void close() {
    }

    @NotNull
    @Override
    public Iterator<ImageFrame> iterator() {
        return frames.iterator();
    }

    public enum Factory implements MediaReaderFactory<ImageFrame> {

        INSTANCE;

        @Override
        public MediaReader<ImageFrame> createReader(File media, String format) throws IOException {
            return new ScrimageGifReader(media, format);
        }

        @Override
        public MediaReader<ImageFrame> createReader(InputStream media, String format) throws IOException {
            return new ScrimageGifReader(media, format);
        }
    }
}
