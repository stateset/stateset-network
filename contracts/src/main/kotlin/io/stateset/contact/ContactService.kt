package io.stateset.contact

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
interface ContactService : SerializeAsToken {

    // Contacts which the calling node hosts.
    fun myContacts(): List<StateAndRef<Contact>>

    // Returns all contacts, including those hosted by other nodes.
    fun allContacts(): List<StateAndRef<Contact>>

    // Creates a new contact and returns the ContactInfo StateAndRef.
    fun createContact(contactName: String): CordaFuture<StateAndRef<Contact>>

    // Overload for creating an contact with a specific contact ID.
    fun createContact(contactName: String, contactId: UUID):
            CordaFuture<StateAndRef<Contact>>

    // Creates a new KeyPair, links it to the contact and returns the publickey.
    fun freshKeyForContact(contactId: UUID): AnonymousParty

    // Returns all the keys used by the contact specified by the contact ID.
    fun contactKeys(contactId: UUID): List<PublicKey>

    // Returns the ContactInfo for an contact name or contact ID.
    fun contactInfo(contactId: UUID): StateAndRef<Contact>?

    // Returns the ContactInfo for a given owning key
    fun contactInfo(owningKey: PublicKey): StateAndRef<Contact>?

    // The assumption here is that Contact names are unique at the node level but are not
    // guaranteed to be unique at the network level. The host Party can be used to
    // de-duplicate contact names at the network level.
    fun contactInfo(contactName: String): StateAndRef<Contact>?

    // Returns the Party which hosts the contact specified by contact ID.
    fun hostForContact(contactId: UUID): Party?

    // Allows the contact host to perform a vault query for the specified contact ID.
    fun ownedByContactVaultQuery(
            contactIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToContactVaultQuery(
            contactIds: List<UUID>,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun ownedByContactVaultQuery(
            contactId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    fun broadcastedToContactVaultQuery(
            contactId: UUID,
            queryCriteria: QueryCriteria
    ): List<StateAndRef<*>>

    // Updates the contact info with new contact details. This may involve creating a
    // new contact on another node with the new details. Once the new contact has
    // been created, all the states can be moved to the new contact.
    fun moveContact(currentInfo: StateAndRef<Contact>, newInfo: Contact)

    // De-activates the contact.
    fun deactivateContact(contactId: UUID)

    // Sends ContactInfo specified by the contact ID, to the specified Party. The
    // receiving Party will be able to access the ContactInfo from their ContactService.
    fun shareContactInfoWithParty(contactId: UUID, party: Party): CordaFuture<Unit>

    fun <T : ContractState> broadcastStateToContact(contactId: UUID, state: StateAndRef<T>): CordaFuture<Unit>
}