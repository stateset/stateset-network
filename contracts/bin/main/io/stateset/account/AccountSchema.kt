package io.stateset.account

import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * The family of schemas for [AccountSchema].
 */

object AccountSchema

/**
 * First version of an [AccountSchema] schema.
 */


object AccountSchemaV1 : MappedSchema(AccountSchema.javaClass, 1, listOf(PersistentAccount::class.java)) {
    @Entity
    @Table(name = "account_states", indexes = arrayOf(Index(name = "idx_account_controller", columnList = "controller"),
            Index(name = "idx_account_accountName", columnList = "account_name")))
    class PersistentAccount(
            @Column(name = "account_id")
            var accountId: String,

            @Column(name = "account_name")
            var accountName: String,

            @Column(name = "account_type")
            var accountType: String,

            @Column(name = "industry")
            var industry: String,

            @Column(name = "phone")
            var phone: String,

            @Column(name = "year_started")
            var yearStarted: String,

            @Column(name = "annual_revenue")
            var annualRevenue: String,

            @Column(name = "business_address")
            var businessAddress: String,

            @Column(name = "business_city")
            var businessCity: String,

            @Column(name = "business_state")
            var businessState: String,

            @Column(name = "business_zipcode")
            var businessZipCode: String,

            @Column(name = "controller")
            var controller: String,

            @Column(name = "processor")
            var processor: String

    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "")
    }

}
