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

package io.stateset.proposal

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table


object ProposalSchema

/**
 * First version of an [ProposalSchema] schema.
 */


object ProposalSchemaV1 : MappedSchema(
        schemaFamily = ProposalSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentProposal::class.java)) {
    @Entity
    @Table(name = "proposal_states", indexes = arrayOf(Index(name = "idx_proposal_party", columnList = "party"),
            Index(name = "idx_proposal_proposal_name", columnList = "proposal_name")))
    class PersistentProposal(
            @Column(name = "proposal_number")
            var proposalNumber: String,

            @Column(name = "proposal_name")
            var proposalName: String,

            @Column(name = "proposal_hash")
            var proposalHash: String,

            @Column(name = "proposal_status")
            var proposalStatus: String,

            @Column(name = "proposal_type")
            var proposalType: String,

            @Column(name = "total_proposal_value")
            var totalProposalValue: String,

            @Column(name = "party")
            var party: String,

            @Column(name = "counterparty")
            var counterparty: String,

            @Column(name = "proposal_startdate")
            var proposalStartDate: String,

            @Column(name = "proposal_enddate")
            var proposalEndDate: String,

            @Column(name = "active")
            var active: String,

            @Column(name = "createdAt")
            var createdAt: String,

            @Column(name = "lastUpdated")
            var lastUpdated: String,

            @Column(name = "linear_id")
            var linearId: String

    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}

object ProposalSchemaV2 : MappedSchema(
        schemaFamily = ProposalSchema.javaClass,
        version = 2,
        mappedTypes = listOf(PersistentProposal::class.java)) {
    @Entity
    @Table(name = "proposal_states2", indexes = arrayOf(Index(name = "idx_proposal_party", columnList = "party"),
            Index(name = "idx_proposal_proposalName", columnList = "proposalName")))
    class PersistentProposal(
            @Column(name = "proposalNumber")
            var proposalNumber: String,

            @Column(name = "proposalName")
            var proposalName: String,

            @Column(name = "proposalHash")
            var proposalHash: String,

            @Column(name = "proposalStatus")
            var proposalStatus: String,

            @Column(name = "proposalType")
            var proposalType: String,

            @Column(name = "totalProposalValue")
            var totalProposalValue: String,

            @Column(name = "party")
            var party: String,

            @Column(name = "counterparty")
            var counterparty: String,

            @Column(name = "proposalStartDate")
            var proposalStartDate: String,

            @Column(name = "proposalEndDate")
            var proposalEndDate: String,

            @Column(name = "active")
            var active: String,

            @Column(name = "createdAt")
            var createdAt: String,

            @Column(name = "lastUpdated")
            var lastUpdated: String,

            @Column(name = "linear_id")
            var linearId: String,

            @Column(name = "external_Id")
            var externalId: String
    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    }
}