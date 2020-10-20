package io.stateset.invoice

/**
 *   Copyright 2020, Stateset.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table


object InvoiceSchema

/**
 * First version of an [InvoiceSchema] schema.
 */


object InvoiceSchemaV1 : MappedSchema(
        schemaFamily = InvoiceSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentInvoice::class.java)) {
    @Entity
    @Table(name = "invoice_states", indexes = arrayOf(Index(name = "idx_invoice_party", columnList = "party"),
            Index(name = "idx_invoice_invoice_name", columnList = "invoice_name")))
    class PersistentInvoice(
            @Column(name = "invoice_number")
            var invoiceNumber: String,

            @Column(name = "invoice_name")
            var invoiceName: String,

            @Column(name = "billing_eason")
            var billingReason: String,

            @Column(name = "amount_due")
            var amountDue: String,

            @Column(name = "amount_paid")
            var amountPaid: String,

            @Column(name = "amount_remaining")
            var amountRemaining: String,

            @Column(name = "subtotal")
            var subtotal: String,

            @Column(name = "total")
            var total: String,

            @Column(name = "party")
            var party: String,

            @Column(name = "counterparty")
            var counterparty: String,

            @Column(name = "due_date")
            var dueDate: String,

            @Column(name = "period_start_date")
            var periodStartDate: String,

            @Column(name = "period_end_date")
            var periodEndDate: String,

            @Column(name = "paid")
            var paid: String,

            @Column(name = "active")
            var active: String,

            @Column(name = "created_at")
            var createdAt: String,

            @Column(name = "last_updated")
            var lastUpdated: String,

            @Column(name = "linear_id")
            var linearId: String,

            @Column(name = "external_Id")
            var externalId: String
    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}