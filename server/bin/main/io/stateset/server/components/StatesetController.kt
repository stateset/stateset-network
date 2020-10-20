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

import net.corda.core.messaging.vaultQueryBy
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import sun.security.timestamp.TSResponse
import org.springframework.web.bind.annotation.PostMapping
import com.github.manosbatsis.corbeans.spring.boot.corda.config.NodeParams
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import javax.annotation.PostConstruct
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.CrossOrigin
import io.stateset.account.*
import io.stateset.agreement.Agreement
import io.stateset.agreement.AgreementStatus
import io.stateset.agreement.AgreementType
import io.stateset.application.Application
import io.stateset.application.ApplicationStatus
import io.stateset.approval.Approval
import io.stateset.approval.ApprovalStatus
import io.stateset.case.*
import io.stateset.contact.*
import io.stateset.invoice.Invoice
import io.stateset.lead.*
import io.stateset.loan.Loan
import io.stateset.loan.LoanStatus
import io.stateset.loan.LoanType
import io.stateset.message.Message
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.vault.*
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.web.multipart.MultipartFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Stateset API Endpoints
 */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
@RestController
@RequestMapping("/api/{nodeName}")
class StatesetController() {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }


    protected lateinit var defaultNodeName: String

    @Autowired
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    protected lateinit var services: Map<String, StatesetService>

    @PostConstruct
    fun postConstruct() {
        // if single node config, use the only node name as default, else reserve explicitly for cordform
        defaultNodeName = if (services.keys.size == 1) services.keys.first() else NodeParams.NODENAME_CORDFORM
        logger.debug("Auto-configured RESTful services for Corda nodes:: {}, default node: {}", services.keys, defaultNodeName)
    }


    fun getService(optionalNodeName: Optional<String>): StatesetService {
        val nodeName = if (optionalNodeName.isPresent) optionalNodeName.get() else defaultNodeName
        return this.services.get("${nodeName}NodeService")
                ?: throw IllegalArgumentException("Node not found: $nodeName")
    }

    /** Maps an Application to a JSON object. */

    private fun Application.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "agent" to agent.name.organisation,
                "provider" to provider.name.organisation,
                "counterparty" to provider.name.toString(),
                "applicationId" to applicationId,
                "applicationName" to applicationName,
                "industry" to industry,
                "applicationStatus" to applicationStatus.toString())
    }



    /** Maps an Agreement to a JSON object. */

    private fun Agreement.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "agreementNumber" to agreementNumber,
                "agreementName" to agreementName,
                "party" to party.name.organisation,
                "counterparty" to counterparty.name.organisation,
                "agreementType" to agreementType.toString(),
                "agreementStatus" to agreementStatus.toString(),
                "agreementStartDate" to agreementStartDate,
                "agreementEndDate" to agreementEndDate,
                "totalAgreementValue" to totalAgreementValue.toString(),
                "agreementHash" to agreementHash,
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
    }

    /** Maps an Application to a JSON object. */

    private fun Approval.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "submitter" to submitter.name.organisation,
                "approver" to approver.name.organisation,
                "approvalId" to approvalId,
                "approvalName" to approvalName,
                "industry" to industry,
                "approvalStatus" to approvalStatus.toString())
    }


    /** Maps an Account to a JSON object. */

    private fun Account.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "accountId" to accountId,
                "accountName" to accountName,
                "accountType" to accountType.toString(),
                "industry" to industry,
                "phone" to phone,
                "yearStarted" to yearStarted.toString(),
                "annualRevenue" to annualRevenue.toString(),
                "businessAddress" to businessAddress,
                "businessCity" to businessCity,
                "businessState" to businessState,
                "businessZipCode" to businessZipCode,
                "controller" to controller.name.organisation,
                "processor" to processor.name.organisation)
    }


    /** Maps an Contact to a JSON object. */

    private fun Contact.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "contactId" to contactId,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "phone" to phone,
                "controller" to controller.name.organisation,
                "processor" to processor.name.organisation,
                "linearId" to linearId.toString())
    }


    /** Maps an Lead to a JSON object. */


    private fun Lead.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "leadId" to leadId,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "phone" to phone,
                "controller" to controller.name.organisation,
                "processor" to processor.name.organisation,
                "linearId" to linearId.toString())
    }


    /** Maps an Case to a JSON object. */

    private fun Case.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "caseId" to caseId,
                "description" to description,
                "caseNumber" to caseNumber,
                "caseStatus" to caseStatus.toString(),
                "priority" to casePriority.toString(),
                "submitter" to submitter.toString(),
                "resolver" to resolver.toString())
    }


    /** Maps an Chat to a JSON object. */

    private fun Message.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "id" to id.toString(),
                "subject" to subject,
                "body" to body,
                "to" to to.name.organisation,
                "from" to from.name.organisation,
                "sentReceipt" to sentReceipt.toString(),
                "deliveredReceipt" to deliveredReceipt.toString(),
                "fromMe" to fromMe.toString(),
                "time" to time.toString(),
                "linearId" to linearId.toString())
    }


    private fun Invoice.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "invoiceNumber" to invoiceNumber,
                "invoiceName" to invoiceName,
                "billingReason" to billingReason,
                "amountDue" to amountDue.toString(),
                "amountPaid" to amountPaid.toString(),
                "amountRemaining" to amountRemaining.toString(),
                "subtotal" to subtotal.toString(),
                "total" to total.toString(),
                "party" to party.name.organisation,
                "counterparty" to counterparty.name.organisation,
                "dueDate" to dueDate,
                "periodStartDate" to periodStartDate,
                "periodEndDate" to periodEndDate,
                "paid" to paid.toString(),
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString()
        )
    }

    private fun Loan.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "loanNumber" to loanNumber,
                "loanName" to loanName,
                "loanReason" to loanReason,
                "loanStatus" to loanStatus.toString(),
                "loanType" to loanType.toString(),
                "amountDue" to amountDue.toString(),
                "amountPaid" to amountPaid.toString(),
                "amountRemaining" to amountRemaining.toString(),
                "subtotal" to subtotal.toString(),
                "total" to total.toString(),
                "party" to party.name.organisation,
                "counterparty" to counterparty.name.organisation,
                "dueDate" to dueDate,
                "periodStartDate" to periodStartDate,
                "periodEndDate" to periodEndDate,
                "paid" to paid.toString(),
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString()
        )
    }


    /** Returns a list of existing Messages. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getMessages")
    @ApiOperation(value = "Get Messages")
    fun getMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val sortAttribute = SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME)
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>(sorting = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.DESC)))).states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Get Messages by UserId */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getMessages/userId")
    @ApiOperation(value = "Get Messages by userId")
    fun getMessagesByUserId(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>().states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Returns a list of received Messages. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getReceivedMessages")
    @ApiOperation(value = "Get Received Messages")
    fun getRecievedMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>().states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }

    /** Returns a list of Sent Messages. */


    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getSentMessages")
    @ApiOperation(value = "Get Sent Messages")
    fun getSentMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>().states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Send Message*/

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/sendMessage")
    @ApiOperation(value = "Send a message to the target party")
    fun sendMessage(@PathVariable nodeName: Optional<String>,
                    @ApiParam(value = "The target party for the message")
                    @RequestParam(required = true) to: String,
                    @ApiParam(value = "The user Id for the message")
                    @RequestParam(required = true) userId: String,
                    @ApiParam(value = "The message subject")
                    @RequestParam(required = true) subject: String,
                    @ApiParam(value = "The message text")
                    @RequestParam("body") body: String): ResponseEntity<Any?> {


        val (status, message) = try {

            val result = getService(nodeName).sendMessage(to, userId, subject, body)

            HttpStatus.CREATED to mapOf<String, String>(
                    "subject" to "$subject",
                    "body" to "$body",
                    "to" to "$to",
                    "userId" to "$userId"
            )

        } catch (e: Exception) {
            logger.error("Error sending message to ${to}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Returns a list of existing Applications. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getApprovals")
    @ApiOperation(value = "Get Approvals")
    fun getApprovals(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val approvalStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Approval::class.java).states
        val approvalStates = approvalStateAndRefs.map { it.state.data }
        return approvalStates.map { it.toJson() }
    }


    /** Returns a list of existing Applications. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getApplications")
    @ApiOperation(value = "Get Applications")
    fun getApplications(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val applicationStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Application::class.java).states
        val applicationStates = applicationStateAndRefs.map { it.state.data }
        return applicationStates.map { it.toJson() }
    }



    /** Returns a list of existing Accounts. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getAccounts")
    @ApiOperation(value = "Get Accounts")
    fun getAccounts(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val accountStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Account::class.java).states
        val accountStates = accountStateAndRefs.map { it.state.data }
        return accountStates.map { it.toJson() }
    }


    /** Returns a list of existing Contacts. */


    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getContacts")
    @ApiOperation(value = "Get Contacts")
    fun getContacts(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val contactStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Contact::class.java).states
        val contactStates = contactStateAndRefs.map { it.state.data }
        return contactStates.map { it.toJson() }
    }


    /** Returns a list of existing Leads. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getLeads")
    @ApiOperation(value = "Get Leads")
    fun getLeads(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val leadStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Lead::class.java).states
        val leadStates = leadStateAndRefs.map { it.state.data }
        return leadStates.map { it.toJson() }
    }

    /** Returns a list of existing Cases. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping("/getCases")
    @ApiOperation(value = "Get Cases")
    fun getCases(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val caseStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Case::class.java).states
        val caseStates = caseStateAndRefs.map { it.state.data }
        return caseStates.map { it.toJson() }
    }


    /** Returns a list of existing Invoices */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping(value = "/getInvoices")
    @ApiOperation(value = "Get Invoices")
    fun getInvoices(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val invoiceStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Invoice::class.java).states
        val invoiceStates = invoiceStateAndRefs.map { it.state.data }
        return invoiceStates.map { it.toJson() }
    }


    /** Returns a list of existing Loans. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping(value = "/getLoans")
    @ApiOperation(value = "Get Loans")
    fun getLoans(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val loanStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Loan::class.java).states
        val loanStates = loanStateAndRefs.map { it.state.data }
        return loanStates.map { it.toJson() }
    }


    /** Creates an Account. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/createAccount")
    @ApiOperation(value = "Create Account")
    fun createAccount(@PathVariable nodeName: Optional<String>,
                      @RequestParam("accountId") accountId: String,
                      @RequestParam("accountName") accountName: String,
                      @RequestParam("accountType") accountType: TypeOfBusiness,
                      @RequestParam("industry") industry: String,
                      @RequestParam("phone") phone: String,
                      @RequestParam("yearStarted") yearStarted: Int,
                      @RequestParam("annualRevenue") annualRevenue: Double,
                      @RequestParam("businessAddress") businessAddress: String,
                      @RequestParam("businessCity") businessCity: String,
                      @RequestParam("businessState") businessState: String,
                      @RequestParam("businessZipCode") businessZipCode: String,
                      @RequestParam("processor") processor: String?): ResponseEntity<Any?> {


        if (processor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createAccount(accountId, accountName, accountType, industry, phone, yearStarted, annualRevenue, businessAddress, businessCity, businessState, businessZipCode, processor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "accountId" to "$accountId",
                    "accountName" to "$accountName",
                    "accountType" to "$accountType",
                    "industy" to "$industry",
                    "phone" to "$phone",
                    "yearStarted" to "$yearStarted",
                    "annualRevenue" to "$annualRevenue",
                    "processor" to "$processor"
            )

        } catch (e: Exception) {
            logger.error("Error sending Account information to ${processor}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates a Contact. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/createContact")
    @ApiOperation(value = "Create Contact")
    fun createContact(@PathVariable nodeName: Optional<String>,
                      @RequestParam("contactId") contactId: String,
                      @RequestParam("firstName") firstName: String,
                      @RequestParam("lastName") lastName: String,
                      @RequestParam("email") email: String,
                      @RequestParam("phone") phone: String,
                      @RequestParam("processor") processor: String?): ResponseEntity<Any?> {


        if (processor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createContact(contactId, firstName, lastName, email, phone, processor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "contactId" to "$contactId",
                    "processor" to "$processor"
            )

        } catch (e: Exception) {
            logger.error("Error sending Contact to ${processor}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates a Lead. */


    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/createLead")
    @ApiOperation(value = "Create Lead")
    fun createLead(@PathVariable nodeName: Optional<String>,
                   @RequestParam("leadId") leadId: String,
                   @RequestParam("firstName") firstName: String,
                   @RequestParam("lastName") lastName: String,
                   @RequestParam("company") company: String,
                   @RequestParam("title") title: String,
                   @RequestParam("email") email: String,
                   @RequestParam("phone") phone: String,
                   @RequestParam("country") country: String,
                   @RequestParam("processor") processor: String?): ResponseEntity<Any?> {


        if (processor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).createLead(leadId, firstName, lastName, company, title, email, phone, country, processor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId",
                    "processor" to "$processor"
            )

        } catch (e: Exception) {
            logger.error("Error sending Lead to ${processor}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates a Case. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/createCase")
    @ApiOperation(value = "Create Case")
    fun createCase(@PathVariable nodeName: Optional<String>,
                   @RequestParam("caseId") caseId: String,
                   @RequestParam("caseName") caseName: String,
                   @RequestParam("caseNumber") caseNumber: String,
                   @RequestParam("description") description: String,
                   @RequestParam("caseStatus") caseStatus: CaseStatus,
                   @RequestParam("casePriority") casePriority: CasePriority,
                   @RequestParam("resolver") resolver: String?): ResponseEntity<Any?> {


        if (resolver == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createCase(caseId, caseName, caseNumber, description, caseStatus, casePriority, resolver)

            HttpStatus.CREATED to mapOf<String, String>(
                    "caseId" to "$caseId",
                    "caseName" to "$caseName",
                    "caseNumber" to "$caseNumber",
                    "description" to "$description",
                    "casePriority" to "$casePriority",
                    "caseStatus" to "$caseStatus",
                    "resolver" to "$resolver"
            )

        } catch (e: Exception) {
            logger.error("Error sending case to ${resolver}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Close the Case. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/closeCase")
    @ApiOperation(value = "Close Case")
    fun closeCase(@PathVariable nodeName: Optional<String>, @RequestParam("caseId") caseId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val caseId = request.getParameter("caseId")
        val (status, message) = try {

            val result = getService(nodeName).closeCase(caseId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "caseId" to "$caseId"
            )

        } catch (e: Exception) {
            logger.error("Error closing case ${caseId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Resolve Case. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/resolveCase")
    @ApiOperation(value = "Resolve Case")
    fun resolveCase(@PathVariable nodeName: Optional<String>, @RequestParam("caseId") caseId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val caseId = request.getParameter("caseId")
        val (status, message) = try {

            val result = getService(nodeName).resolveCase(caseId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "caseId" to "$caseId"
            )

        } catch (e: Exception) {
            logger.error("Error resolving case ${caseId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Escalate Case. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/escalateCase")
    @ApiOperation(value = "Escalate Case")
    fun escalateCase(@PathVariable nodeName: Optional<String>, @RequestParam("caseId") caseId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val caseId = request.getParameter("caseId")
        val (status, message) = try {

            val result = getService(nodeName).escalateCase(caseId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "caseId" to "$caseId"
            )

        } catch (e: Exception) {
            logger.error("Error escalating case ${caseId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates an Application. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/createApplication")
    @ApiOperation(value = "Create Application")
    fun createApplication(@PathVariable nodeName: Optional<String>,
                          @RequestParam("applicationId") applicationId: String,
                          @RequestParam("applicationName") applicationName: String,
                          @RequestParam("industry") industry: String,
                          @RequestParam("applicationStatus") applicationStatus: ApplicationStatus,
                          @RequestParam("partyName") partyName: String?): ResponseEntity<Any?> {


        if (partyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createApplication(applicationId, applicationName, industry, applicationStatus, partyName)

            HttpStatus.CREATED to mapOf<String, String>(
                    "applicationd" to "$applicationId",
                    "partyName" to "$partyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending case to ${partyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }





    /** Approve Application. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/approveApplication")
    @ApiOperation(value = "Approve Application")
    fun approveApplication(@PathVariable nodeName: Optional<String>, @RequestParam("applicationId") applicationId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val applicationId = request.getParameter("agreementId")
        val (status, message) = try {

            val result = getService(nodeName).approveApplication(applicationId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "applicationId" to "$applicationId"
            )

        } catch (e: Exception) {
            logger.error("Error approving application ${applicationId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }



    /** Reject Application. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/rejectApplication")
    @ApiOperation(value = "Reject Application")
    fun rejectApplication(@PathVariable nodeName: Optional<String>, @RequestParam("applicationId") applicationId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val applicationId = request.getParameter("applicationId")
        val (status, message) = try {

            val result = getService(nodeName).rejectApplication(applicationId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "applicationId" to "$applicationId"
            )

        } catch (e: Exception) {
            logger.error("Error rejecting Application ${applicationId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }



    /** Creates an Approval. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping("/createApproval")
    @ApiOperation(value = "Create Approval")
    fun createApproval(@PathVariable nodeName: Optional<String>,
                       @RequestParam("approvalId") approvalId: String,
                       @RequestParam("approvalName") approvalName: String,
                       @RequestParam("industry") industry: String,
                       @RequestParam("approvalStatus") approvalStatus: ApprovalStatus,
                       @RequestParam("partyName") partyName: String?): ResponseEntity<Any?> {


        if (partyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createApproval(approvalId, approvalName, industry, approvalStatus, partyName)

            HttpStatus.CREATED to mapOf<String, String>(
                    "approvald" to "$approvalId",
                    "partyName" to "$partyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending case to ${partyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Approve Approval. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/approveApproval")
    @ApiOperation(value = "Approve Approval")
    fun approveApproval(@PathVariable nodeName: Optional<String>, @RequestParam("approvalId") approvalId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val approvalId = request.getParameter("agreementId")
        val (status, message) = try {

            val result = getService(nodeName).approve(approvalId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "approvalId" to "$approvalId"
            )

        } catch (e: Exception) {
            logger.error("Error approving approval ${approvalId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Reject Approval. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/rejectApproval")
    @ApiOperation(value = "Reject Approval")
    fun rejectApproval(@PathVariable nodeName: Optional<String>, @RequestParam("approvalId") approvalId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val approvalId = request.getParameter("approvalId")
        val (status, message) = try {

            val result = getService(nodeName).reject(approvalId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "approvalId" to "$approvalId"
            )

        } catch (e: Exception) {
            logger.error("Error rejecting Approval ${approvalId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Returns a list of existing Agreements. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping(value = "/getAgreements")
    @ApiOperation(value = "Get Agreements")
    fun getAgreements(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val agreementStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Agreement::class.java).states
        val agreementStates = agreementStateAndRefs.map { it.state.data }
        return agreementStates.map { it.toJson() }
    }


    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping(value = "/getAllAgreements")
    @ApiOperation(value = "Get All Agreements")
    fun agreements(@PathVariable nodeName: Optional<String>): List<Agreement> {
        val agreementStatesAndRefs = this.getService(nodeName).proxy().vaultQuery(Agreement::class.java).states
        return agreementStatesAndRefs
                .map { agreementStateAndRef -> agreementStateAndRef.state.data }
                .map { state ->

                    Agreement(state.party.name.organisation,
                            state.counterparty.name.organisation,
                            state.agreementName,
                            state.agreementStatus,
                            state.agreementType,
                            state.totalAgreementValue,
                            state.party,
                            state.counterparty,
                            state.agreementStartDate,
                            state.agreementEndDate,
                            state.active,
                            state.createdAt,
                            state.lastUpdated,
                            state.linearId)
                }
    }


    /** Creates an Agreement. */

    /** Searchable PDF is mapped by agreement linearId **/
    /** Endpoint setup in BaaR OCR tool and State is created **/

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/createAgreement")
    @ApiOperation(value = "Create Agreement")
    fun createAgreement(@PathVariable nodeName: Optional<String>,
                        @RequestParam("agreementNumber") agreementNumber: String,
                        @RequestParam("agreementName") agreementName: String,
                        @RequestParam("agreementHash") agreementHash: String,
                        @RequestParam("agreementStatus") agreementStatus: AgreementStatus,
                        @RequestParam("agreementType") agreementType: AgreementType,
                        @RequestParam("totalAgreementValue") totalAgreementValue: Int,
                        @RequestParam("agreementStartDate") agreementStartDate: String,
                        @RequestParam("agreementEndDate") agreementEndDate: String,
                        @RequestParam("counterpartyName") counterpartyName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        if (counterpartyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createAgreement(agreementNumber, agreementName, agreementHash, agreementStatus, agreementType, totalAgreementValue, counterpartyName, agreementStartDate, agreementEndDate)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber",
                    "agreementName" to "$agreementName",
                    "agreementHash" to "$agreementHash",
                    "agreementStatus" to "$agreementStatus",
                    "agreementType" to "$agreementType",
                    "totalAgreementValue" to "$totalAgreementValue",
                    "agreementStartDate" to "$agreementStartDate",
                    "agreementEndDate" to "$agreementEndDate",
                    "party" to "$nodeName",
                    "counterpartyName" to "$counterpartyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending Agreement to ${counterpartyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Activate Agreement. */
    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/activateAgreement")
    @ApiOperation(value = "Activate Agreement")
    fun activateAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).activateAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error activating Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Terminate Agreement. */
    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/terminateAgreement")
    @ApiOperation(value = "Terminate Agreement")
    fun terminateAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).terminateAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error terminating Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Renew Agreement. */
    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/renewAgreement")
    @ApiOperation(value = "Renew Agreement")
    fun renweAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).renewAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error renewing Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Amend Agreement. */
    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/amendAgreement")
    @ApiOperation(value = "Amend Agreement")
    fun amendAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).amendAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error amending Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Upload the File. */
    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/upload")
    @ApiOperation(value = "Upload Agreement")
    fun upload(@PathVariable nodeName: Optional<String>, @RequestParam file: MultipartFile, @RequestParam uploader: String): ResponseEntity<String> {
        val filename = file.originalFilename
        require(filename != null) { "File name must be set" }
        val hash: SecureHash = if (!(file.contentType == "zip" || file.contentType == "jar")) {
            uploadZip(nodeName, file.inputStream, uploader, filename!!)
        } else {
            this.getService(nodeName).proxy().uploadAttachmentWithMetadata(
                    jar = file.inputStream,
                    uploader = uploader,
                    filename = filename!!
            )
        }
        return ResponseEntity.created(URI.create("attachments/$hash")).body("Attachment uploaded with hash - $hash")
    }

    private fun uploadZip(nodeName: Optional<String>, inputStream: InputStream, uploader: String, filename: String): AttachmentId {
        val zipName = "$filename-${UUID.randomUUID()}.zip"
        FileOutputStream(zipName).use { fileOutputStream ->
            ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                val zipEntry = ZipEntry(filename)
                zipOutputStream.putNextEntry(zipEntry)
                inputStream.copyTo(zipOutputStream, 1024)
            }
        }
        return FileInputStream(zipName).use { fileInputStream ->
            val hash = this.getService(nodeName).proxy().uploadAttachmentWithMetadata(
                    jar = fileInputStream,
                    uploader = uploader,
                    filename = filename
            )
            Files.deleteIfExists(Paths.get(zipName))
            hash
        }
    }


    /** Download the File. */
    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @GetMapping(value = "/download")
    @ApiOperation(value = "Download Agreement")
    fun downloadByName(@PathVariable nodeName: Optional<String>, @RequestParam name: String): ResponseEntity<InputStreamResource> {
        val attachmentIds: List<AttachmentId> = this.getService(nodeName).proxy().queryAttachments(
                AttachmentQueryCriteria.AttachmentsQueryCriteria(filenameCondition = Builder.equal(name)),
                null
        )
        val inputStreams = attachmentIds.map { this.getService(nodeName).proxy().openAttachment(it) }
        val zipToReturn = if (inputStreams.size == 1) {
            inputStreams.single()
        } else {
            combineZips(inputStreams, name)
        }
        return ResponseEntity.ok().header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"$name.zip\""
        ).body(InputStreamResource(zipToReturn))
    }

    private fun combineZips(inputStreams: List<InputStream>, filename: String): InputStream {
        val zipName = "$filename-${UUID.randomUUID()}.zip"
        FileOutputStream(zipName).use { fileOutputStream ->
            ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                inputStreams.forEachIndexed { index, inputStream ->
                    val zipEntry = ZipEntry("$filename-$index.zip")
                    zipOutputStream.putNextEntry(zipEntry)
                    inputStream.copyTo(zipOutputStream, 1024)
                }
            }
        }
        return try {
            FileInputStream(zipName)
        } finally {
            Files.deleteIfExists(Paths.get(zipName))
        }
    }

    /** Creates an Invoice. */

    /** Searchable PDF is mapped by invoice linearId **/
    /** Endpoint setup in BaaR OCR tool and State is created **/

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/createInvoice")
    @ApiOperation(value = "Create Invoice")
    fun createInvoice(@PathVariable nodeName: Optional<String>,
                      @RequestParam("invoiceNumber") invoiceNumber: String,
                      @RequestParam("invoiceName") invoiceName: String,
                      @RequestParam("billingReason") billingReason: String,
                      @RequestParam("amountDue") amountDue: Int,
                      @RequestParam("amountPaid") amountPaid: Int,
                      @RequestParam("amountRemaining") amountRemaining: Int,
                      @RequestParam("subtotal") subtotal: Int,
                      @RequestParam("total") total: Int,
                      @RequestParam("dueDate") dueDate: String,
                      @RequestParam("periodStartDate") periodStartDate: String,
                      @RequestParam("periodEndDate") periodEndDate: String,
                      @RequestParam("counterpartyName") counterpartyName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        if (counterpartyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createInvoice(invoiceNumber, invoiceName, billingReason, amountDue, amountPaid, amountRemaining, subtotal, total, dueDate, periodStartDate, periodEndDate, counterpartyName)

            HttpStatus.CREATED to mapOf<String, String>(
                    "invoiceNumber" to "$invoiceNumber",
                    "party" to "$nodeName",
                    "counterpartyName" to "$counterpartyName",
                    "amountDue" to "$amountDue",
                    "amountPaid" to "$amountPaid",
                    "amountRemaining" to "$amountRemaining",
                    "subtotal" to "$subtotal",
                    "total" to "$total",
                    "dueDate" to "$dueDate",
                    "periodStartDate" to "$periodStartDate",
                    "periodEndDate" to "$periodEndDate",
                    "party" to "$nodeName",
                    "counterpartyName" to "$counterpartyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending Invoice to ${counterpartyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com", "https://na57.lightning.force.com", "https://stateset.io", "https://stateset.in", "https://stateset.network"])
    @PostMapping(value = "/createLoan")
    @ApiOperation(value = "Create Loan")
    fun createLoan(@PathVariable nodeName: Optional<String>,
                   @RequestParam("loanNumber") loanNumber: String,
                   @RequestParam("loanName") loanName: String,
                   @RequestParam("loanReason") loanReason: String,
                   @RequestParam("loanStatus") loanStatus: LoanStatus,
                   @RequestParam("loanType") loanType: LoanType,
                   @RequestParam("amountDue") amountDue: Int,
                   @RequestParam("amountPaid") amountPaid: Int,
                   @RequestParam("amountRemaining") amountRemaining: Int,
                   @RequestParam("subtotal") subtotal: Int,
                   @RequestParam("total") total: Int,
                   @RequestParam("dueDate") dueDate: String,
                   @RequestParam("periodStartDate") periodStartDate: String,
                   @RequestParam("periodEndDate") periodEndDate: String,
                   @RequestParam("counterpartyName") counterpartyName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        if (counterpartyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createLoan(loanNumber, loanName, loanReason, loanStatus, loanType, amountDue, amountPaid, amountRemaining, subtotal, total, dueDate, periodStartDate, periodEndDate, counterpartyName)

            HttpStatus.CREATED to mapOf<String, String>(
                    "loanNumber" to "$loanNumber",
                    "loanName" to "$loanName",
                    "loanStatus" to "$loanStatus",
                    "loanType" to "$loanType",
                    "amountDue" to "$amountDue",
                    "amountPaid" to "$amountPaid",
                    "amountRemaining" to "$amountRemaining",
                    "subtotal" to "$subtotal",
                    "total" to "$total",
                    "dueDate" to "$dueDate",
                    "periodStartDate" to "$periodStartDate",
                    "periodEndDate" to "$periodEndDate",
                    "party" to "$nodeName",
                    "counterpartyName" to "$counterpartyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending Loan to ${counterpartyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }
}

/*

/** Send UPI Payment */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://stateset.io", "https://stateset.in"])
@PostMapping(value = "/pay")
fun sendPayment(@RequestParam("pa") pa: String,
                @RequestParam("pn") pn: String,
                @RequestParam("mc") mc: String,
                @RequestParam("tid") tid: String,
                @RequestParam("tr") tr: String,
                @RequestParam("tn") tn: String,
                @RequestParam("am") am: String,
                @RequestParam("mam") mam: String,
                @RequestParam("cu") cu: String,
                @RequestParam("url") url : String): ResponseEntity<Any?> {

    if (tid == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'tid' can not be null.\n")
    }

    if (pn == null) {
        return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'pn' missing or has wrong format.\n")
    }

    val counterparty = CordaX500Name.parse(pn)


    val pn = proxy.wellKnownPartyFromX500Name(counterparty)
            ?: return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Party named $pn cannot be found.\n")

    val (status, message) = try {


        val flowHandle = proxy.startFlowDynamic(SendPaymentFlow.InitiatePaymentRequest::class.java, pa, pn, mc, tid, tr, tn, am, mam, cu, url)

        val result = flowHandle.use { it.returnValue.getOrThrow() }

        HttpStatus.CREATED to "Payment sent."

    } catch (e: Exception) {
        HttpStatus.BAD_REQUEST to e.message
    }
    logger.info(message)
    return ResponseEntity<Any?>(message, status)
}


/** Send Proxy Re-encryption Policy */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://stateset.io", "https://stateset.in"])
@PostMapping(value = "/policy")
fun sendPolicy(@RequestParam("alice") alice: String,
            @RequestParam("enrico") enrico: String,
            @RequestParam("bob") bob: String,
            @RequestParam("policyName") policyName: String,
            @RequestParam("policyExpirationDate") policyExpirationDate: String,
            @RequestParam("policyPassword") policyPassword: String,
            @RequestParam("policyId") policyId: String): ResponseEntity<Any?> {

if (policyId == null) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'tid' can not be null.\n")
}

if (bob == null) {
    return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'pn' missing or has wrong format.\n")
}

val counterparty = CordaX500Name.parse(bob)

val bob = proxy.wellKnownPartyFromX500Name(counterparty)
        ?: return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Party named $bob cannot be found.\n")

val (status, message) = try {


    val flowHandle = proxy.startFlowDynamic(SendPolicyFlow.InitiatePolicyRequest::class.java, alice, enrico, bob, policyName, policyExpirationDate, policyPassword, policyId)

    val result = flowHandle.use { it.returnValue.getOrThrow() }

    HttpStatus.CREATED to "Payment sent."

} catch (e: Exception) {
    HttpStatus.BAD_REQUEST to e.message
}
logger.info(message)
return ResponseEntity<Any?>(message, status)
}

*/

    /*

    /** Send UPI Payment */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://stateset.io", "https://stateset.in"])
    @PostMapping(value = "/pay")
    fun sendPayment(@RequestParam("pa") pa: String,
                    @RequestParam("pn") pn: String,
                    @RequestParam("mc") mc: String,
                    @RequestParam("tid") tid: String,
                    @RequestParam("tr") tr: String,
                    @RequestParam("tn") tn: String,
                    @RequestParam("am") am: String,
                    @RequestParam("mam") mam: String,
                    @RequestParam("cu") cu: String,
                    @RequestParam("url") url : String): ResponseEntity<Any?> {

        if (tid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'tid' can not be null.\n")
        }

        if (pn == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'pn' missing or has wrong format.\n")
        }

        val counterparty = CordaX500Name.parse(pn)


        val pn = proxy.wellKnownPartyFromX500Name(counterparty)
                ?: return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Party named $pn cannot be found.\n")

        val (status, message) = try {


            val flowHandle = proxy.startFlowDynamic(SendPaymentFlow.InitiatePaymentRequest::class.java, pa, pn, mc, tid, tr, tn, am, mam, cu, url)

            val result = flowHandle.use { it.returnValue.getOrThrow() }

            HttpStatus.CREATED to "Payment sent."

        } catch (e: Exception) {
            HttpStatus.BAD_REQUEST to e.message
        }
        logger.info(message)
        return ResponseEntity<Any?>(message, status)
    }


/** Send Proxy Re-encryption Policy */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://stateset.io", "https://stateset.in"])
@PostMapping(value = "/policy")
fun sendPolicy(@RequestParam("alice") alice: String,
                @RequestParam("enrico") enrico: String,
                @RequestParam("bob") bob: String,
                @RequestParam("policyName") policyName: String,
                @RequestParam("policyExpirationDate") policyExpirationDate: String,
                @RequestParam("policyPassword") policyPassword: String,
                @RequestParam("policyId") policyId: String): ResponseEntity<Any?> {

    if (policyId == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'tid' can not be null.\n")
    }

    if (bob == null) {
        return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'pn' missing or has wrong format.\n")
    }

    val counterparty = CordaX500Name.parse(bob)

    val bob = proxy.wellKnownPartyFromX500Name(counterparty)
            ?: return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Party named $bob cannot be found.\n")

    val (status, message) = try {


        val flowHandle = proxy.startFlowDynamic(SendPolicyFlow.InitiatePolicyRequest::class.java, alice, enrico, bob, policyName, policyExpirationDate, policyPassword, policyId)

        val result = flowHandle.use { it.returnValue.getOrThrow() }

        HttpStatus.CREATED to "Payment sent."

    } catch (e: Exception) {
        HttpStatus.BAD_REQUEST to e.message
    }
    logger.info(message)
    return ResponseEntity<Any?>(message, status)
}

*/
