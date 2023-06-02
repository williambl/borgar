package io.github.shaksternano.mediamanipulator.media.io.writer;

import com.sksamuel.scrimage.ImmutableImage;
import io.github.shaksternano.mediamanipulator.media.ImageFrame;
import net.ifok.image.image4j.codec.ico.ICOEncoder;

import java.io.File;
import java.io.IOException;

public class Image4jIcoWriter extends NoAudioWriter {

    private final File output;
    private boolean written = false;

    public Image4jIcoWriter(File output) {
        this.output = output;
    }

    @Override
    public void writeImageFrame(ImageFrame frame) throws IOException {
        if (!written) {
            written = true;
            var image = ImmutableImage.wrapAwt(frame.content())
                .bound(256, 256)
                .awt();
            ICOEncoder.write(image, output);
        }
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public void close() {
    }
}
