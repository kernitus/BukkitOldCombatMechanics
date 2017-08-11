package gvlfm78.plugin.OldCombatMechanics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private void versionCheck(){
		if (shouldUpdate(oldVersion, version)){
			result = UpdateResult.UPDATE_AVAILABLE;
		}
		else{
			result = UpdateResult.NO_UPDATE;
		}
	}

	public boolean shouldUpdate(String localVersion, String remoteVersion){
		return isUpdateOut(remoteVersion, localVersion);
		// return !localVersion.equalsIgnoreCase(remoteVersion);
	}

	private static int[] getVersionNumbers(String ver){
		Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.*(\\d*)(beta(\\d*))?").matcher(ver);
		if (!m.matches())
			throw new IllegalArgumentException("Plugin version formatted wrong!");

		return new int[]
				{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3).isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(m.group(3)), m.group(4) == null ? Integer.MAX_VALUE : m.group(5).isEmpty() ? 1 : Integer.parseInt(m.group(5))};
	}

	private static boolean isUpdateOut(String remote, String local){

		int[] testVer = getVersionNumbers(remote);
		int[] baseVer = getVersionNumbers(local);

		for (int i = 0; i < testVer.length; i++)
			if (testVer[i] != baseVer[i])
				return testVer[i] > baseVer[i];

				return false;
	}

	public UpdateResult getResult(){
		return result;
	}

	public String getVersion(){
		return version;
	}
}
