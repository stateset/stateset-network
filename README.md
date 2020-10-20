# Stateset

Stateset is a globally distributed b2b sales and finance network of nodes for enterprise-grade, real-time transactions and asset workflows. It is a platform for developing the next-generation of enterprise software where state and processes can be shared and uniform across multiple trusted entities leading to operational efficiency, speed and better customer experiences. What does the Business-to-Business enterprise space look like when there is a real-time network for shared processes, state and transfer of value. A network that is globally reachable like a next gen Bloomberg but not just for trades and messaging but for any front-office, middle-office or back-office application. The future state is that there is much more secure, automated and deterministic business processes where assets and value flow freely across the business network and are interoperable with other sector specific business networks.

```
	   8 Node Network Graph | 28 Edges | 1 Notary
-------------------------------------------------------------------

	 /--------\   /--------\   /--------\                                   
	|	   | |	        | |          |                  
	|  PartyB  | |  PartyC  | |  PartyD  | 
	|          | |	   	| |          |                   
 	 \--------/   \--------/   \--------/

 /--------\	      /--------\	   /--------\
|	   |	     |	        |	  |	     |
|  PartyA  |	     |  Notary  |	  |  PartyE  | 
|	   |	     |	        |	  |	     | 
 \--------/	      \--------/           \--------/

	 /--------\   /--------\   /--------\                                   
	|	   | |	        | |          |                            
	|  PartyH  | |  PartyG  | |  PartyF  | 
	|          | |	        | |          |                             
 	 \--------/   \--------/   \--------/

--------------------------------------------------------------------


```


### Stateset Network Setup


Stateset Network is running 28 nodes and can be leveraged using the app.stateset.io interface. If you would like to add your node to network or contribute to the Stateset Network please reach out to dom@stateset.io

The following is instructions on how to build and deploy Stateset for development.

The network works locally on your machine or in a VM on AWS, GCP or Azure.

1) Install and Build the Stateset Network locally via Git:

```bash

git clone https://github.com/stateset/stateset-network
cd stateset
gradle clean build -Dsigning.enabled=false

```

2) Deploy the Nodes


```bash

cd stateset && gradlew.bat deployNodes -Dsigning.enabled=false (Windows) OR ./gradlew deployNodes (Linux)

```

3) Run the Nodes

```bash

cd build 
cd nodes
runnodes.bat (Windows) OR ./runnodes (Linux)

```
4) Run the Spring Boot Server

```bash

cd ..
cd ..
cd server
../gradlew.bat bootRun -x test (Windows) OR ../gradlew bootRun -x test

```
The Stateset Network API Swagger will be running at `http://localhost:8080/swagger-ui.html#/`

To change the name of your `organisation` or any other parameters, edit the `node.conf` file and repeat the above steps.


5) Kill Nodes 

```bash

taskkill /f /im java.exe (windows) OR  kill -9 $(ps -ef | pgrep -f "java") (Linux)

```





#### Node Configuration

Configuration 

- Corda version: Corda 4
- Vault SQL Database: PostgreSQL
- Cloud Service Provider: GCP
- JVM or Kubernetes


### Network States

B2B Sales and Finance States are transferred between organizations on the network.

#### Accounts

The first state to be deployed on the network is the `Account`. Version 0.1 of the `Account` State has the following structure:

```jsx

// *********
// * Account State *
// *********

data class Account(val accountId: String,
                   val accountName: String,
                   val accountType: TypeOfBusiness,
                   val industry: String,
                   val phone: String,
                   val yearStarted: Int,
                   val annualRevenue: Double,
                   val businessAddress: String,
                   val businessCity: String,
                   val businessState: String,
                   val businessZipCode: String,
                   val controller: Party,
                   val processor: Party ) : ContractState, QueryableState {


```

The Account has the following business `flows` that can be called:

- `CreateAccount` - Create an Account between your organization and a known counterparty on the DSOA
- `TransferAccount` - Transfer the Account between your organization and a counterparty on the DSOA
- `ShareAccount` - Share the Account Data with a counterparty
- `EraseAccount` - Erase the Account Data

#### Contacts

The second state to be deployed on the network is the `Contact`. Version 0.1 of the `Contact` State has the following structure:

```jsx

// *********
// * Contact State *
// *********

data class Contact(val contactId: String,
                   val firstName: String,
                   val lastName: String,
                   val email: String,
                   val phone: String,
                   val controller: Party,
                   val processor: Party,
                   override val linearId: UniqueIdentifier = UniqueIdentifier())


```


The Contact has the following business `flows` that can be called:

- `CreateContact` - Create a Contact between your organization and a known counterparty on the DSOA
- `TransferContact` - Transfer the Contact between your organization and a counterparty on the DSOA
- `ShareContact` - Share the Contact Data with a counterparty
- `EraseContact` - Erase the Contact Data

#### Leads

The third state to be deployed on the network is the `Lead`. Version 0.1 of the `Lead` State has the following structure:

```jsx

// *********
// * Lead State *
// *********

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
                override val linearId: UniqueIdentifier = UniqueIdentifier())


```


The Lead has the following business `flows` that can be called:

- `CreateLead` - Create a Lead between your organization and a known counterparty on the DSOA
- `TransferLead` - Transfer the Lead between your organization and a counterparty on the DSOA
- `ShareLead` - Share the Lead Data with a counterparty
- `EraseLead` - Erase the Lead Data
- `ConvertLead` - Convert a Lead State into an Account State and Contact State


