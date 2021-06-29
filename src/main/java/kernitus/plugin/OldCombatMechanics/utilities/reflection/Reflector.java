package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Reflector {
    private static String version;

    static{
        try{
            version = Bukkit.getServer().getClass().getName().split("\\.")[3];
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
            throw new RuntimeException("Couldn't load class " + fqn, e);
        }
    }


    public static Method getMethod(Class<?> clazz, String name){
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static Method getMethod(Class<?> clazz, String name, int parameterCount){
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
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

    public static Constructor<?> getConstructor(Class<?> clazz, int numParams){
        return Stream.concat(
                Arrays.stream(clazz.getDeclaredConstructors()),
                Arrays.stream(clazz.getConstructors())
        )
                .filter(constructor -> constructor.getParameterCount() == numParams)
                .peek(it -> it.setAccessible(true))
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
        return Stream.concat(
                Arrays.stream(clazz.getDeclaredConstructors()),
                Arrays.stream(clazz.getConstructors())
        )
                .filter(constructor -> getParameterNames.apply(constructor).equals(typeNames))
                .peek(it -> it.setAccessible(true))
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

    public static <T> T getUnchecked(UncheckedReflectionSupplier<T> supplier){
        try{
            return supplier.get();
        } catch(ReflectiveOperationException e){
            throw new RuntimeException(e);
        }
    }

    public static void doUnchecked(UncheckedReflectionRunnable runnable){
        try{
            runnable.run();
        } catch(ReflectiveOperationException e){
            throw new RuntimeException(e);
        }
    }

    public interface UncheckedReflectionSupplier<T> {
        T get() throws ReflectiveOperationException;
    }

    public interface UncheckedReflectionRunnable {
        void run() throws ReflectiveOperationException;
    }
}
