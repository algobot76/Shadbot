package me.shadorc.shadbot.core.game.player;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import me.shadorc.shadbot.data.database.DBMember;
import me.shadorc.shadbot.data.database.DatabaseManager;
import me.shadorc.shadbot.data.lottery.LotteryManager;
import reactor.core.publisher.Mono;

public class Player {

    private final Snowflake guildId;
    private final Snowflake userId;

    public Player(Snowflake guildId, Snowflake userId) {
        this.guildId = guildId;
        this.userId = userId;
    }

    public Snowflake getUserId() {
        return this.userId;
    }

    public Mono<String> getUsername(DiscordClient client) {
        return client.getUserById(this.userId).map(User::getUsername);
    }

    public DBMember getDBMember() {
        return DatabaseManager.getInstance().getDBMember(this.guildId, this.userId);
    }

    public void win(long coins) {
        this.getDBMember().addCoins(coins);
    }

    public void lose(long coins) {
        this.getDBMember().addCoins(-coins);
        LotteryManager.getInstance().getLottery().addToJackpot(coins);
    }

}