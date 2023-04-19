package io.github.shaksternano.mediamanipulator.media.io.reader;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class FFmpegMediaReader<E> extends BaseMediaReader<E> {

    protected final FFmpegFrameGrabber grabber;
    protected boolean closed = false;
    private InputStream inputStream;

    public FFmpegMediaReader(File input, String format) throws IOException {
        this(new FFmpegFrameGrabber(input), format);
    }

    public FFmpegMediaReader(InputStream input, String format) throws IOException {
        this(new FFmpegFrameGrabber(input), format);
        inputStream = input;
    }

    private FFmpegMediaReader(FFmpegFrameGrabber grabber, String format) throws IOException {
        super(format);
        this.grabber = grabber;
        grabber.start();
        int frameCount = 0;
        Frame frame;
        while ((frame = grabFrame()) != null) {
            frameCount++;
            frame.close();
        }
        frameRate = grabber.getFrameRate();
        this.frameCount = frameCount;
        duration = grabber.getTimestamp();
        frameDuration = 1_000_000 / frameRate;
        audioChannels = grabber.getAudioChannels();
        width = grabber.getImageWidth();
        height = grabber.getImageHeight();
        grabber.setTimestamp(0);
    }

    @Nullable
    protected abstract Frame grabFrame() throws IOException;

    @Nullable
    protected abstract E getNextFrame() throws IOException;

    @Override
    public E frame(long timestamp) throws IOException {
        long circularTimestamp = timestamp % Math.max(duration(), 1);
        return frameNonCircular(circularTimestamp);
    }

    @Override
    public E first() throws IOException {
        return frameNonCircular(0);
    }

    private E frameNonCircular(long timestamp) throws IOException {
        grabber.setTimestamp(timestamp);
        E frame = getNextFrame();
        if (frame == null) {
            throw new NoSuchElementException("No frame at timestamp " + timestamp);
        } else {
            return frame;
        }
    }

    @Override
    public Iterator<E> iterator() {
        try {
            return new FFmpegMediaIterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        grabber.close();
        if (inputStream != null) {
            inputStream.close();
        }
    }

    private class FFmpegMediaIterator implements Iterator<E> {

        @Nullable
        E nextFrame;

        public FFmpegMediaIterator() throws IOException {
            grabber.setTimestamp(0);
            nextFrame = getNextFrame();
        }

        @Override
        public boolean hasNext() {
            return nextFrame != null;
        }

        @Override
        public E next() {
            if (nextFrame == null) {
                throw new NoSuchElementException();
            }
            E frame = nextFrame;
            try {
                nextFrame = getNextFrame();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return frame;
        }
    }
}
