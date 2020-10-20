package io.stateset

import co.paralleluniverse.fibers.Suspendable
import io.stateset.account.*
import io.stateset.account.AccountContract.Companion.ACCOUNT_CONTRACT_ID
import io.stateset.agreement.Agreement
import io.stateset.agreement.AgreementContract
import io.stateset.agreement.AgreementContract.Companion.AGREEMENT_CONTRACT_ID
import io.stateset.agreement.AgreementStatus
import io.stateset.agreement.AgreementType
import io.stateset.application.*
import io.stateset.application.ApplicationContract.Companion.APPLICATION_CONTRACT_ID
import io.stateset.approval.*
import io.stateset.approval.ApprovalContract.Companion.APPROVAL_CONTRACT_ID
import io.stateset.case.*
import io.stateset.case.CaseContract.Companion.CASE_CONTRACT_ID
import io.stateset.contact.*
import io.stateset.message.Message
import io.stateset.contact.ContactContract.Companion.CONTACT_CONTRACT_ID
import io.stateset.lead.LeadContract.Companion.LEAD_CONTRACT_ID
import io.stateset.invoice.Invoice
import io.stateset.invoice.InvoiceContract
import io.stateset.invoice.InvoiceContract.Companion.INVOICE_CONTRACT_ID
import io.stateset.lead.*
import io.stateset.lead.SalesRegion
import io.stateset.loan.Loan
import io.stateset.loan.LoanContract
import io.stateset.loan.LoanContract.Companion.LOAN_CONTRACT_ID
import io.stateset.loan.LoanStatus
import io.stateset.loan.LoanType
import io.stateset.message.MessageContract.Companion.MESSAGE_CONTRACT_ID
import io.stateset.message.MessageContract
import io.stateset.product.Product
import io.stateset.product.ProductContract
import io.stateset.product.ProductContract.Companion.PRODUCT_CONTRACT_ID
import io.stateset.proposal.Proposal
import io.stateset.proposal.ProposalContract
import io.stateset.proposal.ProposalContract.Companion.PROPOSAL_CONTRACT_ID
import io.stateset.proposal.ProposalStatus
import io.stateset.proposal.ProposalType
import io.stateset.purchaseorder.PurchaseOrder
import io.stateset.purchaseorder.PurchaseOrderContract
import io.stateset.purchaseorder.PurchaseOrderContract.Companion.PURCHASE_ORDER_CONTRACT_ID
import io.stateset.purchaseorder.PurchaseOrderStatus
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.lang.Boolean.TRUE
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*



// ***************************************************
// * STATESET B2B SALES AND FINANCE AUTOMATION FLOWS *
// ***************************************************


// *********
// * Create Propsoal Flow *
// *********


object CreateProposalFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Initiator(val proposalNumber: String,
                    val proposalName: String,
                    val proposalHash: String,
                    val proposalStatus: ProposalStatus,
                    val proposalType: ProposalType,
                    val totalProposalValue: Int,
                    val proposalStartDate: String,
                    val proposalEndDate: String,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Proposal.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            // val contactReference = serviceHub.vaultService.queryBy<Contract>(contact_id).state.single()
            // val reference = contactReference.referenced()
            val proposalState = Proposal(proposalNumber, proposalName, proposalHash, proposalStatus, proposalType, totalProposalValue, me,  otherParty, proposalStartDate, proposalEndDate, active, createdAt, lastUpdated)
            val txCommand = Command(ProposalContract.Commands.CreateProposal(), proposalState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    //        .addReferenceState(reference)
                    .addOutputState(proposalState, PROPOSAL_CONTRACT_ID)
                    .addCommand(txCommand)
            // .addOutputState(AttachmentContract.Attachment(attachmentId), ATTACHMENT_ID)
            //  .addCommand(AttachmentContract.Command, ourIdentity.owningKey)
            //  .addAttachment(attachmentId)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Propsoal transaction." using (output is Proposal)
                    val proposal = output as Proposal
                    "I won't accept Proposals with a value under 100." using (proposal.totalProposalValue >= 100)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// *********
// * Accept Proposal Flow *
// *********

object AcceptProposalFlow {
    @InitiatingFlow
    @StartableByRPC
    class AcceptProposalFlow(val proposalNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Proposal Input from the Vault
            val proposalStateAndRef = serviceHub.vaultService.queryBy<Proposal>().states.find {
                it.state.data.proposalNumber == proposalNumber
            } ?: throw IllegalArgumentException("No proposal with ID ${proposalNumber} found.")


            val proposal = proposalStateAndRef.state.data
            val proposalStatus = ProposalStatus.ACCEPTED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Renewal output.

            val renewedProposal = Proposal(
                    proposal.proposalNumber,
                    proposal.proposalName,
                    proposal.proposalHash,
                    proposalStatus,
                    proposal.proposalType,
                    proposal.totalProposalValue,
                    proposal.party,
                    proposal.counterparty,
                    proposal.proposalStartDate,
                    proposal.proposalEndDate,
                    proposal.active,
                    proposal.createdAt,
                    formatted,
                    proposal.linearId)

            // Creating the command.
            val requiredSigners = listOf(proposal.party.owningKey, proposal.counterparty.owningKey)
            val command = Command(ProposalContract.Commands.AcceptProposal(), requiredSigners)

            // Building the transaction.
            val notary = proposalStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(proposalStateAndRef)
            txBuilder.addOutputState(renewedProposal, ProposalContract.PROPOSAL_CONTRACT_ID)
            txBuilder.addCommand(command)

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature
            val counterparty = if (ourIdentity == proposal.party) proposal.counterparty else proposal.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(AcceptProposalFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Proposal>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Accept the Proposal")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}

// *********
// * Reject Proposal Flow *
// *********

object RejectProposalFlow {
    @InitiatingFlow
    @StartableByRPC
    class RejectProposalFlow(val proposalNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Proposal Input from the Vault
            val proposalStateAndRef = serviceHub.vaultService.queryBy<Proposal>().states.find {
                it.state.data.proposalNumber == proposalNumber
            } ?: throw IllegalArgumentException("No proposal with ID $proposalNumber found.")


            val proposal = proposalStateAndRef.state.data
            val proposalStatus = ProposalStatus.REJECTED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Rejected output.

            val rejectedProposal = Proposal(
                    proposal.proposalNumber,
                    proposal.proposalName,
                    proposal.proposalHash,
                    proposalStatus,
                    proposal.proposalType,
                    proposal.totalProposalValue,
                    proposal.party,
                    proposal.counterparty,
                    proposal.proposalStartDate,
                    proposal.proposalEndDate,
                    proposal.active,
                    proposal.createdAt,
                    formatted,
                    proposal.linearId)

            // Creating the command.
            val requiredSigners = listOf(proposal.party.owningKey, proposal.counterparty.owningKey)
            val command = Command(ProposalContract.Commands.RejectProposal(), requiredSigners)

            // Building the transaction.
            val notary = proposalStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(proposalStateAndRef)
            txBuilder.addOutputState(rejectedProposal, ProposalContract.PROPOSAL_CONTRACT_ID)
            txBuilder.addCommand(command)

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature
            val counterparty = if (ourIdentity == proposal.party) proposal.counterparty else proposal.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(RejectProposalFlow::class)
    class Rejector(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Proposal>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Reject the Proposal")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}

// *********
// * Create Purchase Order Flow *
// *********


object CreatePurchaseOrderFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Purchaser(val purchaseOrderNumber: String,
                    val purchaseOrderName: String,
                    val purchaseOrderHash: String,
                    val purchaseOrderStatus: PurchaseOrderStatus,
                    val description: String,
                    val purchaseDate: String,
                    val deliveryDate: String,
                    val subtotal: Int,
                    val total: Int,
                    val financer: Party,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Proposal.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            //val agreementReference = serviceHub.vaultService.queryBy<Agreement>(agreemenNumber).state.single()
            // val reference = agreementReference.referenced()
            val purchaseOrderState = PurchaseOrder(purchaseOrderNumber, purchaseOrderName, purchaseOrderHash, purchaseOrderStatus, description, purchaseDate, deliveryDate, subtotal, total, me, otherParty, financer, createdAt, lastUpdated)
            val txCommand = Command(PurchaseOrderContract.Commands.CreatePurchaseOrder(), purchaseOrderState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    //.addReferenceState(reference)
                    .addOutputState(purchaseOrderState, PURCHASE_ORDER_CONTRACT_ID)
                    .addCommand(txCommand)
            //  .addOutputState(AttachmentContract.Attachment(attachmentId), ATTACHMENT_ID)
            //  .addCommand(AttachmentContract.Command, ourIdentity.owningKey)
            //  .addAttachment(attachmentId)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val financerFlow = initiateFlow(financer)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow, financerFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow, financerFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Purchaser::class)
    class Vendor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Purchase Order transaction." using (output is PurchaseOrder)
                    val purchaseorder = output as PurchaseOrder
                    "I won't accept Purchase Orders with a value under 100." using (purchaseorder.total >= 100)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// ********************************
// * Complete Purchase Order Flow *
// ********************************

    @InitiatingFlow
    @StartableByRPC
    class CompletePurchaseOrderFlow(val purchaseOrderNumber: String) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new PO.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Proposal Input from the Vault
            val purchaseOrderStateAndRef = serviceHub.vaultService.queryBy<PurchaseOrder>().states.find {
                it.state.data.purchaseOrderNumber == purchaseOrderNumber
            } ?: throw IllegalArgumentException("No purchase order with ID ${purchaseOrderNumber} found.")


            val purchaseOrder = purchaseOrderStateAndRef.state.data
            val purchaseOrderStatus = PurchaseOrderStatus.COMPLETE
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Completed output.

            val completedPurchaseOrder = PurchaseOrder(
                    purchaseOrder.purchaseOrderNumber,
                    purchaseOrder.purchaseOrderName,
                    purchaseOrder.purchaseOrderHash,
                    purchaseOrderStatus,
                    purchaseOrder.description,
                    purchaseOrder.purchaseDate,
                    purchaseOrder.deliveryDate,
                    purchaseOrder.subtotal,
                    purchaseOrder.total,
                    purchaseOrder.purchaser,
                    purchaseOrder.vendor,
                    purchaseOrder.financer,
                    purchaseOrder.createdAt,
                    formatted,
                    purchaseOrder.linearId)

            // Creating the command.
            val requiredSigners = listOf(purchaseOrder.purchaser.owningKey, purchaseOrder.vendor.owningKey, purchaseOrder.financer.owningKey)
            val command = Command(PurchaseOrderContract.Commands.CompletePurchaseOrder(), requiredSigners)

            // Building the transaction.
            val notary = purchaseOrderStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(purchaseOrderStateAndRef)
            txBuilder.addOutputState(completedPurchaseOrder, PurchaseOrderContract.PURCHASE_ORDER_CONTRACT_ID)
            txBuilder.addCommand(command)

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature
            val counterparty = if (ourIdentity == purchaseOrder.purchaser) purchaseOrder.vendor else purchaseOrder.purchaser
            val financer = purchaseOrder.financer

            val counterpartySession = initiateFlow(counterparty)
            val financerSession = initiateFlow(financer)

            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(counterpartySession, financerSession), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, setOf(counterpartySession, financerSession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(CompletePurchaseOrderFlow::class)
    class CompleteVendor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<PurchaseOrder>().single().vendor

                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }


// ********************************
// * Cancel Purchase Order Flow *
// ********************************

    @InitiatingFlow
    @StartableByRPC
    class CancelPurchaseOrderFlow(val purchaseOrderNumber: String) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new PO.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Proposal Input from the Vault
            val purchaseOrderStateAndRef = serviceHub.vaultService.queryBy<PurchaseOrder>().states.find {
                it.state.data.purchaseOrderNumber == purchaseOrderNumber
            } ?: throw IllegalArgumentException("No purchase order with ID ${purchaseOrderNumber} found.")


            val purchaseOrder = purchaseOrderStateAndRef.state.data
            val purchaseOrderStatus = PurchaseOrderStatus.CANCELLED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Completed output.

            val cancelledPurchaseOrder = PurchaseOrder(
                    purchaseOrder.purchaseOrderNumber,
                    purchaseOrder.purchaseOrderName,
                    purchaseOrder.purchaseOrderHash,
                    purchaseOrderStatus,
                    purchaseOrder.description,
                    purchaseOrder.purchaseDate,
                    purchaseOrder.deliveryDate,
                    purchaseOrder.subtotal,
                    purchaseOrder.total,
                    purchaseOrder.purchaser,
                    purchaseOrder.vendor,
                    purchaseOrder.financer,
                    purchaseOrder.createdAt,
                    formatted,
                    purchaseOrder.linearId)

            // Creating the command.
            val requiredSigners = listOf(purchaseOrder.purchaser.owningKey, purchaseOrder.vendor.owningKey, purchaseOrder.financer.owningKey)
            val command = Command(PurchaseOrderContract.Commands.CancelPurchaseOrder(), requiredSigners)

            // Building the transaction.
            val notary = purchaseOrderStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(purchaseOrderStateAndRef)
            txBuilder.addOutputState(cancelledPurchaseOrder, PurchaseOrderContract.PURCHASE_ORDER_CONTRACT_ID)
            txBuilder.addCommand(command)

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature
            val counterparty = if (ourIdentity == purchaseOrder.purchaser) purchaseOrder.vendor else purchaseOrder.purchaser
            val financer = purchaseOrder.financer

            val counterpartySession = initiateFlow(counterparty)
            val financerSession = initiateFlow(financer)

            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(counterpartySession, financerSession), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, setOf(counterpartySession, financerSession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(CancelPurchaseOrderFlow::class)
    class CancelVendor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<PurchaseOrder>().single().vendor

                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }

// *********
// * Finance Purchase Order Flow *
// *********
object FinancePurchaseOrderFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class FinancePurchaseOrderFlow(val purchaseOrderNumber: String,
                            val newFinancer: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            // Stage 1. Retrieve Invoice specified by invoice Number from the vault.
            val purchaseOrderStateAndRef = serviceHub.vaultService.queryBy<PurchaseOrder>().states.find {
                it.state.data.purchaseOrderNumber == purchaseOrderNumber
            } ?: throw IllegalArgumentException("No purchase order with number $purchaseOrderNumber found.")

            val inputPurchaseOrder = purchaseOrderStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputPurchaseOrder.purchaser) {
                throw IllegalArgumentException("Purchase Order Financing can only be initiated by the Purchaser Party.")
            }

            // Stage 3. Create the new Invoice state reflecting a new Party.
            val outputPurchaseOrder = inputPurchaseOrder.withNewFinancer(newFinancer)

            // Stage 4. Create the transfer command.
            val signers = (inputPurchaseOrder.participants + newFinancer).map { it.owningKey }
            val financeCommand = Command(PurchaseOrderContract.Commands.FinancePurchaseOrder(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(purchaseOrderStateAndRef,
                    StateAndContract(outputPurchaseOrder, PurchaseOrderContract.PURCHASE_ORDER_CONTRACT_ID),
                    financeCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from purchaser and the new financer and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputPurchaseOrder.participants - ourIdentity + newFinancer).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Purchase Order Financing.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(FinancePurchaseOrderFlow::class)
    class FinancePurchaseOrderFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Purchase Order transaction" using (output is PurchaseOrder)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}


// *********
// * Activate Agreement Flow *
// *********

object ActivateFlow {
    @InitiatingFlow
    @StartableByRPC
    class ActivateAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {


            // Retrieving the Agreement Input from the Vault
            val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
                it.state.data.agreementNumber == agreementNumber
            } ?: throw IllegalArgumentException("No Proposal with ID $agreementNumber found.")


            //   val agreementLineItemStateAndDef = serviceHub.vaultService.queryBy<AgreementLineItem>().states.find {
            //       it.state.data.agreementNumber == agreementNumber
            //    } ?: throw IllegalArgumentException("No Agreement Line Item associated to $agreementNumber found.")


            val agreement = agreementStateAndRef.state.data
            //    val agreementLineItem = agreementLineItemStateAndDef.state.data
            val agreementStatus = AgreementStatus.INEFFECT
            //   val agreementLineItemStatus = AgreementLineItemStatus.ACTIVATED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Activated Agreement output.

            val activatedAgreement = Agreement(
                    agreement.agreementNumber,
                    agreement.agreementName,
                    agreement.agreementHash,
                    agreementStatus,
                    agreement.agreementType,
                    agreement.totalAgreementValue,
                    agreement.party,
                    agreement.counterparty,
                    agreement.agreementStartDate,
                    agreement.agreementEndDate,
                    agreement.active,
                    agreement.createdAt,
                    formatted,
                    agreement.linearId)


            // Creating the command.
            val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
            val command = Command(AgreementContract.Commands.ActivateAgreement(), requiredSigners)

            // Created the Activated Agreement Line Item output.


            // val activatedAgreementLineItem = AgreementLineItem(
            //        agreementLineItem.agreement,
            //       agreementLineItem.agreementNumber,
            //       agreementLineItem.agreementLineItemName,
            //      agreementLineItemStatus,
            //      agreementLineItem.agreementLineItemValue,
            //      agreementLineItem.party,
            //     agreementLineItem.counterparty,
            //     agreementLineItem.lineItem,
            //     agreementLineItem.active,
            //     agreementLineItem.createdAt,
            //     agreementLineItem.lastUpdated,
            //     agreementLineItem.linearId

            //  )

            // Building the transaction.
            val notary = agreementStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(agreementStateAndRef)
            // txBuilder.addInputState((agreementLineItemStateAndDef))
            txBuilder.addOutputState(activatedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
            // txBuilder.addOutputState(activatedAgreementLineItem, AgreementLineItemContract.AGREEMENT_LINEITEM_CONTRACT_ID)
            txBuilder.addCommand(command)
            // txBuilder.addCommand(AgreementLineItemContract.Commands.ActivateAgreementLineItem(), ourIdentity.owningKey)


            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signgature
            val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(ActivateAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can activate the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}


// *********
// * Renew Agreement Flow *
// *********

object RenewFlow {
    @InitiatingFlow
    @StartableByRPC
    class RenewAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Agreement Input from the Vault
            val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
                it.state.data.agreementNumber == agreementNumber
            } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


            val agreement = agreementStateAndRef.state.data
            val agreementStatus = AgreementStatus.RENEWED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Renewal output.

            val renewedAgreement = Agreement(
                    agreement.agreementNumber,
                    agreement.agreementName,
                    agreement.agreementHash,
                    agreementStatus,
                    agreement.agreementType,
                    agreement.totalAgreementValue,
                    agreement.party,
                    agreement.counterparty,
                    agreement.agreementStartDate,
                    agreement.agreementEndDate,
                    agreement.active,
                    agreement.createdAt,
                    formatted,
                    agreement.linearId)

            // Creating the command.
            val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
            val command = Command(AgreementContract.Commands.RenewAgreement(), requiredSigners)

            // Building the transaction.
            val notary = agreementStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(agreementStateAndRef)
            txBuilder.addOutputState(renewedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
            txBuilder.addCommand(command)

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signgature
            val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(RenewAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Renew the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}

// *********
// * Amend Agreement Flow *
// *********

object AmendFlow {
    @InitiatingFlow
    @StartableByRPC
    class AmendAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {


            // Retrieving the Agreement Input from the Vault
            val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
                it.state.data.agreementNumber == agreementNumber
            } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


            val agreement = agreementStateAndRef.state.data
            val agreementStatus = AgreementStatus.AMENDED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the Amended Agreement output.


            val amendedAgreement = Agreement(
                    agreement.agreementNumber,
                    agreement.agreementName,
                    agreement.agreementHash,
                    agreementStatus,
                    agreement.agreementType,
                    agreement.totalAgreementValue,
                    agreement.party,
                    agreement.counterparty,
                    agreement.agreementStartDate,
                    agreement.agreementEndDate,
                    agreement.active,
                    agreement.createdAt,
                    formatted,
                    agreement.linearId)

            // Creating the command.
            val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
            val command = Command(AgreementContract.Commands.AmendAgreement(), requiredSigners)

            // Building the transaction.
            val notary = agreementStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(agreementStateAndRef)
            txBuilder.addOutputState(amendedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
            txBuilder.addCommand(command)


            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signgature
            val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(AmendAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Amend the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}


// *********
// * Terminate Agreement Flow *
// *********

object TerminateFlow {
    @InitiatingFlow
    @StartableByRPC
    class TerminateAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Agreement Input from the Vault
            val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
                it.state.data.agreementNumber == agreementNumber
            } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


            val agreement = agreementStateAndRef.state.data
            val agreementStatus = AgreementStatus.TERMINATED
            // Last Update Val
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)


            // Creating the output.
            val terminatedAgreement = Agreement(
                    agreement.agreementNumber,
                    agreement.agreementName,
                    agreement.agreementHash,
                    agreementStatus,
                    agreement.agreementType,
                    agreement.totalAgreementValue,
                    agreement.party,
                    agreement.counterparty,
                    agreement.agreementStartDate,
                    agreement.agreementEndDate,
                    agreement.active,
                    agreement.createdAt,
                    formatted,
                    agreement.linearId)

            // Creating the command.
            val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
            val command = Command(AgreementContract.Commands.TerminateAgreement(), requiredSigners)

            // Building the transaction.
            val notary = agreementStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(agreementStateAndRef)
            txBuilder.addOutputState(terminatedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
            txBuilder.addCommand(command)


            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signgature
            val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(TerminateAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Terminate the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}






// *********
// * Create Agreement Flow *
// *********



object CreateAgreementFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Initiator(val agreementNumber: String,
                    val agreementName: String,
                    val agreementHash: String,
                    val agreementStatus: AgreementStatus,
                    val agreementType: AgreementType,
                    val totalAgreementValue: Int,
                    val agreementStartDate: String,
                    val agreementEndDate: String,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            // val contactReference = serviceHub.vaultService.queryBy<Contract>(contact_id).state.single()
            // val reference = contactReference.referenced()
            // val agreementState = Agreement(agreementNumber, agreementName, agreementStatus, agreementType, totalAgreementValue, serviceHub.myInfo.legalIdentities.first(), otherParty, agreementStartDate, agreementEndDate, agreementLineItem, attachmentId, active, createdAt, lastUpdated )
            val agreementState = Agreement(agreementNumber, agreementName, agreementHash, agreementStatus, agreementType, totalAgreementValue, me,  otherParty, agreementStartDate, agreementEndDate, active, createdAt, lastUpdated)
            val txCommand = Command(AgreementContract.Commands.CreateAgreement(), agreementState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    //        .addReferenceState(reference)
                    .addOutputState(agreementState, AGREEMENT_CONTRACT_ID)
                    .addCommand(txCommand)
            // .addOutputState(AttachmentContract.Attachment(attachmentId), ATTACHMENT_ID)
            //  .addCommand(AttachmentContract.Command, ourIdentity.owningKey)
            //  .addAttachment(attachmentId)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Agreement)
                    val agreement = output as Agreement
                    "I won't accept Agreements with a value under 100." using (agreement.totalAgreementValue >= 100)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// *********
// * Create Account Flow *
// *********


object CreateAccountFlow {
    @InitiatingFlow
    @StartableByRPC
    class Controller(val accountId: String,
                     val accountName: String,
                     val accountType: TypeOfBusiness,
                     val industry: String,
                     val phone: String,
                     val yearStarted: Int,
                     val website: String,
                     val rating: AccountRating,
                     val annualRevenue: Double,
                     val businessAddress: String,
                     val businessCity: String,
                     val businessState: String,
                     val businessZipCode: String,
                     val processor: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Trade.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            val me = ourIdentityAndCert.party
            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted

            // Generate an unsigned transaction.
            val accountState = Account(accountId, accountName, accountType, industry, phone, yearStarted, website, rating, annualRevenue, businessAddress, businessCity, businessState, businessZipCode, serviceHub.myInfo.legalIdentities.first(), processor, active, createdAt, lastUpdated)
            val txCommand = Command(AccountContract.Commands.CreateAccount(), accountState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary = notary)
                    txBuilder.addOutputState(accountState, ACCOUNT_CONTRACT_ID)
                    txBuilder.addCommand(txCommand)

            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(processor)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    @InitiatedBy(Controller::class)
    class AccountProcessor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Account transaction." using (output is Account)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}

// *********
// * Transfer Account Flow *
// *********
object AccountTransferFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class AccountTransferFlow(val accountId: String,
                              val newController: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            // Stage 1. Retrieve IOU specified by linearId from the vault.
            val accountStateAndRef = serviceHub.vaultService.queryBy<Account>().states.find {
                it.state.data.accountId == accountId
            } ?: throw IllegalArgumentException("No account with ID $accountId found.")


            val inputAccount = accountStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputAccount.controller) {
                throw IllegalArgumentException("Account transfer can only be initiated by the Account Controller.")
            }

            // Stage 3. Create the new IOU state reflecting a new lender.
            val outputAccount = inputAccount.withNewController(newController)

            // Stage 4. Create the transfer command.
            val signers = (inputAccount.participants + newController).map { it.owningKey }
            val transferCommand = Command(AccountContract.Commands.TransferAccount(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(accountStateAndRef,
                    StateAndContract(outputAccount, AccountContract.ACCOUNT_CONTRACT_ID),
                    transferCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from processor and the new controller and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputAccount.participants - ourIdentity + newController).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Account transfers.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(AccountTransferFlow::class)
    class AccountTransferFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Account transaction" using (output is Account)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}




// *********
// * Create Contact Flow *
// *********

object CreateContactFlow {
    @InitiatingFlow
    @StartableByRPC
    class Controller(val contactId: String,
                     val firstName: String,
                     val lastName: String,
                     val email: String,
                     val phone: String,
                     val rating: ContactRating,
                     val contactSource: ContactSource,
                     val contactStatus: ContactStatus,
                     val country: String,
                     val processor: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Trade.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            val me = ourIdentityAndCert.party
            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted

            // Generate an unsigned transaction.
            val contactState = Contact(contactId, firstName, lastName, email, phone, rating, contactSource, contactStatus, country, active, createdAt, lastUpdated, me, processor)
            val txCommand = Command(ContactContract.Commands.CreateContact(), contactState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary = notary)
                    txBuilder.addOutputState(contactState, CONTACT_CONTRACT_ID)
                    txBuilder.addCommand(txCommand)

            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(processor)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow)))
        }
    }


    @InitiatedBy(Controller::class)
    class Processor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Contact transaction." using (output is Contact)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}


// *********
// * Transfer Contact Flow *
// *********
object ContactTransferFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class ContactTransferFlow(val contactId: String,
                              val newController: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            val contactStateAndRef = serviceHub.vaultService.queryBy<Contact>().states.find {
                it.state.data.contactId == contactId
            } ?: throw IllegalArgumentException("No contact with ID $contactId found.")


            val inputContact = contactStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputContact.controller) {
                throw IllegalArgumentException("Contact transfer can only be initiated by the Account Controller.")
            }

            // Stage 3. Create the new IOU state reflecting a new lender.
            val outputContact = inputContact.withNewController(newController)

            // Stage 4. Create the transfer command.
            val signers = (inputContact.participants + newController).map { it.owningKey }
            val transferCommand = Command(ContactContract.Commands.TransferContact(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(contactStateAndRef,
                    StateAndContract(outputContact, ContactContract.CONTACT_CONTRACT_ID),
                    transferCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from processor and the new controller and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputContact.participants - ourIdentity + newController).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Contact transfers.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(ContactTransferFlow::class)
    class ContactTransferFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Contact transaction" using (output is Contact)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}

// *********
// * Create Lead Flow *
// *********


object CreateLeadFlow {
    @InitiatingFlow
    @StartableByRPC
    class Controller(val leadId: String,
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
                     val processor: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Trade.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {

                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
                )
            }

            override val progressTracker = tracker()

            @Suspendable
            override fun call(): SignedTransaction {
                // Obtain a reference to the notary we want to use.
                val notary = serviceHub.networkMapCache.notaryIdentities[0]

                // Stage 1.
                progressTracker.currentStep = GENERATING_TRANSACTION

                val active = false
                val time = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                val formatted = time.format(formatter)
                val createdAt = formatted
                val lastUpdated = formatted

                // Generate an unsigned transaction.
                val leadState = Lead(leadId, firstName, lastName, company, title, email, phone, rating, leadSource, leadStatus, salesRegion, country, serviceHub.myInfo.legalIdentities.first(), processor, active, createdAt, lastUpdated)
                val txCommand = Command(LeadContract.Commands.CreateLead(), leadState.participants.map { it.owningKey })
                val txBuilder = TransactionBuilder(notary = notary)
                txBuilder.addOutputState(leadState, LEAD_CONTRACT_ID)
                txBuilder.addCommand(txCommand)

                val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


                val otherPartyFlow = initiateFlow(processor)
                val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

                // Finalising the transaction.
                return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow)))
            }
        }


        @InitiatedBy(Controller::class)
        class LeadProcessor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val output = stx.tx.outputs.single().data
                        "This must be an Lead transaction." using (output is Lead)
                    }
                }

                val txId = subFlow(signTransactionFlow).id

                return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
            }
        }
    }


// *********
// * Transfer Controller Lead Flow *
// *********

object LeadTransferControllerFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class LeadTransferControllerFlow(val leadId: String,
                           val newController: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
                it.state.data.leadId == leadId
            } ?: throw IllegalArgumentException("No lead with ID $leadId found.")


            val inputLead = leadStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputLead.controller) {
                throw IllegalArgumentException("Lead transfer can only be initiated by the Lead Controller.")
            }

            // Stage 3. Create the new Lead state reflecting a new Controller.
            val outputLead = inputLead.withNewController(newController)

            // Stage 4. Create the transfer command.
            val signers = (inputLead.participants + newController).map { it.owningKey }
            val transferCommand = Command(LeadContract.Commands.TransferControllerLead(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(leadStateAndRef,
                    StateAndContract(outputLead, LeadContract.LEAD_CONTRACT_ID),
                    transferCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from processor and the new controller and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputLead.participants - ourIdentity + newController).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Lead Transfers.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(LeadTransferControllerFlow::class)
    class LeadTransferFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Lead transaction" using (output is Lead)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}


// *********
// * Transfer Processor Lead Flow *
// *********

object LeadTransferProcessorFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class LeadTransferProcessorFlow(val leadId: String,
                           val newProcessor: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
                it.state.data.leadId == leadId
            } ?: throw IllegalArgumentException("No agreement with ID $leadId found.")


            val inputLead = leadStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputLead.processor) {
                throw IllegalArgumentException("Lead transfer can only be initiated by the Lead Processor.")
            }

            // Stage 3. Create the new Lead state reflecting a new Processor.
            val outputLead = inputLead.withNewProcessor(newProcessor)

            // Stage 4. Create the transfer command.
            val signers = (inputLead.participants + newProcessor).map { it.owningKey }
            val transferCommand = Command(LeadContract.Commands.TransferProcessorLead(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(leadStateAndRef,
                    StateAndContract(outputLead, LeadContract.LEAD_CONTRACT_ID),
                    transferCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from processor and the new controller and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputLead.participants - ourIdentity + newProcessor).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Lead Transfers.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(LeadTransferProcessorFlow::class)
    class LeadTransferFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Lead transaction" using (output is Lead)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}



// *********
// * Accept Lead Flow *
// *********

@InitiatingFlow
@StartableByRPC
class AcceptLeadFlow(val leadId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Lead Input from the Vault
        val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
            it.state.data.leadId == leadId
        } ?: throw IllegalArgumentException("No Lead with ID $leadId found.")

        // Update Status
        val lead = leadStateAndRef.state.data
        val leadStatus = LeadStatus.ACCEPTED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val acceptedLead = Lead(
                lead.leadId,
                lead.firstName,
                lead.lastName,
                lead.company,
                lead.title,
                lead.email,
                lead.phone,
                lead.rating,
                lead.leadSource,
                leadStatus,
                lead.salesRegion,
                lead.country,
                lead.controller,
                lead.processor,
                lead.active,
                lead.createdAt,
                formatted,
                lead.linearId)

        val requiredSigners = listOf(lead.controller.owningKey, lead.processor.owningKey)
        val command = Command(LeadContract.Commands.AcceptLead(), requiredSigners)

        // Building the transaction.
        val notary = leadStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(leadStateAndRef)
        txBuilder.addOutputState(acceptedLead, LeadContract.LEAD_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val processor = if (ourIdentity == lead.controller) lead.processor else lead.controller
        val processorSession = initiateFlow(processor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(processorSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(processorSession)))
    }
}

@InitiatedBy(AcceptLeadFlow::class)
class LeadAcceptor(val processorSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(processorSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val processor = ledgerTx.inputsOfType<Lead>().single().processor
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(processorSession, txId))
    }
}


// *********
// * Reject Lead Flow *
// *********

@InitiatingFlow
@StartableByRPC
class RejectLeadFlow(val leadId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Lead Input from the Vault
        val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
            it.state.data.leadId == leadId
        } ?: throw IllegalArgumentException("No Lead with ID $leadId found.")


        val lead = leadStateAndRef.state.data
        val leadStatus = LeadStatus.REJECTED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val rejectedLead = Lead(
                lead.leadId,
                lead.firstName,
                lead.lastName,
                lead.company,
                lead.title,
                lead.email,
                lead.phone,
                lead.rating,
                lead.leadSource,
                leadStatus,
                lead.salesRegion,
                lead.country,
                lead.controller,
                lead.processor,
                lead.active,
                lead.createdAt,
                formatted,
                lead.linearId)

        val requiredSigners = listOf(lead.controller.owningKey, lead.processor.owningKey)
        val command = Command(LeadContract.Commands.RejectLead(), requiredSigners)

        // Building the transaction.
        val notary = leadStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(leadStateAndRef)
        txBuilder.addOutputState(rejectedLead, LeadContract.LEAD_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val processor = if (ourIdentity == lead.controller) lead.processor else lead.controller
        val processorSession = initiateFlow(processor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(processorSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(processorSession)))
    }
}

@InitiatedBy(RejectLeadFlow::class)
class LeadRejector(val processorSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(processorSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val processor = ledgerTx.inputsOfType<Lead>().single().processor
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(processorSession, txId))
    }
}


// *********
// * Engage Lead Flow *
// *********

@InitiatingFlow
@StartableByRPC
class EngageLeadFlow(val leadId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Lead Input from the Vault
        val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
            it.state.data.leadId == leadId
        } ?: throw IllegalArgumentException("No Lead with ID $leadId found.")


        val lead = leadStateAndRef.state.data
        val leadStatus = LeadStatus.ENGAGED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val engagedLead = Lead(
                lead.leadId,
                lead.firstName,
                lead.lastName,
                lead.company,
                lead.title,
                lead.email,
                lead.phone,
                lead.rating,
                lead.leadSource,
                leadStatus,
                lead.salesRegion,
                lead.country,
                lead.controller,
                lead.processor,
                lead.active,
                lead.createdAt,
                formatted,
                lead.linearId)

        val requiredSigners = listOf(lead.controller.owningKey, lead.processor.owningKey)
        val command = Command(LeadContract.Commands.EngageLead(), requiredSigners)

        // Building the transaction.
        val notary = leadStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(leadStateAndRef)
        txBuilder.addOutputState(engagedLead, LeadContract.LEAD_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val processor = if (ourIdentity == lead.controller) lead.processor else lead.controller
        val processorSession = initiateFlow(processor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(processorSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(processorSession)))
    }
}

@InitiatedBy(EngageLeadFlow::class)
class LeadEngager(val processorSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(processorSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val processor = ledgerTx.inputsOfType<Lead>().single().processor
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(processorSession, txId))
    }
}


// *********
// * Converted Lead Flow *
// *********

@InitiatingFlow
@StartableByRPC
class ConvertLeadFlow(val leadId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Lead Input from the Vault
        val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
            it.state.data.leadId == leadId
        } ?: throw IllegalArgumentException("No Lead with ID $leadId found.")


        val lead = leadStateAndRef.state.data
        val leadStatus = LeadStatus.CONVERTED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val convertedLead = Lead(
                lead.leadId,
                lead.firstName,
                lead.lastName,
                lead.company,
                lead.title,
                lead.email,
                lead.phone,
                lead.rating,
                lead.leadSource,
                leadStatus,
                lead.salesRegion,
                lead.country,
                lead.controller,
                lead.processor,
                lead.active,
                lead.createdAt,
                formatted,
                lead.linearId)

        val requiredSigners = listOf(lead.controller.owningKey, lead.processor.owningKey)
        val command = Command(LeadContract.Commands.ConvertLead(), requiredSigners)

        // Building the transaction.
        val notary = leadStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(leadStateAndRef)
        txBuilder.addOutputState(convertedLead, LeadContract.LEAD_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val processor = if (ourIdentity == lead.controller) lead.processor else lead.controller
        val processorSession = initiateFlow(processor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(processorSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(processorSession)))
    }
}

@InitiatedBy(ConvertLeadFlow::class)
class LeadConverter(val processorSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(processorSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val processor = ledgerTx.inputsOfType<Lead>().single().processor
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(processorSession, txId))
    }
}


// *********
// * Unconverted Lead Flow *
// *********

@InitiatingFlow
@StartableByRPC
class UnconvertLeadFlow(val leadId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Lead Input from the Vault
        val leadStateAndRef = serviceHub.vaultService.queryBy<Lead>().states.find {
            it.state.data.leadId == leadId
        } ?: throw IllegalArgumentException("No Lead with ID $leadId found.")


        val lead = leadStateAndRef.state.data
        val leadStatus = LeadStatus.UNCONVERTED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val convertedLead = Lead(
                lead.leadId,
                lead.firstName,
                lead.lastName,
                lead.company,
                lead.title,
                lead.email,
                lead.phone,
                lead.rating,
                lead.leadSource,
                leadStatus,
                lead.salesRegion,
                lead.country,
                lead.controller,
                lead.processor,
                lead.active,
                lead.createdAt,
                formatted,
                lead.linearId)

        val requiredSigners = listOf(lead.controller.owningKey, lead.processor.owningKey)
        val command = Command(LeadContract.Commands.ConvertLead(), requiredSigners)

        // Building the transaction.
        val notary = leadStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(leadStateAndRef)
        txBuilder.addOutputState(convertedLead, LeadContract.LEAD_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val processor = if (ourIdentity == lead.controller) lead.processor else lead.controller
        val processorSession = initiateFlow(processor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(processorSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(processorSession)))
    }
}

@InitiatedBy(UnconvertLeadFlow::class)
class LeadUnConverter(val processorSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(processorSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val processor = ledgerTx.inputsOfType<Lead>().single().processor
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(processorSession, txId))
    }
}



// *********
// * Create Case  Flow *
// *********

object CreateCaseFlow {
    @InitiatingFlow
    @StartableByRPC
    @CordaSerializable
    class Initiator(val caseId: String,
                    val caseName: String,
                    val caseNumber: String,
                    val description: String,
                    val caseStatus: CaseStatus,
                    val casePriority: CasePriority,
                    val resolver: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Trade.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted


            // Generate an unsigned transaction.
            val caseState = Case(caseId, caseName, caseNumber, description, caseStatus, casePriority, serviceHub.myInfo.legalIdentities.first(), resolver, active, createdAt, lastUpdated)
            val txCommand = Command(CaseContract.Commands.CreateCase(), caseState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary = notary)
                    txBuilder.addOutputState(caseState, CASE_CONTRACT_ID)
                    txBuilder.addCommand(txCommand)

            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(resolver)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow)))
        }
    }

    @InitiatedBy(Initiator::class)
    class
    Resolver(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Contact transaction." using (output is Case)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}

// *********
// * Close Case Flow *
// *********

@InitiatingFlow
@StartableByRPC
class CloseCaseFlow(val caseId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val caseStateAndRef = serviceHub.vaultService.queryBy<Case>().states.find {
            it.state.data.caseId == caseId
        } ?: throw IllegalArgumentException("No Case with ID $caseId found.")


        val case = caseStateAndRef.state.data
        val caseStatus = CaseStatus.CLOSED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val closedCase = Case(
                case.caseId,
                case.caseName,
                case.caseNumber,
                case.description,
                caseStatus,
                case.casePriority,
                case.submitter,
                case.resolver,
                case.active,
                case.createdAt,
                formatted,
                case.linearId)

        val requiredSigners = listOf(case.submitter.owningKey, case.resolver.owningKey)
        val command = Command(CaseContract.Commands.CloseCase(), requiredSigners)

        // Building the transaction.
        val notary = caseStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(caseStateAndRef)
        txBuilder.addOutputState(closedCase, CaseContract.CASE_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val resolver = if (ourIdentity == case.submitter) case.resolver else case.submitter
        val resolverSession = initiateFlow(resolver)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(resolverSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(resolverSession)))
    }
}

@InitiatedBy(CloseCaseFlow::class)
class Closer(val resolverSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(resolverSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val resolver = ledgerTx.inputsOfType<Case>().single().resolver
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(resolverSession, txId))
    }
}



// *********
// * Close Case Flow *
// *********

@InitiatingFlow
@StartableByRPC
class ResolveCaseFlow(val caseId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val caseStateAndRef = serviceHub.vaultService.queryBy<Case>().states.find {
            it.state.data.caseId == caseId
        } ?: throw IllegalArgumentException("No Case with ID $caseId found.")


        val case = caseStateAndRef.state.data
        val caseStatus = CaseStatus.RESOLVED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val resolvedCase = Case(
                case.caseId,
                case.caseName,
                case.caseNumber,
                case.description,
                caseStatus,
                case.casePriority,
                case.submitter,
                case.resolver,
                case.active,
                case.createdAt,
                formatted,
                case.linearId)

        val requiredSigners = listOf(case.submitter.owningKey, case.resolver.owningKey)
        val command = Command(CaseContract.Commands.ResolveCase(), requiredSigners)

        // Building the transaction.
        val notary = caseStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(caseStateAndRef)
        txBuilder.addOutputState(resolvedCase, CaseContract.CASE_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val resolver = if (ourIdentity == case.submitter) case.resolver else case.submitter
        val resolverSession = initiateFlow(resolver)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(resolverSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(resolverSession)))
    }
}

@InitiatedBy(ResolveCaseFlow::class)
class Resolver(val resolverSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(resolverSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                val resolver = ledgerTx.inputsOfType<Case>().single().resolver
            }
        }

        val txId = subFlow(signTransactionFlow).id

        return subFlow(ReceiveFinalityFlow(resolverSession, txId))
    }
}


