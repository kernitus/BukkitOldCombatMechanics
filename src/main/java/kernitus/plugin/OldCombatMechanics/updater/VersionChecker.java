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

    public static boolean shouldUpdate(String remoteVersion) {
        return isUpdateOut(remoteVersion, OCMMain.getVersion());
    }

    private static boolean isUpdateOut(String remoteVersion, String localVersion) {
        final int[] testVer = getVersionNumbers(remoteVersion);
        final int[] baseVer = getVersionNumbers(localVersion);

        for (int i = 0; i < testVer.length; i++) {
            if (testVer[i] != baseVer[i])
                return testVer[i] > baseVer[i];
        }

        return false;
    }

    private static int[] getVersionNumbers(String ver) {
        // Support both -beta and -SNAPSHOT
        Matcher m = Pattern
                .compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(beta|SNAPSHOT)(\\d*))?", Pattern.CASE_INSENSITIVE)
                .matcher(ver);
        if (!m.matches())
            throw new IllegalArgumentException("Plugin version formatted wrong!");

        return new int[] {
                Integer.parseInt(m.group(1)), // MAJOR
                Integer.parseInt(m.group(2)), // MINOR
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3)), // PATCH (default 0)
                m.group(4) == null ? Integer.MAX_VALUE : // Release version
                        (m.group(5).isEmpty() ? 0 : Integer.parseInt(m.group(5))) // Pre-release number
        };
    }

}
