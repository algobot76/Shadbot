package me.shadorc.discordbot.utils;

import me.shadorc.discordbot.command.Context;
import me.shadorc.discordbot.data.Storage;
import me.shadorc.discordbot.utils.command.Emoji;

public class GameUtils {

	/**
	 * @param betStr - The bet to check
	 * @param context - The context
	 * @return betStr has an Integer if it's a valid bet, null otherwise
	 */
	public static Integer parseBetOrWarn(String betStr, Context context) {
		if(!StringUtils.isPositiveInt(betStr)) {
			BotUtils.sendMessage(Emoji.GREY_EXCLAMATION + " Invalid bet.", context.getChannel());
			return null;
		}

		int bet = Integer.parseInt(betStr);
		if(Storage.getCoins(context.getGuild(), context.getAuthor()) < bet) {
			BotUtils.sendMessage(TextUtils.NOT_ENOUGH_COINS, context.getChannel());
			return null;
		}

		return bet;
	}
}
