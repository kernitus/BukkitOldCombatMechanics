/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import java.lang.reflect.*

object Reflector {
    fun getClass(fqn: String): Class<*> {
        try {
            return Class.forName(fqn)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Couldn't load class $fqn", e)
        }
    }

    private fun getParameterNames(method: Method): List<String> = method.parameters.map { it.type.simpleName }

    fun getMethod(clazz: Class<*>, name: String): Method? = clazz.methods.firstOrNull { it.name == name }

    fun getMethod(clazz: Class<*>, name: String, parameterCount: Int): Method? =
        clazz.methods.firstOrNull { it.name == name && it.parameterCount == parameterCount }

    fun getMethod(clazz: Class<*>, returnType: Class<*>, vararg parameterTypeSimpleNames: String): Method? {
        val typeNames = parameterTypeSimpleNames.toList()
        return clazz.methods.firstOrNull { method ->
            method.returnType == returnType && getParameterNames(method) == typeNames
        }
    }

    fun getMethod(clazz: Class<*>, name: String, vararg parameterTypeSimpleNames: String): Method? {
        val typeNames = parameterTypeSimpleNames.toList()
        val allMethods = (clazz.declaredMethods.asSequence() + clazz.methods.asSequence()).distinct()
        return allMethods.firstOrNull { method ->
            method.name == name && getParameterNames(method) == typeNames
        }?.apply {
            isAccessible = true
        }
    }

    fun getMethodByReturnAndParamTypes(
        clazz: Class<*>, returnType: Class<*>, vararg parameterTypes: Class<*>
    ): Method? {
        val allMethods = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
        return allMethods.firstOrNull { method ->
            method.returnType == returnType && method.parameterTypes.contentEquals(parameterTypes)
        }?.also { it.isAccessible = true }
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
        clazz: Class<T>, name: String, vararg argTypes: String
    ): (T, U) -> R {
        val method = getMethod(clazz, name, *argTypes)
        return { t: T, u: U ->
            // If they did not want to send any arguments, should be zero-length array
            // This check is necessary cause of varargs, otherwise we get 1 length array of 0-length array
            if (u is Array<*> && u.isArrayOf<Any>() && u.isEmpty()) invokeMethod<R>(method!!, t)
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

    fun getFieldValueByType(obj: Any, simpleClassName: String): Any {
        val clazz = obj.javaClass
        val allFields = clazz.fields + clazz.declaredFields
        val matchingField = allFields.firstOrNull { field ->
            field.type.simpleName == simpleClassName
        } ?: throw NoSuchFieldException("Couldn't find field with type $simpleClassName in ${clazz.name}")

        matchingField.isAccessible = true
        return matchingField.get(obj)
    }

    fun getFieldValue(field: Field, handle: Any?): Any {
        field.isAccessible = true
        try {
            return field[handle]
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    fun setFieldValue(field: Field, handle: Any?, value: Any?) {
        field.isAccessible = true
        try {
            field[handle] = value
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    private fun getParameterNames(constructor: Constructor<*>): List<String> =
        constructor.parameters.map { it.type.simpleName }

    fun getConstructor(clazz: Class<*>, numParams: Int): Constructor<*>? =
        (clazz.declaredConstructors.asSequence() + clazz.constructors.asSequence()).firstOrNull { constructor ->
            constructor.parameterCount == numParams
        }?.also { it.isAccessible = true }

    fun getConstructor(clazz: Class<*>, vararg parameterTypeSimpleNames: String): Constructor<*>? {
        val typeNames = parameterTypeSimpleNames.toList()
        return (clazz.declaredConstructors.asSequence() + clazz.constructors.asSequence()).firstOrNull { constructor ->
            getParameterNames(constructor) == typeNames
        }?.also { it.isAccessible = true }
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
        fun get(): T
    }

    interface UncheckedReflectionRunnable {
        fun run()
    }
}