// *********
// * Escalate Case Flow *
// *********

@InitiatingFlow
@StartableByRPC
class EscalateCaseFlow(val caseId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Case Input from the Vault
        val caseStateAndRef = serviceHub.vaultService.queryBy<Case>().states.find {
            it.state.data.caseId == caseId
        } ?: throw IllegalArgumentException("No Case with ID $caseId found.")


        val case = caseStateAndRef.state.data
        val caseStatus = CaseStatus.ESCALATED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val escalatedCase = Case(
                case.caseId,
                case.caseName,
                case.caseNumber,
                case.description,
                caseStatus,
                case.casePriority,
                case.submitter,
                case.resolver,
                case.active,
                case.createdAt,
                formatted,
                case.linearId)

        val requiredSigners = listOf(case.submitter.owningKey, case.resolver.owningKey)
        val command = Command(CaseContract.Commands.EscalateCase(), requiredSigners)

        // Building the transaction.
        val notary = caseStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(caseStateAndRef)
        txBuilder.addOutputState(escalatedCase, CaseContract.CASE_CONTRACT_ID)
        txBuilder.addCommand(command)

        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val resolver = if (ourIdentity == case.submitter) case.resolver else case.submitter
        val resolverSession = initiateFlow(resolver)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(resolverSession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(resolverSession)))
    }
}

    @InitiatedBy(EscalateCaseFlow::class)
    class Escalator(val resolverSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(resolverSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val resolver = ledgerTx.inputsOfType<Case>().single().resolver
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(resolverSession, txId))
        }
    }




