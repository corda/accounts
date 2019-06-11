async function getRequest(url) {
    return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", url);
        xhr.onload = function () {
            if (this.status >= 200 && this.status < 300) {
                resolve(xhr.response);
            } else {
                reject({
                    status: this.status,
                    statusText: xhr.statusText
                });
            }
        };
        xhr.onerror = function () {
            reject({
                status: this.status,
                statusText: xhr.statusText
            });
        };
        xhr.send();
    });
}

async function postRequest(data, url) {
    xhr = new XMLHttpRequest();
    xhr.open("POST", url, true);
    xhr.setRequestHeader("Content-type", "application/json");
    xhr.onreadystatechange = function () {
        if (xhr.readyState == 4 && xhr.status == 200) {
            var json = JSON.parse(xhr.responseText);
            console.log(json.body)
        }
    }
    xhr.send(data);
}

function importPlayers() {

    let playersPromise = getRequest(`/load-players/`).then(text => JSON.parse(text));

    Promise.all([playersPromise]).then(results => {
        let players = results[0];
        let playersTable = document.createElement("table");
        playersTable.setAttribute("id", "tournamentData")

        players.forEach(player => {
            let row = document.createElement("tr");
            let playerNameCell = document.createElement("td");
            playerNameCell.innerText = player.playerName;
            playerNameCell.setAttribute("class", "firstCol")

            row.appendChild(playerNameCell);

            playersTable.appendChild(row);
        });

        document.getElementById('playersHolder').appendChild(playersTable);
        document.getElementById("playerBtn").disabled = 'true';
    })
}

function createAccounts() {
    let accountsAndTeamsPromise = getRequest(`/create-accounts-issue-teams/`).then(text => JSON.parse(text));

    Promise.all([accountsAndTeamsPromise]).then(results => {
        let accountsAndTeams = results[0];

        let playersTable = document.getElementById('tournamentData');
        let accountIds = Object.keys(accountsAndTeams)
        let teams = Object.values(accountsAndTeams)

        let count;
        teams.forEach( function(element, j=1) {
            createCell(playersTable.rows[j].insertCell(playersTable.rows[j].cells.length), element, 'secondCol');
            count++;
        });

        let count1;
        accountIds.forEach(function(element, i=1) {
            createCell(playersTable.rows[i].insertCell(playersTable.rows[i].cells.length), element, 'thirdCol');
            count1++;
        });
        document.getElementById("accountBtn").disabled = 'true';
    });

}

function createCell(cell, text, style) {
    let div = document.createElement('div'),
        txt = document.createTextNode(text);
    div.appendChild(txt);
    div.setAttribute('class', style);
    div.setAttribute('className', style);    // set DIV class attribute for IE
    cell.appendChild(div);
}

function assignGroups(){
    getRequest(`/issue-groups/`).then(text => console.log(text));

    let playersTable = document.getElementById('tournamentData');

    playersTable.classList.add("grouping");

    document.getElementById("groupBtn").disabled = 'true';
}

function playMatches() {
    let teamsPromise = getRequest(`/load-teams/`).then(text => JSON.parse(text));
    Promise.all([teamsPromise]).then(results => {
        let teamStates = results[0];
        let matchResults = runMatches(teamStates);
        while (matchResults.length > 4) {
            matchResults = runMatches(matchResults)
        }

        let finalResult = shuffle(matchResults);

        let matches = document.getElementById("matches");
        matches.appendChild(document.createTextNode("--------------------------------"));
        matches.appendChild(document.createElement("br"));
        matches.appendChild(document.createTextNode("1st place = " + finalResult[0].team.teamName));
        matches.appendChild(document.createElement("br"));
        matches.appendChild(document.createTextNode("2nd place = " + finalResult[1].team.teamName));
        matches.appendChild(document.createElement("br"));
        matches.appendChild(document.createTextNode("3rd place = " + finalResult[2].team.teamName));
        matches.appendChild(document.createElement("br"));
        matches.appendChild(document.createTextNode("4th place = " + finalResult[3].team.teamName));
        matches.appendChild(document.createElement("br"));
    });
}

function generateWinner(teamA, teamB) {
    let result = Math.random()
    if (result <= 0.5) {
        return teamA
    } else {
        return teamB
    }
}

function runMatches(teamStates) {
    let winningTeams = [];
    for (let i=1; i < teamStates.length; i +=2) {
        let teamA = teamStates[i-1];
        let teamB = teamStates[i];
        let winningTeam = generateWinner(teamA, teamB);
        winningTeams.push(winningTeam);

        let linearIdForTeamA = teamA.linearId.toString();
        let linearIdForTeamB = teamB.linearId.toString();
        let linearIdForWinner = winningTeam.linearId.toString();

        let jsonObj = { teamAId: linearIdForTeamA, teamBId: linearIdForTeamB, winningTeamId: linearIdForWinner }
        let jsonStr = JSON.stringify(jsonObj)
        postRequest(jsonStr, "/play-match/")

        let matches = document.getElementById("matches");
        let matchResult = document.createTextNode(teamA.team.teamName + " are playing " + teamB.team.teamName + " and the winner is: " + winningTeam.team.teamName);
        matches.appendChild(matchResult);
        matches.appendChild(document.createElement("br"));
    }
    return winningTeams;
}

function shuffle(a) {
    for (let i = a.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
}

function distributeWinnings(){

}