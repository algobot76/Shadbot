package me.shadorc.discordbot.command.image;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import me.shadorc.discordbot.Config;
import me.shadorc.discordbot.Emoji;
import me.shadorc.discordbot.MissingArgumentException;
import me.shadorc.discordbot.Shadbot;
import me.shadorc.discordbot.command.AbstractCommand;
import me.shadorc.discordbot.command.Context;
import me.shadorc.discordbot.utils.BotUtils;
import me.shadorc.discordbot.utils.LogUtils;
import me.shadorc.discordbot.utils.MathUtils;
import sx.blah.discord.util.EmbedBuilder;

public class Rule34Cmd extends AbstractCommand {

	public Rule34Cmd() {
		super(Role.USER, "rule34", "r34");
	}

	@Override
	public void execute(Context context) throws MissingArgumentException {
		if(!context.getChannel().isNSFW()) {
			BotUtils.sendMessage(Emoji.EXCLAMATION + " This must be a NSFW-channel.", context.getChannel());
			return;
		}

		if(!context.hasArg()) {
			throw new MissingArgumentException();
		}

		try {
			JSONObject mainObj = XML.toJSONObject(IOUtils.toString(new URL("https://rule34.xxx/index.php?"
					+ "page=dapi"
					+ "&s=post"
					+ "&q=index"
					+ "&limit=5"
					+ "&tags=" + URLEncoder.encode(context.getArg(), "UTF-8")), "UTF-8"));
			JSONObject postsObj = mainObj.getJSONObject("posts");

			if(postsObj.getInt("count") == 0) {
				BotUtils.sendMessage(Emoji.MAGNIFYING_GLASS + " No result for \"" + context.getArg() + "\".", context.getChannel());
				return;
			}

			JSONArray postsArray = postsObj.getJSONArray("post");
			JSONObject postObj = postsArray.getJSONObject(MathUtils.rand(postsArray.length()-1));

			EmbedBuilder embed = new EmbedBuilder()
					.withAuthorName("Rule34 (Search: " + context.getArg() + ")")
					.withAuthorIcon(Shadbot.getClient().getOurUser().getAvatarURL())
					.withThumbnail("http://rule34.paheal.net/themes/rule34v2/rule34_logo_top.png")
					.appendField("Resolution", postObj.getInt("width") + "x" + postObj.getInt("height"), false)
					.appendField("Source", postObj.getString("source"), false)
					.appendField("Tags", postObj.getString("tags").trim().replace(" ", ", "), false)
					.withImage(postObj.getString("file_url"));
			BotUtils.sendEmbed(embed.build(), context.getChannel());

		} catch (IOException e) {
			LogUtils.error("Something went wrong while getting an image from Rule34... Please, try again later.", e, context.getChannel());
		}

	}

	@Override
	public void showHelp(Context context) {
		EmbedBuilder builder = new EmbedBuilder()
				.withAuthorName("Help for " + this.getNames()[0] + " command")
				.withAuthorIcon(Shadbot.getClient().getOurUser().getAvatarURL())
				.withColor(Config.BOT_COLOR)
				.appendDescription("**Show an image corresponding to a tag from Rule34 website.**")
				.appendField("Usage", context.getPrefix() + "rule34 <tag(s)>", false);
		BotUtils.sendEmbed(builder.build(), context.getChannel());
	}

}
