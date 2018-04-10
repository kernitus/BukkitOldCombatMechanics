package kernitus.plugin.OldCombatMechanics.updater;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SpigetUpdateChecker {

    private static final String USER_AGENT = "OldCombatMechanics";
    private static final String VERSIONS_URL = "https://api.spiget.org/v2/resources/19510/versions?size=15000";
    private static final String UPDATES_URL = "https://api.spiget.org/v2/resources/19510/updates?size=15000";
    private String latestVersion = "";

    public boolean isUpdateAvailable(){
        try{
            JSONArray versionsArray = getArray(VERSIONS_URL);
            latestVersion = ((JSONObject) versionsArray.get(versionsArray.size() - 1)).get("name").toString();
            return VersionChecker.shouldUpdate(latestVersion);
        } catch(Exception e){
            return false;
        }
    }

    public String getUpdateURL(){
        try{
            JSONArray updatesArray = getArray(UPDATES_URL);
            String updateId = ((JSONObject) updatesArray.get(updatesArray.size() - 1)).get("id").toString();
            return "https://www.spigotmc.org/resources/oldcombatmechanics.19510/update?update=" + updateId;
        } catch(Exception e){
            return "Error getting update URL";
        }
    }

    public String getLatestVersion(){
        return latestVersion;
    }

    private JSONArray getArray(String urlString){
        try{
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.addRequestProperty("User-Agent", USER_AGENT); // Set User-Agent

            InputStream inputStream = connection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);

            return (JSONArray) JSONValue.parseWithException(reader);
        } catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
