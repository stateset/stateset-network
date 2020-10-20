package io.stateset.product

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


object ProductSchema

/**
 * First version of an [ProductSchema] schema.
 */


object ProductSchemaV1 : MappedSchema(
        schemaFamily = ProductSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentProduct::class.java)) {
    @Entity
    @Table(name = "product_states", indexes = arrayOf(Index(name = "idx_product_party", columnList = "party"),
            Index(name = "idx_product_name", columnList = "name")))
    class PersistentProduct(
            @Column(name = "id")
            var product_id: String,

            @Column(name = "name")
            var name: String,

            @Column(name = "product_url")
            var product_url: String,

            @Column(name = "description")
            var description: String,

            @Column(name = "sku")
            var sku: String,

            @Column(name = "sku_url")
            var sku_url: String,

            @Column(name = "sku_image_url")
            var sku_image_url: String,

            @Column(name = "image_url")
            var image_url: String,

            @Column(name = "ccids")
            var ccids: String,

            @Column(name = "category_breadcrumbs")
            var category_breadcrumbs: String,

            @Column(name = "price")
            var price: String,

            @Column(name = "sale_price")
            var sale_price: String,

            @Column(name = "is_active")
            var is_active: String,

            @Column(name = "stock_quantity")
            var stock_quantity: String,

            @Column(name = "stock_unit")
            var stock_unit: String,

            @Column(name = "brand")
            var brand: String,

            @Column(name = "color")
            var color: String,

            @Column(name = "size")
            var size: String,

            @Column(name = "created_at")
            var createdAt: String,

            @Column(name = "last_updated")
            var lastUpdated: String,

            @Column(name = "party")
            var party: String,

            @Column(name = "counterparty")
            var counterparty: String,

            @Column(name = "linear_id")
            var linearId: String,

            @Column(name = "external_Id")
            var externalId: String
    ) : PersistentState() {
        constructor() : this("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}