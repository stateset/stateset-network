package io.stateset.purchaseorder

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
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table


object PurchaseOrderSchema

/**
 * First version of an [PurchaseOrderSchema] schema.
 */


object PurchaseOrderSchemaV1 : MappedSchema(
        schemaFamily = PurchaseOrderSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentPurchaseOrder::class.java)) {
    @Entity
    @Table(name = "purchaseorder_states", indexes = arrayOf(Index(name = "idx_purchaseorder_purchaser", columnList = "purchaser"),
            Index(name = "idx_purchaseorder_purchase_order_name", columnList = "purchase_order_name")))
    class PersistentPurchaseOrder(
            @Column(name = "purchase_order_number")
            var purchaseOrderNumber: String,

            @Column(name = "purchase_order_name")
            var purchaseOrderName: String,

            @Column(name = "purchase_order_hash")
            var purchaseOrderHash: String,

            @Column(name = "purchase_order_status")
            var purchaseOrderStatus: String,

            @Column(name = "description")
            var description: String,

            @Column(name = "purchase_date")
            var purchaseDate: String,

            @Column(name = "delivery_date")
            var deliveryDate: String,

            @Column(name = "subtotal")
            var subtotal: String,

            @Column(name = "total")
            var total: String,

            @Column(name = "purchaser")
            var purchaser: String,

            @Column(name = "vendor")
            var vendor: String,

            @Column(name = "financer")
            var financer: String,

            @Column(name = "createdAt")
            var createdAt: String,

            @Column(name = "lastUpdated")
            var lastUpdated: String,

            @Column(name = "linear_id")
            var linearId: String,

            @Column(name = "external_Id")
            var externalId: String

    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}