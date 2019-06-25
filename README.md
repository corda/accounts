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
            accounts_release_version = '1.0'
            accounts_release_group = 'com.r3.corda.lib.accounts'
        }
    }

Second, you must add the accounts artifactory repository to the
list of repositories for your project (if it hasn't already been added):

    repositories {
        maven { url 'XXX' }
    }

Now, you can add the accounts dependencies to the `dependencies` block
in each module of your CorDapp. For contract modules add:

    cordaCompile "accounts_release_group:tokens-contracts:$accounts_release_version"

In your workflow `build.gradle` add:

    cordaCompile "$accounts_release_group:tokens-workflows:$accounts_release_version"

If you want to use the `deployNodes` task, you will need to add the
following dependencies to your root `build.gradle` file:

    cordapp "$accounts_release_group:contracts:$accounts_release_version"
    cordapp "$accounts_release_group:workflows:$accounts_release_version"

These should also be added to the `deployNodes` task with the following syntax:

    nodeDefaults {
        projectCordapp {
            deploy = false
        }
        cordapp("$accounts_release_group:contracts:$accounts_release_version")
        cordapp("$accounts_release_group:workflows:$accounts_release_version")
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