package io.javalin.core.util

import io.javalin.apibuilder.CrudFunctionHandler
import java.lang.reflect.Field
import java.lang.reflect.Method

internal val Any.kotlinFieldName // this is most likely a very stupid solution
    get() = this.javaClass.toString().removePrefix(this.parentClass.toString() + "$").takeWhile { it != '$' }

internal val Any.javaFieldName: String?
    get() = try {
        parentClass.declaredFields.find { it.isAccessible = true; it.get(it) == this }?.name
    } catch (ignored: Exception) { // Nothing really matters.
        null
    }

internal val Any.methodName: String? // broken in jdk9+ since ConstantPool has been removed
    get() {
//        val constantPool = Class::class.java.getDeclaredMethod("getConstantPool").apply { isAccessible = true }.invoke(javaClass) as ConstantPool
//        for (i in constantPool.size downTo 0) {
//            try {
//                val name = constantPool.getMemberRefInfoAt(i)[1]
//                // Autogenerated ($), constructor, or kotlin's check (fix maybe?)
//                if (name.contains("(\\$|<init>|checkParameterIsNotNull)".toRegex())) {
//                    continue
//                } else {
//                    return name
//                }
//            } catch (ignored: Exception) {
//            }
//        }
        return null
    }
internal val Any.parentClass: Class<*> get() = Class.forName(this.javaClass.name.takeWhile { it != '$' }, false, this.javaClass.classLoader)

internal val Any.implementingClassName: String? get() = this.javaClass.name

internal val Any.isClass: Boolean get() = this is Class<*>

internal val Any.isKotlinAnonymousLambda: Boolean get() = this.javaClass.enclosingMethod != null

internal val Any.isCrudFunction: Boolean get() = (this is CrudFunctionHandler)

internal val Any.isKotlinMethodReference: Boolean get() = this.javaClass.declaredFields.count { it.name == "function" || it.name == "\$tmp0" } == 1

internal val Any.isKotlinField: Boolean get() = this.javaClass.fields.any { it.name == "INSTANCE" }

internal val Any.isJavaAnonymousClass: Boolean get() = this.javaClass.isAnonymousClass

internal val Any.isJavaMemberClass: Boolean get() = this.javaClass.isMemberClass

internal val Any.isJavaAnonymousLambda: Boolean get() = this.javaClass.isSynthetic

internal val Any.hasMethodName: Boolean get() = methodName != null

internal val Any.isJavaNonStaticMethodReference: Boolean get() = javaClass.declaredMethods.any { it.name == methodReferenceReflectionMethodName }

internal val Any.isJavaField: Boolean get() = this.javaFieldName != null

internal fun Any.runMethod(name: String): Any = this.javaClass.getMethod(name).apply { isAccessible = true }.invoke(this)

internal val Any.lambdaField: Field?
    get() = when {
        isKotlinField -> parentClass.getDeclaredFieldByName(kotlinFieldName)
        isJavaField -> parentClass.getDeclaredFieldByName(javaFieldName!!)
        else -> null
    }

internal fun Any.getFieldValue(fieldName: String): Any {
    val field = this::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this)
}

internal fun Class<*>.getMethodByName(methodName: String): Method? {
    val isName = { method: Method -> method.name == methodName }
    return declaredMethods.find(isName) ?: methods.find(isName)
}

internal fun Class<*>.getDeclaredFieldByName(methodName: String): Field? = declaredFields
    .find { it.name == methodName }

internal val Class<*>.methodsNotDeclaredByObject
    get(): Array<Method> = (declaredMethods + methods)
        .toSet()
        .filter { it.declaringClass != Object::class.java }
        .toTypedArray()

const val methodReferenceReflectionMethodName = "get\$Lambda"

/**
 * Allow other modules to use the reflection utils without pollute Any.* for lib users
 */
class Reflection(val obj: Any) {
    companion object {
        /**
         * Short name for convenience
         */
        fun rfl(obj: Any) = Reflection(obj)

        private fun classOf(obj: Any): Class<*> = if (obj is Class<*>) obj else obj.javaClass
    }

    val kotlinFieldName: String get() = obj.kotlinFieldName
    val javaFieldName: String? get() = obj.javaFieldName
    val parentClass: Class<*> get() = obj.parentClass
    val implementingClassName: String? get() = obj.implementingClassName
    val isClass: Boolean get() = obj is Class<*>
    val isKotlinAnonymousLambda: Boolean get() = obj.isKotlinAnonymousLambda
    val isKotlinMethodReference: Boolean get() = obj.isKotlinMethodReference
    val isKotlinField: Boolean get() = obj.isKotlinField
    val isJavaAnonymousClass: Boolean get() = obj.isJavaAnonymousClass
    val isJavaAnonymousLambda: Boolean get() = obj.isJavaAnonymousLambda
    val isJavaMemberClass: Boolean get() = obj.isJavaMemberClass
    val hasMethodName: Boolean get() = obj.hasMethodName
    val isJavaNonStaticMethodReference: Boolean get() = obj.isJavaNonStaticMethodReference
    val isJavaField: Boolean get() = obj.isJavaField
    val lambdaField: Field? get() = obj.lambdaField
    val methodsNotDeclaredByObject: Array<Method> get() = classOf(obj).methodsNotDeclaredByObject
    fun runMethod(name: String) = obj.runMethod(name)
    fun getFieldValue(fieldName: String) = obj.getFieldValue(fieldName)
    fun getMethodByName(methodName: String) = classOf(obj).getMethodByName(methodName)
    fun getDeclaredFieldByName(methodName: String) = classOf(obj).getDeclaredFieldByName(methodName)
}
