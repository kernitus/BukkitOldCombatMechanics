/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Reflector {
    private static String version;
    private static int majorVersion, minorVersion, patchVersion;

    static {
        try {
            // Split on the "-" to just get the version information
            version = Bukkit.getServer().getBukkitVersion().split("-")[0];
            final String[] splitVersion = version.split("\\.");

            majorVersion = Integer.parseInt(splitVersion[0]);
            minorVersion = Integer.parseInt(splitVersion[1]);
            if(splitVersion.length > 2) {
                patchVersion = Integer.parseInt(splitVersion[2]);
            } else {
                patchVersion = 0;
            }
        } catch (Exception e) {
            System.err.println("Failed to load Reflector: " + e.getMessage());
        }
    }

    public static String getVersion() {
        return version;
    }

    /**
     * Checks if the current server version is newer or equal to the one provided.
     *
     * @param major the target major version
     * @param minor the target minor version. 0 for all
     * @param patch the target patch version. 0 for all
     * @return true if the server version is newer or equal to the one provided
     */
    public static boolean versionIsNewerOrEqualTo(int major, int minor, int patch) {
        if (getMajorVersion() < major) return false;
        if (getMinorVersion() < minor) return false;
        return getPatchVersion() >= patch;
    }

    private static int getMajorVersion() {
        return majorVersion;
    }

    private static int getMinorVersion() {
        return minorVersion;
    }

    private static int getPatchVersion() {
        return patchVersion;
    }

    public static Class<?> getClass(ClassType type, String name) {
        return getClass(type.qualifyClassName(name));
    }

    public static Class<?> getClass(String fqn) {
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't load class " + fqn, e);
        }
    }

    public static Method getMethod(Class<?> clazz, String name) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static Method getMethod(Class<?> clazz, String name, int parameterCount) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
                .findFirst()
                .orElse(null);
    }

    public static Method getMethod(Class<?> clazz, Class<?> returnType, String... parameterTypeSimpleNames){
        List<String> typeNames = Arrays.asList(parameterTypeSimpleNames);
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getReturnType() == returnType)
                .filter(it -> getParameterNames.apply(it).equals(typeNames))
                .findFirst()
                .orElse(null);
    }

    private static final Function<Method, List<String>> getParameterNames = method -> Arrays
            .stream(method.getParameters())
            .map(Parameter::getType)
            .map(Class::getSimpleName)
            .collect(Collectors.toList());

    public static Method getMethod(Class<?> clazz, String name, String... parameterTypeSimpleNames) {
        List<String> typeNames = Arrays.asList(parameterTypeSimpleNames);
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredMethods()),
                        Arrays.stream(clazz.getMethods())
                )
                .filter(it -> it.getName().equals(name))
                .filter(it -> getParameterNames.apply(it).equals(typeNames))
                .peek(it -> it.setAccessible(true))
                .findFirst()
                .orElse(null);
    }

    public static Method getMethodByGenericReturnType(TypeVariable<?> typeVar, Class<?> clazz){
        for (Method method : clazz.getMethods()){
            if (method.getGenericReturnType().getTypeName().equals(typeVar.getName())){
                return method;
            }
        }
        throw new RuntimeException("Method with type " + typeVar + " not found");
    }


    public static <T> T invokeMethod(Method method, Object handle, Object... params) {
        try {
            @SuppressWarnings("unchecked")
            T t = (T) method.invoke(handle, params);
            return t;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves the given method, caches it and then uses that instance for all future invocations.
     * <p>
     * The returned function just invokes the cached method for a given target.
     *
     * @param clazz the clazz the method is in
     * @param name  the name of the method
     * @param <T>   the type of the handle
     * @param <U>   the type of the parameter(s)
     * @param <R>   the type of the method result
     * @return a function that invokes the retrieved cached method for its argument
     */
    public static <T, U, R> BiFunction<T, U, R> memoiseMethodInvocation(Class<T> clazz, String name, String... argTypes) {
        final Method method = getMethod(clazz, name, argTypes);
        return (t, u) -> {
            // If they did not want to send any arguments, should be zero-length array
            // This check is necessary cause of varargs, otherwise we get 1 length array of 0-length array
            if(u instanceof Object[] && ((Object[]) u).length == 0)
                return invokeMethod(method, t);

            return invokeMethod(method, t, u);
        };
    }

    public static Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Field getFieldByType(Class<?> clazz, String simpleClassName) {
        for (Field declaredField : clazz.getDeclaredFields()) {
            if (declaredField.getType().getSimpleName().equals(simpleClassName)) {
                declaredField.setAccessible(true);
                return declaredField;
            }
        }
        throw new RuntimeException("Field with type " + simpleClassName + " not found");
    }

    public static Field getMapFieldWithTypes(Class<?> clazz, Class<?> keyType, Class<?> valueType) {
        for (Field field : clazz.getDeclaredFields()) {
            // Check if the field is a Map
            if (Map.class.isAssignableFrom(field.getType())) {
                // Get the generic type of the field
                final Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    final ParameterizedType parameterizedType = (ParameterizedType) genericType;
                    final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    // Check if the map's key and value types match the specified classes
                    if (actualTypeArguments.length == 2 &&
                            actualTypeArguments[0].equals(keyType) &&
                            actualTypeArguments[1].equals(valueType)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
            }
        }
        throw new RuntimeException("Map field with key type " + keyType.getSimpleName() +
                " and value type " + valueType.getSimpleName() + " not found");
    }

    public static Object getFieldValueByType(Object object, String simpleClassName) throws Exception {
        Stream<Field> publicFields = Stream.of(object.getClass().getFields());
        Stream<Field> declaredFields = Stream.of(object.getClass().getDeclaredFields());
        Stream<Field> allFields = Stream.concat(publicFields, declaredFields);

        // Find the first field that matches the type name
        Field matchingField = allFields
                .filter(declaredField -> declaredField.getType().getSimpleName().equals(simpleClassName))
                .findFirst()
                .orElseThrow(() -> new NoSuchFieldException("Couldn't find field with type " + simpleClassName + " in " + object.getClass()));

        // Make the field accessible and return its value
        matchingField.setAccessible(true);
        return matchingField.get(object);
    }

    public static Object getFieldValue(Field field, Object handle) {
        field.setAccessible(true);
        try {
            return field.get(handle);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setFieldValue(Field field, Object handle, Object value) {
        field.setAccessible(true);
        try {
            field.set(handle, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Constructor<?> getConstructor(Class<?> clazz, int numParams) {
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredConstructors()),
                        Arrays.stream(clazz.getConstructors())
                )
                .filter(constructor -> constructor.getParameterCount() == numParams)
                .peek(it -> it.setAccessible(true))
                .findFirst()
                .orElse(null);
    }

    public static Constructor<?> getConstructor(Class<?> clazz, String... parameterTypeSimpleNames) {
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
    public static boolean inheritsFrom(Class<?> toCheck, Class<?> inheritedClass) {
        if (inheritedClass.isAssignableFrom(toCheck)) {
            return true;
        }

        for (Class<?> implementedInterface : toCheck.getInterfaces()) {
            if (inheritsFrom(implementedInterface, inheritedClass)) {
                return true;
            }
        }

        return false;
    }

    public static <T> T getUnchecked(UncheckedReflectionSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void doUnchecked(UncheckedReflectionRunnable runnable) {
        try {
            runnable.run();
        } catch (ReflectiveOperationException e) {
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
