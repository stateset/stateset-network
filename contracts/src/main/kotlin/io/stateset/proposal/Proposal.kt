
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

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.toBase58String


// *****************
// * Proposal State *
// *****************

@BelongsToContract(ProposalContract::class)
data class Proposal(val proposalNumber: String,
                    val proposalName: String,
                    val proposalHash: String,
                    val proposalStatus: ProposalStatus,
                    val proposalType: ProposalType,
                    val totalProposalValue: Int,
                    val party: Party,
                    val counterparty: Party,
                    val proposalStartDate: String,
                    val proposalEndDate: String,
                    val active: Boolean?,
                    val createdAt: String?,
                    val lastUpdated: String?,
                    override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {

    override val participants: List<AbstractParty> get() = listOf(party, counterparty)

    override fun toString(): String {
        val partyString = (party as? Party)?.name?.organisation ?: party.owningKey.toBase58String()
        val counterpartyString = (counterparty as? Party)?.name?.organisation ?: counterparty.owningKey.toBase58String()
        return "Proposal ($linearId): $counterpartyString has sent a proposal to $partyString for $totalProposalValue and the current status is $proposalStatus."
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ProposalSchemaV1 -> ProposalSchemaV1.PersistentProposal(
                    proposalNumber = this.proposalNumber,
                    proposalName = this.proposalName,
                    proposalHash = this.proposalHash,
                    proposalStatus = this.proposalStatus.toString(),
                    proposalType = this.proposalType.toString(),
                    totalProposalValue = this.totalProposalValue.toString(),
                    party = this.party.name.toString(),
                    counterparty = this.counterparty.name.toString(),
                    proposalStartDate = this.proposalStartDate,
                    proposalEndDate = this.proposalEndDate,
                    active = this.active.toString(),
                    createdAt = this.createdAt.toString(),
                    lastUpdated = this.lastUpdated.toString(),
                    linearId = this.linearId.id.toString()

            )
            is ProposalSchemaV2 -> ProposalSchemaV2.PersistentProposal (
                    proposalNumber = this.proposalNumber,
                    proposalName = this.proposalName,
                    proposalHash = this.proposalHash,
                    proposalStatus = this.proposalStatus.toString(),
                    proposalType = this.proposalType.toString(),
                    totalProposalValue = this.totalProposalValue.toString(),
                    party = this.party.name.toString(),
                    counterparty = this.counterparty.name.toString(),
                    proposalStartDate = this.proposalStartDate,
                    proposalEndDate = this.proposalEndDate,
                    active = this.active.toString(),
                    createdAt = this.createdAt.toString(),
                    lastUpdated = this.lastUpdated.toString(),
                    linearId = this.linearId.id.toString(),
                    externalId = this.linearId.id.toString()

            )
            else -> throw IllegalArgumentException("Unrecognized schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ProposalSchemaV1, ProposalSchemaV2)
}

@CordaSerializable
enum class ProposalStatus {
    REQUEST, APPROVAL_REQUIRED, APPROVED, IN_REVIEW, ACCEPTED, REJECTED
}



@CordaSerializable
enum class ProposalType {
    NDA, MSA, SLA, SOW
}



// **********************
// * Proposal Contract *
// **********************

class ProposalContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        val PROPOSAL_CONTRACT_ID = ProposalContract::class.java.canonicalName
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {

        class CreateProposal : TypeOnlyCommandData(), Commands
        class ReviewProposal : TypeOnlyCommandData(),Commands
        class AcceptProposal : TypeOnlyCommandData(),Commands
        class RejectProposal : TypeOnlyCommandData(),Commands


    }


    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val proposalInputs = tx.inputsOfType<Proposal>()
        val proposalOutputs = tx.outputsOfType<Proposal>()
        val proposalCommand = tx.commandsOfType<ProposalContract.Commands>().single()

        when(proposalCommand.value) {
            is Commands.CreateProposal -> requireThat {
                "no inputs should be consumed" using (proposalInputs.isEmpty())
                // TODO we might allow several jobs to be proposed at once later
                "one output should be produced" using (proposalOutputs.size == 1)

                val proposalOutput = proposalOutputs.single()
                "the party should be different to the counterparty" using (proposalOutput.party != proposalOutput.counterparty)
                "the status should be set as request" using (proposalOutput.proposalStatus == ProposalStatus.REQUEST)

                "the party and counterparty are required signers" using
                        (proposalCommand.signers.containsAll(listOf(proposalOutput.party.owningKey, proposalOutput.counterparty.owningKey)))
            }

            is Commands.ReviewProposal -> requireThat {
                "one input should be consumed" using (proposalInputs.size == 1)
                "one output should bbe produced" using (proposalOutputs.size == 1)

                val proposalInput = proposalInputs.single()
                val proposalOutput = proposalOutputs.single()
                "the status should be set to request" using (proposalOutput.proposalStatus == ProposalStatus.REQUEST)
                "the previous status should not be IN_REVIEW" using (proposalInput.proposalStatus != ProposalStatus.IN_REVIEW)

            }

            is Commands.AcceptProposal -> requireThat {
                "one input should be produced" using (proposalInputs.size == 1)
                "one output should be produced" using (proposalOutputs.size == 1)

                val proposalInput = proposalInputs.single()
                val proposalOutput = proposalOutputs.single()

                "the input status must be set as Request" using (proposalInput.proposalStatus == ProposalStatus.REQUEST)
                "the output status should be set as Activated" using (proposalOutput.proposalStatus == ProposalStatus.ACCEPTED)
            }


            is Commands.RejectProposal -> requireThat {
                "one input should be produced" using (proposalInputs.size == 1)
                "one output should be produced" using (proposalOutputs.size == 1)

                val proposalInput = proposalInputs.single()
                val proposalOutput = proposalOutputs.single()

                "the input status must be set as in effect" using (proposalInput.proposalStatus == ProposalStatus.REQUEST)
                "the output status should be set as renewed" using (proposalOutput.proposalStatus ==ProposalStatus.REJECTED)


            }

            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

}