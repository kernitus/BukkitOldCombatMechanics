package gvlfm78.plugin.OldCombatMechanics.utilities.reflection;

import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Created by Rayzr522 on 7/11/16.
 */

public class Reflector {
    private static Method PLAYER_HANDLE;
    private static String version = "";

    static{
        try{
            version = Bukkit.getServer().getClass().getName().split("\\.")[3];

            Class<?> CRAFT_PLAYER = getClass(ClassType.CRAFTBUKKIT, "entity.CraftPlayer");
            assert CRAFT_PLAYER != null;

            PLAYER_HANDLE = getMethod(CRAFT_PLAYER, "getHandle");

        } catch(Exception e){
            System.err.println("Failed to load Reflector");
            e.printStackTrace();
        }

    }

    public static String getVersion(){
        return version;
    }

    public static Class<?> getClass(ClassType type, String name){
        try{
            return Class.forName(String.format("%s.%s.%s", type.getPackage(), version, name));
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

    public static void invokeMethod(Object object, String name, Object... params) throws InvocationTargetException, IllegalAccessException{
        getMethod((object.getClass()), name).invoke(object, params);
    }

    public static Field getField(Class<?> clazz, String fieldName) throws Exception{
        return clazz.getDeclaredField(fieldName);
    }

    public static Field getInaccessibleField(Class<?> clazz, String fieldName) throws Exception{
        Field field = getField(clazz, fieldName);
        field.setAccessible(true);
        return field;
    }

    public static Object getFieldValue(Object object, String fieldName) throws Exception{
        return getInaccessibleField(object.getClass(), fieldName).get(object);
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


    private static Object getPlayerHandler(Player player){
        try{
            return PLAYER_HANDLE.invoke(player);
        } catch(InvocationTargetException | IllegalAccessException e){
            e.printStackTrace();
            return null;
        }
    }

    public static class Packets {
        public static Class<?> getPacket(PacketType type, String name){
            return Reflector.getClass(ClassType.NMS, "Packet" + type.prefix + name);
        }

        public static void sendPacket(Player player, Object packet){
            try{
                Object nmsPlayer = getPlayerHandler(player);
                assert nmsPlayer != null;

                Object con = getFieldValue(nmsPlayer, "playerConnection");
                assert con != null;

                invokeMethod(con, "sendPacket", packet);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}