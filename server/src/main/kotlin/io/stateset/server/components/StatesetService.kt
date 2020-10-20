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

package io.stateset.server.components

import com.github.manosbatsis.corbeans.spring.boot.corda.rpc.NodeRpcConnection
import com.github.manosbatsis.corbeans.spring.boot.corda.service.CordaNodeServiceImpl
import io.stateset.*
import io.stateset.account.AccountRating
import io.stateset.account.TypeOfBusiness
import io.stateset.agreement.AgreementStatus
import io.stateset.agreement.AgreementType
import io.stateset.application.*
import io.stateset.approval.ApprovalStatus
import io.stateset.case.CasePriority
import io.stateset.case.CaseStatus
import io.stateset.contact.ContactRating
import io.stateset.contact.ContactSource
import io.stateset.contact.ContactStatus
import io.stateset.lead.LeadSource
import io.stateset.lead.LeadStatus
import io.stateset.lead.SalesRegion
import io.stateset.lead.LeadRating
import io.stateset.loan.LoanStatus
import io.stateset.loan.LoanType
import io.stateset.message.Message
import io.stateset.proposal.ProposalStatus
import io.stateset.proposal.ProposalType
import io.stateset.purchaseorder.PurchaseOrderStatus
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory

class StatesetService(
        nodeRpcConnection: NodeRpcConnection
) : CordaNodeServiceImpl(nodeRpcConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(CordaNodeServiceImpl::class.java)
    }

    /** Send a Message! */
    fun sendMessage(to: String, toUserId: String, fromUserId: String, subject: String, body: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(to, exactMatch = true)
        logger.debug("sendMessage, peers: {}", this.peers())
        logger.debug("sendMessage, target: {}, matches: {}", to, matches)

        val to: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$to\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$to\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(SendMessageFlow::class.java, to, toUserId, fromUserId, subject, body).returnValue.getOrThrow()
    }

    /** Create an Application */
    fun createApplication(applicationId: String, applicationName: String, businessAgeRange: BusinessAgeRange, businessEmail: String, businessPhone: String, businessRevenueRange: BusinessRevenueRange, businessType: BusinessType, estimatedPurchaseAmount: Int, estimatedPurchaseFrequency: EstimatedPurchaseFrequency, industry: String, applicationStatus: ApplicationStatus, partyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(partyName, exactMatch = true)
        logger.debug("createAccount, peers: {}", this.peers())
        logger.debug("createAccount, target: {}, matches: {}", partyName, matches)

        val processor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$partyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$partyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateApplicationFlow.Initiator::class.java, applicationId, applicationName, businessAgeRange, businessEmail, businessPhone, businessRevenueRange, businessType, estimatedPurchaseAmount, estimatedPurchaseFrequency, industry, applicationStatus, processor).returnValue.getOrThrow()
    }


    /** Approve an Application! */
    fun approveApplication(applicationId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ApproveApplicationFlow::class.java, applicationId).returnValue.getOrThrow()
    }


    /** Reject an Application! */
    fun rejectApplication(applicationId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(RejectApplicationFlow::class.java, applicationId).returnValue.getOrThrow()
    }


    /** Create an Approval */
    fun createApproval(approvalId: String, approvalName: String, description: String, industry: String, approvalStatus: ApprovalStatus, partyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(partyName, exactMatch = true)
        logger.debug("createAccount, peers: {}", this.peers())
        logger.debug("createAccount, target: {}, matches: {}", partyName, matches)

        val processor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$partyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$partyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateApprovalFlow.Initiator::class.java, approvalId, approvalName, description, industry, approvalStatus, processor).returnValue.getOrThrow()
    }


    /** Approve an Approval! */
    fun approve(approvalId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ApproveFlow::class.java, approvalId).returnValue.getOrThrow()
    }


    /** Reject an Approval! */
    fun reject(approvalId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(RejectFlow::class.java, approvalId).returnValue.getOrThrow()
    }


    /** Create an Account! */
    fun createAccount(accountId: String, accountName: String, accountType: TypeOfBusiness, industry: String, phone: String, yearStarted: Int, website: String, rating: AccountRating, annualRevenue: Double, businessAddress: String, businessCity: String, businessState: String, businessZipCode: String, processor: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(processor, exactMatch = true)
        logger.debug("createAccount, peers: {}", this.peers())
        logger.debug("createAccount, target: {}, matches: {}", processor, matches)

        val processor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$processor\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$processor\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateAccountFlow.Controller::class.java, accountId, accountName, accountType, industry, phone, yearStarted, website, rating, annualRevenue, businessAddress, businessCity, businessState, businessZipCode, processor).returnValue.getOrThrow()
    }


    /** Transfer an Account! */
    fun transferAccount(accountId: String, newController: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newController, exactMatch = true)
        logger.debug("transferAccount, peers: {}", this.peers())
        logger.debug("transferAccount, target: {}, matches: {}", newController, matches)

        val newController: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newController\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newController\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(AccountTransferFlow.AccountTransferFlow::class.java, accountId, newController).returnValue.getOrThrow()
    }


    /** Create a Contact! */
    fun createContact(contactId: String, firstName: String, lastName: String, phone: String, email: String, rating: ContactRating, contactSource: ContactSource, contactStatus: ContactStatus, processor: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(processor, exactMatch = true)
        logger.debug("sendMessage, peers: {}", this.peers())
        logger.debug("sendMessage, target: {}, matches: {}", processor, matches)

        val processor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$processor\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$processor\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateContactFlow.Controller::class.java, contactId, firstName, lastName, phone, email, rating, contactSource, contactStatus, processor).returnValue.getOrThrow()
    }

    /** Transfer an Account! */
    fun transferContact(contactId: String, newController: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newController, exactMatch = true)
        logger.debug("transferContact, peers: {}", this.peers())
        logger.debug("transferContact, target: {}, matches: {}", newController, matches)

        val newController: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newController\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newController\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ContactTransferFlow.ContactTransferFlow::class.java, contactId, newController).returnValue.getOrThrow()
    }


    /** Create a Lead */
    fun createLead(leadId: String, firstName: String, lastName: String, company: String, title: String, email: String, phone: String, rating: LeadRating, leadSource: LeadSource, leadStatus: LeadStatus, salesRegion: SalesRegion, country: String, processor: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(processor, exactMatch = true)
        logger.debug("createLead, peers: {}", this.peers())
        logger.debug("createLead, target: {}, matches: {}", processor, matches)

        val processor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$processor\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$processor\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateLeadFlow.Controller::class.java, leadId, firstName, lastName, company, title, email, phone, rating, leadSource, leadStatus, salesRegion, country, processor).returnValue.getOrThrow()
    }

    /** Accept a Lead! */
    fun acceptLead(leadId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(AcceptLeadFlow::class.java, leadId).returnValue.getOrThrow()
    }

    /** Reject a Lead! */
    fun rejectLead(leadId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(RejectLeadFlow::class.java, leadId).returnValue.getOrThrow()
    }

    /** Engage a Lead! */
    fun engageLead(leadId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(EngageLeadFlow::class.java, leadId).returnValue.getOrThrow()
    }

    /** Convert a Lead! */
    fun convertLead(leadId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ConvertLeadFlow::class.java, leadId).returnValue.getOrThrow()
    }

    /** Unconvert a Lead! */
    fun unconvertLead(leadId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(UnconvertLeadFlow::class.java, leadId).returnValue.getOrThrow()
    }

    /** Transfer a Lead to a new Controller! */
    fun transferLeadController(leadId: String, newController: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newController, exactMatch = true)
        logger.debug("transferLead, peers: {}", this.peers())
        logger.debug("transferLead, target: {}, matches: {}", newController, matches)

        val newController: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newController\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newController\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(LeadTransferControllerFlow.LeadTransferControllerFlow::class.java, leadId, newController).returnValue.getOrThrow()
    }

    /** Transfer a Lead  to a new Processor! */
    fun transferLeadProcessor(leadId: String, newProcessor: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newProcessor, exactMatch = true)
        logger.debug("transferLead, peers: {}", this.peers())
        logger.debug("transferLead, target: {}, matches: {}", newProcessor, matches)

        val newProcessor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newProcessor\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newProcessor\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(LeadTransferProcessorFlow.LeadTransferProcessorFlow::class.java, leadId, newProcessor).returnValue.getOrThrow()
    }


    /** Create a Case */
    fun createCase(caseId: String, caseName: String, caseNumber: String, description: String, caseStatus: CaseStatus, casePriority: CasePriority, resolver: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(resolver, exactMatch = true)
        logger.debug("sendMessage, peers: {}", this.peers())
        logger.debug("sendMessage, target: {}, matches: {}", resolver, matches)

        val resolver: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$resolver\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$resolver\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateCaseFlow.Initiator::class.java, caseId, caseName, caseNumber, description, caseStatus, casePriority, resolver).returnValue.getOrThrow()
    }

    /** Close a Case! */
    fun closeCase(caseId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CloseCaseFlow::class.java, caseId).returnValue.getOrThrow()
    }

    /** Resolve a Case! */
    fun resolveCase(caseId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ResolveCaseFlow::class.java, caseId).returnValue.getOrThrow()
    }

    /** Escalate a Case! */
    fun escalateCase(caseId: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(EscalateCaseFlow::class.java, caseId).returnValue.getOrThrow()
    }


    /** Create a Purchase Order! */
    fun createPurchaseOrder(purchaseOrderNumber: String, purchaseOrderName: String, purchaseOrderHash: String, purchaseOrderStatus: PurchaseOrderStatus, description: String, purchaseDate: String, deliveryDate: String, subtotal: Int, total: Int, financerName: String, vendorName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val vendorMatches = proxy.partiesFromName(vendorName, exactMatch = true)
        val financerMatches = proxy.partiesFromName(financerName, exactMatch = true)

        val vendorName: Party = when {
            vendorMatches.isEmpty() -> throw IllegalArgumentException("Target string \"$vendorName\" doesn't match any nodes on the network.")
            vendorMatches.size > 1 -> throw IllegalArgumentException("Target string \"$vendorName\"  matches multiple nodes on the network.")
            else -> vendorMatches.single()
        }

        val financerName: Party = when {
            financerMatches.isEmpty() -> throw IllegalArgumentException("Target string \"$financerName\" doesn't match any nodes on the network.")
            financerMatches.size > 1 -> throw IllegalArgumentException("Target string \"$financerName\"  matches multiple nodes on the network.")
            else -> financerMatches.single()
        }

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreatePurchaseOrderFlow.Purchaser::class.java, purchaseOrderNumber, purchaseOrderName, purchaseOrderHash, purchaseOrderStatus, description, purchaseDate, deliveryDate, subtotal, total, financerName, vendorName).returnValue.getOrThrow()
    }

    /** Complete a Purchase Order! */
    fun completePurchaseOrder(purchaseOrderNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CompletePurchaseOrderFlow::class.java, purchaseOrderNumber).returnValue.getOrThrow()
    }


    /** Cancel a Purchase Order! */
    fun cancelPurchaseOrder(purchaseOrderNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CancelPurchaseOrderFlow::class.java, purchaseOrderNumber).returnValue.getOrThrow()
    }


    /** Finance a Purchase Order! */
    fun financePurchaseOrder(purchaseOrderNumber: String, newFinancer: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newFinancer, exactMatch = true)
        logger.debug("financerParty, peers: {}", this.peers())
        logger.debug("financerParty, target: {}, matches: {}", newFinancer, matches)

        val newFinancer: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newFinancer\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newFinancer\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(FinancePurchaseOrderFlow.FinancePurchaseOrderFlow::class.java, purchaseOrderNumber, newFinancer).returnValue.getOrThrow()
    }


    /** Create an Proposal! */
    fun createProposal(proposalNumber: String, proposalName: String, proposalHash: String, proposalStatus: ProposalStatus, proposalType: ProposalType, totalProposalValue: Int, counterpartyName: String, proposalStartDate: String, proposalEndDate: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        logger.debug("createProposal, peers: {}", this.peers())
        logger.debug("createProposal, peer names: {}", this.peerNames())
        logger.debug("createProposal, target: {}, matches: {}", counterpartyName, matches)

        val counterpartyName: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateProposalFlow.Initiator::class.java, proposalNumber, proposalName, proposalHash, proposalStatus, proposalType, totalProposalValue, proposalStartDate, proposalEndDate, counterpartyName).returnValue.getOrThrow()
    }

    /** Accept a Proposal! */
    fun acceptProposal(proposalNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(AcceptProposalFlow.AcceptProposalFlow::class.java, proposalNumber).returnValue.getOrThrow()
    }


    /** Reject a Proposal! */
    fun rejectProposal(proposalNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(RejectProposalFlow.RejectProposalFlow::class.java, proposalNumber).returnValue.getOrThrow()
    }


    /** Create an Agreement! */
    fun createAgreement(agreementNumber: String, agreementName: String, agreementHash: String, agreementStatus: AgreementStatus, agreementType: AgreementType, totalAgreementValue: Int, counterpartyName: String, agreementStartDate: String, agreementEndDate: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        logger.debug("createAgreement, peers: {}", this.peers())
        logger.debug("createAgreement, peer names: {}", this.peerNames())
        logger.debug("createAgreement, target: {}, matches: {}", counterpartyName, matches)

        val counterpartyName: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateAgreementFlow.Initiator::class.java, agreementNumber, agreementName, agreementHash, agreementStatus, agreementType, totalAgreementValue, agreementStartDate, agreementEndDate, counterpartyName).returnValue.getOrThrow()
    }


    /** Activate an Agreement! */
    fun activateAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ActivateFlow.ActivateAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }


    /** Renew an Agreement! */
    fun renewAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(RenewFlow.RenewAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }


    /** Amend an Agreement! */
    fun amendAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(AmendFlow.AmendAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }


    /** Terminate an Agreement! */
    fun terminateAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(TerminateFlow.TerminateAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }

    /** Create an Invoice! */
    fun createInvoice(invoiceNumber: String, invoiceName: String, billingReason: String, amountDue: Int, amountPaid: Int, amountRemaining: Int, subtotal: Int, total: Int, dueDate: String, periodStartDate: String, periodEndDate: String, factorName: String, counterpartyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val counterpartyMatches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        val factorMatches = proxy.partiesFromName(factorName, exactMatch = true)

        val counterpartyName: Party = when {
            counterpartyMatches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            counterpartyMatches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> counterpartyMatches.single()
        }

        val factorName: Party = when {
            factorMatches.isEmpty() -> throw IllegalArgumentException("Target string \"$factorName\" doesn't match any nodes on the network.")
            factorMatches.size > 1 -> throw IllegalArgumentException("Target string \"$factorName\"  matches multiple nodes on the network.")
            else -> factorMatches.single()
        }

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateInvoiceFlow.Invoicer::class.java, invoiceNumber, invoiceName, billingReason, amountDue, amountPaid, amountRemaining, subtotal, total, dueDate, periodStartDate, periodEndDate, factorName, counterpartyName).returnValue.getOrThrow()
    }

    /** Transfer an Invoice! */
    fun transferInvoice(invoiceNumber: String, newParty: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newParty, exactMatch = true)
        logger.debug("transferParty, peers: {}", this.peers())
        logger.debug("transferParty, target: {}, matches: {}", newParty, matches)

        val newParty: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newParty\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newParty\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(InvoiceTransferFlow.InvoiceTransferFlow::class.java, invoiceNumber, newParty).returnValue.getOrThrow()
    }


    /** Factor an Invoice! */
    fun factorInvoice(invoiceNumber: String, newFactor: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(newFactor, exactMatch = true)
        logger.debug("factorParty, peers: {}", this.peers())
        logger.debug("factorParty, target: {}, matches: {}", newFactor, matches)

        val newFactor: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$newFactor\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$newFactor\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(FactorInvoiceFlow.FactorInvoiceFlow::class.java, invoiceNumber, newFactor).returnValue.getOrThrow()
    }


    /** Create a Loan! */
    fun createLoan(loanNumber: String, loanName: String, loanReason: String, loanStatus: LoanStatus, loanType: LoanType, amountDue: Int, amountPaid: Int, amountRemaining: Int, subtotal: Int, total: Int, dueDate: String, periodStartDate: String, periodEndDate: String, counterpartyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        logger.debug("createLoan, peers: {}", this.peers())
        logger.debug("createLoan, peer names: {}", this.peerNames())
        logger.debug("createLoan, target: {}, matches: {}", counterpartyName, matches)

        val counterpartyName: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateLoanFlow.Loaner::class.java, loanNumber, loanName, loanReason, loanStatus, loanType, amountDue, amountPaid, amountRemaining, subtotal, total, dueDate, periodStartDate, periodEndDate, counterpartyName).returnValue.getOrThrow()
    }


    /** Create a Product! */
    fun createProduct(product_id: String, name: String, product_url: String, image_url: String, description: String, sku: String, sku_url: String, sku_image_url: String, ccids: String, category_breadcrumbs: String, price: Float, sale_price: Float, is_active: Int, stock_quantity: Int, stock_unit: String, brand: String, color: String, size: String, counterpartyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        StatesetService.logger.debug("createProduct, peers: {}", this.peers())
        StatesetService.logger.debug("createProduct, peer names: {}", this.peerNames())
        StatesetService.logger.debug("createProduct, target: {}, matches: {}", counterpartyName, matches)

        val counterpartyName: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateProductFlow.Initiator::class.java, product_id, name, product_url, image_url, description, sku, sku_url, sku_image_url, ccids, category_breadcrumbs, price, sale_price, is_active, stock_quantity, stock_unit, brand, color, size, counterpartyName).returnValue.getOrThrow()
    }
}