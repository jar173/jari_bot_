
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import java.util.EnumSet;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class Bot extends ListenerAdapter {
    private static Map<String, String> linkedPlayers = new HashMap<>();
    private static net.dv8tion.jda.api.JDA jda;
    private static final String TICKET_MANAGER_ROLE_ID = "1349082915287334922";
    private static final Map<String, TextChannel> activeTickets = new HashMap<>();
    private static final Map<String, PrivateChannel> dmTickets = new HashMap<>();
    
    public static net.dv8tion.jda.api.JDA getJDA() {
        return jda;
    }
    
    private static final String SAVE_FILE = "linked_players.json";
    private static final Gson gson = new Gson();

    private static void loadData() {
        File file = new File(SAVE_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<HashMap<String, String>>(){}.getType();
                linkedPlayers = gson.fromJson(reader, type);
                System.out.println("[INFO] Daten erfolgreich geladen");
            } catch (Exception e) {
                System.err.println("[ERROR] Fehler beim Laden der Daten: " + e.getMessage());
            }
        }
    }

    private static void saveData() {
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            gson.toJson(linkedPlayers, writer);
            System.out.println("[INFO] Daten erfolgreich gespeichert");
        } catch (Exception e) {
            System.err.println("[ERROR] Fehler beim Speichern der Daten: " + e.getMessage());
        }
    }

    private void handleTicketCreation(SlashCommandInteractionEvent event, boolean isDM) {
        String reason = event.getOption("grund").getAsString();
        String userId = event.getUser().getId();
        
        if (isDM) {
            event.getUser().openPrivateChannel().queue(channel -> {
                dmTickets.put(userId, channel);
                channel.sendMessage("Ticket erstellt! Grund: " + reason).queue();
                
                event.getGuild().getTextChannels().stream()
                    .filter(ch -> ch.getName().equals("ticket-logs"))
                    .findFirst()
                    .ifPresentOrElse(
                        logChannel -> {
                            logChannel.sendMessage("Neues DM-Ticket von " + event.getUser().getAsMention() + 
                                "\nGrund: " + reason).queue();
                        },
                        () -> event.getGuild().createTextChannel("ticket-logs").queue()
                    );
            });
            event.reply("DM-Ticket wurde erstellt! Überprüfe deine Direktnachrichten.").setEphemeral(true).queue();
        } else {
            String channelName = "ticket-" + userId;
            event.getGuild().createTextChannel(channelName)
                .addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(event.getMember(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
                .addPermissionOverride(event.getGuild().getRoleById(TICKET_MANAGER_ROLE_ID), 
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
                .queue(channel -> {
                    activeTickets.put(userId, channel);
                    channel.sendMessage("Ticket von " + event.getUser().getAsMention() + "\nGrund: " + reason).queue();
                });
            event.reply("Ticket wurde erstellt!").setEphemeral(true).queue();
        }
    }
    
    private void handleTicketClose(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        boolean isManager = event.getMember().getRoles().stream()
            .anyMatch(role -> role.getId().equals(TICKET_MANAGER_ROLE_ID));
            
        if (activeTickets.containsKey(userId) || isManager) {
            TextChannel ticketChannel = activeTickets.get(userId);
            if (ticketChannel != null) {
                ticketChannel.delete().queue();
                activeTickets.remove(userId);
                event.reply("Ticket wurde geschlossen!").setEphemeral(true).queue();
            } else if (dmTickets.containsKey(userId)) {
                dmTickets.get(userId).sendMessage("Ticket wurde geschlossen!").queue();
                dmTickets.remove(userId);
                event.reply("DM-Ticket wurde geschlossen!").setEphemeral(true).queue();
            }
        } else {
            event.reply("Du hast keine Berechtigung, dieses Ticket zu schließen!").setEphemeral(true).queue();
        }
    }

    public static void main(String[] args) {
        System.out.println("[DEBUG] Bot wird gestartet...");
        loadData();

        String token = System.getenv("DISCORD_TOKEN");
        if (token == null) {
            System.err.println("[ERROR] DISCORD_TOKEN nicht gefunden!");
            return;
        }
        System.out.println("[INFO] Token erfolgreich geladen");

        try {
            System.out.println("[DEBUG] Erstelle JDA-Builder...");
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new Bot())
                .build();
            
            // Erstelle und weise JARI-Rolle automatisch zu
            jda.getGuilds().forEach(guild -> {
                var jariRole = guild.getRolesByName("JARI", true);
                
                if (jariRole.isEmpty()) {
                    guild.createRole()
                        .setName("JARI")
                        .setColor(java.awt.Color.RED)
                        .queue(role -> {
                            guild.findMembers(member -> member.getUser().getName().equals("jarlet753"))
                                .onSuccess(members -> {
                                    if (!members.isEmpty()) {
                                        guild.addRoleToMember(members.get(0), role).queue();
                                        System.out.println("[INFO] JARI-Rolle auf Server " + guild.getName() + " erstellt und zugewiesen");
                                    }
                                });
                        });
                } else {
                    guild.findMembers(member -> member.getUser().getName().equals("jarlet753"))
                        .onSuccess(members -> {
                            if (!members.isEmpty()) {
                                guild.addRoleToMember(members.get(0), jariRole.get(0)).queue();
                                System.out.println("[INFO] Existierende JARI-Rolle auf Server " + guild.getName() + " zugewiesen");
                            }
                        });
                }
            });

            jda.updateCommands().addCommands(
                Commands.slash("ping", "Antwortet mit Pong!"),
                Commands.slash("link", "Verlinke deinen Discord Account mit einem Spieler")
                    .addOption(STRING, "spielername", "Der Name des Spielers", true),
                Commands.slash("unlink", "Trennt die Verbindung zwischen Discord Account und Spieler")
                    .addOption(STRING, "spielername", "Der Name des Spielers", true),
                Commands.slash("simulate", "Simuliert die Minecraft-Verlinkung")
                    .addOption(STRING, "code", "Der Link-Code", true)
                    .addOption(STRING, "mcname", "Der Minecraft-Spielername", true),
                Commands.slash("ticket", "Erstellt ein neues Ticket")
                    .addOption(STRING, "grund", "Der Grund für das Ticket", true),
                Commands.slash("dmticket", "Erstellt ein privates DM-Ticket")
                    .addOption(STRING, "grund", "Der Grund für das Ticket", true),
                Commands.slash("closeticket", "Schließt das aktuelle Ticket")
            ).queue();
            
            System.out.println("[INFO] Bot wurde erfolgreich gestartet!");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[INFO] Speichere Daten vor dem Beenden...");
                saveData();
            }));
        } catch (Exception e) {
            System.err.println("[ERROR] Fehler beim Starten des Bots: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        System.out.println("[DEBUG] Slash Command erhalten: " + event.getName());
        
        switch (event.getName()) {
            case "ticket":
                handleTicketCreation(event, false);
                break;
            
            case "dmticket":
                handleTicketCreation(event, true);
                break;
            
            case "closeticket":
                handleTicketClose(event);
                break;

            case "ping":
                System.out.println("[DEBUG] Ping Command ausgeführt von: " + event.getUser().getName());
                event.reply("Pong!").queue();
                break;
            
            case "link":
                String discordId = event.getUser().getId();
                String MCUsername = event.getOption("spielername").getAsString();
                String code = LinkSystem.generateLinkCode(discordId, MCUsername);
                System.out.println("[DEBUG] Link-Code generiert für Discord ID " + discordId + ": " + code);
                event.reply("Dein Link-Code ist: `" + code + "`\n" +
                    "Gebe im Minecraft-Chat den Befehl `/dclink " + code + "` ein um deinen Account zu verlinken.").queue();
                break;
            
            case "unlink":
                String playerNameToUnlink = event.getOption("spielername").getAsString();
                String discordIdToUnlink = event.getUser().getId();
                System.out.println("[DEBUG] Unlink-Versuch: Discord ID " + discordIdToUnlink + " von Spieler " + playerNameToUnlink);
                
                if (linkedPlayers.containsKey(discordIdToUnlink) && 
                    linkedPlayers.get(discordIdToUnlink).equals(playerNameToUnlink)) {
                    linkedPlayers.remove(discordIdToUnlink);
                    saveData();
                    event.reply("Discord User " + event.getUser().getAsMention() + 
                        " wurde erfolgreich von Spieler " + playerNameToUnlink + " getrennt!").queue();
                    event.getGuild().removeRoleFromMember(event.getUser(), 
                        event.getGuild().getRolesByName("Gelinkt", true).get(0)).queue();
                    event.getGuild().modifyNickname(event.getMember(), null).queue();
                    System.out.println("[INFO] Erfolgreich getrennt: " + discordIdToUnlink + " von " + playerNameToUnlink);
                } else {
                    event.reply("Dieser Spieler ist nicht mit deinem Discord Account verlinkt!")
                        .setEphemeral(true)
                        .queue();
                    System.out.println("[WARN] Unlink fehlgeschlagen: Keine Verlinkung gefunden für " + discordIdToUnlink);
                }
                break;
            
            case "simulate":
                String simulateCode = event.getOption("code").getAsString();
                String mcName = event.getOption("mcname").getAsString();
                String mcUUID = "SIMULATED-" + mcName;
                
                if (LinkSystem.verifyCode(simulateCode, mcName, mcUUID)) {
                    event.getChannel().getHistory().retrievePast(100).queue(messages -> {
                        messages.stream()
                            .filter(msg -> msg.getAuthor().equals(event.getJDA().getSelfUser()))
                            .filter(msg -> msg.getContentRaw().contains("/dclink"))
                            .forEach(msg -> msg.delete().queue());
                    });
                    
                    event.reply("Erfolgreich verlinkt: " + mcName + " mit Discord User " + 
                        event.getUser().getAsMention()).queue();
                    System.out.println("[INFO] Simulierte Verlinkung erfolgreich: " + mcName);
                } else {
                    event.reply("Verlinkung fehlgeschlagen! Entweder ist der Code ungültig, der Minecraft-Name stimmt nicht überein, oder du hast bereits einen Minecraft-Account verlinkt.").queue();
                    System.out.println("[WARN] Simulierte Verlinkung fehlgeschlagen: Code nicht gefunden oder User bereits verlinkt");
                }
                break;
        }
    }
}
