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

package io.stateset.message

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
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

// ************
// * Message State
// ************

@BelongsToContract(MessageContract::class)
data class Message(val id: UniqueIdentifier,
                   val subject: String,
                   val body: String,
                   val fromUserId: String,
                   val to: Party,
                   val from: Party,
                   val toUserId: String,
                   val sentReceipt: Boolean?,
                   val deliveredReceipt: Boolean?,
                   val fromMe: Boolean?,
                   val time: String?,
                   val messageNumber: String,
                   override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState {

    override val participants: List<AbstractParty> get() = listOf(to, from)
    }


    // **********************
    // * Message Contract   *
    // **********************

    class MessageContract : Contract {

        companion object {
            val MESSAGE_CONTRACT_ID = MessageContract::class.java.canonicalName
        }

        interface Commands : CommandData {

            class SendMessage : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: LedgerTransaction) {
            val messageInputs = tx.inputsOfType<Message>()
            val messageOutputs = tx.inputsOfType<Message>()
            val messageCommand = tx.commandsOfType<MessageContract.Commands>().single()

            when (messageCommand.value) {
                is Commands.SendMessage -> requireThat {
                    "no inputs should be consumed" using (messageInputs.isEmpty())
                }
                else -> throw IllegalArgumentException("Unrecognised command.")

            }
        }
    }


    object MessageSchema

    object MessageSchemaV1 : MappedSchema(MessageSchema.javaClass, 1, listOf(PersistentMessages::class.java)) {
        @Entity
        @Table(name = "messages")
        class PersistentMessages(
                @Column(name = "id")
                var id: String = "",
                @Column(name = "subject")
                var subject: String = "",
                @Column(name = "body")
                var body: String = "",
                @Column(name = "fromUserId")
                var fromUserId: String = "",
                @Column(name = "to")
                var to: String = "",
                @Column(name = "from")
                var from: String = "",
                @Column(name = "toUserId")
                var toUserId: String = "",
                @Column(name = "sentReceipt")
                var sentReceipt: String = "",
                @Column(name = "deliveredReceipt")
                var deliveredReceipt: String = "",
                @Column(name = "fromMe")
                var fromMe: String = "",
                @Column(name = "time")
                var time: String = "",
                @Column(name = "messageNumber")
                var messageNumber: String = "",
                @Column(name = "linearId")
                var linearId: String = ""
        ) : PersistentState()
    }