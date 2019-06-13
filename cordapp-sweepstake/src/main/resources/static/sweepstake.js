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
    return new Promise(function (resolve, reject) {
        xhr = new XMLHttpRequest();
        xhr.open("POST", url, true);
        xhr.setRequestHeader("Content-type", "application/json");

        xhr.onload = function() {
            if (xhr.status == 200) {
                resolve(xhr.response);
            } else {
                reject(Error(xhr.statusText));
            }
        };
        xhr.onerror = function() {
            reject(Error("Network error"));
        };
        xhr.onreadystatechange = function () {
            if (xhr.readyState == 4 && xhr.status == 200) {
                var json = JSON.parse(xhr.responseText);
            }
        };
        xhr.send(data);
    });
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

function createAccountsAndIssueTeams() {
    let accountsAndTeamsPromise = getRequest(`/create-accounts-issue-teams/`).then(text => JSON.parse(text));

    Promise.all([accountsAndTeamsPromise]).then(results => {
        let accountsAndTeams = results[0];

        let playersTable = document.getElementById('tournamentData');
        let accountIds = Object.keys(accountsAndTeams)
        let teams = Object.values(accountsAndTeams)

        teams.forEach( function(element, i=1) {
            createCell(playersTable.rows[i].insertCell(playersTable.rows[i].cells.length), element, 'secondCol');
        });

        accountIds.forEach(function(element, j=1) {
            createCell(playersTable.rows[j].insertCell(playersTable.rows[j].cells.length), element, 'thirdCol');
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
        let matches = document.getElementById("matches");
        let header = document.createElement("H1");
        let headerText = document.createTextNode("Match Results")
        header.appendChild(headerText);
        matches.appendChild(header);

        let teamStates = results[0];
        let matchResults = runMatches(teamStates);
        while (matchResults.length > 4) {
            matchResults = runMatches(matchResults)
        }

        let finalResult = shuffle(matchResults);
        let firstTeam = finalResult[0];
        let secondTeam = finalResult[1];
        let thirdTeam = finalResult[2];
        let fourthTeam = finalResult[3];

        let winners = {
            'one': firstTeam.linearId.id.toString(),
            'two': secondTeam.linearId.id.toString(),
            'three': thirdTeam.linearId.id.toString(),
            'four': fourthTeam.linearId.id.toString(),
        }

        localStorage.setItem('winners', JSON.stringify(winners));

        setTimeout(function(){
            matches.appendChild(document.createTextNode("--------------------------------"));
            matches.appendChild(document.createElement("br"));
            matches.appendChild(document.createTextNode("1st place = " + firstTeam.team.teamName));
            matches.appendChild(document.createElement("br"));
            matches.appendChild(document.createTextNode("2nd place = " + secondTeam.team.teamName));
        }, 20000);

        setTimeout(function() {
            matches.appendChild(document.createElement("br"));
            matches.appendChild(document.createTextNode("3rd place = " + thirdTeam.team.teamName));
            matches.appendChild(document.createElement("br"));
            matches.appendChild(document.createTextNode("4th place = " + fourthTeam.team.teamName));
            matches.appendChild(document.createElement("br"))
        },22000);

        document.getElementById("playMatchesBtn").disabled = 'true';

        setTimeout(function() {
            document.getElementById("distWinningsBtn").disabled = false;
        }, 22500);
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

        let linearIdForTeamA = teamA.linearId.id.toString();
        let linearIdForTeamB = teamB.linearId.id.toString();
        let linearIdForWinner = winningTeam.linearId.id.toString();

        let jsonObj = { teamAId: linearIdForTeamA, teamBId: linearIdForTeamB, winningTeamId: linearIdForWinner }
        let jsonStr = JSON.stringify(jsonObj);
        // postRequest(jsonStr, "/play-match/")

        (function(index) {
            setTimeout(function() {
                let matchResult = document.createTextNode(teamA.team.teamName + " are playing " + teamB.team.teamName + " and the winner is: " + winningTeam.team.teamName);
                matches.appendChild(matchResult);
                matches.appendChild(document.createElement("br"));
            }, i*500);
        })(i);
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

function distributeWinnings() {
    let prize = prompt("Enter the total prize pot:");

    let winners = localStorage.getItem('winners');
    let json = JSON.parse(winners);
    json.prize = prize;
    json = JSON.stringify(json);
    let accountIdPromise = postRequest(json, "/distribute-winnings/");

    Promise.all([accountIdPromise]).then(results => {
        let json = results[0];
        let accountIds = JSON.parse(json);
        let table = document.getElementById('tournamentData');
        let accounts = document.getElementsByClassName('thirdCol');
        let arr = Array.from(accounts);
        // Remove header from the array
        arr.shift();

        let individualPrize = prize / accountIds.length;
        arr.forEach( function(element, i=0) {
            createCell(table.rows[i].insertCell(table.rows[i].cells.length), '£0.00', 'fourthCol');
        });
        // Disgusting
        for (let i = 0; i < arr.length; i++) {
            for (let j = 0; j < accountIds.length; j++) {
                if (accountIds[j] == arr[i].textContent) {
                    console.log("changing innerHTML of i: " + i)
                    let col = table.rows[i].cells;
                    col[3].innerHTML = "£" + individualPrize.toFixed(2);
                }
            }
        }
        document.getElementById("distWinningsBtn").disabled = 'true';
    });
}