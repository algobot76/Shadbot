package com.shadorc.shadbot;

import com.shadorc.shadbot.api.BotListStats;
import com.shadorc.shadbot.command.game.lottery.LotteryCmd;
import com.shadorc.shadbot.data.Config;
import com.shadorc.shadbot.utils.ExceptionHandler;
import com.shadorc.shadbot.utils.TextUtils;
import com.shadorc.shadbot.utils.Utils;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.gateway.GatewayClientGroup;
import io.prometheus.client.Gauge;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskManager {

    private final GatewayDiscordClient gateway;
    private final Scheduler defaultScheduler;
    private final Logger logger;
    private final BotListStats botListStats;
    private final List<Disposable> tasks;

    public TaskManager(GatewayDiscordClient gateway) {
        this.gateway = gateway;
        this.defaultScheduler = Schedulers.boundedElastic();
        this.logger = Loggers.getLogger("shadbot.taskmanager");
        this.botListStats = new BotListStats(gateway);
        this.tasks = new ArrayList<>(4);
    }

    public void schedulesPresenceUpdates() {
        this.logger.info("Scheduling presence updates...");
        final Disposable task = Flux.interval(Duration.ZERO, Duration.ofMinutes(30), this.defaultScheduler)
                .flatMap(ignored -> {
                    final String presence = String.format("%shelp | %s", Config.DEFAULT_PREFIX,
                            TextUtils.TIPS.getRandomTextFormatted());
                    return this.gateway.updatePresence(Presence.online(Activity.playing(presence)));
                })
                .onErrorContinue((err, obj) -> ExceptionHandler.handleUnknownError(err))
                .subscribe(null, ExceptionHandler::handleUnknownError);
        this.tasks.add(task);
    }

    public void schedulesLottery() {
        this.logger.info("Starting lottery... Next lottery draw in {}", LotteryCmd.getDelay());
        final Disposable task = Flux.interval(LotteryCmd.getDelay(), Duration.ofDays(7), this.defaultScheduler)
                .flatMap(ignored -> LotteryCmd.draw(this.gateway))
                .onErrorContinue((err, obj) -> ExceptionHandler.handleUnknownError(err))
                .subscribe(null, ExceptionHandler::handleUnknownError);
        this.tasks.add(task);
    }

    public void schedulesPeriodicStats() {
        this.logger.info("Scheduling system resources log...");

        final Gauge ramUsageGauge = Gauge.build()
                .namespace("process")
                .name("ram_usage_mb")
                .help("Ram usage in MB")
                .register();

        final Gauge cpuUsageGauge = Gauge.build()
                .namespace("process")
                .name("cpu_usage_percent")
                .help("CPU usage in percent")
                .register();

        final Gauge threadCountGauge = Gauge.build()
                .namespace("process")
                .name("thread_count")
                .help("Thread count")
                .register();

        final Gauge responseTimeGauge = Gauge.build()
                .namespace("shard")
                .name("response_time")
                .help("Shard response time")
                .labelNames("shard_id")
                .register();

        final GatewayClientGroup group = this.gateway.getGatewayClientGroup();
        final Mono<Map<Integer, Long>> getResponseTimes = Flux.range(0, group.getShardCount())
                .map(i -> Tuples.of(i, group.find(i).orElseThrow().getResponseTime().toMillis()))
                .collectMap(Tuple2::getT1, Tuple2::getT2);

        final Disposable task = Flux.interval(Duration.ZERO, Duration.ofSeconds(10), this.defaultScheduler)
                .flatMap(ignored -> getResponseTimes)
                .doOnNext(responseTimeMap -> {
                    responseTimeMap.forEach((key, value) -> responseTimeGauge.labels(key.toString()).set(value));

                    ramUsageGauge.set(Utils.getMemoryUsed());
                    cpuUsageGauge.set(Utils.getCpuUsage());
                    threadCountGauge.set(Thread.activeCount());
                })
                .subscribe(null, ExceptionHandler::handleUnknownError);

        this.tasks.add(task);
    }

    public void schedulesPostStats() {
        this.logger.info("Starting bot list stats scheduler...");
        final Disposable task = Flux.interval(Duration.ofHours(3), Duration.ofHours(3), this.defaultScheduler)
                .flatMap(ignored -> this.botListStats.postStats())
                .subscribe(null, ExceptionHandler::handleUnknownError);
        this.tasks.add(task);
    }

    public void stop() {
        this.tasks.forEach(Disposable::dispose);
    }

}
