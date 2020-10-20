package io.stateset.purchaseorder

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


import io.stateset.invoice.InvoiceContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.toBase58String
import java.lang.Boolean.TRUE
import java.lang.IllegalArgumentException
import java.util.*

// ************************
// * Purchase Order State *
// ************************

@BelongsToContract(PurchaseOrderContract::class)
data class PurchaseOrder(val purchaseOrderNumber: String,
                         val purchaseOrderName: String,
                         val purchaseOrderHash: String,
                         val purchaseOrderStatus: PurchaseOrderStatus,
                         val description: String,
                         val purchaseDate: String,
                         val deliveryDate: String,
                         val subtotal: Int,
                         val total: Int,
                         val purchaser: Party,
                         val vendor: Party,
                         val financer: Party,
                         val createdAt: String?,
                         val lastUpdated: String?,
                         override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {

    override val participants: List<Party> get() = listOf(purchaser, vendor, financer)
    fun withNewVendor(newVendor: Party) = copy(vendor = newVendor)
    fun withNewFinancer(newFinancer: Party) = copy(financer = newFinancer)

    override fun toString(): String {
        val purchaserString = (purchaser as? Party)?.name?.organisation ?: purchaser.owningKey.toBase58String()
        val vendorString = (vendor as? Party)?.name?.organisation ?: vendor.owningKey.toBase58String()
        val financerString = (financer as? Party)?.name?.organisation ?: financer.owningKey.toBase58String()
        return "PO ($linearId): $vendorString has an received a Purchase Order from $purchaserString for $total."
    }


    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is PurchaseOrderSchemaV1 -> PurchaseOrderSchemaV1.PersistentPurchaseOrder(
                    purchaseOrderNumber = this.purchaseOrderNumber,
                    purchaseOrderName = this.purchaseOrderName,
                    purchaseOrderHash = this.purchaseOrderHash,
                    purchaseOrderStatus = this.purchaseOrderStatus.toString(),
                    description = this.description,
                    purchaseDate = this.purchaseDate,
                    deliveryDate = this.deliveryDate,
                    subtotal = this.subtotal.toString(),
                    total = this.total.toString(),
                    purchaser = this.purchaser.toString(),
                    vendor = this.vendor.toString(),
                    financer = this.financer.toString(),
                    createdAt = this.createdAt.toString(),
                    lastUpdated = this.lastUpdated.toString(),
                    linearId = this.linearId.id.toString(),
                    externalId = this.linearId.id.toString()

            )
            else -> throw IllegalArgumentException("Unrecognized schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(PurchaseOrderSchemaV1)
}

@CordaSerializable
enum class PurchaseOrderStatus {
    REQUEST, APPROVAL_REQUIRED, APPROVED, IN_REVIEW, PENDING, ACCEPTED, REJECTED, COMPLETE, RECEIVED, CANCELLED
}


// **********************
// * Purchase Order Contract *
// **********************

class PurchaseOrderContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        val PURCHASE_ORDER_CONTRACT_ID = PurchaseOrderContract::class.java.canonicalName
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {

        class CreatePurchaseOrder : TypeOnlyCommandData(), Commands
        class ReviewPurchaseOrder : TypeOnlyCommandData(),Commands
        class FinancePurchaseOrder: TypeOnlyCommandData(), Commands
        class CompletePurchaseOrder : TypeOnlyCommandData(),Commands
        class CancelPurchaseOrder : TypeOnlyCommandData(),Commands


    }


    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val purchaseOrderInputs = tx.inputsOfType<PurchaseOrder>()
        val purchaseOrderOutputs = tx.outputsOfType<PurchaseOrder>()
        val purchaseOrderCommand = tx.commandsOfType<PurchaseOrderContract.Commands>().single()

        when(purchaseOrderCommand.value) {
            is Commands.CreatePurchaseOrder -> requireThat {
                "no inputs should be consumed" using (purchaseOrderInputs.isEmpty())
                // TODO we might allow several jobs to be proposed at once later
                "one output should be produced" using (purchaseOrderOutputs.size == 1)

                val purchaseOrderOutput = purchaseOrderOutputs.single()
                "the purchaser should be different to the vendor" using (purchaseOrderOutput.purchaser != purchaseOrderOutput.vendor)
                "the status should be set as request" using (purchaseOrderOutput.purchaseOrderStatus == PurchaseOrderStatus.REQUEST)

                "the purchaser and vendor are required signers" using
                        (purchaseOrderCommand.signers.containsAll(listOf(purchaseOrderOutput.vendor.owningKey, purchaseOrderOutput.vendor.owningKey)))
            }

            is Commands.ReviewPurchaseOrder -> requireThat {
                "one input should be consumed" using (purchaseOrderInputs.size == 1)
                "one output should bbe produced" using (purchaseOrderOutputs.size == 1)

                val purchaseOrderInput = purchaseOrderInputs.single()
                val purchaseOrderOutput = purchaseOrderOutputs.single()
                "the status should be set to request" using (purchaseOrderOutput.purchaseOrderStatus == PurchaseOrderStatus.REQUEST)
                "the previous status should not be IN_REVIEW" using (purchaseOrderInput.purchaseOrderStatus != PurchaseOrderStatus.IN_REVIEW)

            }

            is Commands.CompletePurchaseOrder -> requireThat {
                "one input should be produced" using (purchaseOrderInputs.size == 1)
                "one output should be produced" using (purchaseOrderOutputs.size == 1)

                val purchaseOrderInput = purchaseOrderInputs.single()
                val purchaseOrderOutput = purchaseOrderOutputs.single()

                "the input status must be set as Request" using (purchaseOrderInput.purchaseOrderStatus == PurchaseOrderStatus.REQUEST)
                "the output status should be set as Activated" using (purchaseOrderOutput.purchaseOrderStatus == PurchaseOrderStatus.COMPLETE)
            }


            is Commands.CancelPurchaseOrder -> requireThat {
                "one input should be produced" using (purchaseOrderInputs.size == 1)
                "one output should be produced" using (purchaseOrderOutputs.size == 1)

                val purchaseOrderInput = purchaseOrderInputs.single()
                val purchaseOrderOutput = purchaseOrderOutputs.single()

                "the input status must be set as in effect" using (purchaseOrderInput.purchaseOrderStatus == PurchaseOrderStatus.REQUEST)
                "the output status should be set as renewed" using (purchaseOrderOutput.purchaseOrderStatus ==PurchaseOrderStatus.CANCELLED)


            }

            is Commands.FinancePurchaseOrder -> requireThat {
                "one input should be produced" using (purchaseOrderInputs.size == 1)
                "one output should be produced" using (purchaseOrderOutputs.size == 1)

                val purchaseOrderOutput = purchaseOrderOutputs.single()

            }

            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

}