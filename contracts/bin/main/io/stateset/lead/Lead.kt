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
                val country: String,
                val controller: Party,
                val processor: Party,
                override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {


    override val participants: List<AbstractParty> get() = listOf(controller, processor)

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
                    country = this.country,
                    controller = this.controller.toString(),
                    processor = this.processor.toString(),
                    linearId = this.linearId.toString()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }


    @CordaSerializable
    enum class LeadStatus {
        OPEN,
        WORKING,
        CLOSED_CONVERTED,
        CLOSED_NONCONVERTED
    }

}



class LeadContract : Contract {
    companion object {
        val LEAD_CONTRACT_ID = LeadContract::class.java.canonicalName
    }

    interface Commands : CommandData {
        class CreateLead : TypeOnlyCommandData(), Commands
        class TransferLead : TypeOnlyCommandData(), Commands
        class ShareLead : TypeOnlyCommandData(), Commands
        class UpdateLead : TypeOnlyCommandData(), Commands
        class DeleteLead : TypeOnlyCommandData(), Commands
        class ConvertLead : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.CreateLead -> verifyCreate(tx, setOfSigners)
            is Commands.TransferLead -> verifyTransfer(tx, setOfSigners)
            is Commands.ShareLead -> verifyShare(tx, setOfSigners)
            is Commands.UpdateLead -> verifyUpdate(tx, setOfSigners)
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

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val inputLeads = tx.inputsOfType<Lead>()
        "There must be one input obligation." using (inputLeads.size == 1)


        val inputLead = inputLeads.single()
        val outputs = tx.outputsOfType<Lead>()
        // If the obligation has been partially settled then it should still exist.
        "There must be one output Lead." using (outputs.size == 1)

        // Check only the paid property changes.
        val output = outputs.single()
        "Must not not change Lead data except owner field value." using (inputLead == output.copy(controller = inputLead.controller))
        "Owner only may sign the Lead issue transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyShare(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "Only one Account should be shared." using (tx.inputStates.size == 1)
        "Only one Account should be shared." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Account share transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Account issue transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyDelete(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "There should be two 3 output states" using (tx.outputStates.size == 0)
        val output = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Convert Lead transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyConvert(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "There should be two 3 output states" using (tx.outputStates.size == 3)
        val leadOutput = tx.outputsOfType<Lead>().single()
        "Owner only may sign the Convert Lead transaction." using (leadOutput.controller.owningKey in signers)
    }
}
