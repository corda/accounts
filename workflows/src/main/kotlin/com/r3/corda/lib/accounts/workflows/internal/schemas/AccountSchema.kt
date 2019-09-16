package com.r3.corda.lib.accounts.workflows.internal.schemas

import net.corda.core.schemas.MappedSchema

object AccountSchema

object AccountSchemaV1 : MappedSchema(
        schemaFamily = AccountSchema::class.java,
        version = 1,
        mappedTypes = listOf(AllowedToSeeStateMapping::class.java)
)
