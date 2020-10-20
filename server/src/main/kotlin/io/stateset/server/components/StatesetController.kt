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
import io.stateset.application.*
import io.stateset.approval.Approval
import io.stateset.approval.ApprovalStatus
import io.stateset.case.*
import io.stateset.contact.*
import io.stateset.invoice.Invoice
import io.stateset.lead.*
import io.stateset.lead.LeadRating
import io.stateset.loan.Loan
import io.stateset.loan.LoanStatus
import io.stateset.loan.LoanType
import io.stateset.message.Message
import io.stateset.product.Product
import io.stateset.proposal.Proposal
import io.stateset.proposal.ProposalStatus
import io.stateset.proposal.ProposalType
import io.stateset.purchaseorder.PurchaseOrder
import io.stateset.purchaseorder.PurchaseOrderStatus
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.Vault
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

@CrossOrigin(origins = ["http://localhost:8080"])
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

    private fun Application.toJson(): Map<String, String?> {
        return kotlin.collections.mapOf(
                "applicationId" to applicationId,
                "applicationName" to applicationName,
                "businessAgeRange" to businessAgeRange.toString(),
                "businessEmail" to businessEmail,
                "businessPhone" to businessPhone,
                "businessRevenueRange" to businessRevenueRange.toString(),
                "businessType" to businessType.toString(),
                "estimatedPurchaseAmount" to estimatedPurchaseAmount.toString(),
                "estimatedPurchaseFrequency" to estimatedPurchaseFrequency.toString(),
                "submitted" to submitted.toString(),
                "submittedAt" to submittedAt.toString(),
                "industry" to industry,
                "applicationStatus" to applicationStatus.toString(),
                "agent" to agent.name.organisation,
                "provider" to provider.name.organisation,
                "active" to active.toString(),
                "createdAt" to createdAt,
                "lastUpdated" to lastUpdated)
    }

    /** Maps a Proposal to a JSON object. */

    private fun Proposal.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "proposalNumber" to proposalNumber,
                "proposalName" to proposalName,
                "party" to party.name.organisation,
                "counterparty" to counterparty.name.organisation,
                "proposalType" to proposalType.toString(),
                "proposalStatus" to proposalStatus.toString(),
                "proposalStartDate" to proposalStartDate,
                "proposalEndDate" to proposalEndDate,
                "totalProposalValue" to totalProposalValue.toString(),
                "proposalHash" to proposalHash,
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
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


    /** Maps a Purchase Order to a JSON object. */

    private fun PurchaseOrder.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "purchaseOrderNumber" to purchaseOrderNumber,
                "purchaseOrderName" to purchaseOrderName,
                "purchaseOrderHash" to purchaseOrderHash,
                "description" to description,
                "purchaser" to purchaser.name.organisation,
                "vendor" to vendor.name.organisation,
                "financer" to financer.name.organisation,
                "purchaseOrderStatus" to purchaseOrderStatus.toString(),
                "purchaseDate" to purchaseDate,
                "deliveryDate" to deliveryDate,
                "subtotal" to subtotal.toString(),
                "total" to total.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
    }

    /** Maps an Application to a JSON object. */

    private fun Approval.toJson(): Map<String, String?> {
        return kotlin.collections.mapOf(
                "approvalId" to approvalId,
                "approvalName" to approvalName,
                "description" to description,
                "industry" to industry,
                "submitter" to submitter.name.organisation,
                "approver" to approver.name.organisation,
                "approvalStatus" to approvalStatus.toString(),
                "createdAt" to createdAt,
                "lastUpdated" to lastUpdated)
    }


    /** Maps an Account to a JSON object. */

    private fun Account.toJson(): Map<String, String?> {
        return kotlin.collections.mapOf(
                "accountId" to accountId,
                "accountName" to accountName,
                "accountType" to accountType.toString(),
                "industry" to industry,
                "phone" to phone,
                "yearStarted" to yearStarted.toString(),
                "website" to website,
                "rating" to rating.toString(),
                "annualRevenue" to annualRevenue.toString(),
                "businessAddress" to businessAddress,
                "businessCity" to businessCity,
                "businessState" to businessState,
                "businessZipCode" to businessZipCode,
                "controller" to controller.name.organisation,
                "processor" to processor.name.organisation,
                "createdAt" to createdAt,
                "lastUpdated" to lastUpdated)
    }


    /** Maps an Contact to a JSON object. */

    private fun Contact.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "contactId" to contactId,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "phone" to phone,
                "rating" to rating.toString(),
                "contactSource" to contactSource.toString(),
                "contactStatus" to contactStatus.toString(),
                "country" to country,
                "controller" to controller.name.organisation,
                "processor" to processor.name.organisation,
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
    }


    /** Maps an Lead to a JSON object. */


    private fun Lead.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "leadId" to leadId,
                "firstName" to firstName,
                "lastName" to lastName,
                "company" to company,
                "title" to title,
                "email" to email,
		        "title" to title,
		        "company" to company,
                "country" to country,
                "phone" to phone,
                "rating" to rating.toString(),
                "leadSource" to leadSource.toString(),
                "leadStatus" to leadStatus.toString(),
                "salesRegion" to salesRegion.toString(),
                "country" to country,
                "controller" to controller.name.organisation,
                "processor" to processor.name.organisation,
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
    }


    /** Maps an Case to a JSON object. */

    private fun Case.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "caseId" to caseId,
                "caseName" to caseName,
                "description" to description,
                "caseNumber" to caseNumber,
                "caseStatus" to caseStatus.toString(),
                "priority" to casePriority.toString(),
                "submitter" to submitter.toString(),
                "resolver" to resolver.toString(),
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
    }


    /** Maps an Chat to a JSON object. */

    private fun Message.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "id" to id.toString(),
                "subject" to subject,
                "body" to body,
                "to" to to.name.organisation,
                "toUserId" to toUserId,
                "from" to from.name.organisation,
                "fromUserId" to fromUserId,
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
                "factor" to factor.name.organisation,
                "dueDate" to dueDate,
                "periodStartDate" to periodStartDate,
                "periodEndDate" to periodEndDate,
                "paid" to paid.toString(),
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
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


    private fun Product.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "product_id" to product_id,
                "name" to name,
                "product_url" to product_url,
                "image_url" to image_url,
                "description" to description,
                "sku" to sku,
                "sku_url" to sku_url,
                "sku_image_url" to sku_image_url,
                "ccids" to ccids,
                "category_breadcrumbs" to category_breadcrumbs,
                "price" to price.toString(),
                "sale_price" to sale_price.toString(),
                "is_active" to is_active.toString(),
                "stock_quantity" to stock_quantity.toString(),
                "stock_unit" to stock_unit,
                "brand" to brand,
                "color" to color,
                "size" to size,
                "party" to party.name.organisation,
                "counterparty" to counterparty.name.organisation,
                "createdAt" to createdAt,
                "lastUpdated" to lastUpdated,
                "linearId" to linearId.toString())
    }


    /** Returns a list of existing Messages. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getMessages")
    @ApiOperation(value = "Get Messages")
    fun getMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val sortAttribute = SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME)
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>(sorting = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.DESC)))).states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Get Messages by UserId */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getMessages/userId")
    @ApiOperation(value = "Get Messages by userId")
    fun getMessagesByUserId(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>().states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Returns a list of received Messages. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getReceivedMessages")
    @ApiOperation(value = "Get Received Messages")
    fun getRecievedMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>().states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }

    /** Returns a list of Sent Messages. */


    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getSentMessages")
    @ApiOperation(value = "Get Sent Messages")
    fun getSentMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQueryBy<Message>().states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Send Message*/

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/sendMessage")
    @ApiOperation(value = "Send a message to the target party")
    fun sendMessage(@PathVariable nodeName: Optional<String>,
                    @ApiParam(value = "The target party for the message")
                    @RequestParam(required = true) to: String,
                    @ApiParam(value = "The user Id for the message")
                    @RequestParam(required = true) toUserId: String,
                    @ApiParam(value = "The from user Id for the message")
                    @RequestParam(required = true) fromUserId: String,
                    @ApiParam(value = "The message subject")
                    @RequestParam(required = true) subject: String,
                    @ApiParam(value = "The message text")
                    @RequestParam("body") body: String): ResponseEntity<Any?> {


        val (status, message) = try {

            val result = getService(nodeName).sendMessage(to, toUserId, fromUserId, subject, body)

            HttpStatus.CREATED to mapOf<String, String>(
                    "subject" to "$subject",
                    "body" to "$body",
                    "to" to "$to",
                    "toUserId" to "$toUserId",
                    "fromUserId" to "$fromUserId"
            )

        } catch (e: Exception) {
            logger.error("Error sending message to ${to}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Returns a list of existing Applications. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getApprovals")
    @ApiOperation(value = "Get Approvals")
    fun getApprovals(@PathVariable nodeName: Optional<String>): List<Map<String, String?>> {
        val approvalStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Approval::class.java).states
        val approvalStates = approvalStateAndRefs.map { it.state.data }
        return approvalStates.map { it.toJson() }
    }


    /** Returns a list of existing Applications. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getApplications")
    @ApiOperation(value = "Get Applications")
    fun getApplications(@PathVariable nodeName: Optional<String>): List<Map<String, String?>> {
        val applicationStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Application::class.java).states
        val applicationStates = applicationStateAndRefs.map { it.state.data }
        return applicationStates.map { it.toJson() }
    }

    /** Returns a list of existing Proposals. */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping(value = "/getProposals")
    @ApiOperation(value = "Get Proposals")
    fun getProposals(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val proposalStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Proposal::class.java).states
        val proposalStates = proposalStateAndRefs.map { it.state.data }
        return proposalStates.map { it.toJson() }
    }

    /** Returns a list of existing Purchase Orders. */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping(value = "/getPurchaseOrders")
    @ApiOperation(value = "Get Purchase Orders")
    fun getPurchaseOrders(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val purchaseOrderStateAndRefs = this.getService(nodeName).proxy().vaultQuery(PurchaseOrder::class.java).states
        val purchaseOrderStates = purchaseOrderStateAndRefs.map { it.state.data }
        return purchaseOrderStates.map { it.toJson() }
    }


    /** Returns a list of existing Accounts. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getAccounts")
    @ApiOperation(value = "Get Accounts")
    fun getAccounts(@PathVariable nodeName: Optional<String>): List<Map<String, String?>> {
        val accountStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Account::class.java).states
        val accountStates = accountStateAndRefs.map { it.state.data }
        return accountStates.map { it.toJson() }
    }


    /** Returns a list of existing Contacts. */


    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getContacts")
    @ApiOperation(value = "Get Contacts")
    fun getContacts(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val contactStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Contact::class.java).states
        val contactStates = contactStateAndRefs.map { it.state.data }
        return contactStates.map { it.toJson() }
    }


    /** Returns a list of existing Leads. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getLeads")
    @ApiOperation(value = "Get Leads")
    fun getLeads(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val leadStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Lead::class.java).states
        val leadStates = leadStateAndRefs.map { it.state.data }
        return leadStates.map { it.toJson() }
    }

    /** Returns a list of existing states for a LinearId. */

   // @CrossOrigin(origins = ["http://localhost:8080"])
   // @GetMapping("/getLinearStates")
   // @ApiOperation(value = "Get Linear States")
   // fun getLinearStates(@PathVariable nodeName: Optional<String>,
    //                    @RequestParam("linearId") linearId: UniqueIdentifier): List<Map<String, String>> {
   //     val linearStateCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = linearId, status = Vault.StateStatus.ALL)
   //     val vaultCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
   //     val result = this.getService(nodeName).proxy().vaultQuery.(<LinearState>(linearStateCriteria and vaultCriteria))
   // }
    /** Returns a list of existing Cases. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping("/getCases")
    @ApiOperation(value = "Get Cases")
    fun getCases(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val caseStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Case::class.java).states
        val caseStates = caseStateAndRefs.map { it.state.data }
        return caseStates.map { it.toJson() }
    }


    /** Returns a list of existing Invoices */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping(value = "/getInvoices")
    @ApiOperation(value = "Get Invoices")
    fun getInvoices(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val invoiceStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Invoice::class.java).states
        val invoiceStates = invoiceStateAndRefs.map { it.state.data }
        return invoiceStates.map { it.toJson() }
    }


    /** Returns a list of existing Loans. */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping(value = "/getLoans")
    @ApiOperation(value = "Get Loans")
    fun getLoans(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val loanStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Loan::class.java).states
        val loanStates = loanStateAndRefs.map { it.state.data }
        return loanStates.map { it.toJson() }
    }


    /** Returns a list of existing Products. */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping(value = "/getProducts")
    @ApiOperation(value = "Get Products")
    fun getProducts(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val productStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Product::class.java).states
        val productOrderStates = productStateAndRefs.map { it.state.data }
        return productOrderStates.map { it.toJson() }
    }


    /** Creates an Account. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/createAccount")
    @ApiOperation(value = "Create Account")
    fun createAccount(@PathVariable nodeName: Optional<String>,
                      @RequestParam("accountId") accountId: String,
                      @RequestParam("accountName") accountName: String,
                      @RequestParam("accountType") accountType: TypeOfBusiness,
                      @RequestParam("industry") industry: String,
                      @RequestParam("phone") phone: String,
                      @RequestParam("yearStarted") yearStarted: Int,
                      @RequestParam("website") website: String,
                      @RequestParam("rating") rating: AccountRating,
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

            val result = getService(nodeName).createAccount(accountId, accountName, accountType, industry, phone, yearStarted, website, rating, annualRevenue, businessAddress, businessCity, businessState, businessZipCode, processor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "accountId" to "$accountId",
                    "accountName" to "$accountName",
                    "accountType" to "$accountType",
                    "industy" to "$industry",
                    "phone" to "$phone",
                    "yearStarted" to "$yearStarted",
                    "website" to "$website",
                    "rating" to "$rating",
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


    /** Transfers an Account. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/transferAccount")
    @ApiOperation(value = "Transfer Account")
    fun transferAccount(@PathVariable nodeName: Optional<String>,
                        @RequestParam("accountId") accountId: String,
                        @RequestParam("newController") newController: String?): ResponseEntity<Any?> {

        if (newController == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newController' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).transferAccount(accountId, newController)

            HttpStatus.CREATED to mapOf<String, String>(
                    "accountId" to "$accountId",
                    "newController" to "$newController"
            )

        } catch (e: Exception) {
            logger.error("Error transfering Account to ${newController}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates a Contact. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/createContact")
    @ApiOperation(value = "Create Contact")
    fun createContact(@PathVariable nodeName: Optional<String>,
                      @RequestParam("contactId") contactId: String,
                      @RequestParam("firstName") firstName: String,
                      @RequestParam("lastName") lastName: String,
                      @RequestParam("email") email: String,
                      @RequestParam("phone") phone: String,
                      @RequestParam("rating") rating: ContactRating,
                      @RequestParam("contactSource") contactSource: ContactSource,
                      @RequestParam("contactStatus") contactStatus: ContactStatus,
                      @RequestParam("processor") processor: String?): ResponseEntity<Any?> {


        if (processor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createContact(contactId, firstName, lastName, email, phone, rating, contactSource, contactStatus, processor)

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


    /** Transfers a Contact. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/transferContact")
    @ApiOperation(value = "Transfer Contact")
    fun transferContact(@PathVariable nodeName: Optional<String>,
                        @RequestParam("contactId") contactId: String,
                        @RequestParam("newController") newController: String?): ResponseEntity<Any?> {

        if (newController == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newController' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).transferContact(contactId, newController)

            HttpStatus.CREATED to mapOf<String, String>(
                    "contactId" to "$contactId",
                    "newController" to "$newController"
            )

        } catch (e: Exception) {
            logger.error("Error transfering Contact to ${newController}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates a Lead. */


    @CrossOrigin(origins = ["http://localhost:8080"])
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
                   @RequestParam("rating") rating: LeadRating,
                   @RequestParam("leadSource") leadSource: LeadSource,
                   @RequestParam("leadStatus") leadStatus: LeadStatus,
                   @RequestParam("salesRegion") salesRegion: SalesRegion,
                   @RequestParam("country") country: String,
                   @RequestParam("processor") processor: String?): ResponseEntity<Any?> {


        if (processor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).createLead(leadId, firstName, lastName, company, title, email, phone, rating, leadSource, leadStatus, salesRegion, country, processor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId",
                    "firstName" to "$firstName",
                    "lastName" to "$lastName",
                    "title" to "$title",
                    "company" to "$company",
                    "country" to "$country",
                    "email" to "$email",
                    "phone" to "$phone",
                    "processor" to "$processor"
            )

        } catch (e: Exception) {
            logger.error("Error sending Lead to ${processor}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Transfers a Lead Controller */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/transferLeadController")
    @ApiOperation(value = "Transfer Lead Controller")
    fun transferLeadController(@PathVariable nodeName: Optional<String>,
                     @RequestParam("leadId") leadId: String,
                     @RequestParam("newController") newController: String?): ResponseEntity<Any?> {

        if (newController == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newController' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).transferLeadController(leadId, newController)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId",
                    "newController" to "$newController"
            )

        } catch (e: Exception) {
            logger.error("Error transfering Lead to ${newController}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Transfers a Lead Processor */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/transferLeadProcessor")
    @ApiOperation(value = "Transfer Lead Processor")
    fun transferLeadProcessor(@PathVariable nodeName: Optional<String>,
                     @RequestParam("leadId") leadId: String,
                     @RequestParam("newProcessor") newProcessor: String?): ResponseEntity<Any?> {

        if (newProcessor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newProcessor' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).transferLeadProcessor(leadId, newProcessor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId",
                    "newProcessor" to "$newProcessor"
            )

        } catch (e: Exception) {
            logger.error("Error transfering Lead to ${newProcessor}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }



    /** Accept the Lead. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/acceptLead")
    @ApiOperation(value = "Accept Lead")
    fun acceptLead(@PathVariable nodeName: Optional<String>, @RequestParam("leadId") leadId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val leadId = request.getParameter("leadId")
        val (status, message) = try {

            val result = getService(nodeName).acceptLead(leadId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId"
            )

        } catch (e: Exception) {
            logger.error("Error accept Lead ${leadId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Reject the Lead. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/rejectLead")
    @ApiOperation(value = "Reject Lead")
    fun rejectLead(@PathVariable nodeName: Optional<String>, @RequestParam("leadId") leadId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val leadId = request.getParameter("leadId")
        val (status, message) = try {

            val result = getService(nodeName).rejectLead(leadId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId"
            )

        } catch (e: Exception) {
            logger.error("Error rejecting Lead ${leadId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Engage the Lead. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/engageLead")
    @ApiOperation(value = "Engage Lead")
    fun engageLead(@PathVariable nodeName: Optional<String>, @RequestParam("leadId") leadId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val leadId = request.getParameter("leadId")
        val (status, message) = try {

            val result = getService(nodeName).engageLead(leadId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId"
            )

        } catch (e: Exception) {
            logger.error("Error engaging Lead ${leadId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Convert the Lead. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/convertLead")
    @ApiOperation(value = "Convert Lead")
    fun convertLead(@PathVariable nodeName: Optional<String>, @RequestParam("leadId") leadId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val leadId = request.getParameter("leadId")
        val (status, message) = try {

            val result = getService(nodeName).convertLead(leadId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId"
            )

        } catch (e: Exception) {
            logger.error("Error rejecting Lead ${leadId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }

    /** Unconvert the Lead. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/unconvertLead")
    @ApiOperation(value = "Unconvert Lead")
    fun unconvertLead(@PathVariable nodeName: Optional<String>, @RequestParam("leadId") leadId: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val leadId = request.getParameter("leadId")
        val (status, message) = try {

            val result = getService(nodeName).unconvertLead(leadId)

            HttpStatus.CREATED to mapOf<String, String>(
                    "leadId" to "$leadId"
            )

        } catch (e: Exception) {
            logger.error("Error unconverting Lead ${leadId}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }



    /** Creates a Case. */

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/createApplication")
    @ApiOperation(value = "Create Application")
    fun createApplication(@PathVariable nodeName: Optional<String>,
                          @RequestParam("applicationId") applicationId: String,
                          @RequestParam("applicationName") applicationName: String,
                          @RequestParam("businessAgeRange") businessAgeRange: BusinessAgeRange,
                          @RequestParam("businessEmail") businessEmail: String,
                          @RequestParam("businessPhone") businessPhone: String,
                          @RequestParam("businessRevenueRange") businessRevenueRange: BusinessRevenueRange,
                          @RequestParam("businessType") businessType: BusinessType,
                          @RequestParam("estimatedPurchaseAmount") estimatedPurchaseAmount: Int,
                          @RequestParam("estimatedPurchaseFrequency") estimatedPurchaseFrequency: EstimatedPurchaseFrequency,
                          @RequestParam("industry") industry: String,
                          @RequestParam("applicationStatus") applicationStatus: ApplicationStatus,
                          @RequestParam("partyName") partyName: String?): ResponseEntity<Any?> {


        if (partyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createApplication(applicationId, applicationName, businessAgeRange, businessEmail, businessPhone, businessRevenueRange, businessType, estimatedPurchaseAmount, estimatedPurchaseFrequency, industry, applicationStatus, partyName)

            HttpStatus.CREATED to mapOf<String, String>(
                    "applicationd" to "$applicationId",
                    "applicationName" to "$applicationName",
                    "businessAgeRange" to "$businessAgeRange",
                    "businessEmail" to "$businessEmail",
                    "businessPhone" to "$businessPhone",
                    "businessType" to "$businessType",
                    "applicationStatus" to "$applicationStatus",
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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/createApproval")
    @ApiOperation(value = "Create Approval")
    fun createApproval(@PathVariable nodeName: Optional<String>,
                       @RequestParam("approvalId") approvalId: String,
                       @RequestParam("approvalName") approvalName: String,
                       @RequestParam("description") description: String,
                       @RequestParam("industry") industry: String,
                       @RequestParam("approvalStatus") approvalStatus: ApprovalStatus,
                       @RequestParam("partyName") partyName: String?): ResponseEntity<Any?> {


        if (partyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createApproval(approvalId, approvalName, description, industry, approvalStatus, partyName)

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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

    @CrossOrigin(origins = ["http://localhost:8080"])
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

   @CrossOrigin(origins = ["http://localhost:8080"])
    @GetMapping(value = "/getAgreements")
    @ApiOperation(value = "Get Agreements")
    fun getAgreements(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val agreementStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Agreement::class.java).states
        val agreementStates = agreementStateAndRefs.map { it.state.data }
        return agreementStates.map { it.toJson() }
    }


   @CrossOrigin(origins = ["http://localhost:8080"])
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


    /** Creates a Proposal. */


   @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/createProposal")
    @ApiOperation(value = "Create Proposal")
    fun createProposal(@PathVariable nodeName: Optional<String>,
                       @RequestParam("proposalNumber") proposalNumber: String,
                       @RequestParam("proposalName") proposalName: String,
                       @RequestParam("proposalHash") proposalHash: String,
                       @RequestParam("proposalStatus") proposalStatus: ProposalStatus,
                       @RequestParam("proposalType") proposalType: ProposalType,
                       @RequestParam("totalProposalValue") totalProposalValue: Int,
                       @RequestParam("proposalStartDate") proposalStartDate: String,
                       @RequestParam("proposalEndDate") proposalEndDate: String,
                       @RequestParam("counterpartyName") counterpartyName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        if (counterpartyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createProposal(proposalNumber, proposalName, proposalHash, proposalStatus, proposalType, totalProposalValue, counterpartyName, proposalStartDate, proposalEndDate)

            HttpStatus.CREATED to mapOf<String, String>(
                    "proposalNumber" to "$proposalNumber",
                    "proposalName" to "$proposalName",
                    "proposalHash" to "$proposalHash",
                    "proposalStatus" to "$proposalStatus",
                    "proposalType" to "$proposalType",
                    "totalProposalValue" to "$totalProposalValue",
                    "proposalStartDate" to "$proposalStartDate",
                    "proposalEndDate" to "$proposalEndDate",
                    "party" to "$nodeName",
                    "counterpartyName" to "$counterpartyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending Proposal to ${counterpartyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Accept Proposal. */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/acceptProposal")
    @ApiOperation(value = "Accept Proposal")
    fun acceptProposal(@PathVariable nodeName: Optional<String>, @RequestParam("proposalNumber") proposalNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val proposalNumber = request.getParameter("proposalNumber")
        val (status, message) = try {

            val result = getService(nodeName).acceptProposal(proposalNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "proposalNumber" to "$proposalNumber"
            )

        } catch (e: Exception) {
            logger.error("Error accepting Proposal ${proposalNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Reject Proposal  */

   @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/rejectProposal")
    @ApiOperation(value = "Reject Proposal")
    fun rejectProposal(@PathVariable nodeName: Optional<String>, @RequestParam("proposalNumber") proposalNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val proposalNumber = request.getParameter("proposalNumber")
        val (status, message) = try {

            val result = getService(nodeName).rejectProposal(proposalNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "proposalNumber" to "$proposalNumber"
            )

        } catch (e: Exception) {
            logger.error("Error rejecting Proposal ${proposalNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates a Purchase Order. */


   @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/createPurchaseOrder")
    @ApiOperation(value = "Create Purchase Order")
    fun createPurchaseOrder(@PathVariable nodeName: Optional<String>,
                            @RequestParam("purchaseOrderNumber") purchaseOrderNumber: String,
                            @RequestParam("purchaseOrderName") purchaseOrderName: String,
                            @RequestParam("purchaseOrderHash") purchaseOrderHash: String,
                            @RequestParam("purchaseOrderStatus") purchaseOrderStatus: PurchaseOrderStatus,
                            @RequestParam("description") description: String,
                            @RequestParam("purchaseDate") purchaseDate: String,
                            @RequestParam("deliveryDate") deliveryDate: String,
                            @RequestParam("subtotal") subtotal: Int,
                            @RequestParam("total") total: Int,
                            @RequestParam("financerName") financerName: String,
                            @RequestParam("vendorName") vendorName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'vendorName' missing or has wrong format.\n")
        }


        if (vendorName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'vendorName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createPurchaseOrder(purchaseOrderNumber, purchaseOrderName, purchaseOrderHash, purchaseOrderStatus, description, purchaseDate, deliveryDate, subtotal, total, financerName, vendorName)

            HttpStatus.CREATED to mapOf<String, String>(
                    "purchaseOrderNumber" to "$purchaseOrderNumber",
                    "purchaseOrderName" to "$purchaseOrderName",
                    "purchaseOrderHash" to "$purchaseOrderHash",
                    "purchaseOrderStatus" to "$purchaseOrderStatus",
                    "description" to "$description",
                    "total" to "$total",
                    "purchaseDate" to "$purchaseDate",
                    "deliveryDate" to "$deliveryDate",
                    "purchaser" to "$nodeName",
                    "vendor" to "$vendorName",
                    "financer" to "$financerName"
            )

        } catch (e: Exception) {
            logger.error("Error sending Purchase Order to ${vendorName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Complete Purchase Order */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/completePurchaseOrder")
    @ApiOperation(value = "Complete Purchase Order")
    fun completePurchaseOrder(@PathVariable nodeName: Optional<String>, @RequestParam("purchaseOrderNumber") purchaseOrderNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val purchaseOrderNumber = request.getParameter("purchaseOrderNumber")
        val (status, message) = try {

            val result = getService(nodeName).completePurchaseOrder(purchaseOrderNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "purchaseOrderNumber" to "$purchaseOrderNumber"
            )

        } catch (e: Exception) {
            logger.error("Error completing PO ${purchaseOrderNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Cancel Purchase Order. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping(value = "/cancelPurchaseOrder")
    @ApiOperation(value = "Cancel Purchase Order")
    fun cancelPurchaseOrder(@PathVariable nodeName: Optional<String>, @RequestParam("purchaseOrderNumber") purchaseOrderNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val purchaseOrderNumber = request.getParameter("purchaseOrderNumber")
        val (status, message) = try {

            val result = getService(nodeName).cancelPurchaseOrder(purchaseOrderNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "purchaseOrderNumber" to "$purchaseOrderNumber"
            )

        } catch (e: Exception) {
            logger.error("Error cancelling PO ${purchaseOrderNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }



    /** Finance a Purchase Order */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/financePurchaseOrder")
    @ApiOperation(value = "Finance Purchase Order")
    fun financePurchaseOrder(@PathVariable nodeName: Optional<String>,
                      @RequestParam("purchaseOrderNumber") purchaseOrderNumber: String,
                      @RequestParam("newFinancer") newFinancer: String?): ResponseEntity<Any?> {

        if (newFinancer == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newFinancer' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).financePurchaseOrder(purchaseOrderNumber, newFinancer)

            HttpStatus.CREATED to mapOf<String, String>(
                    "purchaseOrderNumber" to "$purchaseOrderNumber",
                    "newFinancer" to "$newFinancer"
            )

        } catch (e: Exception) {
            logger.error("Error sending PO to ${newFinancer} for financing.", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Creates an Agreement. */

    /** Searchable PDF is mapped by agreement linearId **/
    /** Endpoint setup in BaaR OCR tool and State is created **/

   @CrossOrigin(origins = ["http://localhost:8080"])
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
   @CrossOrigin(origins = ["http://localhost:8080"])
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
   @CrossOrigin(origins = ["http://localhost:8080"])
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
   @CrossOrigin(origins = ["http://localhost:8080"])
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
   @CrossOrigin(origins = ["http://localhost:8080"])
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
   @CrossOrigin(origins = ["http://localhost:8080"])
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
   @CrossOrigin(origins = ["http://localhost:8080"])
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

   @CrossOrigin(origins = ["http://localhost:8080"])
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
                      @RequestParam("factorName") factorName: String,
                      @RequestParam("counterpartyName") counterpartyName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        if (counterpartyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createInvoice(invoiceNumber, invoiceName, billingReason, amountDue, amountPaid, amountRemaining, subtotal, total, dueDate, periodStartDate, periodEndDate, factorName, counterpartyName)

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


    /** Transfers an Invoice */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/transferInvoice")
    @ApiOperation(value = "Transfer Invoice")
    fun transferInvoice(@PathVariable nodeName: Optional<String>,
                        @RequestParam("invoiceNumber") invoiceNumber: String,
                        @RequestParam("newParty") newParty: String?): ResponseEntity<Any?> {

        if (newParty == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newParty' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).transferInvoice(invoiceNumber, newParty)

            HttpStatus.CREATED to mapOf<String, String>(
                    "invoiceNumber" to "$invoiceNumber",
                    "newParty" to "$newParty"
            )

        } catch (e: Exception) {
            logger.error("Error transfering Invoice to ${newParty}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Factor an Invoice */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/factorInvoice")
    @ApiOperation(value = "Factor Invoice")
    fun factorInvoice(@PathVariable nodeName: Optional<String>,
                        @RequestParam("invoiceNumber") invoiceNumber: String,
                        @RequestParam("newFactor") newFactor: String?): ResponseEntity<Any?> {

        if (newFactor == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'newFactor' missing or has wrong format.\n")
        }

        val (status, message) = try {

            val result = getService(nodeName).factorInvoice(invoiceNumber, newFactor)

            HttpStatus.CREATED to mapOf<String, String>(
                    "invoiceNumber" to "$invoiceNumber",
                    "newFactor" to "$newFactor"
            )

        } catch (e: Exception) {
            logger.error("Error factoring Invoice to ${newFactor}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


   @CrossOrigin(origins = ["http://localhost:8080"])
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


    /** Creates a Product. */

    @CrossOrigin(origins = ["http://localhost:8080"])
    @PostMapping("/createProduct")
    @ApiOperation(value = "Create Product")
    fun createProduct(@PathVariable nodeName: Optional<String>,
                      @RequestParam("product_id") product_id: String,
                      @RequestParam("name") name: String,
                      @RequestParam("product_url") product_url: String,
                      @RequestParam("image_url") image_url: String,
                      @RequestParam("description") description: String,
                      @RequestParam("sku") sku: String,
                      @RequestParam("sku_url") sku_url: String,
                      @RequestParam("sku_image_url") sku_image_url: String,
                      @RequestParam("ccids") ccids: String,
                      @RequestParam("category_breadcrumbs") category_breadcrumbs: String,
                      @RequestParam("price") price: Float,
                      @RequestParam("sale_price") sale_price: Float,
                      @RequestParam("is_active") is_active: Int,
                      @RequestParam("stock_quantity") stock_quantity: Int,
                      @RequestParam("stock_unit") stock_unit: String,
                      @RequestParam("brand") brand: String,
                      @RequestParam("color") color: String,
                      @RequestParam("size") size: String,
                      @RequestParam("counterparty") counterparty: String?): ResponseEntity<Any?> {


        if (counterparty == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).createProduct(product_id, name, product_url, image_url, description, sku, sku_url, sku_image_url, ccids, category_breadcrumbs, price, sale_price, is_active, stock_quantity, stock_unit, brand, color, size, counterparty
            )

            HttpStatus.CREATED to mapOf<String, String>(
                    "product_id" to "$product_id",
                    "name" to "$name",
                    "product_url" to "$product_url",
                    "image_url" to "$image_url",
                    "description" to "$description",
                    "sku" to "$sku",
                    "sku_url" to "$sku_url",
                    "sku_image_url" to "$sku_image_url",
                    "ccids" to "$ccids",
                    "category_breadcrumbs" to "$category_breadcrumbs",
                    "price" to "$price",
                    "sale_price" to "$sale_price",
                    "is_active" to "$is_active",
                    "stock_quantity" to "$stock_quantity",
                    "stock_unit" to "$stock_unit",
                    "brand" to "$brand",
                    "color" to "$color",
                    "size" to "$size",
                    "counterparty" to "$counterparty"
            )

        } catch (e: Exception) {
            StatesetController.logger.error("Error sending product data to ${counterparty}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }
}

/*

/** Send UPI Payment */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://app.stateset.io", "https://shopify.stateset.io", "https://halocollar.stateset.io", "https://saaspartners.stateset.io", "https://riggsandporter.stateset.io", "https://integrityfirst.stateset.io", "https://jurrutia.stateset.io", "https://demo.stateset.io",  "https://behir.stateset.io", "https://bluebear.stateset.io", "https://ecoy.stateset.io", "https://sukhi.stateset.io", "https://shahi.stateset.io", "https://syndicate.stateset.io", "https://polrlake.stateset.io", "https://tangerine.stateset.io",  "https://cimed.stateset.io", "https://embraer.stateset.io", "https://adapt.stateset.io", "https://dapps.stateset.io", "https://magiadasvelas.stateset.io",  "https://saasteps.stateset.io", "https://saasteps.lightning.force.com", "https://na110.lightning.force.com", "https://dsoa.na110.visual.force.com", "https://stateset.in"])
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

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://app.stateset.io", "https://shopify.stateset.io", "https://halocollar.stateset.io", "https://saaspartners.stateset.io", "https://riggsandporter.stateset.io", "https://integrityfirst.stateset.io", "https://jurrutia.stateset.io", "https://demo.stateset.io",  "https://behir.stateset.io", "https://bluebear.stateset.io", "https://ecoy.stateset.io", "https://sukhi.stateset.io", "https://shahi.stateset.io", "https://syndicate.stateset.io", "https://polrlake.stateset.io", "https://tangerine.stateset.io",  "https://cimed.stateset.io", "https://embraer.stateset.io", "https://adapt.stateset.io", "https://dapps.stateset.io", "https://magiadasvelas.stateset.io",  "https://saasteps.stateset.io", "https://saasteps.lightning.force.com", "https://na110.lightning.force.com", "https://dsoa.na110.visual.force.com", "https://stateset.in"])
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

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://app.stateset.io", "https://shopify.stateset.io", "https://halocollar.stateset.io", "https://saaspartners.stateset.io", "https://riggsandporter.stateset.io", "https://integrityfirst.stateset.io", "https://jurrutia.stateset.io", "https://demo.stateset.io",  "https://behir.stateset.io", "https://bluebear.stateset.io", "https://ecoy.stateset.io", "https://sukhi.stateset.io", "https://shahi.stateset.io", "https://syndicate.stateset.io", "https://polrlake.stateset.io", "https://tangerine.stateset.io",  "https://cimed.stateset.io", "https://embraer.stateset.io", "https://adapt.stateset.io", "https://dapps.stateset.io", "https://magiadasvelas.stateset.io",  "https://saasteps.stateset.io", "https://saasteps.lightning.force.com", "https://na110.lightning.force.com", "https://dsoa.na110.visual.force.com", "https://stateset.in"])
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

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://app.stateset.io", "https://shopify.stateset.io", "https://halocollar.stateset.io", "https://saaspartners.stateset.io", "https://riggsandporter.stateset.io", "https://integrityfirst.stateset.io", "https://jurrutia.stateset.io", "https://demo.stateset.io",  "https://behir.stateset.io", "https://bluebear.stateset.io", "https://ecoy.stateset.io", "https://sukhi.stateset.io", "https://shahi.stateset.io", "https://syndicate.stateset.io", "https://polrlake.stateset.io", "https://tangerine.stateset.io",  "https://cimed.stateset.io", "https://embraer.stateset.io", "https://adapt.stateset.io", "https://dapps.stateset.io", "https://magiadasvelas.stateset.io",  "https://saasteps.stateset.io", "https://saasteps.lightning.force.com", "https://na110.lightning.force.com", "https://dsoa.na110.visual.force.com", "https://stateset.in"])
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
