package io.stateset.contact

import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * The family of schemas for [ContactSchema].
 */

object ContactSchema

/**
 * First version of an [ContactSchema] schema.
 */


object ContactSchemaV1 : MappedSchema(ContactSchema.javaClass, 1, listOf(PersistentContact::class.java)) {
    @Entity
    @Table(name = "contact_states", indexes = arrayOf(Index(name = "idx_contact_controller", columnList = "controller"),
            Index(name = "idx_contact_last_name", columnList = "last_name")))
    class PersistentContact(
            @Column(name = "contact_id")
            var contactId: String,

            @Column(name = "first_name")
            var firstName: String,

            @Column(name = "last_name")
            var lastName: String,

            @Column(name = "phone")
            var phone: String,

            @Column(name = "email")
            var email: String,

            @Column(name = "rating")
            var rating: String,

            @Column(name = "contactSource")
            var contactSource: String,

            @Column(name = "contactStatus")
            var contactStatus: String,

            @Column(name = "country")
            var country: String,

            @Column(name = "controller")
            var controller: String,

            @Column(name = "processor")
            var processor: String,

            @Column(name = "active")
            var active: String,

            @Column(name = "createdAt")
            var createdAt: String,

            @Column(name = "lastUpdated")
            var lastUpdated: String,

            @Column(name = "linear_id")
            var linearId: String

    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}