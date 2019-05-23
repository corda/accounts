package net.corda.accounts.cordapp.sweepstake.flows

class TestUtils {

companion object {
    val JAPAN: String = "Japan"
    val BELGIUM: String = "Belgium"
    val REQUIRED_CORDAPP_PACKAGES = listOf(
            "net.corda.accounts.cordapp.sweepstake.states",
            "net.corda.accounts.cordapp.sweepstake.contracts",
            "net.corda.accounts.cordapp.sweepstake.flows",
            "net.corda.accounts.cordapp.sweepstake.service",
            "net.corda.accounts.service",
            "net.corda.accounts.contracts",
            "net.corda.accounts.flows")
    }
}
