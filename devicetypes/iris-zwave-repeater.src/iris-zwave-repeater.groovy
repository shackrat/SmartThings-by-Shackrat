/**
 *  Copyright 2017 Steve White
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Iris Z-Wave Repeater", namespace: "shackrat", author: "Steve White") {
	capability "Health Check"
	capability "Polling"
	capability "Refresh"
	capability "Sensor"
	capability "Configuration"

	command "testCommunication"
	command "interrogateDevice"

	fingerprint prod: "0001", model: "0001", cc: "85,59,5A,72,73,86,5E",  deviceJoinName: "Iris Z-Wave Repeater"
}

	tiles(scale: 2)
    {
		multiAttributeTile(name: "status", type: "generic", width: 6, height: 4)
		{
			tileAttribute("device.status", key: "PRIMARY_CONTROL")
			{
				attributeState "unknown", label: 'unknown', icon: "st.motion.motion.inactive", backgroundColor: "#ffffff"
				attributeState "online", label: 'online', icon: "st.motion.motion.active", backgroundColor: "#00A0DC"
				attributeState "offline", label: 'offline', icon: "st.motion.motion.inactive", backgroundColor: "#ffffff"
			}
		}
        
		valueTile("powerLevel", "powerLevel", width: 3, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'POWER LEVEL\n${currentValue}'
		}
		valueTile("deviceVersion", "deviceVersion", width: 3, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'VERSION\n${currentValue}'
		}

		standardTile("refresh", "device.refresh", width: 3, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'REFRESH', action:"refresh", icon:"st.secondary.refresh"
		}
        
		standardTile("configure", "device.reconfigure", width: 3, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'Configure', action:"configure", icon:"st.motion.motion.inactive"
		}

		standardTile("testCommunication", "testCommunication", width: 3, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'Z-Wave Test', action:"testCommunication", icon:"st.Entertainment.entertainment15"
		}

		standardTile("interrogateDevice", "interrogateDevice", width: 3, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'Interrogate', action:"interrogateDevice", icon:"st.Office.office9"
		}


		valueTile("lastTestLbl", "lastTestLbl", width: 2, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'Last Test:'
		}
		valueTile("lastTest", "lastTest", width: 4, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'${currentValue}'
		}
		valueTile("lastRefreshLbl", "lastRefreshLbl", width: 2, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'Last Refresh:'
		}
		valueTile("lastRefresh", "lastRefresh", width: 4, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'${currentValue}'
		}
		valueTile("lastMsgRcvdLbl", "lastMsgRcvdLbl", width: 2, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'Last Response:'
		}
		valueTile("lastMsgRcvd", "lastMsgRcvd", width: 4, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'${currentValue}'
		}

		main "status"
		details (["status", "powerLevel", "deviceVersion", "lastTestLbl", "lastTest", "lastRefreshLbl", "lastRefresh", "lastMsgRcvdLbl", "lastMsgRcvd", "refresh", "configure", "testCommunication", "interrogateDevice"])
	}
}


def parse(String description)
{
	log.trace "Parsing ${description}"
	def result = []
	if (description.startsWith("Err"))
	{
		result = createEvent(descriptionText:description, isStateChange:true)
	}
	else
	{
		def cmd = zwave.parse(description, [0x20: 1, 0x70: 1, 0x84: 1, 0x98: 1, 0x56: 1, 0x60: 3])
		if (cmd)
		{
			result += zwaveEvent(cmd)
		}
	}
	return result
}


def updated()
{
    configure()
}


/*
	configure
    
	Configures the Z-Wave repeater associations and establishes periodic device check.
*/
def configure()
{
	unschedule()

	// Three failed pings and the device is considered offline
	state.failedPings = 0

	// Health check stuff...  This appears to do nothing useful.
	// Device-Watch simply pings if no device events received for checkInterval duration of 32min = 2 * 15min + 2min lag time
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    
	log.debug "Sending configuration commands to Iris Smart Plug..."
	def request =
	[
		// Associate Group 1 (Lifeline) with the Hub.
		zwave.associationV2.associationSet(groupingIdentifier:1, nodeId: zwaveHubNodeId),
	]
	sendCommands(request)

	// Check every 30 minutes to see if the device is responding.
	runEvery30Minutes(refresh)
}


