## CHANGELOG

### V1.1.x (unreleased)

#### General

* The Accounts SDK no longer depends on Hibernate (ENT-15178). `accounts-workflows` used to declare
  `org.hibernate:hibernate-core:5.6.14.Final` as a Corda-provided dependency, which Corda 4.15 (Hibernate 7) no longer provides.
  The `javax.persistence` and `jakarta.persistence` APIs are compile-only dependencies now; neither is bundled, the node provides
  the one its Hibernate version implements. The column mappings are unchanged.
* The schema classes carry both the `javax.persistence` (Corda 4.14 and earlier) and the `jakarta.persistence` (Corda 4.15)
  annotations, so one jar runs on both. Build the SDK against Corda 4.12 (the default) for a jar that runs on 4.12 and 4.15
  nodes; a jar built against 4.15 runs on 4.15 only, because its `withEntityManager` code is bound to the Jakarta
  `EntityManager`.
* The Corda version to build and test against is a Gradle property: `./gradlew build -PcordaReleaseVersion=4.15-RC02`.
  The default, `4.12.13` (the latest Corda 4.12 release, instead of the `4.12-SNAPSHOT` the build used to pin), is
  `cordaReleaseVersion` in `gradle.properties`. Corda release candidates resolve from the
  `corda-releases` repository on `software.r3.com`, with the `CORDA_ARTIFACTORY_USERNAME` and `CORDA_ARTIFACTORY_PASSWORD`
  environment variables.
* The gold-trading example stores its `UUID` element collections through a JPA `AttributeConverter` (the same 36-character
  string that Hibernate's `uuid-char` type wrote) instead of the Hibernate-specific `@Type` annotation; its `UUID` id uses
  Hibernate's default mapping, as Hibernate 7 does not allow a converter on an id.
* The examples that use the Token SDK drop the `org.hibernate:hibernate-core` dependency that `tokens-workflows` 1.3.4 and
  earlier declare at runtime scope (the node provides Hibernate), so they resolve against Corda 4.15 as well. The Token SDK
  version the examples use is a Gradle property too: `-PtokensReleaseVersion=...` overrides the default in `gradle.properties`.

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