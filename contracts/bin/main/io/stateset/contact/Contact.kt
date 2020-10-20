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

package io.stateset.contact

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey



/**
 * The state object recording CRM assets between two parties.
 *
 * A state must implement [Contact] or one of its descendants.
 *
 * @Param contactId of the Contact.
 * @Param firstName of the Contact.
 * @Param lastName of the Contact.
 * @param email of the Contact.
 * @param phone of the Contact.
 * @param owner the party who owns the Contact.
 */

@CordaSerializable
@BelongsToContract(ContactContract::class)
data class Contact(val contactId: String,
                   val firstName: String,
                   val lastName: String,
                   val email: String,
                   val phone: String,
                   val controller: Party,
                   val processor: Party,
                   override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {

    override val participants: List<AbstractParty> get() = listOf(controller, processor)

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ContactSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ContactSchemaV1 -> ContactSchemaV1.PersistentContact(
                    contactId = this.contactId,
                    firstName = this.firstName,
                    lastName = this.lastName,
                    email = this.email,
                    phone = this.phone,
                    controller = this.controller.toString(),
                    processor = this.processor.toString(),
                    linearId = this.linearId.toString()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}

class ContactContract : Contract {
    companion object {
        val CONTACT_CONTRACT_ID = ContactContract::class.java.canonicalName
    }

    interface Commands : CommandData {
        class CreateContact : TypeOnlyCommandData(), Commands
        class TransferContact : TypeOnlyCommandData(), Commands
        class ShareContact : TypeOnlyCommandData(), Commands
        class UpdateContact : TypeOnlyCommandData(), Commands
        class DeleteContact : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.CreateContact -> verifyCreate(tx, setOfSigners)
            is Commands.TransferContact -> verifyTransfer(tx, setOfSigners)
            is Commands.ShareContact -> verifyShare(tx, setOfSigners)
            is Commands.UpdateContact -> verifyUpdate(tx, setOfSigners)
            is Commands.DeleteContact -> verifyDelete(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.isEmpty())
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Contact>().single()
        "Owner only may sign the Contact issue transaction." using (output.controller.owningKey in signers)
    }

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val inputContacts = tx.inputsOfType<Contact>()
        //val inputContactTransfers = tx.inputsOfType<ContactTransfer>()
        "There must be one input Contact." using (inputContacts.size == 1)


        val inputContact = inputContacts.single()
        val outputs = tx.outputsOfType<Contact>()
        // If the obligation has been partially settled then it should still exist.
        "There must be one output Contact." using (outputs.size == 1)

        // Check only the paid property changes.
        val output = outputs.single()
        "Must not not change Contact data except processor field value." using (inputContact == output.copy(controller = inputContact.controller))
        "Owner only may sign the Contact transfer transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyShare(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "Only one Account should be shared." using (tx.inputStates.size == 1)
        "Only one Account should be shared." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Contact>().single()
        "Owner only may sign the Account share transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "Only one out state should be created." using (tx.outputStates.size == 1)
        val output = tx.outputsOfType<Contact>().single()
        "Owner only may sign the Account issue transaction." using (output.controller.owningKey in signers)
    }


    private fun verifyDelete(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs must be consumed." using (tx.inputStates.size == 1)
        "There should be no output state" using (tx.outputStates.size == 0)
        val output = tx.outputsOfType<Contact>().single()
        "Owner only may sign the Account issue transaction." using (output.controller.owningKey in signers)
    }
}