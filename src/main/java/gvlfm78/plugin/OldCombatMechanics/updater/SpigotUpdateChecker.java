package gvlfm78.plugin.OldCombatMechanics.updater;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SpigotUpdateChecker {

	//Taken from https://www.spigotmc.org/threads/resource-updater-for-your-plugins-v1-1.37315/page-2#post-1272537
	//and modified by gvlfm78 to better fit with this plugin

	private final String API_KEY = "98BE0FE67F88AB82B4C197FAF1DC3B69206EFDCC4D3B80FC83A00037510B99B4";
	private final String REQUEST_METHOD = "POST";
	private String RESOURCE_ID = "";
	private final String HOST = "http://www.spigotmc.org";
	private final String QUERY = "/api/general.php";
	private String WRITE_STRING;

	private String version;
	private String oldVersion;

	private SpigotUpdateChecker.UpdateResult result = SpigotUpdateChecker.UpdateResult.NO_UPDATE;

	private HttpURLConnection connection;

	public enum UpdateResult{
		NO_UPDATE, FAIL_SPIGOT, FAIL_NOVERSION, BAD_RESOURCEID, UPDATE_AVAILABLE
	}

	public SpigotUpdateChecker(OCMMain plugin, Integer resourceId){
		RESOURCE_ID = resourceId + "";
		oldVersion = plugin.getDescription().getVersion();

		try{
			connection = (HttpURLConnection) new URL(HOST + QUERY).openConnection();
		}
		catch (IOException e){
			result = UpdateResult.FAIL_SPIGOT;
			return;
		}

		WRITE_STRING = "key=" + API_KEY + "&resource=" + RESOURCE_ID;
		run();
	}

	private void run(){
		connection.setDoOutput(true);

		try{
			connection.setRequestMethod(REQUEST_METHOD);
			connection.getOutputStream().write(WRITE_STRING.getBytes("UTF-8"));
		}
		catch (IOException e){
			result = UpdateResult.FAIL_SPIGOT;
			return;
		}

		String version;
		try{
			version = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
		}
		catch (Exception e){
			result = UpdateResult.BAD_RESOURCEID;
			return;
		}

		if (version != null && version.length() <= 7){
			this.version = version;
			version.replace("[^A-Za-z]", "").replace("|", "");
			versionCheck();
			return;
		}

		result = UpdateResult.BAD_RESOURCEID;
	}

    private void versionCheck() {
        result = VersionChecker.shouldUpdate(oldVersion, version) ? UpdateResult.UPDATE_AVAILABLE : UpdateResult.NO_UPDATE;
    }

    public UpdateResult getResult(){
		return result;
	}

	public String getVersion(){
		return version;
	}
}
