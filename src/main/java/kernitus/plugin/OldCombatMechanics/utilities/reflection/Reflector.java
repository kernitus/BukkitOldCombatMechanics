package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import kernitus.plugin.OldCombatMechanics.utilities.packet.Packet;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Rayzr522 on 7/11/16.
 */

public class Reflector {
    private static String version = "";

    static{
        try{
            version = Bukkit.getServer().getClass().getName().split("\\.")[3];

            Class<?> CRAFT_PLAYER = getClass(ClassType.CRAFTBUKKIT, "entity.CraftPlayer");
            assert CRAFT_PLAYER != null;

        } catch(Exception e){
            System.err.println("Failed to load Reflector");
            e.printStackTrace();
        }

    }

    public static String getVersion(){
        return version;
    }

    /**
     * Checks if the current server version is newer or equal to the one provided.
     *
     * @param major the target major version
     * @param minor the target minor version. 0 for all
     * @param patch the target patch version. 0 for all
     * @return true of the server version is newer or equal to the one provided
     */
    public static boolean versionIsNewerOrEqualAs(int major, int minor, int patch){
        if(getMajorVersion() < major){
            return false;
        }
        if(getMinorVersion() < minor){
            return false;
        }
        return getPatchVersion() >= patch;
    }

    private static int getMajorVersion(){
        return Integer.parseInt(getVersionSanitized().split("_")[0]);
    }

    private static String getVersionSanitized(){
        return getVersion().replaceAll("[^\\d_]", "");
    }

    private static int getMinorVersion(){
        return Integer.parseInt(getVersionSanitized().split("_")[1]);
    }

    private static int getPatchVersion(){
        String[] split = getVersionSanitized().split("_");
        if(split.length < 3){
            return 0;
        }
        return Integer.parseInt(split[2]);
    }

    public static Class<?> getClass(ClassType type, String name){
        return getClass(type.qualifyClassName(name));
    }
    public static Class<?> getClass(String fqn){
        try{
            return Class.forName(fqn);
        } catch(ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }
    }


    public static Method getMethod(Class<?> clazz, String name){
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static <T> T invokeMethod(Method method, Object handle, Object... params){
        try{
            @SuppressWarnings("unchecked")
            T t = (T) method.invoke(handle, params);
            return t;
        } catch(IllegalAccessException | InvocationTargetException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves the given method, caches it and then uses that instance for all future invocations.
     * <p>
     * The returned function just invokes the cached method for a given target.
     *
     * @param clazz  the clazz the method is in
     * @param name   the name of the method
     * @param params the parameters for the method call
     * @param <T>    the type of the handle
     * @param <R>    the type of the method result
     * @return a function that invokes the retrieved cached method for its argument
     */
    public static <T, R> Function<T, R> memoizeMethodAndInvoke(Class<T> clazz, String name, Object... params){
        Method method = getMethod(clazz, name);
        return t -> invokeMethod(method, t, params);
    }

    public static Field getField(Class<?> clazz, String fieldName){
        try{
            return clazz.getDeclaredField(fieldName);
        } catch(NoSuchFieldException e){
            throw new RuntimeException(e);
        }
    }

    public static Field getFieldByType(Class<?> clazz, String simpleClassName){
        for(Field declaredField : clazz.getDeclaredFields()){
            if(declaredField.getType().getSimpleName().equals(simpleClassName)){
                declaredField.setAccessible(true);
                return declaredField;
            }
        }
        throw new RuntimeException("Field with type " + simpleClassName + " not found");
    }

    public static Field getInaccessibleField(Class<?> clazz, String fieldName){
        Field field = getField(clazz, fieldName);
        field.setAccessible(true);
        return field;
    }

    public static Object getDeclaredFieldValueByType(Object object, String simpleClassName) throws Exception{
        for(Field declaredField : object.getClass().getDeclaredFields()){
            if(declaredField.getType().getSimpleName().equals(simpleClassName)){
                declaredField.setAccessible(true);
                return declaredField.get(object);
            }
        }
        throw new NoSuchFieldException("Couldn't find field with type " + simpleClassName + " in " + object.getClass());
    }

    public static Object getFieldValue(Field field, Object handle){
        field.setAccessible(true);
        try{
            return field.get(handle);
        } catch(IllegalAccessException e){
            throw new RuntimeException(e);
        }
    }

    public static void setFieldValue(Object object, String fieldName, Object value) throws Exception{
        getInaccessibleField(object.getClass(), fieldName).set(object, value);
    }

    public static Constructor<?> getConstructor(Class<?> clazz, int numParams){
        return Arrays.stream(clazz.getConstructors())
                .filter(constructor -> constructor.getParameterCount() == numParams)
                .findFirst()
                .orElse(null);
    }

    public static Constructor<?> getConstructor(Class<?> clazz, String... parameterTypeSimpleNames){
        Function<Constructor<?>, List<String>> getParameterNames = constructor -> Arrays
                .stream(constructor.getParameters())
                .map(Parameter::getType)
                .map(Class::getSimpleName)
                .collect(Collectors.toList());
        List<String> typeNames = Arrays.asList(parameterTypeSimpleNames);
        return Arrays.stream(clazz.getConstructors())
                .filter(constructor -> getParameterNames.equals(typeNames))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a given class <i>somehow</i> inherits from another class
     *
     * @param toCheck        The class to check
     * @param inheritedClass The inherited class, it should have
     * @return True if {@code toCheck} somehow inherits from
     * {@code inheritedClass}
     */
    public static boolean inheritsFrom(Class<?> toCheck, Class<?> inheritedClass){
        if(inheritedClass.isAssignableFrom(toCheck)){
            return true;
        }

        for(Class<?> implementedInterface : toCheck.getInterfaces()){
            if(inheritsFrom(implementedInterface, inheritedClass)){
                return true;
            }
        }

        return false;
    }


    public static class Packets {
        public static Class<?> getPacket(PacketType type, String name){
            return Reflector.getClass(ClassType.NMS, "network.protocol.game.Packet" + type.prefix + name);
        }

        public static void sendPacket(Player player, Object packet){
            try{
                Packet.createFromNMSPacket(packet).send(player);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}
