package io.stateset.lead

import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

// *********
// * Schema *
// *********


object LeadSchema

/**
 * First version of an [LeadSchema] schema.
 */


object LeadSchemaV1 : MappedSchema(schemaFamily = LeadSchema.javaClass, version = 1, mappedTypes = listOf(PersistentLead::class.java)) {
    @Entity
    @Table(name = "lead_states", indexes = arrayOf(Index(name = "idx_lead_controller", columnList = "controller"),
            Index(name = "idx_lead_last_name", columnList = "last_name")))
    class PersistentLead(
            @Column(name = "lead_id")
            var leadId: String,

            @Column(name = "first_name")
            var firstName: String,

            @Column(name = "last_name")
            var lastName: String,

            @Column(name = "company")
            var company: String,

            @Column(name = "title")
            var title: String,

            @Column(name = "email")
            var email: String,

            @Column(name = "phone")
            var phone: String,

            @Column(name = "country")
            var country: String,

            @Column(name = "controller")
            var controller: String,

            @Column(name = "processor")
            var processor: String,

            @Column(name = "linear_id")
            var linearId: String


    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "")
    }
}