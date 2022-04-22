package io.github.shaksternano.mediamanipulator.mediamanipulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains registered {@link MediaManipulator}s.
 */
@SuppressWarnings("unused")
public class MediaManipulators {

    private static final List<MediaManipulator> manipulatorsToRegister = new ArrayList<>();

    /**
     * The {@link MediaManipulator} that deals with static image files.
     */
    public static final MediaManipulator IMAGE = addManipulatorToRegister(new StaticImageManipulator());

    /**
     * The {@link MediaManipulator} that deals with GIF files.
     */
    public static final MediaManipulator GIF = addManipulatorToRegister(new GifManipulator());

    private static <T extends MediaManipulator> T addManipulatorToRegister(T manipulator) {
        manipulatorsToRegister.add(manipulator);
        return manipulator;
    }

    /**
     * Registers all the {@link MediaManipulator}s.
     */
    public static void registerMediaManipulators() {
        MediaManipulatorRegistry.register(manipulatorsToRegister.toArray(new MediaManipulator[0]));
    }
}