We created the `Carmen Dashboard` to provide the ability for organizations to create `Accounts`, `Contacts`, and `Leads` with counterparties on the network.


#### Cases


```jsx

// *********
// * Case State *
// *********

data class Case(val caseId: String,
                val description: String,
                val caseNumber: String,
                val caseStatus: CaseStatus,
                val casePriority: CasePriority,
                val submitter: Party,
                val resolver: Party,
                override val linearId: UniqueIdentifier = UniqueIdentifier()) 


```

The Case has the following business `flows` that can be called:

- `CreateCase` - Create a Case between your organization and a known counterparty on the DSOA
- `StartCase` - Start on an unstarted Case
- `CloseCase` - Close the Case with a counterparty
- `EscalateCase` - Escalate the Case


#### Proposals

The seventh state to be deployed on the network is the Proposal. Version 0.1 of the Proposal State has the following structure:

```jsx

// *****************
// * Proposal State *
// *****************

@BelongsToContract(ProposalContract::class)
data class Proposal(val proposalNumber: String,
                     val proposalName: String,
                     val proposalHash: String,
                     val proposalStatus: ProposalStatus,
                     val proposalType: ProposalType,
                     val totalProposalValue: Int,
                     val party: Party,
                     val counterparty: Party,
                     val proposalStartDate: String,
                     val proposalEndDate: String,
                     val active: Boolean?,
                     val createdAt: String?,
                     val lastUpdated: String?,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {

```

The Proposal has the following business `flows` that can be called:

- `CreateProposal` - Create an Proposal between your organization and a known counterparty on Stateset
- `AcceptProposal` - Accept the Proposal between your organization and a counterparty on Stateset
- `RejectProposal` - Reject the Proposal between your organization and a counterparty on Stateset

The `Proposal Status` and `Proposal Type` enums are listed as follows:

```jsx

@CordaSerializable
enum class ProposalStatus {
    REQUEST, APPROVAL_REQUIRED, APPROVED, IN_REVIEW, ACCEPTED, REJECTED
}

@CordaSerializable
enum class ProposalType {
    NDA, MSA, SLA, SOW, PO
}

```

#### Agreements


```jsx

// *****************
// * Agreement State *
// *****************

@BelongsToContract(AgreementContract::class)
data class Agreement(val agreementNumber: String,
                     val agreementName: String,
                     val agreementHash: String,
                     val agreementStatus: AgreementStatus,
                     val agreementType: AgreementType,
                     val totalAgreementValue: Int,
                     val party: Party,
                     val counterparty: Party,
                     val agreementStartDate: String,
                     val agreementEndDate: String,
                     val active: Boolean?,
                     val createdAt: String?,
                     val lastUpdated: String?,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {


```

The Agreement has the following business `flows` that can be called:

- `CreateAgreement` - Create an Agreement between your organization and a known counterparty on the DSOA
- `ActivateAgreement` - Activate the Agreement between your organization and a counterparty on the DSOA
- `TerminateAgreement` - Terminate an existing or active agreement
- `RenewAgreement` - Renew an existing agreement that is or is about to expire
- `ExpireAgreement` - Expire a currently active agreement between you and a counterparty

The `Agreement Status` and `Agreement Type` enums are listed as follows:

```jsx


@CordaSerializable
enum class AgreementStatus {
    REQUEST, APPROVAL_REQUIRED, APPROVED, IN_REVIEW, ACTIVATED, INEFFECT, REJECTED, RENEWED, TERMINATED, AMENDED, SUPERSEDED, EXPIRED
}

@CordaSerializable
enum class AgreementType {
    NDA, MSA, SLA, SOW
}

```

#### Loans

```jsx


// *****************
// * Loan State *
// *****************

@BelongsToContract(LoanContract::class)
data class Loan(val loanNumber: String,
                val loanName: String,
                val loanReason: String,
                val loanStatus: LoanStatus,
                val loanType: LoanType,
                val amountDue: Int,
                val amountPaid: Int,
                val amountRemaining: Int,
                val subtotal: Int,
                val total: Int,
                val party: Party,
                val counterparty: Party,
                val dueDate: String,
                val periodStartDate: String,
                val periodEndDate: String,
                val paid: Boolean?,
                val active: Boolean?,
                val createdAt: String?,
                val lastUpdated: String?,
                override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {


```

The Loan has the following business `flows` that can be called:

- `CreateLoan` - Create a Loan between your organization and a known counterparty
- `PayLoan` - Pay off a Loan


#### Invoices

```jsx


// *****************
// * Invoice State *
// *****************

@BelongsToContract(InvoiceContract::class)
data class Invoice(val invoiceNumber: String,
                   val invoiceName: String,
                   val billingReason: String,
                   val amountDue: Int,
                   val amountPaid: Int,
                   val amountRemaining: Int,
                   val subtotal: Int,
                   val total: Int,
                   val party: Party,
                   val counterparty: Party,
                   val dueDate: String,
                   val periodStartDate: String,
                   val periodEndDate: String,
                   val paid: Boolean?,
                   val active: Boolean?,
                   val createdAt: String?,
                   val lastUpdated: String?,
                   override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {


```

The Invoice has the following business `flows` that can be called:

- `CreateInvoice` - Create a Invoice between your organization and a known counterparty
- `PayInvoice` - Pay an Invoice
- `FactorInvoice` - Factor an Invoice
