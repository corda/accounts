<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda Accounts Library

## Reminder

This project is open source under an Apache 2.0 licence. That means you
can submit PRs to fix bugs and add new features if they are not currently
available.

## What is the accounts library?

In the context of Corda, the accounts library allows a Corda node to partition 
the vault—a collection of state objects—into a number of subsets, where each 
subset represents an account. In other words, the accounts library allows a 
Corda node operator to split the vault into multiple "logical" sub-vaults. 
This is advantageous for a couple of reasons:

* Node operators can reduce costs by hosting multiple entities, as accounts, on 
  one node
* Node operators can partition the vault on a per entity basis

Accounts are created by host nodes, which are just regular Corda nodes. Hosts 
can create accounts for a range of purposes such as customer accounts, balance 
sheets or P&L accounts, employee accounts, etc.

The accounts library takes the form of a JAR which can be dropped into the 
CorDapps folder. Use of it is optional. This means that some nodes will support 
accounts but others will not. This is intentional by design as not all nodes 
will need to support accounts and the optional nature of accounts reduces the 
learning curve for new CorDapp developers.

## How to use the library?

See the docs [here](docs.md) for how to build CorDapps using accounts.

By far the easiest way to get started with the accounts SDK is to use the "tokens template"
which is a branch on the kotlin version of the "CorDapp template". You can obtain 
it with the following commands:

    git clone https://github.com/corda/cordapp-template-kotlin
    cd cordapp-template-kotlin
    git checkout token-template

Once you have cloned the repository, you should open it with IntelliJ. This will give 
you a template repo with tokens and accounts dependencies already included.

### Example Account Projects

There are three projects demonstrating how to use accounts in the examples sub-directory:

* [Sweepstake](examples/cordapp-sweepstake)
* [Gold Trading](examples/gold-trading)
* [Tokens](examples/tokens-integration-test) - using tokens with accounts

### Adding accounts dependencies to an existing CorDapp

First, add a variable for the accounts release group and the version you 
wish to use and set the corda version that should've been installed locally::

    buildscript {
        ext {
            corda_release_version = '4.3-RC01'
            accounts_release_version = '1.0-RC04'
            accounts_release_group = 'com.r3.corda.lib.accounts'
            confidential_id_release_group = "com.r3.corda.lib.ci"
            confidential_id_release_version = "1.0-RC03"
        }
    }

Second, you must add the accounts artifactory repository to the
list of repositories for your project (if it hasn't already been added):

    repositories {
        maven { url 'https://software.r3.com/artifactory/corda-lib-dev' }
        maven { url 'https://software.r3.com/artifactory/corda-lib' }
    }

Now, you can add the accounts dependencies to the `dependencies` block
in each module of your CorDapp. For contract modules add:

    cordaCompile "$accounts_release_group:accounts-contracts:$accounts_release_version"

In your workflow `build.gradle` add:

    cordaCompile "$confidential_id_release_group:ci-workflows:$confidential_id_release_version"
    cordaCompile "$accounts_release_group:accounts-workflows:$accounts_release_version"

If you want to use the `deployNodes` task, you will need to add the
following dependencies to your root `build.gradle` file:

    cordapp "$confidential_id_release_group:ci-workflows:$confidential_id_release_version"
    cordapp "$accounts_release_group:accounts-contracts:$accounts_release_version"
    cordapp "$accounts_release_group:accounts-workflows:$accounts_release_version"

These should also be added to the `deployNodes` task with the following syntax:

    nodeDefaults {
        projectCordapp {
            deploy = false
        }
        cordapp("$confidential_id_release_group:ci-workflows:$confidential_id_release_version")
        cordapp("$accounts_release_group:accounts-contracts:$accounts_release_version")
        cordapp("$accounts_release_group:accounts-workflows:$accounts_release_version")
    }

### Modifying An Existing CorDapp to Use Accounts

States should use `AnonymousParty` instead of `Party` as a Party refers to a node and the PublicKey can refer to an account.

In order to create your state you need to request the PublicKey with a flow. e.g.

    val lenderKey = subFlow(RequestKeyForAccount(lenderAccountInfo.state.data)).owningKey

Once you have keys you need to have logic to determine who signs.

If your accounts are on the same node that you are running the flow on then they can all be on the `signInitialTransaction`, however, if one is on another node you need to use a `CollectSignatureFlow` 

When calling the `FinalityFlow` you will need different sessions depending on if all the accounts are on one node or on different nodes.
 
If accounts are on different nodes you need to `shareAccountInfoWithParty` before you can transact between accounts otherwise the nodes running the flows wont be aware of the accounts on the other nodes.

Currently, if accounts are on different nodes you also need to run `shareStateAndSyncAccounts` after the flow to make sure that you can use all methods to look up accountInfo.

## Installing the accounts library

If you wish to build the accounts library from source then do the following to
publish binaries to your local maven repository:

    git clone https://github.com/corda/accounts
    cd accounts
    ./gradlew clean install

## Other useful links

[Contributing](CONTRIBUTING.md)
