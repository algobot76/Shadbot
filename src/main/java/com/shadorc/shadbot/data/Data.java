package com.shadorc.shadbot.data;

import com.shadorc.shadbot.Shadbot;
import com.shadorc.shadbot.utils.ExceptionHandler;
import com.shadorc.shadbot.utils.LogUtils;
import com.shadorc.shadbot.utils.Utils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

public abstract class Data {

    private static final File SAVE_DIR = new File("./saves");

    private final File file;

    protected Data(String fileName, Duration initialDelay, Duration period) {
        this.file = new File(SAVE_DIR, fileName);

        if (!SAVE_DIR.exists() && !SAVE_DIR.mkdir()) {
            throw new RuntimeException(String.format("%s could not be created.", SAVE_DIR.getName()));
        }

        Flux.interval(initialDelay, period, Schedulers.elastic())
                .doOnNext(ignored -> Mono.fromRunnable(this::save))
                .subscribe(null, err -> ExceptionHandler.handleUnknownError(Shadbot.getClient(), err));
    }

    public abstract Object getData();

    public void write() throws IOException {
        try (final BufferedWriter writer = Files.newBufferedWriter(this.file.toPath())) {
            writer.write(Utils.MAPPER.writeValueAsString(this.getData()));
        }
    }

    public void save() {
        try {
            this.write();
            LogUtils.info("%s saved.", this.file.getName());
        } catch (final IOException err) {
            LogUtils.error(err, String.format("An error occurred while saving %s.", this.file.getName()));
        }
    }

    public File getFile() {
        return this.file;
    }

}