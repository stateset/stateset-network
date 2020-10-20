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

package io.stateset.lead

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey


// *********
// * State *
// *********


/**
 * The state object recording CRM assets between two parties.
 *
 * A state must implement [LeadState] or one of its descendants.
 *
 * @Param firstName of the Lead.
 * @Param lastName of the Lead.
 * @param email of the Lead.
 * @param phone of the Lead.
 * @param status of the Lead.
 * @param controller the party who controls the Lead Data.
 * @param processor the party who is processing Lead Data.
 */

@CordaSerializable
@BelongsToContract(LeadContract::class)
data class Lead(val leadId: String,
                val firstName: String,
                val lastName: String,
                val company: String,
                val title: String,
                val email: String,
                val phone: String,
                val rating: LeadRating,
                val leadSource: LeadSource,
                val leadStatus: LeadStatus,
                val salesRegion: SalesRegion,
                val country: String,
                val controller: Party,
                val processor: Party,
                val active: Boolean?,
                val createdAt: String?,
                val lastUpdated: String?,
                override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {


    override val participants: List<Party> get() = listOf(controller, processor)
    fun withNewController(newController: Party) = copy(controller = newController)
    fun withNewProcessor(newProcessor: Party) = copy(processor = newProcessor)

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(LeadSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is LeadSchemaV1 -> LeadSchemaV1.PersistentLead(
                    leadId = this.leadId,
                    firstName = this.firstName,
                    lastName = this.lastName,
                    company = this.company,
                    title = this.title,
                    email = this.email,
                    phone = this.phone,
                    rating = this.rating.toString(),
                    leadSource = this.leadSource.toString(),
                    leadStatus = this.leadStatus.toString(),
                    salesRegion = this.salesRegion.toString(),
                    country = this.country,
                    controller = this.controller.toString(),
                    processor = this.processor.toString(),
                    active = this.active.toString(),
                    createdAt = this.createdAt.toString(),
                    lastUpdated = this.lastUpdated.toString(),
                    linearId = this.linearId.toString()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}

    @CordaSerializable
    enum class LeadStatus {
        NEW, IN_REVIEW, APPROVED, ACCEPTED, ENGAGED, REJECTED, OPEN, WORKING, CONVERTED, UNCONVERTED
    }

    @CordaSerializable
    enum class LeadSource {
        WEBSITE, PARTNER, WEBINAR, FACEBOOK, INSTAGRAM, GOOGLE, ORGANIC, OTHER
    }

    @CordaSerializable
    enum class SalesRegion {
        WEST, EAST, CENTRAL, APAC, EMEA
    }

    @CordaSerializable
    enum class LeadRating {
        LOW, MEDIUM, HIGH
    }

class LeadContract : Contract {
    companion object {
        val LEAD_CONTRACT_ID = LeadContract::class.java.canonicalName
    }

    interface Commands : CommandData {
        class CreateLead : TypeOnlyCommandData(), Commands
        class TransferControllerLead : TypeOnlyCommandData(), Commands
        class TransferProcessorLead : TypeOnlyCommandData(), Commands
        class ShareLead : TypeOnlyCommandData(), Commands
        class UpdateLead : TypeOnlyCommandData(), Commands
        class AcceptLead : TypeOnlyCommandData(), Commands
        class RejectLead : TypeOnlyCommandData(), Commands
        class ReviewLead: TypeOnlyCommandData(), Commands
        class EngageLead : TypeOnlyCommandData(), Commands
        class DeleteLead : TypeOnlyCommandData(), Commands
        class ConvertLead : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.CreateLead -> verifyCreate(tx, setOfSigners)
            is Commands.TransferControllerLead -> verifyTransferController(tx, setOfSigners)
            is Commands.TransferProcessorLead -> verifyTransferProcessor(tx, setOfSigners)
            is Commands.ShareLead -> verifyShare(tx, setOfSigners)
            is Commands.UpdateLead -> verifyUpdate(tx, setOfSigners)
            is Commands.ReviewLead -> verifyReview(tx, setOfSigners)
            is Commands.EngageLead -> verifyEngage(tx, setOfSigners)
            is Commands.AcceptLead -> verifyAccept(tx, setOfSigners)
            is Commands.RejectLead -> verifyReject(tx, setOfSigners)
            is Commands.DeleteLead -> verifyDelete(tx, setOfSigners)
            is Commands.ConvertLead -> verifyConvert(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.isEmpty())
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Account issue transaction." using (output.controller.owningKey in signers)
    }

    private fun verifyTransferController(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val inputLeads = tx.inputsOfType<Lead>()
        "There must be one input Lead." using (inputLeads.size == 1)


        val inputLead = inputLeads.single()
        val outputs = tx.outputsOfType<Lead>()
        "There must be one output Lead." using (outputs.size == 1)

        val output = outputs.single()
        "Must not not change Lead data except controller field value." using (inputLead == output.copy(controller = inputLead.controller))
        "Controller only may sign the Lead Transfer Controller transaction." using (output.controller.owningKey in signers)
    }

    private fun verifyTransferProcessor(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val inputLeads = tx.inputsOfType<Lead>()
        "There must be one input Lead." using (inputLeads.size == 1)


        val inputLead = inputLeads.single()
        val outputs = tx.outputsOfType<Lead>()
        "There must be one output Lead." using (outputs.size == 1)

        val output = outputs.single()
        "Must not not change Lead data except processor field value." using (inputLead == output.copy(processor = inputLead.processor))
        "Only the Processor may sign the Lead Transfer Processor transaction." using (output.processor.owningKey in signers)

    }


    private fun verifyShare(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "Only one Lead should be shared." using (tx.inputStates.size == 1)
        "Only one Lead should be shared." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Controller only may sign the Lead share transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyReview(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Controller only may sign the Lead review" using (output.controller.owningKey in signers)
    }

    private fun verifyAccept(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Controller only may sign the Lead review" using (output.controller.owningKey in signers)
    }

    private fun verifyReject(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Controller only may sign the Lead review" using (output.controller.owningKey in signers)
    }


    private fun verifyEngage(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Controller only may sign the Lead review" using (output.controller.owningKey in signers)
    }


    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Controller only may sign the Lead Update transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyDelete(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "There should be two 3 output states" using (tx.outputStates.size == 0)
        val output = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Convert Lead transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyConvert(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "There should be 1 output states" using (tx.outputStates.size == 1)
        val leadOutput = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Convert Lead transaction." using (leadOutput.controller.owningKey in signers)
    }
}
