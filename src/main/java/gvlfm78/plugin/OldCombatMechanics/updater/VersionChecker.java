package kernitus.plugin.OldCombatMechanics.updater;

import kernitus.plugin.OldCombatMechanics.OCMMain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionChecker {

    public static boolean shouldUpdate(String remoteVersion){
        return shouldUpdate(OCMMain.getInstance().getDescription().getVersion(), remoteVersion);
    }

    private static boolean shouldUpdate(String localVersion, String remoteVersion){
        return isUpdateOut(remoteVersion, localVersion);
    }

    private static boolean isUpdateOut(String remote, String local){
        int[] testVer = getVersionNumbers(remote);
        int[] baseVer = getVersionNumbers(local);

        for(int i = 0; i < testVer.length; i++)
            if(testVer[i] != baseVer[i])
                return testVer[i] > baseVer[i];

        return false;
    }

    private static int[] getVersionNumbers(String ver){
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.*(\\d*)(beta(\\d*))?").matcher(ver);
        if(!m.matches())
            throw new IllegalArgumentException("Plugin version formatted wrong!");

        return new int[]{
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                m.group(3).isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(m.group(3)),
                m.group(4) == null ? Integer.MAX_VALUE : m.group(5).isEmpty() ? 1 : Integer.parseInt(m.group(5))
        };
    }
}
