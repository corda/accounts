# Accounts World Cup Sweepstake CorDapp

# Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

# Usage

From the root directory run the following commands:

* `./gradlew clean deployNodes`
* `cordapp-application/build/nodes/runnodes`

Once built, start the spring boot web application [WorldCupApplication.kt] using the following command:
`./gradlew runTournamentServer`

Navigate to the server
`http://localhost:8080/`