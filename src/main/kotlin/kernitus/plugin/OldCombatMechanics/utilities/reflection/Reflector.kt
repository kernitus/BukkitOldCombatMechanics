/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType
import org.bukkit.Bukkit
import java.lang.reflect.*
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

object Reflector {
    @JvmField
    var version: String? = null
    private var majorVersion = 0
    private var minorVersion = 0
    private var patchVersion = 0

    init {
        try {
            // Split on the "-" to just get the version information
            version =
                Bukkit.getServer().bukkitVersion.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            val splitVersion = version!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            majorVersion = splitVersion[0].toInt()
            minorVersion = splitVersion[1].toInt()
            patchVersion = if (splitVersion.size > 2) {
                splitVersion[2].toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            System.err.println("Failed to load Reflector: " + e.message)
        }
    }

    /**
     * Checks if the current server version is newer or equal to the one provided.
     *
     * @param major the target major version
     * @param minor the target minor version. 0 for all
     * @param patch the target patch version. 0 for all
     * @return true if the server version is newer or equal to the one provided
     */
    @JvmStatic
    fun versionIsNewerOrEqualTo(major: Int, minor: Int, patch: Int): Boolean {
        if (majorVersion < major) return false
        if (minorVersion < minor) return false
        return patchVersion >= patch
    }

    fun getClass(type: ClassType, name: String): Class<*> {
        return getClass(type.qualifyClassName(name))
    }

    fun getClass(fqn: String): Class<*> {
        try {
            return Class.forName(fqn)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Couldn't load class $fqn", e)
        }
    }

    fun getMethod(clazz: Class<*>, name: String): Method? {
        return Arrays.stream(clazz.methods).filter { method: Method -> method.name == name }.findFirst().orElse(null)
    }

    fun getMethod(clazz: Class<*>, name: String, parameterCount: Int): Method? {
        return Arrays.stream(clazz.methods)
            .filter { method: Method -> method.name == name && method.parameterCount == parameterCount }.findFirst()
            .orElse(null)
    }

    fun getMethod(clazz: Class<*>, returnType: Class<*>?, vararg parameterTypeSimpleNames: String?): Method? {
        val typeNames = listOf(*parameterTypeSimpleNames)
        return Arrays.stream(clazz.methods).filter { method: Method -> method.returnType == returnType }
            .filter { it: Method -> getParameterNames.apply(it) == typeNames }.findFirst().orElse(null)
    }

    private val getParameterNames = Function { method: Method ->
        Arrays.stream(method.parameters).map { obj: Parameter -> obj.type }.map { obj: Class<*> -> obj.simpleName }
            .collect(Collectors.toList())
    }

    fun getMethod(clazz: Class<*>, name: String, vararg parameterTypeSimpleNames: String?): Method? {
        val typeNames = listOf(*parameterTypeSimpleNames)
        return Stream.concat(
            Arrays.stream(clazz.declaredMethods), Arrays.stream(clazz.methods)
        ).filter { it: Method -> it.name == name }.filter { it: Method -> getParameterNames.apply(it) == typeNames }
            .peek { it: Method -> it.isAccessible = true }.findFirst().orElse(null)
    }

    fun getMethodByGenericReturnType(typeVar: TypeVariable<*>, clazz: Class<*>): Method {
        for (method in clazz.methods) {
            if (method.genericReturnType.typeName == typeVar.name) {
                return method
            }
        }
        throw RuntimeException("Method with type $typeVar not found")
    }


    fun <T> invokeMethod(method: Method, handle: Any?, vararg params: Any?): T {
        try {
            val t = method.invoke(handle, *params) as T
            return t
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Resolves the given method, caches it and then uses that instance for all future invocations.
     *
     *
     * The returned function just invokes the cached method for a given target.
     *
     * @param clazz the clazz the method is in
     * @param name  the name of the method
     * @param <T>   the type of the handle
     * @param <U>   the type of the parameter(s)
     * @param <R>   the type of the method result
     * @return a function that invokes the retrieved cached method for its argument
    </R></U></T> */
    fun <T, U, R> memoiseMethodInvocation(
        clazz: Class<T>, name: String, vararg argTypes: String?
    ): (T, U) -> R {
        val method = getMethod(clazz, name, *argTypes)
        return { t: T, u: U ->
            // If they did not want to send any arguments, should be zero-length array
            // This check is necessary cause of varargs, otherwise we get 1 length array of 0-length array
            if (u is Array<*> && u.isArrayOf<Any>() && (u as Array<*>).isEmpty())
                invokeMethod<R>(method!!, t)
            invokeMethod(method!!, t, u)
        }
    }

    fun getField(clazz: Class<*>, fieldName: String): Field {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            return field
        } catch (e: NoSuchFieldException) {
            throw RuntimeException(e)
        }
    }

    fun getFieldByType(clazz: Class<*>, simpleClassName: String): Field {
        for (declaredField in clazz.declaredFields) {
            if (declaredField.type.simpleName == simpleClassName) {
                declaredField.isAccessible = true
                return declaredField
            }
        }
        throw RuntimeException("Field with type $simpleClassName not found")
    }

    fun getMapFieldWithTypes(clazz: Class<*>, keyType: Class<*>, valueType: Class<*>): Field {
        for (field in clazz.declaredFields) {
            // Check if the field is a Map
            if (MutableMap::class.java.isAssignableFrom(field.type)) {
                // Get the generic type of the field
                val genericType = field.genericType
                if (genericType is ParameterizedType) {
                    val actualTypeArguments = genericType.actualTypeArguments
                    // Check if the map's key and value types match the specified classes
                    if (actualTypeArguments.size == 2 && actualTypeArguments[0] == keyType && actualTypeArguments[1] == valueType) {
                        field.isAccessible = true
                        return field
                    }
                }
            }
        }
        throw RuntimeException(
            "Map field with key type " + keyType.simpleName + " and value type " + valueType.simpleName + " not found"
        )
    }

    @Throws(Exception::class)
    fun getFieldValueByType(`object`: Any, simpleClassName: String): Any {
        val publicFields = Stream.of(*`object`.javaClass.fields)
        val declaredFields = Stream.of(*`object`.javaClass.declaredFields)
        val allFields = Stream.concat(publicFields, declaredFields)

        // Find the first field that matches the type name
        val matchingField =
            allFields.filter { declaredField: Field -> declaredField.type.simpleName == simpleClassName }.findFirst()
                .orElseThrow { NoSuchFieldException("Couldn't find field with type " + simpleClassName + " in " + `object`.javaClass) }

        // Make the field accessible and return its value
        matchingField.isAccessible = true
        return matchingField[`object`]
    }

    @JvmStatic
    fun getFieldValue(field: Field, handle: Any?): Any {
        field.isAccessible = true
        try {
            return field[handle]
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun setFieldValue(field: Field, handle: Any?, value: Any?) {
        field.isAccessible = true
        try {
            field[handle] = value
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    fun getConstructor(clazz: Class<*>, numParams: Int): Constructor<*>? {
        return Stream.concat(
            Arrays.stream(clazz.declaredConstructors), Arrays.stream(clazz.constructors)
        ).filter { constructor: Constructor<*> -> constructor.parameterCount == numParams }
            .peek { it: Constructor<*> -> it.setAccessible(true) }.findFirst().orElse(null)
    }

    fun getConstructor(clazz: Class<*>, vararg parameterTypeSimpleNames: String?): Constructor<*>? {
        val getParameterNames = Function { constructor: Constructor<*> ->
            Arrays.stream(constructor.parameters).map { obj: Parameter -> obj.type }
                .map { obj: Class<*> -> obj.simpleName }.collect(Collectors.toList())
        }
        val typeNames = listOf(*parameterTypeSimpleNames)
        return Stream.concat(
            Arrays.stream(clazz.declaredConstructors), Arrays.stream(clazz.constructors)
        ).filter { constructor: Constructor<*> -> getParameterNames.apply(constructor) == typeNames }
            .peek { it: Constructor<*> -> it.setAccessible(true) }.findFirst().orElse(null)
    }

    /**
     * Checks if a given class *somehow* inherits from another class
     *
     * @param toCheck        The class to check
     * @param inheritedClass The inherited class, it should have
     * @return True if `toCheck` somehow inherits from
     * `inheritedClass`
     */
    fun inheritsFrom(toCheck: Class<*>, inheritedClass: Class<*>): Boolean {
        if (inheritedClass.isAssignableFrom(toCheck)) {
            return true
        }

        for (implementedInterface in toCheck.interfaces) {
            if (inheritsFrom(implementedInterface, inheritedClass)) {
                return true
            }
        }

        return false
    }

    fun <T> getUnchecked(supplier: UncheckedReflectionSupplier<T>): T {
        try {
            return supplier.get()
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }
    }

    fun doUnchecked(runnable: UncheckedReflectionRunnable) {
        try {
            runnable.run()
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }
    }

    interface UncheckedReflectionSupplier<T> {
        @Throws(ReflectiveOperationException::class)
        fun get(): T
    }

    interface UncheckedReflectionRunnable {
        @Throws(ReflectiveOperationException::class)
        fun run()
    }
}
