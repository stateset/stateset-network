package io.stateset.lead

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SerializeAsToken
import java.security.PublicKey
import java.util.*

@CordaService
interface LeadService : SerializeAsToken {

    // Leads which the calling node hosts.
    fun myLeads(): List<StateAndRef<Lead>>

    // Returns all leads, including those hosted by other nodes.
    fun allLeads(): List<StateAndRef<Lead>>

    // Creates a new lead and returns the LeadInfo StateAndRef.
    fun createLead(leadName: String): CordaFuture<StateAndRef<Lead>>

    // Overload for creating an lead with a specific lead ID.
    fun createLead(leadName: String, leadId: UUID):
            CordaFuture<StateAndRef<Lead>>

    // Creates a new KeyPair, links it to the lead and returns the publickey.
    fun freshKeyForLead(leadId: UUID): AnonymousParty

    // Returns all the keys used by the lead specified by the lead ID.
    fun leadKeys(leadId: UUID): List<PublicKey>

    // Returns the LeadInfo for an lead name or lead ID.
    fun leadInfo(leadId: UUID): StateAndRef<Lead>?

    // Returns the LeadInfo for a given owning key
    fun leadInfo(owningKey: PublicKey): StateAndRef<Lead>?

    // The assumption here is that Lead names are unique at the node level but are not
    // guaranteed to be unique at the network level. The host Party can be used to
    // de-duplicate lead names at the network level.
    fun leadInfo(leadName: String): StateAndRef<Lead>?

    // Returns the Party which hosts the lead specified by lead ID.
    fun hostForLead(leadId: UUID): Party?

    // Allows the lead host to perform a vault query for the specified lead ID.
    fun ownedByLeadVaultQuery(
            leadIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToLeadVaultQuery(
            leadIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun ownedByLeadVaultQuery(
            leadId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToLeadVaultQuery(
            leadId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    // Updates the lead info with new lead details. This may involve creating a
    // new lead on another node with the new details. Once the new lead has
    // been created, all the states can be moved to the new lead.
    fun moveLead(currentInfo: StateAndRef<Lead>, newInfo: Lead)

    // De-activates the lead.
    fun deactivateLead(leadId: UUID)

    // Sends LeadInfo specified by the lead ID, to the specified Party. The
    // receiving Party will be able to access the LeadInfo from their LeadService.
    fun shareLeadInfoWithParty(leadId: UUID, party: Party): CordaFuture<Unit>

    fun <T : ContractState> broadcastStateToLead(leadId: UUID, state: StateAndRef<T>): CordaFuture<Unit>
}