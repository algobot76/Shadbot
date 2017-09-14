package me.shadorc.discordbot.command.game;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Timer;

import me.shadorc.discordbot.Emoji;
import me.shadorc.discordbot.MissingArgumentException;
import me.shadorc.discordbot.RateLimiter;
import me.shadorc.discordbot.command.AbstractCommand;
import me.shadorc.discordbot.command.Context;
import me.shadorc.discordbot.data.Storage;
import me.shadorc.discordbot.utils.BotUtils;
import me.shadorc.discordbot.utils.MathUtils;
import me.shadorc.discordbot.utils.StringUtils;
import me.shadorc.discordbot.utils.Utils;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

public class DiceCmd extends AbstractCommand {

	protected static final ConcurrentHashMap<IChannel, DiceManager> CHANNELS_DICE = new ConcurrentHashMap<>();
	protected static final int MULTIPLIER = 6;

	private final RateLimiter rateLimiter;

	public DiceCmd() {
		super(Role.USER, "dice");
		this.rateLimiter = new RateLimiter(RateLimiter.GAME_COOLDOWN, ChronoUnit.SECONDS);
	}

	@Override
	public void execute(Context context) throws MissingArgumentException {
		if(rateLimiter.isSpamming(context)) {
			return;
		}

		if(!context.hasArg()) {
			throw new MissingArgumentException();
		}

		DiceManager diceManager = CHANNELS_DICE.get(context.getChannel());

		if(diceManager == null) {
			String[] splitArgs = context.getArg().split(" ");
			if(splitArgs.length != 2) {
				throw new MissingArgumentException();
			}

			String betStr = splitArgs[0];
			if(!StringUtils.isPositiveInt(betStr)) {
				BotUtils.sendMessage(Emoji.EXCLAMATION + " Invalid bet.", context.getChannel());
				return;
			}

			int bet = Integer.parseInt(betStr);
			if(context.getPlayer().getCoins() < bet) {
				BotUtils.sendMessage(Emoji.BANK + " You don't have enough coins for this.", context.getChannel());
				return;
			}

			String numStr = splitArgs[1];
			if(!StringUtils.isValidDiceNum(numStr)) {
				BotUtils.sendMessage(Emoji.EXCLAMATION + " Invalid number, must be between 1 and 6.", context.getChannel());
				return;
			}

			int num = Integer.parseInt(numStr);

			diceManager = new DiceManager(context, bet);
			diceManager.addPlayer(context.getAuthor(), num);
			diceManager.start();
			CHANNELS_DICE.putIfAbsent(context.getChannel(), diceManager);

		} else {
			if(context.getPlayer().getCoins() < diceManager.getBet()) {
				BotUtils.sendMessage(Emoji.BANK + " You don't have enough coins to join this game.", context.getChannel());
				return;
			}

			if(diceManager.isPlaying(context.getAuthor())) {
				BotUtils.sendMessage(Emoji.INFO + " You're already participating.", context.getChannel());
				return;
			}

			String numStr = context.getArg();
			if(!StringUtils.isValidDiceNum(numStr)) {
				BotUtils.sendMessage(Emoji.EXCLAMATION + " Invalid number, must be between 1 and 6.", context.getChannel());
				return;
			}

			int num = Integer.parseInt(numStr);

			if(diceManager.isBet(num)) {
				BotUtils.sendMessage(Emoji.EXCLAMATION + " This number has already been bet, please try with another one.", context.getChannel());
				return;
			}

			diceManager.addPlayer(context.getAuthor(), num);
			BotUtils.sendMessage(Emoji.DICE + " " + context.getAuthor().mention() + " bet on " + num + ".", context.getChannel());
		}
	}

	@Override
	public void showHelp(Context context) {
		EmbedBuilder builder = Utils.getDefaultEmbed(this)
				.appendDescription("**Start a dice game with a common bet or join a game in progress.**")
				.appendField("Usage", "**Create a game:** " + context.getPrefix() + "dice <bet> <num>"
						+ "\n**Join a game:** " + context.getPrefix() + "dice <num>", false)
				.appendField("Restrictions", "**num** - must be between 1 and 6"
						+ "\nYou can't bet on a number that has already been chosen by another player.", false)
				.appendField("Gains", "The winner gets the common bet multiplied by " + MULTIPLIER + " plus the number of players."
						+ "\ngains = bet * (" + MULTIPLIER + " + players)", false);
		BotUtils.sendEmbed(builder.build(), context.getChannel());
	}

	protected class DiceManager {

		private static final int GAME_DURATION = 30;

		private final Map<Integer, IUser> numsPlayers;
		private final Context context;
		private final Timer timer;
		private final int bet;

		protected DiceManager(Context context, int bet) {
			this.context = context;
			this.bet = bet;
			this.numsPlayers = new HashMap<>();
			this.timer = new Timer(GAME_DURATION * 1000, event -> {
				this.stop();
			});
		}

		protected void addPlayer(IUser user, int num) {
			numsPlayers.put(num, user);
		}

		public int getBet() {
			return bet;
		}

		protected boolean isPlaying(IUser user) {
			return numsPlayers.containsValue(user);
		}

		protected boolean isBet(int num) {
			return numsPlayers.containsKey(num);
		}

		protected void start() {
			EmbedBuilder builder = Utils.getDefaultEmbed()
					.withAuthorName("Dice Game")
					.withThumbnail("http://findicons.com/files/icons/2118/nuvola/128/package_games_board.png")
					.appendField(context.getAuthor().getName() + " started a dice game.",
							"Use `" + context.getPrefix() + "dice <num>` to join the game with a **" + bet + " coins** putting.", false)
					.withFooterText("You have " + (timer.getDelay() / 1000) + " seconds to make your bets.");
			BotUtils.sendEmbed(builder.build(), context.getChannel());

			timer.start();
		}

		protected void stop() {
			timer.stop();

			int winningNum = MathUtils.rand(1, 6);
			BotUtils.sendMessage(Emoji.DICE + " The dice is rolling... **" + winningNum + "** !", context.getChannel());

			if(this.isBet(winningNum)) {
				IUser winner = numsPlayers.get(winningNum);
				int gains = bet * (numsPlayers.size() + MULTIPLIER);
				BotUtils.sendMessage(Emoji.DICE + " Congratulations " + winner.mention() + ", you win " + gains + " coins !", context.getChannel());
				Storage.getPlayer(context.getGuild(), winner).addCoins(gains);
				numsPlayers.remove(winningNum);
			}

			if(!numsPlayers.isEmpty()) {
				StringBuilder strBuilder = new StringBuilder(Emoji.MONEY_WINGS + " Sorry, ");
				for(int num : numsPlayers.keySet()) {
					Storage.getPlayer(context.getGuild(), numsPlayers.get(num)).addCoins(-bet);
					strBuilder.append(numsPlayers.get(num).mention() + ", ");
				}
				strBuilder.append("you have lost " + bet + " coin(s).");
				BotUtils.sendMessage(strBuilder.toString(), context.getChannel());
			}

			CHANNELS_DICE.remove(context.getChannel());
		}
	}
}
