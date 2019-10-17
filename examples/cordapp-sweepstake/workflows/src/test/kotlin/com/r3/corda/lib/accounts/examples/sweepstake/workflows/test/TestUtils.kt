package com.r3.corda.lib.accounts.examples.sweepstake.workflows.test

import com.r3.corda.lib.accounts.examples.sweepstake.contracts.states.WorldCupTeam
import com.r3.corda.lib.accounts.workflows.services.AccountService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.TestCordapp

class TestUtils {

    companion object {
        val JAPAN: String = "Japan"
        val BELGIUM: String = "Belgium"
        val FRANCE: String = "France"
        val GERMANY: String = "Germany"
        val TURKEY: String = "Turkey"
        val IRAN: String = "Iran"
        val RUSSIA: String = "Russia"
        val NIGERIA: String = "Nigeria"

        val teams = listOf(
                WorldCupTeam(JAPAN, true),
                WorldCupTeam(BELGIUM, true),
                WorldCupTeam(FRANCE, true),
                WorldCupTeam(GERMANY, true),
                WorldCupTeam(TURKEY, true),
                WorldCupTeam(IRAN, true),
                WorldCupTeam(RUSSIA, true),
                WorldCupTeam(NIGERIA, true)
        )

        val REQUIRED_CORDAPP_PACKAGES = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.examples.sweepstake.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.examples.sweepstake.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.ci")
        )
    }

}

internal fun createAccountsForNode(accountService: AccountService) {
    accountService.createAccount("TEST_ACCOUNT_1").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_2").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_3").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_4").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_5").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_6").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_7").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_8").getOrThrow()
}