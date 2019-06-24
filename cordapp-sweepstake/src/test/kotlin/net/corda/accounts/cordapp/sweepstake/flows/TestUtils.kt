package net.corda.accounts.cordapp.sweepstake.flows

import net.corda.accounts.workflows.services.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow

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
                "net.corda.accounts.cordapp.sweepstake.states",
                "net.corda.accounts.cordapp.sweepstake.contracts",
                "net.corda.accounts.cordapp.sweepstake.flows",
                "net.corda.accounts.cordapp.sweepstake.service",
                "net.corda.accounts.workflows",
                "net.corda.accounts.contracts",
                "com.r3.corda.lib.tokens.contracts",
                "com.r3.corda.lib.tokens.workflows"
        )
    }

}

internal fun createAccountsForNode(accountService: KeyManagementBackedAccountService) {
    accountService.createAccount("TEST_ACCOUNT_1").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_2").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_3").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_4").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_5").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_6").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_7").getOrThrow()
    accountService.createAccount("TEST_ACCOUNT_8").getOrThrow()
}

