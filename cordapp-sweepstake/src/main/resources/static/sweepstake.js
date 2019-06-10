async function makeRequest(method, url) {
    return new Promise(function (resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, url);
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

function importPlayers() {

    let playersPromise = makeRequest("GET", `/load-players/`).then(text => JSON.parse(text));

    Promise.all([playersPromise]).then(results => {
        let players = results[0];
        let playersTable = document.createElement("table");
        playersTable.setAttribute("id", "tournamentData")

        // let headerRow = document.createElement("tr");
        //
        // let playerHeader = document.createElement("th");
        // playerHeader.innerText = "Player name";
        //
        // let teamHeader = document.createElement("th");
        // teamHeader.innerText = "Team";
        //
        // let accountHeader = document.createElement("th");
        // accountHeader.innerText = "Account ID";
        //
        // let groupHeader = document.createElement("th");
        // groupHeader.innerText = "Group";
        //
        // headerRow.appendChild(playerHeader);
        // headerRow.appendChild(teamHeader);
        // headerRow.appendChild(accountHeader);
        // headerRow.appendChild(groupHeader);
        // playersTable.appendChild(headerRow);

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
    let accountsAndTeamsPromise = makeRequest("GET", `/create-accounts-issue-teams/`).then(text => JSON.parse(text));

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

function assignGroups(){
    let groupsPromise = makeRequest("GET", `/issue-groups/`).then(text => JSON.parse(text));
    let playersTable = document.getElementById('tournamentData');
    playersTable.classList.add("grouping");
    // Promise.all([groupsPromise]).then(results => {
    //     let groups = results[0];
    //
    //     let count=0;
    //     groups.forEach(function(element, i) {
    //         createCell(playersTable.rows[i].insertCell(playersTable.rows[i].cells.length), element.groupName, 'col');
    //         count++;
    //     });

        document.getElementById("groupBtn").disabled = 'true';
    // })

}

function createCell(cell, text, style) {
    let div = document.createElement('div'), // create DIV element
        txt = document.createTextNode(text); // create text node
    div.appendChild(txt);                    // append text node to the DIV
    div.setAttribute('class', style);        // set DIV class attribute
    div.setAttribute('className', style);    // set DIV class attribute for IE (?!)
    cell.appendChild(div);                   // append DIV to the table cell
}
