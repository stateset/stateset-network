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

package io.stateset.case

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey

// *********
// * Case State *
// *********

@CordaSerializable
@BelongsToContract(CaseContract::class)
data class Case(val caseId: String,
                val caseName: String,
                val caseNumber: String,
                val description: String,
                val caseStatus: CaseStatus,
                val casePriority: CasePriority,
                val submitter: Party,
                val resolver: Party,
                override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, ContractState, QueryableState {


    override val participants = listOf(submitter, resolver)

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CaseSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is CaseSchemaV1 -> CaseSchemaV1.PersistentCase(
                    caseId = this.caseId,
                    caseName = this.caseName,
                    caseNumber = this.caseNumber,
                    description = this.description,
                    caseStatus = this.caseStatus.toString(),
                    casePriority = this.casePriority.toString(),
                    submitter = this.submitter.toString(),
                    resolver = this.resolver.toString(),
                    linearId = this.linearId.toString()
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

}

@CordaSerializable
enum class CaseStatus {
    NEW, UNSTARTED, STARTED, WORKING, ESCALATED, CLOSED, OUTOFSCOPE, RESOLVED
}

@CordaSerializable
enum class CasePriority {
    HIGH, MEDIUM, LOW

}



// *****************
// * Contract Code *
// *****************

class CaseContract : Contract {

    companion object {
        val CASE_CONTRACT_ID = CaseContract::class.java.canonicalName
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class CreateCase : TypeOnlyCommandData(), Commands
        class CloseCase : TypeOnlyCommandData(), Commands
        class EscalateCase : TypeOnlyCommandData(), Commands
        class ResolveCase: TypeOnlyCommandData(), Commands

    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.CreateCase -> verifyCreate(tx, setOfSigners)
            is Commands.CloseCase -> verifyClose(tx, setOfSigners)
            is Commands.ResolveCase -> verifyResolve(tx, setOfSigners)
            is Commands.EscalateCase -> verifyEscalate(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
                "no inputs should be consumed" using (tx.inputStates.isEmpty())
                // TODO we might allow several jobs to be proposed at once later
                "one output should be produced" using (tx.outputStates.size == 1)

                val output = tx.outputsOfType<Case>().single()
                "the submitter should be different to the resolver" using (output.resolver != output.submitter)

                "Submitter only may sign the issue transaction." using (output.submitter.owningKey in signers)
            }

    private fun verifyClose(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

                "one input should be produced" using (tx.inputStates.size == 1)
                "one output should be produced" using (tx.outputStates.size == 1)

                //    "the input status must be set as started" using (caseInputs.single().caseStatus == CaseStatus.STARTED)
                //   "the output status should be set as finished" using (caseOutputs.single().caseStatus == CaseStatus.CLOSED)
                //   "only the status must change" using (caseInput.copy(caseStatus = CaseStatus.CLOSED) == caseOutput)
                // "the update must be signed by the contractor of the " using (tx.inputStates.single().submitter == caseInputs.single().submitter)
                // "the submitter should be signer" using (caseCommand.signers.contains(caseOutputs.single().submitter.owningKey))
                val output = tx.outputsOfType<Case>().single()
                "the submitter should be different to the resolver" using (output.resolver != output.submitter)

                "Submitter only may sign the issue transaction." using (output.submitter.owningKey in signers)

            }

    private fun verifyResolve(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
                "one input should be produced" using (tx.inputStates.size == 1)
                "one output should be produced" using (tx.inputStates.size == 1)

                //    "the input status must be set as started" using (caseInputs.single().caseStatus == CaseStatus.STARTED)
                //   "the output status should be set as finished" using (caseOutputs.single().caseStatus == CaseStatus.CLOSED)
                //   "only the status must change" using (caseInput.copy(caseStatus = CaseStatus.CLOSED) == caseOutput)
                //  " the update must be signed by the contractor of the " using (caseOutputs.single().submitter == caseInputs.single().submitter)
                 // "the submitter should be signer" using (caseCommand.signers.contains(caseOutputs.single().submitter.owningKey))
                val output = tx.outputsOfType<Case>().single()
                "the submitter should be different to the resolver" using (output.resolver != output.submitter)

                "Submitter only may sign the issue transaction." using (output.submitter.owningKey in signers)

            }

    private fun verifyEscalate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

                //     "Status should show rejected" using (caseOutput.caseStatus == CaseStatus.OUTOFSCOPE)
                //      "Job must have been previously started" using (caseInput.caseStatus == CaseStatus.STARTED)

                //      "Resolver should be a signer" using (caseCommand.signers.contains(caseOutput.resolver.owningKey))
                        "one input should be produced" using (tx.inputStates.size == 1)
                        "one output should be produced" using (tx.outputStates.size == 1)
                        val output = tx.outputsOfType<Case>().single()
                        "the submitter should be different to the resolver" using (output.resolver != output.submitter)

                        "Submitter only may sign the issue transaction." using (output.submitter.owningKey in signers)
        }
    }
