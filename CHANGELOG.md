## CHANGELOG

### V1

We are pleased to announce the first release of long requested functionality - accounts!

With this feature you can run multiple client accounts on one node, with logical vault division.
You can create accounts for a range of purposes such as customer accounts, balance 
sheets or P&L accounts, employee accounts, etc.

If you wish to explore the sdk in detail [readme](README.md) and [documentation](docs.md) are a good place to start.

We would like to thank our external contributors for PRs and raising issues. Special thanks to:
@opticyclic
Etaro Ito @kotaro-dr
Adel Rustum @adelRestom
@HaoLiu1987

and to our QA from TCS: Rajesh Vijayan - @rajvjn and Gokulnath - @GokulnathViswa

#### Contracts

* Introduced `AccountInfo` state to hold information about host network identity, account name and unique identifier.
This way it's easy to share with other counterparties the information about the accounts residing on your node.
If you wish to use accounts feature with your CorDapp, there are no changes required to your states or contracts.
Simply, you would have to use the account public key as one of the participants. Main modifications require additional
steps in flows.

#### Workflows

* For ease of usage, lots of utility workflows were introduced. The best way is to examine `AccountService` that gathers
most of the common tasks you would perform when using the accounts.
* Those tasks include: creating account with `CreateAccount` flow, sharing and requesting account information with
`ShareAccountInfo` and `RequestAccountInfo` flows. These flows initiating (so you can start them via RPC), there are also
inline versions (`ShareAccountInfoFlow` and `RequestAccountInfoFlow`) that need to have participants sessions passed - 
can be used as part of more complicated flows. There are corresponding responder flows: `RequestAccountInfoHandlerFlow` and
`ShareAccountInfoHandlerFlow`.
* More advanced use cases include sharing and syncing states and account info using `ShareStateAndSyncAccounts` and `ShareStateWithAccount`.
They also come in inline versions: `ShareStateAndSyncAccountsFlow` and `ShareStateWithAccountFlow` with corresponding
responder flows: `ReceiveStateAndSyncAccountsFlow` and `ReceiveStateForAccountFlow`.
`ShareStateAndSyncAccounts` flow takes `StateAndRef` and shares it with the specified party, performing synchronisation of
`AccountInfo`s related to this state as well.
`ShareStateWithAccount` flow shares a `StateAndRef` with an account as opposed to a full node. It's done the way that
account we are sharing state with is allowed to see it whenquerying node's vault.
* To be able to transact within account model one needs to associate fresh key with the account. It is done by calling
`RequestKeyForAccount` flow - think of it as confidential identities in the accounts world.
As before the inline version follows our naming convention - `RequestKeyForAccountFlow`, responder is called: `SendKeyForAccountFlow`.

#### Vault query

* To get a view of states belonging to an account with given id, we introduced special `VaultQueryCriteria`.
It's sufficient to specify list of `externalIds`: `QueryCriteria.VaultQueryCriteria(externalIds = listOf(accountId))`

#### Known issues

* By design accounts don't have default key, to transact key has to be explicitly requested.