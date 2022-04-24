/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.updater;

import kernitus.plugin.OldCombatMechanics.OCMMain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionChecker {

    public static boolean shouldUpdate(String remoteVersion){
        return shouldUpdate(remoteVersion, OCMMain.getVersion());
    }

    public static boolean shouldUpdate(String remoteVersion, String localVersion){
        return isUpdateOut(remoteVersion, localVersion);
    }

    private static boolean isUpdateOut(String remoteVersion, String localVersion){
        int[] testVer = getVersionNumbers(remoteVersion);
        int[] baseVer = getVersionNumbers(localVersion);

        for(int i = 0; i < testVer.length; i++) {
            if (testVer[i] != baseVer[i])
                return testVer[i] > baseVer[i];
        }

        return false;
    }

    private static int[] getVersionNumbers(String ver){
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.*(\\d*)(-beta(\\d*))?", Pattern.CASE_INSENSITIVE).matcher(ver);
        if(!m.matches()) throw new IllegalArgumentException("Plugin version formatted wrong!");

        // Group 1 = MAJOR
        // Group 2 = MINOR
        // Group 3 = PATCH
        // Group 4 = beta
        // Group 5 = beta_version

        //This parses it to MAJOR.MINOR.PATCH.beta_version
        //MAJOR & MINOR required, anything else is set to highest value possible if omitted
        return new int[]{
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                m.group(3).isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(m.group(3)),
                m.group(4) == null ? Integer.MAX_VALUE : (m.group(5).isEmpty() ? 1 : Integer.parseInt(m.group(5)))
        };
    }
}
