package me.shadorc.shadbot.utils;

import org.jsoup.Jsoup;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import discord4j.core.object.entity.User;
import me.shadorc.shadbot.Config;
import me.shadorc.shadbot.core.setting.SettingEnum;
import me.shadorc.shadbot.utils.command.Emoji;

public class TextUtils {

	public static final String MISSING_ARG = Emoji.WHITE_FLAG + " Some arguments are missing, here is the help for this command.";

	public static final String PLAYLIST_LIMIT_REACHED =
			String.format(Emoji.WARNING + " You've reached the maximum number (%d) of tracks in a playlist. "
					+ "You can remove this limit by contributing to Shadbot. More info on **%s**", Config.DEFAULT_PLAYLIST_SIZE, Config.PATREON_URL);

	public static final String NO_PLAYING_MUSIC = Emoji.MUTE + " No currently playing music.";

	public static final String[] SPAM_MESSAGES = { "Take it easy, we are not in a hurry !",
			"Phew.. give me time to rest, you're too fast for me.",
			"I'm not going anywhere, no need to be this fast.",
			"I don't think everyone here want to be spammed by us, just wait a little bit." };

	public static final String[] TIPS_MESSAGES = { String.format("Check %slotto", Config.DEFAULT_PREFIX),
			String.format("Add a music first using %splayfirst", Config.DEFAULT_PREFIX),
			String.format("Help me keep Shadbot alive ! %s", Config.PATREON_URL),
			String.format("Support server: %s", Config.SUPPORT_SERVER_URL) };

	public static String notEnoughCoins(User user) {
		return String.format(Emoji.BANK + " (**%s**) You don't have enough coins. You can get some by playing **RPS**, **Hangman** "
				+ "or **Trivia**.", user.getUsername());
	}

	public static String mustBeNsfw(String prefix) {
		return String.format(Emoji.GREY_EXCLAMATION + " This must be a NSFW-channel. If you're an admin, you can use `%ssetting %s enable`",
				prefix, SettingEnum.NSFW);
	}

	/**
	 * @param err - the exception containing the error message to clean
	 * @return A cleaned version of the error message, without HTML tags and YouTube links
	 */
	public static String cleanLavaplayerErr(FriendlyException err) {
		return Jsoup.parse(StringUtils.remove(err.getMessage(), "Watch on YouTube")).text().trim();
	}
}
