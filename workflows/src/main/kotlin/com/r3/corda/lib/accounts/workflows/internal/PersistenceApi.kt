package com.r3.corda.lib.accounts.workflows.internal

import net.corda.core.node.ServiceHub
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists [entity] with the node's entity manager without binding to either persistence API.
 *
 * Corda 4.14 and earlier hand the block of [ServiceHub.withEntityManager] a `javax.persistence.EntityManager`, Corda 4.15 a
 * `jakarta.persistence.EntityManager`. A block typed on the `EntityManager` of the Corda the CorDapp is built against
 * (`javax.persistence` for 4.12) casts to that interface in its compiled code. A Corda 4.15 node converts such a class to
 * `jakarta.persistence` as it loads the CorDapp jar, but where the classes are not converted (`MockNetwork`, `MockServices` and
 * in-process driver nodes of a CorDapp tested on Corda 4.15) it fails with `NoClassDefFoundError: javax/persistence/EntityManager`.
 * So the block takes the entity manager as [Any], which compiles to no cast, and calls `persist` through the persistence API interface
 * the object implements.
 */
internal fun ServiceHub.persistWithEitherPersistenceApi(entity: Any) {
    // An (Any) -> Unit is an EntityManager.() -> Unit, but its compiled code does not cast to either EntityManager interface.
    val persist: (Any) -> Unit = { entityManager -> entityManagerPersist.call(entityManager, entity) }
    withEntityManager(persist)
}

/** `EntityManager.persist(Object)`. */
private val entityManagerPersist = PersistenceApiMethod("persist", Any::class.java)

/**
 * A method of the persistence API, called through the `javax.persistence` or `jakarta.persistence` interface that declares it. The
 * interface method is looked up on the first call for each implementation class (the node's entity manager class, which does not
 * change while it runs) and reused after that.
 */
private class PersistenceApiMethod(private val name: String, private vararg val parameterTypes: Class<*>) {
    private val methodByImplementation = ConcurrentHashMap<Class<*>, Method>()

    fun call(target: Any, vararg arguments: Any?): Any? {
        val method = methodByImplementation.computeIfAbsent(target.javaClass, ::findMethod)
        return try {
            method.invoke(target, *arguments)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun findMethod(implementation: Class<*>): Method {
        return persistenceApiInterfaces(implementation).mapNotNull { api ->
            try {
                api.getMethod(name, *parameterTypes)
            } catch (e: NoSuchMethodException) {
                null
            }
        }.firstOrNull() ?: throw IllegalStateException(
                "${implementation.name} implements no persistence API interface with $name(${parameterTypes.joinToString { it.simpleName }})")
    }
}

/** The `javax.persistence` and `jakarta.persistence` interfaces that [type] implements, directly or through its supertypes. */
private fun persistenceApiInterfaces(type: Class<*>): Sequence<Class<*>> {
    val interfaces = LinkedHashSet<Class<*>>()
    fun collect(from: Class<*>) {
        from.interfaces.forEach { if (interfaces.add(it)) collect(it) }
    }
    generateSequence(type) { it.superclass }.forEach(::collect)
    return interfaces.asSequence().filter { it.name.startsWith("javax.persistence.") || it.name.startsWith("jakarta.persistence.") }
}
