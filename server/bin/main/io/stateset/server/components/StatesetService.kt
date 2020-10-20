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
import io.stateset.account.TypeOfBusiness
import io.stateset.agreement.AgreementStatus
import io.stateset.agreement.AgreementType
import io.stateset.application.ApplicationStatus
import io.stateset.approval.ApprovalStatus
import io.stateset.case.CasePriority
import io.stateset.case.CaseStatus
import io.stateset.loan.LoanStatus
import io.stateset.loan.LoanType
import io.stateset.message.Message
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
    fun sendMessage(to: String, userId: String, subject: String, body: String): SignedTransaction {
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
        return proxy.startFlowDynamic(SendMessageFlow::class.java, to, userId, subject, body).returnValue.getOrThrow()
    }

    /** Create an Application */
    fun createApplication(applicationId: String, applicationName: String, industry: String, applicationStatus: ApplicationStatus, partyName: String ): SignedTransaction {
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
        return proxy.startFlowDynamic(CreateApplicationFlow.Initiator::class.java, applicationId, applicationName, industry, applicationStatus, processor).returnValue.getOrThrow()
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
    fun createApproval(approvalId: String, approvalName: String, industry: String, approvalStatus: ApprovalStatus, partyName: String): SignedTransaction {
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
        return proxy.startFlowDynamic(CreateApprovalFlow.Initiator::class.java, approvalId, approvalName, industry, approvalStatus, processor).returnValue.getOrThrow()
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
    fun createAccount(accountId: String, accountName: String, accountType: TypeOfBusiness, industry: String, phone: String, yearStarted: Int, annualRevenue: Double, businessAddress: String, businessCity: String, businessState: String, businessZipCode: String, processor: String): SignedTransaction {
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
        return proxy.startFlowDynamic(CreateAccountFlow.Controller::class.java, accountId, accountName, accountType, industry, phone, yearStarted, annualRevenue, businessAddress, businessCity, businessState, businessZipCode, processor).returnValue.getOrThrow()
    }


    /** Create a Contact! */
    fun createContact(contactId: String, firstName: String, lastName: String, phone: String, email: String, processor: String): SignedTransaction {
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
        return proxy.startFlowDynamic(CreateContactFlow.Controller::class.java, contactId, firstName, lastName, phone, email, processor).returnValue.getOrThrow()
    }


    /** Create a Lead */
    fun createLead(leadId: String, firstName: String, lastName: String, company: String, title: String, phone: String, email: String, country: String, processor: String): SignedTransaction {
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
        return proxy.startFlowDynamic(CreateLeadFlow.Controller::class.java, leadId, firstName, lastName, company, title, phone, email, country, processor).returnValue.getOrThrow()
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
    fun createInvoice(invoiceNumber: String, invoiceName: String, billingReason: String, amountDue: Int, amountPaid: Int, amountRemaining: Int, subtotal: Int, total: Int, dueDate: String, periodStartDate: String, periodEndDate: String, counterpartyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        logger.debug("createInvoice, peers: {}", this.peers())
        logger.debug("createInvoice, peer names: {}", this.peerNames())
        logger.debug("createInvoice, target: {}, matches: {}", counterpartyName, matches)

        val counterpartyName: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateInvoiceFlow.Invoicer::class.java, invoiceNumber, invoiceName, billingReason, amountDue, amountPaid, amountRemaining, subtotal, total, dueDate, periodStartDate, periodEndDate, counterpartyName).returnValue.getOrThrow()
    }

    /** Create a Loan! */
    fun createLoan(loanNumber: String, loanName: String, loanReason: String, loanStatus: LoanStatus, loanType: LoanType,  amountDue: Int, amountPaid: Int, amountRemaining: Int, subtotal: Int, total: Int, dueDate: String, periodStartDate: String, periodEndDate: String, counterpartyName: String): SignedTransaction {
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
}