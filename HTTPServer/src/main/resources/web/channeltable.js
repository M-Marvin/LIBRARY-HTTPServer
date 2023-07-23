const MAX_CHANNELS = 100; // TODO Query from server
const CHANNELROW_TEMPLATE = document.getElementById("channelrow_template");
document.getElementById("channeltable").deleteRow(1);

function getChannelRow(index) {
	const table = document.getElementById("channeltable");
	return table.rows[index + 1];
}
function getChannelCount() {
	const table = document.getElementById("channeltable");
	return table.rows.length - 2;
}
function findChannelRow(channel) {
	const table = document.getElementById("channeltable");
	for (var i = 1; i < table.rows.length - 1; i++) {
		if (table.rows[i].querySelector('td[name="channel"]').innerHTML == channel) {
			return i - 1;
		}
	}
}
function getNextFreeChannel() {
	const channeltable = document.getElementById("channeltable");
	const channelsUsed = new Array();
	for (var i = 1; i < channeltable.rows.length - 1; i++) {
		channelsUsed.push(parseInt(channeltable.rows[i].querySelector('td[name="channel"]').innerHTML));
	}
	for (var i = 0; i < MAX_CHANNELS; i++) {
		if (!channelsUsed.includes(i)) return i;
	}
	return -1;
}
function appendChannelRow(newChannel) {
	const channeltable = document.getElementById("channeltable");
	const numChannels = getChannelCount();
	const templateRow = CHANNELROW_TEMPLATE;
	const lastRow = channeltable.rows[channeltable.rows.length - 1];
	
	const newRow = templateRow.cloneNode(true);
	channeltable.tBodies[0].insertBefore(newRow, lastRow);
	newRow.querySelector('td[name="channel"]').innerHTML = newChannel;
	setChannelRow(numChannels, "new channel", 0, 0);
	
	newRow.querySelector('button[name="cntrl_learn"]').addEventListener("click", () => triggerLearn(newChannel, true));
	newRow.querySelector('button[name="cntrl_up"]').addEventListener("click", () => triggerUp(newChannel, true));
	newRow.querySelector('button[name="cntrl_down"]').addEventListener("click", () => triggerDown(newChannel, true));
	newRow.querySelector('button[name="cntrl_stop"]').addEventListener("click", () => triggerStop(newChannel, true));
	
	newRow.querySelector('input[name="name"]').addEventListener("change", () => renameChannel(newChannel));
	newRow.querySelector('button[name="deletebtn"]').addEventListener("click", () => deleteChannel(newChannel));
	newRow.querySelector('button[name="initbnt"]').addEventListener("click", () => initChannel(newChannel));
	return numChannels;
}
function deleteRow(index) {
	const table = document.getElementById("channeltable");
	if (index - table.rows.length <= -3) {
		table.deleteRow(index + 1);
	}
}
function setChannelRow(index, name, serial, counter) {
	const row = getChannelRow(index);
	row.querySelector('input[name="name"]').value = name;
	row.querySelector('input[name="serial"]').value = serial.toString(16).toUpperCase();
	row.querySelector('td[name="countervalue"]').innerHTML = counter;
}
function updateChannelTable(channelConfiguration) {
	const table = document.getElementById("channeltable");
	for (var i = 0; i < getChannelCount(); i++) deleteRow(i);
	for (var i = 0; i < channelConfiguration.channels.length; i++) {
		appendChannelRow(channelConfiguration.channels[i].channel);
		setChannelRow(i, channelConfiguration.channels[i].name, channelConfiguration.channels[i].serial, channelConfiguration.channels[i].counter);
	}
}
function updateChannelRow(singleChannelConfiguration) {
	const channel = singleChannelConfiguration.channel;
	const name = singleChannelConfiguration.name;
	const serial = singleChannelConfiguration.serial;
	const counter = singleChannelConfiguration.counter;
	const index = findChannelRow(channel);
	setChannelRow(index, name, serial, counter);
}



function verifyHexInput(inputField) {
	const hexInput = inputField.value
	if (hexInput.match("[A-Fa-f0-9]+") != hexInput || hexInput.length > 8) {
		inputField.value = "00000000";
	}
}



function addNewChannel() {
	const index = appendChannelRow(getNextFreeChannel());
	const channelrow = getChannelRow(index);
	
	const channel = channelrow.querySelector('td[name="channel"]').innerHTML;
	const newChannelName = channelrow.querySelector('input[name="name"]').value;
	fetch("./apply", { method: "POST", body: JSON.stringify({ action: "create_channel", channel: channel, new_name: newChannelName }), headers: {	"Content-type": "application/json; charset=UTF-8" }});
}
function renameChannel(channel) {
	const index = findChannelRow(channel);
	const channelrow = getChannelRow(index);
	
	const newChannelName = channelrow.querySelector('input[name="name"]').value;
	fetch("./apply", { method: "POST", body: JSON.stringify({ action: "rename_channel", channel: channel, new_name: newChannelName }), headers: {	"Content-type": "application/json; charset=UTF-8" }});
}
function deleteChannel(channel) {
	const index = findChannelRow(channel);
	const channelrow = getChannelRow(index);
	if (!confirm("Delete channel " + channelrow.querySelector('input[name="name"]').value + " ?")) return;
	deleteRow(index);
	
	fetch("./apply", { method: "POST", body: JSON.stringify({ action: "delete_channel", channel: channel }), headers: {	"Content-type": "application/json; charset=UTF-8" }});
}
function initChannel(channel) {
	const index = findChannelRow(channel);
	const channelrow = getChannelRow(index);
	if (!confirm("Re-Init channel " + channelrow.querySelector('input[name="name"]').value + " with the displayed settings ?")) return;
	
	const counterState = channelrow.querySelector('td[name="countervalue"]').innerHTML;
	const newSerial = parseInt(channelrow.querySelector('input[name="serial"]').value, 16);
	fetch("./apply", { method: "POST", body: JSON.stringify({ action: "init_channel", channel: channel, new_serial: newSerial, new_counter: counterState }), headers: {	"Content-type": "application/json; charset=UTF-8" }});	
}



function triggerLearn(channel, isTest) {
	fetch("./controll?channel=" + channel + "&action=learn&test=" + isTest, { method: "GET" })
	.then((response) => response.json())
	.then((json) => updateChannelRow(json));
}
function triggerUp(channel, isTest) {
	fetch("./controll?channel=" + channel + "&action=up&test=" + isTest, { method: "GET" })
	.then((response) => response.json())
	.then((json) => updateChannelRow(json));
}
function triggerDown(channel, isTest) {
	fetch("./controll?channel=" + channel + "&action=down&test=" + isTest, { method: "GET" })
	.then((response) => response.json())
	.then((json) => updateChannelRow(json));
}
function triggerStop(channel, isTest) {
	fetch("./controll?channel=" + channel + "&action=stop&test=" + isTest, { method: "GET" })
	.then((response) => response.json())
	.then((json) => updateChannelRow(json));
}



function queryChannelTable() {
	fetch("./channel_table", { method: "GET" })
	.then((response) => response.json())
	.then((json) => updateChannelTable(json));
}

queryChannelTable();