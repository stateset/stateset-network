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

package io.stateset.approval

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table


object ApprovalSchema

object ApprovalSchemaV1 : MappedSchema(
        schemaFamily = ApprovalSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentApproval::class.java)) {
    @Entity
    @Table(name = "approval_states")
    class PersistentApproval(
            @Column(name = "approval_id")
            var approvalId: String,

            @Column(name = "approval_name")
            var approvalName: String,

            @Column(name = "description")
            var description: String,

            @Column(name = "industry")
            var industry: String,

            @Column(name = "approval_status")
            var approvalStatus: String,

            @Column(name = "submitter")
            var submitter: String,

            @Column(name = "approver")
            var approver: String,

            @Column(name = "linear_id")
            var linearId: String
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(
                approvalId = "",
                approvalName = "",
                description = "",
                industry = "",
                approvalStatus = "",
                submitter = "",
                approver = "",
                linearId = ""
        )
    }
}