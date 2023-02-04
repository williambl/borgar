package io.github.shaksternano.mediamanipulator.io;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

public interface ImageProcessor<T> extends Closeable {

    BufferedImage transformImage(BufferedImage image, FrameData frameData, T globalData) throws IOException;

    T globalData(BufferedImage image) throws IOException;

    @Override
    default void close() throws IOException {
    }
}
