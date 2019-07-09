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

### Using the accounts template.

By far the easiest way to get started with the tokens SDK is to use the tokens-template 
which is a branch on the kotlin version of the "CorDapp template". You can obtain 
it with the following commands:

    git clone http://github.com/corda/cordapp-template-kotlin
    cd cordapp-template-kotlin
    git checkout token-template

Once you have cloned the repository, you should open it with IntelliJ. This will give 
you a template repo with tokens and accounts dependencies already included.

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

### Installing the accounts library

If you wish to build the accounts library from source then do the following to
publish binaries to your local maven repository:

    git clone http://github.com/corda/accounts
    cd accounts
    ./gradlew clean install

## Where to go next?

TODO

## Other useful links

TODO