// *********
// * Send Message Flows *
// *********


@InitiatingFlow
@StartableByRPC
class SendMessageFlow(val to: Party,
                      val toUserId: String,
                      val fromUserId: String,
                      val subject: String,
                      val body: String) : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Message.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */


    @Suspendable
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        progressTracker.currentStep = GENERATING_TRANSACTION

        // Generate an unsigned transaction.
        val me = ourIdentityAndCert.party
        val sent = true
        val delivered = true
        val fromMe = true
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)
        val messageNumber = "msg_" + formatted.toString()
        val messageState = Message(UniqueIdentifier(), subject, body, fromUserId, to, me, toUserId, sent, delivered, fromMe, formatted, messageNumber)
        val txCommand = Command(MessageContract.Commands.SendMessage(), messageState.participants.map { it.owningKey })
        progressTracker.currentStep = VERIFYING_TRANSACTION

        val txb = TransactionBuilder(notary)
        txb.addOutputState(messageState, MESSAGE_CONTRACT_ID)
        txb.addCommand(txCommand)

        txb.verify(serviceHub)
        // Sign the transaction.
        progressTracker.currentStep = SIGNING_TRANSACTION
        val partSignedTx = serviceHub.signInitialTransaction(txb)

        val otherPartySession = initiateFlow(to)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartySession)))
    }

    @InitiatedBy(SendMessageFlow::class)
    // The flow is open
    open class SendMessageResponder(val session: FlowSession) : FlowLogic<SignedTransaction>() {

        // An overridable function to contain validation is provided
        open fun checkTransaction(stx: SignedTransaction) {
            // To be implemented by sub type flows - otherwise do nothing
        }

        @Suspendable
        final override fun call(): SignedTransaction {
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // The validation function is called
                    this@SendMessageResponder.checkTransaction(stx)
                    // Any other rules the CorDapp developer wants executed
                }
            })
            return subFlow(ReceiveFinalityFlow(otherSideSession = session, expectedTxId = stx.id))
        }
    }
}





