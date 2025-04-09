
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class LinkSystem {
    private static Map<String, LinkedMCPlayer> linkedPlayers = new HashMap<>();
    private static Map<String, pendinglinkrequest> pendingLinks = new HashMap<>(); // code -> discordId
    private static final String SAVE_FILE = "linked_players.json";
    private static final Gson gson = new Gson();

    public static class pendinglinkrequest{
        String code;
        String MCName;
        String DCUserID;

        public pendinglinkrequest(String code, String MCName, String DCUserID){
            this.code = code;
            this.MCName = MCName;
            this.DCUserID = DCUserID;
        }
    }
    
    public static String generateLinkCode(String discordId, String MCName) {
        System.out.println("[DEBUG] Generiere Link-Code für Discord ID: " + discordId);
        String code = generateRandomCode();
        pendinglinkrequest request = new pendinglinkrequest(code, MCName, discordId);
        pendingLinks.put(code, request);
        System.out.println("[DEBUG] Link-Code generiert: " + code);
        return code;
    }
    
    private static String generateRandomCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 6-stelliger Code
        return String.valueOf(code);
    }
    
    public static boolean verifyCode(String code, String mcName, String mcUUID) {
        System.out.println("[DEBUG] Überprüfe Code: " + code + " für Spieler: " + mcName + " (UUID: " + mcUUID + ")");
        if (pendingLinks.containsKey(code)) {
            System.out.println("[DEBUG] Code gefunden in pendingLinks");
            pendinglinkrequest pending = pendingLinks.get(code);
            
            // Überprüfe ob der Minecraft-Name übereinstimmt
            if (!pending.MCName.equals(mcName)) {
                System.out.println("[DEBUG] Minecraft-Name stimmt nicht überein");
                return false;
            }
            
            String discordID = pending.DCUserID;

            // Prüfe ob der Discord-User bereits verlinkt ist
            for (LinkedMCPlayer existingPlayer : linkedPlayers.values()) {
                if (existingPlayer.DiscordID.equals(discordID)) { // Hier sollte discordID verwendet werden
                    return false;
                }
            }

            LinkedMCPlayer player = new LinkedMCPlayer();
            player.UUID = mcUUID;
            player.Name = mcName;
            player.DiscordID = discordID;
            linkedPlayers.put(mcUUID, player);
            pendingLinks.remove(code);
            saveData();
            
            // Rolle und Nickname in Discord aktualisieren
            Bot.getJDA().getGuilds().forEach(guild -> {
                guild.retrieveMemberById(discordID).queue(member -> {
                    // Überprüfe ob die Rolle bereits existiert
                    var linkedRole = guild.getRolesByName("Gelinkt", true);
                    if (linkedRole.isEmpty()) {
                        // Rolle existiert nicht, also erstellen
                        guild.createRole()
                            .setName("Gelinkt")
                            .setColor(java.awt.Color.GREEN)
                            .queue(role -> {
                                guild.addRoleToMember(member, role).queue();
                            });
                    } else {
                        // Rolle existiert bereits, also direkt hinzufügen
                        guild.addRoleToMember(member, linkedRole.get(0)).queue();
                    }
                    
                    // Nickname auf Minecraft-Namen setzen
                    guild.modifyNickname(member, mcName).queue();
                });
            });
            
            return true;
        }
        return false;
    }

    public static LinkedMCPlayer getLinkedPlayer(String mcUUID) {
        return linkedPlayers.get(mcUUID);
    }
    
    public static void loadData() {
        System.out.println("[DEBUG] Versuche Daten zu laden aus: " + SAVE_FILE);
        File file = new File(SAVE_FILE);
        if (file.exists()) {
            System.out.println("[DEBUG] Datei gefunden, lade Inhalte...");
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<HashMap<String, LinkedMCPlayer>>(){}.getType();
                linkedPlayers = gson.fromJson(reader, type);
                System.out.println("[INFO] Link-System Daten geladen");
            } catch (Exception e) {
                System.err.println("[ERROR] Fehler beim Laden der Link-Daten: " + e.getMessage());
            }
        }
    }
    
    private static void saveData() {
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            gson.toJson(linkedPlayers, writer);
            System.out.println("[INFO] Link-System Daten gespeichert");
        } catch (Exception e) {
            System.err.println("[ERROR] Fehler beim Speichern der Link-Daten: " + e.getMessage());
        }
    }
}
