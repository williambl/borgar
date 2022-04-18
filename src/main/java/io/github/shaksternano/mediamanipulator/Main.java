package io.github.shaksternano.mediamanipulator;

import io.github.shaksternano.mediamanipulator.command.Commands;
import io.github.shaksternano.mediamanipulator.listener.CommandListener;
import io.github.shaksternano.mediamanipulator.mediamanipulator.MediaManipulators;
import io.github.shaksternano.mediamanipulator.util.FileUtil;
import io.github.shaksternano.mediamanipulator.util.Fonts;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger("Media Manipulator");
    private static long ownerId = 0;

    public static void main(String[] args) {
        FileUtil.cleanTempDirectory();

        String token = null;

        try {
            token = getToken(args);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Please provide a token as the first argument!");
            System.exit(1);
        }

        JDA jda = null;

        try {
            jda = JDABuilder.createDefault(token).build();
        } catch (LoginException e) {
            LOGGER.error("Invalid token!");
            System.exit(1);
        }

        jda.getPresence().setActivity(Activity.playing("gaming"));
        jda.addEventListener(CommandListener.INSTANCE);
        jda.updateCommands()
                .addCommands(net.dv8tion.jda.api.interactions.commands.build.Commands.slash(Commands.HELP.getName(), Commands.HELP.getDescription()))
                .queue();

        jda.retrieveApplicationInfo().queue(
                applicationInfo -> ownerId = applicationInfo.getOwner().getIdLong(),
                throwable -> LOGGER.error("Failed to get the owner ID of this bot, owner exclusive functionality won't available!", throwable)
        );

        Commands.registerCommands();
        Fonts.registerFonts();
        MediaManipulators.registerMediaManipulators();
    }

    public static long getOwnerId() {
        return ownerId;
    }

    private static String getToken(String[] args) {
        if (args.length > 0) {
            return args[0];
        } else {
            throw new IllegalArgumentException();
        }
    }
}