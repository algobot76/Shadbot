package me.shadorc.shadbot.command.info;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import discord4j.core.object.Region;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildChannel;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.VoiceChannel;
import discord4j.core.object.util.Image.Format;
import discord4j.core.spec.EmbedCreateSpec;
import me.shadorc.shadbot.Config;
import me.shadorc.shadbot.core.command.AbstractCommand;
import me.shadorc.shadbot.core.command.CommandCategory;
import me.shadorc.shadbot.core.command.Context;
import me.shadorc.shadbot.core.command.annotation.Command;
import me.shadorc.shadbot.core.command.annotation.RateLimited;
import me.shadorc.shadbot.data.database.DBGuild;
import me.shadorc.shadbot.data.database.DatabaseManager;
import me.shadorc.shadbot.utils.BotUtils;
import me.shadorc.shadbot.utils.DiscordUtils;
import me.shadorc.shadbot.utils.FormatUtils;
import me.shadorc.shadbot.utils.TimeUtils;
import me.shadorc.shadbot.utils.embed.EmbedUtils;
import me.shadorc.shadbot.utils.embed.HelpBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RateLimited
@Command(category = CommandCategory.INFO, names = { "serverinfo", "server_info", "server-info" })
public class ServerInfoCmd extends AbstractCommand {

	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy - HH'h'mm", Locale.ENGLISH);

	@Override
	public Mono<Void> execute(Context context) {
		final DBGuild dbGuild = DatabaseManager.getDBGuild(context.getGuildId());
		final StringBuilder settingsStr = new StringBuilder();

		if(!dbGuild.getPrefix().equals(Config.DEFAULT_PREFIX)) {
			settingsStr.append(String.format("**Prefix:** %s", context.getPrefix()));
		}

		if(dbGuild.getDefaultVol().intValue() != Config.DEFAULT_VOLUME) {
			settingsStr.append(String.format("%n**Default volume:** %d%%", dbGuild.getDefaultVol()));
		}

		if(!dbGuild.getBlacklistedCmd().isEmpty()) {
			settingsStr.append(String.format("%n**Blacklisted commands:**%n\t%s", String.join("\n\t", dbGuild.getBlacklistedCmd())));
		}

		final Mono<Void> allowedChannelsStr = Flux.fromIterable(dbGuild.getAllowedTextChannels())
				.flatMap(context.getClient()::getTextChannelById)
				.map(TextChannel::getName)
				.collectList()
				.filter(channels -> !channels.isEmpty())
				.map(channels -> settingsStr.append(String.format("%n**Allowed channels:**%n\t%s", String.join("\n\t", channels))))
				.then();

		final Mono<Void> autoRolesStr = Flux.fromIterable(dbGuild.getAutoRoles())
				.flatMap(roleId -> context.getClient().getRoleById(context.getGuildId(), roleId))
				.map(Role::getMention)
				.collectList()
				.filter(roles -> !roles.isEmpty())
				.map(roles -> settingsStr.append(String.format("%n**Auto-roles:**%n\t%s", String.join("\n\t", roles))))
				.then();

		final Mono<Void> permissionsStr = Flux.fromIterable(dbGuild.getAllowedRoles())
				.flatMap(roleId -> context.getClient().getRoleById(context.getGuildId(), roleId))
				.map(Role::getMention)
				.collectList()
				.filter(roles -> !roles.isEmpty())
				.map(roles -> settingsStr.append(String.format("%n**Permissions:**%n\t%s", String.join("\n\t", roles))))
				.then();

		return allowedChannelsStr
				.then(autoRolesStr)
				.then(permissionsStr)
				.then(context.getGuild())
				.flatMap(guild -> Mono.zip(context.getGuild(), guild.getOwner(), guild.getChannels().collectList(),
						guild.getRegion(), context.getAvatarUrl()))
				.map(tuple5 -> {
					final Guild guild = tuple5.getT1();
					final Member owner = tuple5.getT2();
					final List<GuildChannel> channels = tuple5.getT3();
					final Region region = tuple5.getT4();
					final String avatarUrl = tuple5.getT5();

					final String creationDate = String.format("%s%n(%s)",
							TimeUtils.toLocalDate(DiscordUtils.getSnowflakeTimeFromID(guild.getId())).format(this.dateFormatter),
							FormatUtils.longDuration(DiscordUtils.getSnowflakeTimeFromID(guild.getId())));
					final long voiceChannels = channels.stream().filter(VoiceChannel.class::isInstance).count();
					final long textChannels = channels.stream().filter(TextChannel.class::isInstance).count();

					final EmbedCreateSpec embed = EmbedUtils.getDefaultEmbed()
							.setAuthor(String.format("Info about \"%s\"", guild.getName()), null, avatarUrl)
							.setThumbnail(guild.getIconUrl(Format.JPEG).get())
							.addField("Owner", owner.getUsername(), true)
							.addField("Server ID", guild.getId().asString(), true)
							.addField("Creation date", creationDate, true)
							.addField("Region", region.getName(), true)
							.addField("Channels", String.format("**Voice:** %d", voiceChannels)
									+ String.format("%n**Text:** %d", textChannels), true)
							.addField("Members", Integer.toString(guild.getMemberCount().getAsInt()), true);

					if(!settingsStr.toString().isEmpty()) {
						embed.addField("Settings", settingsStr.toString(), true);
					}

					return embed;
				})
				.flatMap(embed -> BotUtils.sendMessage(embed, context.getChannel()))
				.then();
	}

	@Override
	public Mono<EmbedCreateSpec> getHelp(Context context) {
		return new HelpBuilder(this, context)
				.setDescription("Show info about this server.")
				.build();
	}

}
