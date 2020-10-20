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

            @Column(name = "industry")
            var industry: String,

            @Column(name = "applicationStatus")
            var applicationStatus: String,

            @Column(name = "agent")
            var agent: String,

            @Column(name = "provider")
            var provider: String,

            @Column(name = "linearId")
            var linearId: String
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
                applicationId = "",
                applicationName = "",
                industry = "",
                applicationStatus = "",
                agent = "",
                provider = "",
                linearId = ""
        )
    }
}
