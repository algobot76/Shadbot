package me.shadorc.shadbot.listener;

import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.VoiceChannel;
import discord4j.core.object.util.Snowflake;
import me.shadorc.shadbot.music.GuildMusic;
import me.shadorc.shadbot.music.GuildMusicManager;
import me.shadorc.shadbot.utils.BotUtils;
import me.shadorc.shadbot.utils.embed.log.LogUtils;
import me.shadorc.shadbot.utils.object.Emoji;
import reactor.core.publisher.Mono;

public class VoiceStateUpdateListener {

	public static Mono<Void> onVoiceStateUpdateEvent(VoiceStateUpdateEvent event) {
		return Mono.justOrEmpty(event.getClient().getSelfId())
				.flatMap(selfId -> {
					if(event.getCurrent().getUserId().equals(selfId)) {
						return VoiceStateUpdateListener.onBotEvent(event);
					} else {
						return VoiceStateUpdateListener.onUserEvent(event);
					}
				});
	}

	private static Mono<Void> onBotEvent(VoiceStateUpdateEvent event) {
		// If the bot is no more in a voice channel and the guild music still exists, destroy it
		return Mono.fromRunnable(() -> {
			if(!event.getCurrent().getChannelId().isPresent()) {
				final Snowflake guildId = event.getCurrent().getGuildId();
				final GuildMusic guildMusic = GuildMusicManager.GUILD_MUSIC_MAP.get(guildId);
				if(guildMusic != null) {
					guildMusic.destroy();
					LogUtils.info("{Guild ID: %d} Voice channel left.", guildId.asLong());
				}
			}
		});
	}

	private static Mono<Void> onUserEvent(VoiceStateUpdateEvent event) {
		final Snowflake guildId = event.getCurrent().getGuildId();

		final GuildMusic guildMusic = GuildMusicManager.GUILD_MUSIC_MAP.get(guildId);
		// The bot is not playing music, ignore the event
		if(guildMusic == null) {
			return Mono.empty();
		}

		return event.getClient()
				.getSelf()
				.flatMap(self -> self.asMember(guildId))
				.flatMap(Member::getVoiceState)
				.flatMap(VoiceState::getChannel)
				.flatMapMany(VoiceChannel::getVoiceStates)
				.count()
				.flatMap(memberCount -> {
					// The bot is now alone: pause, schedule leave and warn users
					if(memberCount == 1 && !guildMusic.isLeavingScheduled()) {
						guildMusic.getTrackScheduler().getAudioPlayer().setPaused(true);
						guildMusic.scheduleLeave();
						return Mono.just(Emoji.INFO + " Nobody is listening anymore, music paused. I will leave the voice channel in 1 minute.");
					}
					// The bot is no more alone: unpause, cancel leave and warn users
					else if(memberCount != 1 && guildMusic.isLeavingScheduled()) {
						guildMusic.getTrackScheduler().getAudioPlayer().setPaused(false);
						guildMusic.cancelLeave();
						return Mono.just(Emoji.INFO + " Somebody joined me, music resumed.");
					}
					// Ignore the event
					return Mono.empty();
				})
				.flatMap(content -> guildMusic.getMessageChannel()
						.flatMap(channel -> BotUtils.sendMessage(content.toString(), channel)))
				.then();
	}

}
