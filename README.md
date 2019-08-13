<p align="center">
    <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda Accounts Library

## Reminder

This project is open source under an Apache 2.0 licence. That means you
can submit PRs to fix bugs and add new features if they are not currently
available.

## What is the accounts library?

TODO

## How to use the library?

### Using the accounts template.

TODO

### Adding accounts dependencies to an existing CorDapp

First, add a variable for the accounts release group and the version you 
wish to use:

    buildscript {
        ext {
            accounts_release_version = '1.0-RC01'
            accounts_release_group = 'com.r3.corda.lib.accounts'
        }
    }

Second, you must add the accounts artifactory repository to the
list of repositories for your project (if it hasn't already been added):

    repositories {
        maven { url 'http://ci-artifactory.corda.r3cev.com/artifactory/corda-lib-dev' }
        maven { url 'http://ci-artifactory.corda.r3cev.com/artifactory/corda-lib' }
    }

Now, you can add the accounts dependencies to the `dependencies` block
in each module of your CorDapp. For contract modules add:

    cordaCompile "accounts_release_group:accounts-contracts:$accounts_release_version"

In your workflow `build.gradle` add:

    cordaCompile "$accounts_release_group:accounts-workflows:$accounts_release_version"

If you want to use the `deployNodes` task, you will need to add the
following dependencies to your root `build.gradle` file:

    cordapp "$accounts_release_group:accounts-contracts:$accounts_release_version"
    cordapp "$accounts_release_group:accounts-workflows:$accounts_release_version"

These should also be added to the `deployNodes` task with the following syntax:

    nodeDefaults {
        projectCordapp {
            deploy = false
        }
        cordapp("$accounts_release_group:accounts-contracts:$accounts_release_version")
        cordapp("$accounts_release_group:accounts-workflows:$accounts_release_version")
    }

### Modifying An Existing CorDapp to Use Accounts

States should use `PublicKey` instead of `Party` as a Party refers to a node and the PublicKey can refer to an account.

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

    git clone http://github.com/corda/accounts
    cd accounts
    ./gradlew clean install

## Where to go next?

TODO

## Other useful links

TODO