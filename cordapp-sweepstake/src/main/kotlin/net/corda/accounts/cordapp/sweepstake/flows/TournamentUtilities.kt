package net.corda.accounts.cordapp.sweepstake.flows

import com.google.common.annotations.VisibleForTesting
import net.corda.core.CordaInternal
import java.io.File

@CordaInternal
@VisibleForTesting
fun generateTeamsFromFile(filePath: String): List<WorldCupTeam> {
    return if (filePath != null) {
        File(filePath).readLines().toMutableList().map {
            teamString -> WorldCupTeam(teamString)
        }.shuffled()
    } else {
        emptyList()
    }
}

@CordaInternal
@VisibleForTesting
fun generateParticipantsFromFile(filePath: String) : List<String> {
    return if (filePath != null) {
        File(filePath).readLines().shuffled()
    } else {
        emptyList()
    }
}