/*
	VERSION REPORT RESPONSE
*/
def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd)
{
	def fwVersion = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	log.debug "VERSION REPORT V1: ${device.displayName} is running firmware version: ${fwVersion}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"

	def dateTime = new Date()
	sendEvent(name: "deviceVersion", value: fwVersion, displayed: false)
    sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": VERSION REPORT V1", displayed: false)
    updateStatus()
}


/*
	POWER LEVEL REPORT RESPONSE
*/
def zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelReport cmd)
{
	def pwrLevel = (cmd.powerLevel > 0) ? "${cmd.powerLevel}dBm." : "NOMINAL"
	log.debug "POWER LEVEL REPORT V1: Transmit power for ${device.displayName} is ${pwrLevel}."

	def dateTime = new Date()
	sendEvent(name: "powerLevel", value: pwrLevel, displayed: false)
    sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": POWER LEVEL REPORT V1", displayed: false)
    updateStatus()
}


/*
	POWER LEVEL TEST REPORT RESPONSE

	Status Codes:
	STATUS_OF_OPERATION_ZW_TEST_FAILED	= 0
	STATUS_OF_OPERATION_ZW_TEST_SUCCES	= 1
	STATUS_OF_OPERATION_ZW_TEST_INPROGRESS	= 2
*/
def zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelTestNodeReport cmd)
{
	log.debug "statusOfOperation: ${cmd.statusOfOperation}"

	def testResults = ""
	switch (cmd.statusOfOperation)
	{
		case 0:
			testResults = "FAILED with frame count of ${cmd.testFrameCount}."
			break;
		case 1:
			testResults = "SUCCEEDED with frame count of ${cmd.testFrameCount}."
			break;
		case 2:
			testResults = "IN PROGRESS with frame count of ${cmd.testFrameCount}."
			if (state.testRefreshCount <= 2) runIn(10, getCommunicationTestResults)
			break;
	}
	if (state.testRefreshCount >= 3) testResults = "FAILED due to TIMEOUT."
	sendEvent(name: "lastTest", value: testResults, displayed: false)

	def dateTime = new Date()
	log.debug testResults
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": POWER LEVEL NODE TEST REPORT V1", displayed: false)
	updateStatus()
}


/*
	MANUFACTURER SPECIFIC RESPONSE

	Note: Used for device up/down checks.
*/
def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd)
{
	// Thank you Z-Wave Tweaker!!
	def manufacturerIdDisp = String.format("%04X",cmd.manufacturerId)
	def productIdDisp = String.format("%04X",cmd.productId)
	def productTypeIdDisp = String.format("%04X",cmd.productTypeId)
    
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)
	updateDataValue("manufacturer", cmd.manufacturerName)

	def dateTime = new Date()
	log.debug "MANUFACTURER SPECIFIC V2: Manufacturer ID: ${manufacturerIdDisp}, Manufacturer Name: ${cmd.manufacturerName}, Product Type ID: ${productTypeIdDisp}, Product ID: ${productIdDisp}"
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": MANUFACTURER SPECIFIC V2", displayed: false)
    
	updateStatus()
}


/*
	FIRMWARE REPORT RESPONSE
    
	Note: Not currently used
*/
def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd)
{
	// Thank you Z-Wave Tweaker!!
	def firmwareIdDisp = String.format("%04X",cmd.firmwareId)
	def checksumDisp = String.format("%04X",cmd.checksum)

	def dateTime = new Date()
	log.debug "FIRMWARE METADATA REPORT V1: ${device.displayName} is running firmware ID: ${firmwareIdDisp} with checksum: ${checksumDisp}"
    sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": FIRMWARE METADATA REPORT V1", displayed: false)
	updateStatus()
}


def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	createEvent(descriptionText: "Log: $device.displayName: $cmd", isStateChange: true)
}


