package gvlfm78.plugin.OldCombatMechanics.utilities.reflection;

import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.HandleType;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Created by Rayzr522 on 7/11/16.
 */

public class Reflector {

    public static Class<?> CRAFT_PLAYER;
    public static Class<?> CRAFT_ENTITY;
    public static Class<?> CRAFT_SERVER;
    public static Class<?> CRAFT_WORLD;
    public static Class<?> CRAFT_ITEM_STACK;
    private static String version = "";
    private static Method PLAYER_HANDLE;
    private static Method ENTITY_HANDLE;
    private static Method SERVER_HANDLE;
    private static Method WORLD_HANDLE;

    static{
        try{
            version = Bukkit.getServer().getClass().getName().split("\\.")[3];

            CRAFT_PLAYER = getClass(ClassType.CRAFTBUKKIT, "entity.CraftPlayer");
            CRAFT_ENTITY = getClass(ClassType.CRAFTBUKKIT, "entity.CraftEntity");
            CRAFT_SERVER = getClass(ClassType.CRAFTBUKKIT, "CraftServer");
            CRAFT_WORLD = getClass(ClassType.CRAFTBUKKIT, "CraftWorld");
            CRAFT_ITEM_STACK = getClass(ClassType.CRAFTBUKKIT, "inventory.CraftItemStack");

            PLAYER_HANDLE = getMethod(CRAFT_PLAYER, "getHandle");
            ENTITY_HANDLE = getMethod(CRAFT_ENTITY, "getHandle");
            SERVER_HANDLE = getMethod(CRAFT_SERVER, "getHandle");
            WORLD_HANDLE = getMethod(CRAFT_WORLD, "getHandle");

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

    public static Object getHandle(HandleType type, Object object){
        try{
            switch(type){
                case ENTITY:
                    return ENTITY_HANDLE.invoke(object);
                case PLAYER:
                    return PLAYER_HANDLE.invoke(object);
                case SERVER:
                    return SERVER_HANDLE.invoke(object);
                case WORLD:
                    return WORLD_HANDLE.invoke(object);
                default:
                    return null;
            }

        } catch(Exception e){
            return null;
        }
    }

    public static Object getNMSItem(ItemStack item){
        try{
            return getFieldValue(item, "handle");
        } catch(Exception e){
            return null;
        }
    }

    public static class Packets {
        public static Class<?> getPacket(PacketType type, String name){
            return Reflector.getClass(ClassType.NMS, "Packet" + type.prefix + name);
        }

        public static void sendPacket(Player player, Object packet){
            try{
                Object nmsPlayer = getHandle(HandleType.PLAYER, player);
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