// *********
// * Create Approval Flow *
// *********



object CreateApprovalFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Initiator(val approvalId: String,
                    val approvalName: String,
                    val description: String,
                    val industry: String,
                    val approvalStatus: ApprovalStatus,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted

            val approvalState = Approval(approvalId, approvalName, description, industry, approvalStatus, serviceHub.myInfo.legalIdentities.first(), otherParty, active, lastUpdated, createdAt)
            val txCommand = Command(ApprovalContract.Commands.CreateApproval(), approvalState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(approvalState, APPROVAL_CONTRACT_ID)
                    .addCommand(txCommand)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            val otherPartySession = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartySession)))
        }
    }


    @InitiatedBy(Initiator::class)
    // The flow is open
    open class Acceptor(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        // An overridable function to contain validation is provided
        open fun checkTransaction(stx: SignedTransaction) {
            // To be implemented by sub type flows - otherwise do nothing
        }

        @Suspendable
        final override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Approval transaction." using (output is Approval)

                    this@Acceptor.checkTransaction(stx)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}





// *********
// * Approve Flow *
// *********

@InitiatingFlow
@StartableByRPC
class ApproveFlow(val approvalId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val approvalStateAndRef = serviceHub.vaultService.queryBy<Approval>().states.find {
            it.state.data.approvalId == approvalId
        } ?: throw IllegalArgumentException("No agreement with ID $approvalId found.")


        val approval = approvalStateAndRef.state.data
        val approvalStatus = ApprovalStatus.APPROVED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val approvedApproval = Approval(
                approval.approvalId,
                approval.approvalName,
                approval.description,
                approval.industry,
                approvalStatus,
                approval.submitter,
                approval.approver,
                approval.active,
                approval.createdAt,
                formatted,
                approval.linearId)

        // Building the transaction.
        val notary = approvalStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(approvalStateAndRef)
        txBuilder.addOutputState(approvedApproval, ApprovalContract.APPROVAL_CONTRACT_ID)
        txBuilder.addCommand(ApprovalContract.Commands.Approve(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(ApproveFlow::class)
    // The Approve flow is open
    open class Approver(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        // An overridable function to contain validation is provided
        open fun checkTransaction(stx: SignedTransaction) {
            // To be implemented by sub type flows - otherwise do nothing
        }

        @Suspendable
        final override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Approval transaction." using (output is Approval)
                    val approval = output as Approval
                    val approvalStatus = ApprovalStatus.APPROVED

                    this@Approver.checkTransaction(stx)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}




// *********
// * Reject Approval Flow *
// *********


@InitiatingFlow
@StartableByRPC
class RejectFlow(val approvalId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val approvalStateAndRef = serviceHub.vaultService.queryBy<Approval>().states.find {
            it.state.data.approvalId == approvalId
        } ?: throw IllegalArgumentException("No approval with ID $approvalId found.")


        val approval = approvalStateAndRef.state.data
        val approvalStatus = ApprovalStatus.REJECTED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)

        // Creating the output.
        val rejectedApproval = Approval(
                approval.approvalId,
                approval.approvalName,
                approval.industry,
                approval.description,
                approvalStatus,
                approval.submitter,
                approval.approver,
                approval.active,
                approval.createdAt,
                formatted,
                approval.linearId)


        // Building the transaction.
        val notary = approvalStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(approvalStateAndRef)
        txBuilder.addOutputState(rejectedApproval, ApprovalContract.APPROVAL_CONTRACT_ID)
        txBuilder.addCommand(ApprovalContract.Commands.Reject(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(RejectFlow::class)
    // The Reject flow is open
    open class Rejecter(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        // An overridable function to contain validation is provided
        open fun checkTransaction(stx: SignedTransaction) {
            // To be implemented by sub type flows - otherwise do nothing
        }

        @Suspendable
        final override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Approval transaction." using (output is Approval)
                    val approval = output as Approval
                    val approvalStatus = ApprovalStatus.REJECTED

                    this@Rejecter.checkTransaction(stx)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}


// *********
// * Create Invoice Flow *
// *********

object CreateInvoiceFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Invoicer(val invoiceNumber: String,
                   val invoiceName: String,
                   val billingReason: String,
                   val amountDue: Int,
                   val amountPaid: Int,
                   val amountRemaining: Int,
                   val subtotal: Int,
                   val total: Int,
                   val dueDate: String,
                   val periodStartDate: String,
                   val periodEndDate: String,
                   val factor: Party,
                   val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            val active = false
            val paid = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            // val agreementReference = serviceHub.vaultService.queryBy<Agreement>().states.single()
            // val reference = agreementReference.referenced()
            val invoiceState = Invoice(invoiceNumber, invoiceName, billingReason, amountDue, amountPaid, amountRemaining, subtotal, total, me, otherParty, factor, dueDate, periodStartDate, periodEndDate, paid, active, createdAt, lastUpdated)
            val txCommand = Command(InvoiceContract.Commands.CreateInvoice(), invoiceState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    // .addReferenceState(reference)
                    .addOutputState(invoiceState, INVOICE_CONTRACT_ID)
                    .addCommand(txCommand)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val factorFlow = initiateFlow(factor)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow, factorFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow, factorFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Invoicer::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Invoice transaction." using (output is Invoice)
                    val invoice = output as Invoice
                    "I won't accept Invoices with a value under 100." using (invoice.total >= 100)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// *********
// * Transfer Invoice Flow *
// *********
object InvoiceTransferFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class InvoiceTransferFlow(val invoiceNumber: String,
                              val newParty: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            // Stage 1. Retrieve Invoice specified by invoice Number from the vault.
            val invoiceStateAndRef = serviceHub.vaultService.queryBy<Invoice>().states.find {
                it.state.data.invoiceNumber == invoiceNumber
            } ?: throw IllegalArgumentException("No invoice with number $invoiceNumber found.")

            val inputInvoice = invoiceStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputInvoice.party) {
                throw IllegalArgumentException("Invoice transfer can only be initiated by the Invoice Party.")
            }

            // Stage 3. Create the new Invoice state reflecting a new Party.
            val outputInvoice = inputInvoice.withNewParty(newParty)

            // Stage 4. Create the transfer command.
            val signers = (inputInvoice.participants + newParty).map { it.owningKey }
            val transferCommand = Command(InvoiceContract.Commands.TransferInvoice(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(invoiceStateAndRef,
                    StateAndContract(outputInvoice, InvoiceContract.INVOICE_CONTRACT_ID),
                    transferCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from processor and the new controller and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputInvoice.participants - ourIdentity + newParty).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Invoice Transfers.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(InvoiceTransferFlow::class)
    class InvoiceTransferFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Invoice transaction" using (output is Invoice)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}


// *********
// * Factor Invoice Flow *
// *********
object FactorInvoiceFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class FactorInvoiceFlow(val invoiceNumber: String,
                            val newFactor: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            // Stage 1. Retrieve Invoice specified by invoice Number from the vault.
            val invoiceStateAndRef = serviceHub.vaultService.queryBy<Invoice>().states.find {
                it.state.data.invoiceNumber == invoiceNumber
            } ?: throw IllegalArgumentException("No invoice with number $invoiceNumber found.")

            val inputInvoice = invoiceStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            if (ourIdentity != inputInvoice.party) {
                throw IllegalArgumentException("Invoice factoring can only be initiated by the Invoice Party.")
            }

            // Stage 3. Create the new Invoice state reflecting a new Party.
            val outputInvoice = inputInvoice.withNewFactor(newFactor)

            // Stage 4. Create the transfer command.
            val signers = (inputInvoice.participants + newFactor).map { it.owningKey }
            val factorCommand = Command(InvoiceContract.Commands.FactorInvoice(), signers)

            // Stage 5. Get a reference to a transaction builder.
            // Note: ongoing work to support multiple notary identities is still in progress.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary = notary)

            // Stage 6. Create the transaction which comprises one input, one output and one command.
            builder.withItems(invoiceStateAndRef,
                    StateAndContract(outputInvoice, InvoiceContract.INVOICE_CONTRACT_ID),
                    factorCommand)

            // Stage 7. Verify and sign the transaction.
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from invoicer and the new factor and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val sessions = (inputInvoice.participants - ourIdentity + newFactor).map { initiateFlow(it) }.toSet()
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

            // Stage 9. Notarise and record the transaction in our vaults.
            return subFlow(FinalityFlow(stx, sessions))
        }
    }

    /**
     * This is the flow which signs Invoice Factoring.
     * The signing is handled by the [SignTransactionFlow].
     */
    @InitiatedBy(FactorInvoiceFlow::class)
    class FactorInvoiceFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Invoice transaction" using (output is Invoice)
                }
            }
            val txWeJustSignedId = subFlow(signedTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
        }
    }
}



// *********
// * Create Loan Flow *
// *********

object CreateLoanFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Loaner(val loanNumber: String,
                 val loanName: String,
                 val loanReason: String,
                 val loanStatus: LoanStatus,
                 val loanType: LoanType,
                 val amountDue: Int,
                 val amountPaid: Int,
                 val amountRemaining: Int,
                 val subtotal: Int,
                 val total: Int,
                 val dueDate: String,
                 val periodStartDate: String,
                 val periodEndDate: String,
                 val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            val active = false
            val paid = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            // val agreementReference = serviceHub.vaultService.queryBy<Agreement>().states.single()
            // val reference = agreementReference.referenced()
            val loanState = Loan(loanNumber, loanName, loanReason, loanStatus, loanType, amountDue, amountPaid, amountRemaining, subtotal, total, me, otherParty, dueDate, periodStartDate, periodEndDate, paid, active, createdAt, lastUpdated)
            val txCommand = Command(LoanContract.Commands.CreateLoan(), loanState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    // .addReferenceState(reference)
                    .addOutputState(loanState, LOAN_CONTRACT_ID)
                    .addCommand(txCommand)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Loaner::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Loan transaction." using (output is Loan)
                    val loan = output as Loan
                    "I won't accept Loans with a value under 100." using (loan.total >= 100)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// *********
// * Create Application Flow *
// *********



object CreateApplicationFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Initiator(val applicationId: String,
                    val applicationName: String,
                    val businessAgeRange: BusinessAgeRange,
                    val businessEmail: String,
                    val businessPhone: String,
                    val businessRevenueRange: BusinessRevenueRange,
                    val businessType: BusinessType,
                    val estimatedPurchaseAmount: Int,
                    val estimatedPurchaseFrequency: EstimatedPurchaseFrequency,
                    val industry: String,
                    val applicationStatus: ApplicationStatus,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val submitted = TRUE
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val submittedAt = formatted
            val active = false
            val createdAt = formatted
            val lastUpdated = formatted

            progressTracker.currentStep = GENERATING_TRANSACTION

            val applicationState = Application(applicationId, applicationName, businessAgeRange, businessEmail, businessPhone, businessRevenueRange, businessType, estimatedPurchaseAmount, estimatedPurchaseFrequency, submitted, submittedAt, industry, applicationStatus, serviceHub.myInfo.legalIdentities.first(), otherParty, active, lastUpdated, createdAt)
            val txCommand = Command(ApplicationContract.Commands.CreateApplication(), applicationState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(applicationState, APPLICATION_CONTRACT_ID)
                    .addCommand(txCommand)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            val otherPartySession = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartySession)))
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Application transaction." using (output is Application)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}





// *********
// * Approve Application Flow *
// *********

@InitiatingFlow
@StartableByRPC
class ApproveApplicationFlow(val applicationId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val applicationStateAndRef = serviceHub.vaultService.queryBy<Application>().states.find {
            it.state.data.applicationId == applicationId
        } ?: throw IllegalArgumentException("No agreement with ID $applicationId found.")


        val application = applicationStateAndRef.state.data
        val applicationStatus = ApplicationStatus.APPROVED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)


        // Creating the output.
        val approvedApplication = Application(
                application.applicationId,
                application.applicationName,
                application.businessAgeRange,
                application.businessEmail,
                application.businessPhone,
                application.businessRevenueRange,
                application.businessType,
                application.estimatedPurchaseAmount,
                application.estimatedPurchaseFrequency,
                application.submitted,
                application.submittedAt,
                application.industry,
                applicationStatus,
                application.agent,
                application.provider,
                application.active,
                application.createdAt,
                formatted,
                application.linearId)


        // Building the transaction.
        val notary = applicationStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(applicationStateAndRef)
        txBuilder.addOutputState(approvedApplication, ApplicationContract.APPLICATION_CONTRACT_ID)
        txBuilder.addCommand(ApplicationContract.Commands.ApproveApplication(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(ApproveApplicationFlow::class)
    class Approver(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Application)
                    val application = output as Application
                    val applicationStatus = ApplicationStatus.APPROVED
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}




// *********
// * Reject Application Flow *
// *********


@InitiatingFlow
@StartableByRPC
class RejectApplicationFlow(val applicationId: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val applicationStateAndRef = serviceHub.vaultService.queryBy<Application>().states.find {
            it.state.data.applicationId == applicationId
        } ?: throw IllegalArgumentException("No agreement with ID $applicationId found.")


        val application = applicationStateAndRef.state.data
        val applicationStatus = ApplicationStatus.REJECTED
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)

        // Creating the output.
        val rejectedApplication = Application(
                application.applicationId,
                application.applicationName,
                application.businessAgeRange,
                application.businessEmail,
                application.businessPhone,
                application.businessRevenueRange,
                application.businessType,
                application.estimatedPurchaseAmount,
                application.estimatedPurchaseFrequency,
                application.submitted,
                application.submittedAt,
                application.industry,
                applicationStatus,
                application.agent,
                application.provider,
                application.active,
                application.createdAt,
                formatted,
                application.linearId)

        // Building the transaction.
        val notary = applicationStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(applicationStateAndRef)
        txBuilder.addOutputState(rejectedApplication, ApplicationContract.APPLICATION_CONTRACT_ID)
        txBuilder.addCommand(ApplicationContract.Commands.RejectApplication(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(RejectApplicationFlow::class)
    class Rejecter(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Application)
                    val application = output as Application
                    val applicationStatus = ApplicationStatus.REJECTED
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}




// ***************************
// * Review Application Flow *
// ***************************



@InitiatingFlow
@StartableByRPC
class ReviewApplicationFlow(val applicationId: String): FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val applicationStateAndRef = serviceHub.vaultService.queryBy<Application>().states.find {
            it.state.data.applicationId == applicationId
        } ?: throw IllegalArgumentException("No agreement with ID $applicationId found.")


        val application = applicationStateAndRef.state.data
        val applicationStatus = ApplicationStatus.INREVIEW
        // Last Update Val
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)

        // Creating the output.
        val reviewedApplication = Application(
                application.applicationId,
                application.applicationName,
                application.businessAgeRange,
                application.businessEmail,
                application.businessPhone,
                application.businessRevenueRange,
                application.businessType,
                application.estimatedPurchaseAmount,
                application.estimatedPurchaseFrequency,
                application.submitted,
                application.submittedAt,
                application.industry,
                applicationStatus,
                application.agent,
                application.provider,
                application.active,
                application.createdAt,
                formatted,
                application.linearId)

        // Building the transaction.
        val notary = applicationStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(applicationStateAndRef)
        txBuilder.addOutputState(reviewedApplication, ApplicationContract.APPLICATION_CONTRACT_ID)
        txBuilder.addCommand(ApplicationContract.Commands.RejectApplication(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(ReviewApplicationFlow::class)
    class Reviewer(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Application)
                    val application = output as Application
                    val applicationStatus =  ApplicationStatus.INREVIEW
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}


// *********
// * Create Product  Flow *
// *********

object CreateProductFlow {
    @InitiatingFlow
    @StartableByRPC
    @CordaSerializable
    class Initiator(val product_id: String,
                    val name: String,
                    val product_url: String,
                    val image_url: String,
                    val description: String,
                    val sku: String,
                    val sku_url: String,
                    val sku_image_url: String,
                    val ccids: String,
                    val category_breadcrumbs: String,
                    val price: Float,
                    val sale_price: Float,
                    val is_active: Int,
                    val stock_quantity: Int,
                    val stock_unit: String,
                    val brand: String,
                    val color: String,
                    val size: String,
                    val counterparty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Trade.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION

            val me = ourIdentityAndCert.party
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            // Generate an unsigned transaction.
            val productState = Product(product_id, name, product_url, image_url, description, sku, sku_url, sku_image_url, ccids, category_breadcrumbs, price, sale_price, is_active, stock_quantity, stock_unit, brand, color, size, createdAt, lastUpdated,  serviceHub.myInfo.legalIdentities.first(), counterparty)
            val txCommand = Command(ProductContract.Commands.CreateProduct(), productState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary = notary)
            txBuilder.addOutputState(productState, PRODUCT_CONTRACT_ID)
            txBuilder.addCommand(txCommand)

            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Resolver(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Product transaction." using (output is Product)
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}



/*

// *********
// * Pay Invoice Flow *
// *********



object PayInvoice {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val linearId: UniqueIdentifier,
                    private val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining Obligation from vault.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING)


            fun getInvoiceByLinearId(linearId: UniqueIdentifier): StateAndRef<Invoice> {
                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                        null,
                        ImmutableList.of(linearId),
                        Vault.StateStatus.UNCONSUMED, null)

                return getService(nodeName).proxy().vaultService.queryBy<Invoice>(queryCriteria).states.singleOrNull()
                        ?: throw FlowException("Invoice with id $linearId not found.")
            }

            fun resolveIdentity(abstractParty: AbstractParty): Party {
                return getService(nodeName).proxy().identityService.requireWellKnownPartyFromAnonymous(abstractParty)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {

            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = Initiator.Companion.PREPARATION
            val invoiceToPay = getInvoiceByLinearId(linearId)
            val inputInvoice = invoiceToPay.state.data

            val partyIdentity = resolveIdentity(inputInvoice.party)
            val counterpartyIdentity = resolveIdentity(inputInvoice.counterparty)

            // Stage 3. This flow can only be initiated by the current recipient.
            check(partyIdentity == ourIdentity) {
                throw FlowException("Pay Invoice flow must be initiated by the counterparty.")
            }

            // Stage 4. Check we have enough cash to settle the requested amount.
            val cashBalance = serviceHub.getCashBalance(amount.token)
            val amountLeftToPay = inputInvoice.amountRemaining
            check(cashBalance.quantity > 0L) {
                throw FlowException("Counterpary has no ${amount.token} to pay the invoice.")
            }
            check(cashBalance >= amount) {
                throw FlowException("Borrower has only $cashBalance but needs $amount to pay the invoice.")
            }
            check(amountLeftToPay >= amount) {
                throw FlowException("There's only $amountLeftToPay left to pay but you pledged $amount.")
            }

            // Stage 5. Create a pay command.
            val payCommand = Command(
                    InvoiceContract.Commands.PayInvoice(),
                    inputInvoice.participants.map { it.owningKey })

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.currentStep = BUILDING
            val builder = TransactionBuilder(notary)
                    .addInputState(invoiceToPay)
                    .addCommand(payCommand)

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            // We pay cash to the lenders obligation key.
            val lenderPaymentKey = inputInvoice.party
            val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, builder, amount, lenderPaymentKey)

            // Stage 8. Only add an output obligation state if the obligation has not been fully settled.
            val amountRemaining = amountLeftToPay - amount
            if (amountRemaining > Amount.zero(amount.token)) {
                val outputObligation = inputInvoice.pay(amount)
                builder.addOutputState(outputObligation, INVOICE_CONTRACT_ID)
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashSigningKeys + inputInvoice.counterparty.owningKey)

            // Stage 10. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            val session = initiateFlow(partyIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashSigningKeys + inputInvoice.counterparty.owningKey,
                    COLLECTING.childProgressTracker())
            )

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING

            // Finalising the transaction.
            return subFlow(FinalityFlow(stx, listOf(otherPartySession), CreateInvoiceFlow.Invoicer.Companion.FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Invoice transaction." using (output is Invoice)
                    val invoice = output as Invoice
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// *********
// * Factor Invoice Flow *
// *********



object FactorInvoice {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val linearId: UniqueIdentifier,
                    private val amount: Amount<Currency>,
                    private val borrower: Party,
                    private val lender: Party) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining Obligation from vault.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING)


            fun getInvoiceByLinearId(linearId: UniqueIdentifier): StateAndRef<Invoice> {
                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                        null,
                        ImmutableList.of(linearId),
                        Vault.StateStatus.UNCONSUMED, null)

                return serviceHub.vaultService.queryBy<Invoice>(queryCriteria).states.singleOrNull()
                        ?: throw FlowException("Invoice with id $linearId not found.")
            }

            fun resolveIdentity(abstractParty: AbstractParty): Party {
                return serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {

            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = Initiator.Companion.PREPARATION
            val invoiceToFactor = getInvoiceByLinearId(linearId)
            val inputInvoice = invoiceToFactor.state.data

            val borrowerIdentity = resolveIdentity(borrower)
            val lenderIdentity = resolveIdentity(lender)
            val invoiceReference = serviceHub.vaultService.queryBy<Invoice>().states.single()
            val reference = invoiceReference.referenced()

            // Stage 3. This flow can only be initiated by the current recipient.
            check(borrowerIdentity == ourIdentity) {
                throw FlowException("Factor Invoice flow must be initiated by the party.")
            }

            // Stage 4. Check we have enought to issue the loan based on the requested loan amount.
            val cashBalance = serviceHub.getCashBalance(amount.token)
            val amountLeftToPay = inputInvoice.amountRemaining
            check(cashBalance.quantity > 0) {
                throw FlowException("Lender has no ${amount.token} to factor the invoice.")
            }
            check(cashBalance >= amount) {
                throw FlowException("Borrower has only $cashBalance but needs $amount to pay the invoice.")
            }
            check(amountLeftToPay >= amount) {
                throw FlowException("There's only $amountLeftToPay left to pay but you pledged $amount.")
            }

            // Stage 5. Create a pay command.
            val factorCommand = Command(
                    InvoiceContract.Commands.FactorInvoice(),
                    inputInvoice.participants.map { it.owningKey })

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.currentStep = BUILDING
            val builder = TransactionBuilder(notary)
                    .addReferenceState(reference)
                    .addInputState(invoiceToFactor)
                    .addCommand(factorCommand)

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            // We pay cash to the lenders obligation key.
            val borrowerPaymentKey = borrower
            val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, builder, amount, borrowerPaymentKey)

            // Stage 8. Add a Loan Output State with a Reference State to the Invoice
            val amountRemaining = amountLeftToPay - amount
            if (amountRemaining > Amount.zero(amount.token)) {
                val outputLoan = inputInvoice.pay(amount)
                builder.addOutputState(outputLoan, INVOICE_CONTRACT_ID)
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashSigningKeys + inputInvoice.party.owningKey)

            // Stage 10. Get the Lender's signature.
            progressTracker.currentStep = COLLECTING
            val session = initiateFlow(lenderIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashSigningKeys + lender.owningKey,
                    COLLECTING.childProgressTracker())
            )

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING

            // Finalising the transaction.
            return subFlow(FinalityFlow(stx, listOf(otherPartySession), CreateInvoiceFlow.Invoicer.Companion.FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Invoice transaction." using (output is Invoice)
                    val invoice = output as Invoice
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

      */

