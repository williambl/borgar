package io.github.shaksternano.borgar.command.util;

import io.github.shaksternano.borgar.io.NamedFile;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record CommandResponse<T>(List<MessageCreateData> responses, @Nullable T responseData) {

    public CommandResponse(String message) {
        this(MessageCreateData.fromContent(message));
    }

    public CommandResponse(File file, String fileName) {
        this(MessageCreateData.fromFiles(FileUpload.fromData(file, fileName)));
    }

    public CommandResponse(NamedFile file) {
        this(file.file(), file.name());
    }

    public CommandResponse(MessageCreateData message) {
        this(List.of(message));
    }

    public CommandResponse(List<MessageCreateData> responses) {
        this(responses, null);
    }

    public CommandResponse<T> withResponseData(T responseData) {
        return new CommandResponse<>(responses, responseData);
    }

    public CompletableFuture<CommandResponse<T>> asFuture() {
        return CompletableFuture.completedFuture(this);
    }
}
