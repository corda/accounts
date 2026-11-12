package net.corda.gold.trading.workflows.schemas

import java.util.UUID

/**
 * Stores a [UUID] as its 36-character string, the same format Hibernate 5's `uuid-char` type used, so that the example
 * no longer needs a Hibernate dependency. One implementation per persistence API, as a class implementing both interfaces
 * could not be loaded on a node that has only one of them on its classpath.
 */
class UUIDConverter : javax.persistence.AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(attribute: UUID?): String? = uuidToDatabaseColumn(attribute)
    override fun convertToEntityAttribute(dbData: String?): UUID? = uuidFromDatabaseColumn(dbData)
}

class JakartaUUIDConverter : jakarta.persistence.AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(attribute: UUID?): String? = uuidToDatabaseColumn(attribute)
    override fun convertToEntityAttribute(dbData: String?): UUID? = uuidFromDatabaseColumn(dbData)
}

private fun uuidToDatabaseColumn(attribute: UUID?): String? = attribute?.toString()

private fun uuidFromDatabaseColumn(dbData: String?): UUID? = dbData?.let(UUID::fromString)
