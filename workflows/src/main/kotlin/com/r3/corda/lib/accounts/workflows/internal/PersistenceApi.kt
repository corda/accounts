package com.r3.corda.lib.accounts.workflows.internal

import net.corda.core.node.ServiceHub
import java.lang.reflect.InvocationTargetException

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
    val persist: (Any) -> Unit = { entityManager ->
        val api = persistenceApiInterfaces(entityManager.javaClass).firstOrNull { it.simpleName == "EntityManager" }
                ?: throw IllegalStateException("${entityManager.javaClass.name} implements neither javax.persistence.EntityManager nor jakarta.persistence.EntityManager")
        try {
            api.getMethod("persist", Any::class.java).invoke(entityManager, entity)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
    // An (Any) -> Unit is an EntityManager.() -> Unit, but its compiled code does not cast to either EntityManager interface.
    withEntityManager(persist)
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
