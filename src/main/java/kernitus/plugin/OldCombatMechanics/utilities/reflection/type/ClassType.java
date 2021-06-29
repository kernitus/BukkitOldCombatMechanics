package kernitus.plugin.OldCombatMechanics.utilities.reflection.type;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;

public enum ClassType {

    NMS {
        @Override
        public String qualifyClassName(String partialName){
            if(Reflector.versionIsNewerOrEqualAs(1, 17, 0)){
                return "net.minecraft." + partialName;
            }
            // FIXME: Assumes class names are upper case and trims off the preceding package name
            // entity.foo.Bar.Test
            // ^^^^^^^^^^ ^^^^^^^^^
            //   Group 1   Group 2
            String className = partialName.replaceAll("([a-z.]+)\\.([a-zA-Z.]+)", "$2");

            return "net.minecraft.server." + Reflector.getVersion() + "." + className;
        }
    },
    CRAFTBUKKIT {
        @Override
        public String qualifyClassName(String partialName){
            return String.format("%s.%s.%s", "org.bukkit.craftbukkit", Reflector.getVersion(), partialName);
        }
    };

    public abstract String qualifyClassName(String partialName);
}
