package io.github.shaksternano.mediamanipulator.media.io.writer;

import io.github.shaksternano.mediamanipulator.media.AudioFrame;
import io.github.shaksternano.mediamanipulator.media.ImageFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class FFmpegVideoWriter implements MediaWriter {

    private static final int MAX_AUDIO_FRAME_RATE = 1000;

    @Nullable
    private FFmpegFrameRecorder recorder;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final File output;
    private final String outputFormat;
    private final int audioChannels;
    private final int audioSampleRate;
    private final int audioBitrate;
    private final long maxFileSize;
    private final long maxDuration;
    private boolean closed = false;

    public FFmpegVideoWriter(
        File output,
        String outputFormat,
        int audioChannels,
        int audioSampleRate,
        int audioBitrate,
        long maxFileSize,
        long maxDuration
    ) {
        this.output = output;
        this.outputFormat = outputFormat;
        this.audioChannels = audioChannels;
        this.audioSampleRate = audioSampleRate;
        this.audioBitrate = audioBitrate;
        this.maxFileSize = maxFileSize;
        this.maxDuration = maxDuration;
    }

    @Override
    public void writeImageFrame(ImageFrame frame) throws IOException {
        var image = frame.content();
        if (recorder == null) {
            double fps = 1_000_000.0 / frame.duration();
            recorder = createFFmpegRecorder(
                output,
                outputFormat,
                image.getWidth(),
                image.getHeight(),
                fps,
                audioChannels,
                audioSampleRate,
                audioBitrate,
                maxFileSize,
                maxDuration
            );
            recorder.start();
        }
        recorder.record(converter.convert(image));
    }

    @Override
    public void writeAudioFrame(AudioFrame frame) throws IOException {
        if (recorder == null) {
            throw new IllegalStateException("Cannot record an audio frame before an image frame");
        }
        // Prevent errors from occurring when the frame rate is too high.
        if (recorder.getFrameRate() <= MAX_AUDIO_FRAME_RATE) {
            recorder.record(frame.content());
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (recorder != null) {
            recorder.close();
        }
        converter.close();
    }

    private static FFmpegFrameRecorder createFFmpegRecorder(
        File file,
        String format,
        int imageWidth,
        int imageHeight,
        double fps,
        int audioChannels,
        int audioSampleRate,
        int audioBitrate,
        long maxFileSize,
        long maxDuration
    ) {
        var videoBitrate = calculateVideoBitrate(imageWidth, imageHeight, fps, 0.1);
        var totalBitrate = videoBitrate + audioBitrate;
        var estimatedFileSize = estimateFileSize(totalBitrate, maxDuration / 1_000_000);
        if (maxFileSize > 0 && estimatedFileSize > maxFileSize) {
            var reductionRatio = (double) maxFileSize / estimatedFileSize;
            videoBitrate *= reductionRatio;
            audioBitrate *= reductionRatio;
        }

        var recorder = new FFmpegFrameRecorder(
            file,
            imageWidth,
            imageHeight,
            audioChannels
        );
        recorder.setFormat(format);
        recorder.setInterleaved(true);

        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        /*
        Decrease "startup" latency in FFMPEG
        (see: https://trac.ffmpeg.org/wiki/StreamingGuide).
         */
        recorder.setVideoOption("tune", "zerolatency");
        /*
        Tradeoff between quality and encode speed.
        Possible values are: ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow.
        Ultrafast offers us the least amount of compression
        (lower encoder CPU) at the cost of a larger stream size.
        At the other end, veryslow provides the best compression
        (high encoder CPU) while lowering the stream size
        (see: https://trac.ffmpeg.org/wiki/Encode/H.264).
         */
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setFrameRate(fps);
        recorder.setVideoBitrate(videoBitrate);
        /*
        Key frame interval, in our case every 2 seconds -> fps * 2
        (GOP length)
         */
        recorder.setGopSize((int) (fps * 2));

        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        // Highest quality
        recorder.setAudioQuality(0);
        recorder.setSampleRate(audioSampleRate);
        recorder.setAudioBitrate(audioBitrate);
        return recorder;
    }

    @SuppressWarnings("SameParameterValue")
    private static int calculateVideoBitrate(int width, int height, double fps, double bitsPerPixel) {
        return (int) (width * height * fps * bitsPerPixel);
    }

    private static long estimateFileSize(int bitrate, long duration) {
        return bitrate * duration;
    }
}
