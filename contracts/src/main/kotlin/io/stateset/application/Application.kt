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


package io.stateset.application

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.util.*

// *********
// * Application State *
// *********

@CordaSerializable
@BelongsToContract(ApplicationContract::class)
data class Application(val applicationId: String,
                       val applicationName: String,
                       val businessAgeRange: BusinessAgeRange,
                       val businessEmail: String,
                       val businessPhone: String,
                       val businessRevenueRange: BusinessRevenueRange,
                       val businessType: BusinessType,
                       val estimatedPurchaseAmount: Int,
                       val estimatedPurchaseFrequency: EstimatedPurchaseFrequency,
                       val submitted: Boolean,
                       val submittedAt: String,
                       val industry: String,
                       val applicationStatus: ApplicationStatus,
                       val agent: Party,
                       val provider: Party,
                       val active: Boolean?,
                       val createdAt: String?,
                       val lastUpdated: String?,
                       override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, ContractState {


    override val participants: List<AbstractParty> get() = listOf(agent, provider)


}

@CordaSerializable
enum class ApplicationStatus {
    SUBMITTED, REQUESTED, UNSTARTED, STARTED, INREVIEW, MEDICAL_CHECK, FINANCIAL_CHECK, WORKING, ESCALATED, APPROVED, REJECTED
}

@CordaSerializable
enum class BusinessAgeRange(val age: Int) {
    AGE_0_2(0-2),AGE_2_5(2-5), AGE_5_10(5-10), AGE_10(10)
}

@CordaSerializable
enum class BusinessRevenueRange(val revenue: Int) {
    REV_0_200K(0-200000), REV_200K_500K(200000-500000), REV_500K_2M(500000-2000000), REV_2M_5M(2000000-5000000), REV_5M_25M(5000000-25000000), REV_25M_100M(2500000-100000000)
}

@CordaSerializable
enum class BusinessType {
    SOLE_PROP, LLC, CORP, NONPROFIT, GOV
}

@CordaSerializable
enum class EstimatedPurchaseFrequency {
    ONCE, WEEKLY, MONTHLY, YEARLY
}


class ApplicationContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        val APPLICATION_CONTRACT_ID = ApplicationContract::class.java.canonicalName
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {

        class CreateApplication: Commands
        class ReviewApplication : Commands
        class ApproveApplication: Commands
        class RejectApplication: Commands


    }


    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val applicationInputs = tx.inputsOfType<Application>()
        val applicationOutputs = tx.outputsOfType<Application>()
        val applicationCommand = tx.commandsOfType<ApplicationContract.Commands>().single()

        when(applicationCommand.value) {
            is Commands.CreateApplication -> requireThat {
                "no inputs should be consumed" using (applicationInputs.isEmpty())
                // TODO we might allow several jobs to be proposed at once later
                "one output should be produced" using (applicationOutputs.size == 1)

            }


            is Commands.ReviewApplication -> requireThat {
                "one input should be produced" using (applicationInputs.size == 1)
                "one output should be produced" using (applicationOutputs.size == 1)

                val applicationInput = applicationInputs.single()
                val applicationOutput = applicationOutputs.single()

                "the input status must be set as started" using (applicationInputs.single().applicationStatus == ApplicationStatus.REQUESTED)
                "the output status should be set as ineffect" using (applicationOutputs.single().applicationStatus == ApplicationStatus.INREVIEW)
                "the update must be signed by the contractor of the " using (applicationOutputs.single().agent == applicationInputs.single().provider)
                "the contractor should be signer" using (applicationCommand.signers.contains(applicationOutputs.single().provider.owningKey))

            }



            is Commands.ApproveApplication -> requireThat {
                "one input should be produced" using (applicationInputs.size == 1)
                "one output should be produced" using (applicationOutputs.size == 1)

                val applicationInput = applicationInputs.single()
                val applicationOutput = applicationOutputs.single()

                "the input status must be set as started" using (applicationInput.applicationStatus == ApplicationStatus.REQUESTED)
                "the output status should be set as approved" using (applicationOutput.applicationStatus == ApplicationStatus.APPROVED)

            }


            is Commands.RejectApplication -> requireThat {
                "one input should be produced" using (applicationInputs.size == 1)
                "one output should be produced" using (applicationOutputs.size == 1)

                val applicationInput = applicationInputs.single()
                val applicationOutput = applicationOutputs.single()

                "the input status must be set as in effect" using (applicationInput.applicationStatus == ApplicationStatus.REQUESTED)
                "the output status should be set as renewed" using (applicationOutput.applicationStatus ==ApplicationStatus.REJECTED)


            }


            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

}