/*
	updateStatus
    
	Sets the device state to online, but only if previously offline.
*/
def updateStatus()
{
    Map deviceState = [
		name: "status",
		isStateChange: (device.currentValue('status') != 'online' ? true : false),
		displayed: (device.currentValue('status') != 'online' ? true : false),
		value: 'online',
		descriptionText: "$device.displayName is online"
	]

	state.failedPings = 0
	log.info "Device is online"
	sendEvent(deviceState)
}


/*
	poll
    
	Sets the device state to online, but only if previously offline.

	Note: This has *NEVER* been called by the hub in testing, but is included for use with 3rd party SmartApps
*/
def poll()
{
	log.debug "Poll called..."
    refresh()
}


/*
	testCommunication
    
	Executes a Z-Wave powerlevel test with 10 transmission frames. 

	Note: Useful for testing Z-Wave signal reliability
*/
def testCommunication()
{
	// Test 10 frames at nominal power
	sendHubCommand(new physicalgraph.device.HubAction(zwave.powerlevelV1.powerlevelTestNodeSet(powerLevel: 0, testFrameCount: 10, testNodeid: device.deviceNetworkId.toInteger()).format()))
	runIn(10, getCommunicationTestResults)
    state.testRefreshCount = 0
    
	sendEvent(name: "lastMsgRcvd", value: "", displayed: false)
    log.trace "Sending test command to device..."
	sendEvent(name: "lastTest", value: "Sending test command to device...", displayed: false)
}


/*
	getCommunicationTestResults
    
	Requests results from device of test initiated by testCommunication() 

	Note: Attempts to retreive results at 10 second intervals, up to 3 times before timing out.
*/
def getCommunicationTestResults()
{
	sendHubCommand(new physicalgraph.device.HubAction(zwave.powerlevelV1.powerlevelTestNodeGet().format()))
	state.testRefreshCount = state.testRefreshCount + 1

    log.trace "Requesting test results from device..."
	sendEvent(name: "lastTest", value: "Requesting results from device...", displayed: false)
}


/*
	interrogateDevice
    
	Requests power level and device version details from device.

	Note: SmartPlugs can be slow to respond to Z-Wave commands; it's not unusual for one not be handled by the device.
*/
def interrogateDevice()
{
	log.trace "Interrogating device..."
	sendEvent(name: "lastMsgRcvd", value: "", displayed: false)
	sendEvent(name: "powerLevel", value: "--", displayed: false)
	sendEvent(name: "deviceVersion", value: "--", displayed: false)

    if (state.failedPings >= 3)
    {
		if (device.currentValue('status') != 'offline')
		{
			sendEvent([
				name: "status",
				isStateChange: true,
				displayed: true,
				value: 'offline',
				descriptionText: "${device.displayName} is offline"
			])
		}
		log.info "Device is offline"
	}
	state.failedPings = state.failedPings + 1
    
    def cmds = []
    cmds << zwave.powerlevelV1.powerlevelGet()
    cmds << zwave.versionV1.versionGet()
    sendCommands(cmds, 2000)
}


/*
	refresh
    
	Refreshes the device by requesting manufacturer-specific information.

	Note: Three missed communication attempts will cause the device to be declared offline.
*/
def refresh()
{
	log.trace "Checking to see if device is online..."

	def dateTime = new Date()
	sendEvent(name: "lastRefresh", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: false)
	sendEvent(name: "lastMsgRcvd", value: "", displayed: false)

    if (state.failedPings >= 3)
    {
		if (device.currentValue('status') != 'offline')
		{
			sendEvent([
				name: "status",
				isStateChange: true,
				displayed: true,
				value: 'offline',
				descriptionText: "${device.displayName} is offline"
			])
		}
		log.info "Device is offline"
	}
	state.failedPings = state.failedPings + 1
    
    def cmds = []

    //cmds << zwave.firmwareUpdateMdV2.firmwareMdGet()
    cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
	//cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
    sendCommands(cmds)
}


private sendCommand(physicalgraph.zwave.Command cmd)
{
	sendHubCommand(new physicalgraph.device.HubAction(cmd.format()))
}


private sendCommands(commands, delay=500)
{
	delayBetween(commands.collect{ sendCommand(it) }, delay)
}
