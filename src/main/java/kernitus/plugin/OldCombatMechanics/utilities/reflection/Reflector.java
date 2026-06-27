/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Reflector {
    private static final String UNKNOWN_VERSION = "unknown";

    private static String version = UNKNOWN_VERSION;
    private static int majorVersion = -1, minorVersion = -1, patchVersion = -1;
    private static boolean versionKnown;

    static {
        final MinecraftVersion parsedVersion = resolveMinecraftVersion();
        if (parsedVersion != null) {
            version = parsedVersion.toString();
            majorVersion = parsedVersion.major;
            minorVersion = parsedVersion.minor;
            patchVersion = parsedVersion.patch;
            versionKnown = true;
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
        if (!versionKnown) {
            return false;
        }

        return VersionComparator.isNewerOrEqualTo(
                getMajorVersion(),
                getMinorVersion(),
                getPatchVersion(),
                major,
                minor,
                patch
        );
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

    private static MinecraftVersion resolveMinecraftVersion() {
        MinecraftVersion parsedVersion = parseMinecraftVersion(getMinecraftVersion());
        if (parsedVersion != null) {
            return parsedVersion;
        }

        parsedVersion = parseMinecraftVersion(getBukkitVersion());
        if (parsedVersion != null) {
            return parsedVersion;
        }

        return parseMinecraftVersion(getServerVersion());
    }

    private static String getMinecraftVersion() {
        try {
            final Method getMinecraftVersion = Bukkit.class.getMethod("getMinecraftVersion");
            final Object minecraftVersion = getMinecraftVersion.invoke(null);
            return minecraftVersion instanceof String ? (String) minecraftVersion : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String getBukkitVersion() {
        try {
            return Bukkit.getServer() == null ? null : Bukkit.getServer().getBukkitVersion();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String getServerVersion() {
        try {
            return Bukkit.getVersion();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static MinecraftVersion parseMinecraftVersion(String rawVersion) {
        return MinecraftVersionParser.parse(rawVersion);
    }

    public static Class<?> getClass(String fqn) {
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't load class " + fqn, e);
        }
    }

    public static Method getMethod(Class<?> clazz, String name) {
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredMethods()),
                        Arrays.stream(clazz.getMethods())
                )
                .filter(method -> method.getName().equals(name))
                .peek(method -> method.setAccessible(true))
                .findFirst()
                .orElse(null);
    }

    public static Method getMethod(Class<?> clazz, String name, int parameterCount) {
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredMethods()),
                        Arrays.stream(clazz.getMethods())
                )
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
                .peek(method -> method.setAccessible(true))
                .findFirst()
                .orElse(null);
    }

    public static Method getMethod(Class<?> clazz, Class<?> returnType, String... parameterTypeSimpleNames){
        List<String> typeNames = Arrays.asList(parameterTypeSimpleNames);
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredMethods()),
                        Arrays.stream(clazz.getMethods())
                )
                .filter(method -> method.getReturnType() == returnType)
                .filter(it -> getParameterNames.apply(it).equals(typeNames))
                .peek(method -> method.setAccessible(true))
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

    /**
     * Finds a method by name where the provided parameter types are assignable to the method parameters.
     * Null entries in {@code parameterTypes} act as wildcards.
     */
    public static Method getMethodAssignable(Class<?> clazz, String name, Class<?>... parameterTypes) {
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredMethods()),
                        Arrays.stream(clazz.getMethods())
                )
                .filter(it -> it.getName().equals(name))
                .filter(it -> it.getParameterCount() == parameterTypes.length)
                .filter(it -> areParametersAssignable(it.getParameterTypes(), parameterTypes))
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
     * Finds a constructor where the provided parameter types are assignable to the constructor parameters.
     * Null entries in {@code parameterTypes} act as wildcards.
     */
    public static Constructor<?> getConstructorAssignable(Class<?> clazz, Class<?>... parameterTypes) {
        return Stream.concat(
                        Arrays.stream(clazz.getDeclaredConstructors()),
                        Arrays.stream(clazz.getConstructors())
                )
                .filter(constructor -> constructor.getParameterCount() == parameterTypes.length)
                .filter(constructor -> areParametersAssignable(constructor.getParameterTypes(), parameterTypes))
                .peek(it -> it.setAccessible(true))
                .findFirst()
                .orElse(null);
    }

    /**
     * Attempts to resolve an enum constant by name, trying each provided name in order.
     */
    public static Object getEnumConstant(Class<?> enumClass, String... names) {
        if (!enumClass.isEnum()) {
            throw new IllegalArgumentException(enumClass.getName() + " is not an enum");
        }
        @SuppressWarnings("unchecked")
        Class<? extends Enum> typedEnum = (Class<? extends Enum>) enumClass;
        for (String name : names) {
            if (name == null) continue;
            try {
                return Enum.valueOf(typedEnum, name);
            } catch (IllegalArgumentException ignored) {
                // try next
            }
            for (Object constant : enumClass.getEnumConstants()) {
                Enum<?> enumConstant = (Enum<?>) constant;
                if (enumConstant.name().equalsIgnoreCase(name) || enumConstant.toString().equals(name)) {
                    return enumConstant;
                }
            }
        }
        throw new IllegalArgumentException("No enum constant found in " + enumClass.getName());
    }

    private static boolean areParametersAssignable(Class<?>[] target, Class<?>[] provided) {
        if (target.length != provided.length) return false;
        for (int i = 0; i < target.length; i++) {
            Class<?> providedType = provided[i];
            if (providedType == null) continue;
            if (!target[i].isAssignableFrom(providedType)) return false;
        }
        return true;
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

final class MinecraftVersionParser {
    private static final Pattern MINECRAFT_MARKER = Pattern.compile("\\(MC:\\s*([^)]*)\\)");
    private static final Pattern NUMERIC_VERSION = Pattern.compile("(?<![0-9.])([1-9][0-9]*)\\.([0-9]+)(?:\\.([0-9]+))?(?![0-9.])");

    private MinecraftVersionParser() {
    }

    static MinecraftVersion parse(String rawVersion) {
        if (rawVersion == null) {
            return null;
        }

        final Matcher marker = MINECRAFT_MARKER.matcher(rawVersion);
        while (marker.find()) {
            final MinecraftVersion parsed = parseNumericVersion(marker.group(1));
            if (parsed != null) {
                return parsed;
            }
        }

        return parseNumericVersion(rawVersion);
    }

    private static MinecraftVersion parseNumericVersion(String value) {
        final Matcher matcher = NUMERIC_VERSION.matcher(value);
        while (matcher.find()) {
            try {
                final int major = Integer.parseInt(matcher.group(1));
                final int minor = Integer.parseInt(matcher.group(2));
                final int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                return new MinecraftVersion(major, minor, patch);
            } catch (NumberFormatException ignored) {
                // Keep looking for a realistic Minecraft version.
            }
        }

        return null;
    }
}

final class MinecraftVersion {
    final int major;
    final int minor;
    final int patch;

    MinecraftVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
