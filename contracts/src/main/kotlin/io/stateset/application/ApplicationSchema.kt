/**
 *   Copyright 2020, Stateset
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

package io.stateset.application

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table


object ApplicationSchema

object ApplicationSchemaV1 : MappedSchema(
        schemaFamily = ApplicationSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentApplication::class.java)) {
    @Entity
    @Table(name = "application_states")
    class PersistentApplication(
            @Column(name = "applicationId")
            var applicationId: String,

            @Column(name = "applicationName")
            var applicationName: String,

            @Column(name = "businessAgeRange")
            var businessAgeRange: String,

            @Column(name = "businessEmail")
            var businessEmail: String,

            @Column(name = "businessPhone")
            var businessPhone: String,

            @Column(name = "businessRevenueRange")
            var businessRevenueRange: String,

            @Column(name = "businessType")
            var businessType: String,

            @Column(name = "estimatedPurchaseAmount")
            var estimatedPurchaseAmount: String,

            @Column(name = "estimatedPurchaseFrequency")
            var estimatedPurchaseFrequency: String,

            @Column(name = "submitted")
            var submitted: String,

            @Column(name = "submittedAt")
            var submittedAt: String,

            @Column(name = "industry")
            var industry: String,

            @Column(name = "applicationStatus")
            var applicationStatus: String,

            @Column(name = "agent")
            var agent: String,

            @Column(name = "provider")
            var provider: String,

            @Column(name = "active")
            var active: String,

            @Column(name = "createdAt")
            var createdAt: String,

            @Column(name = "lastUpdated")
            var lastUpdated: String,

            @Column(name = "linear_id")
            var linearId: String
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
                applicationId = "",
                applicationName = "",
                businessAgeRange = "",
                businessEmail = "",
                businessPhone = "",
                businessRevenueRange= "",
                businessType = "",
                estimatedPurchaseAmount = "",
                estimatedPurchaseFrequency = "",
                submitted = "",
                submittedAt = "",
                industry = "",
                applicationStatus = "",
                agent = "",
                provider = "",
                active = "",
                createdAt = "",
                lastUpdated = "",
                linearId = ""
        )
    }
}
