<p align="center">
<a href="https://ibb.co/vcTTKgC"><img src="https://i.ibb.co/nwYY5Zq/Corda-World-Cup.jpg" alt="Corda-World-Cup" border="0"></a>
</p>

# Accounts World Cup Sweepstake CorDapp

This app can be used to demonstrate the concept of _account grouping_ and integration with the `tokens-sdk`. We represent a simplified World Cup that is just a 32 team knockout tournament. For simplicity this is all run on one node but if we were to extend the app to run over multiple nodes we would just need to invoke the `ShareStateAndSyncAccountsFlow`. Examples of this can be seen in the `SimulateWorldCup`  integration test. 

We import a list of `Participants`, each of which is randomly assigned to a single team in the tournament. Each `Participant` has an account created for them. We randomly assign each account to a single team. This is modelled as a 'TeamState'. 

Once the teams and accounts have been assigned, we then combine the accounts into groups of four. These groups are modelled as 'AccountGroup' states which contain a list of `accountId` for the four accounts in that group. 

The matches are simulated using the `MatchDayFlow` where two `TeamStates` are taken as the input states and the winning `TeamState` as the output state. This flow is repeated until four teams remain. The final four teams are shuffled to determine 1st, 2nd, 3rd and 4th place. 

The total prize money in `GBP` is then distributed across winning accounts using the `DistributeWinningsFlow`. A `VaultQuery` is used to find the account linked to top four `TeamState`, and then find the list of accounts that share the same `AccountGroup` as those four winning accounts. 

The prize money is split across the number of winning accounts and issued as `GBP` tokens using the `IssueTokens` flow.  
 
# Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

# Usage

From the root directory run the following commands:

* `./gradlew clean deployNodes`
* `cordapp-application/build/nodes/runnodes`

Once built, start the spring boot web application `WorldCupApplication` using the following command:
`./gradlew runTournamentServer`

Navigate to the server
`http://localhost:8080/`

1. Click the 'Import players' button
2. Click the 'Create accounts and issue teams' button. Wait for the two columns on the table to be populated
3. Click the 'Create groups' button
4. Click the 'Play matches' button. The results will be printed at the bottom of the screen. Wait for final results to be generated
5. Click the 'Enter prize' button and enter some amount that will be split between the winning accounts

