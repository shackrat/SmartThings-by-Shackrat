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
metadata
{
	definition (name: "Iris Z-Wave Repeater", namespace: "shackrat", author: "Steve White")
	{
		capability "Health Check"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Configuration"

		command "testCommunication"
		command "interrogateDevice"

		fingerprint prod: "0001", model: "0001", cc: "85,59,5A,72,73,86,5E",  deviceJoinName: "Iris Z-Wave Repeater"
	}

	preferences
	{
		section("Device Health")
		{
			paragraph "SmartThings is unable to accurately check the health of these devices.  Instead, the device can be checked every 30 minutes to ensure that it's online and healthy."
			input "enablePing", "bool", title: "Enable device online check?", description: "Periodically check in with device to ensure it's online and healthy.", defaultValue: true, required: true, multiple: false
		}

		section("Device Logging")
		{
			paragraph "By default, only device status is reported.  If you are experiencing an issue with the SmartPlug, you can enable additional output in the IDE."
			input "enableTrace", "bool", title: "Enable trace logging?", description: "Indicates high-level activities during the operation of the device.", defaultValue: false, required: true, multiple: false
			input "enableDebug", "bool", title: "Enable debug logging?", description: "Shows detailed responses to Z-Wave commands.", defaultValue: false, required: true, multiple: false
		}
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
		valueTile("deviceMSR", "deviceMSR", width: 3, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'MSR\n${currentValue}'
		}
		valueTile("assocGroup", "assocGroup", width: 3, height: 1, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'ASSOCIATION\n${currentValue}'
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
			state "default", label:'Status:'
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
		details ([
			"status", "powerLevel", "deviceVersion", "deviceMSR", "assocGroup", "lastTestLbl", "lastTest", "lastMsgRcvdLbl",
			"lastMsgRcvd", "lastRefreshLbl", "lastRefresh", "refresh", "interrogateDevice", "configure", "testCommunication"
		])
	}
}


/*
	updated
    
	Doesn't do much other than call configure().
*/
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
    
	logTrace("Sending configuration commands to Iris Smart Plug...")

	// Associate Group 1 (Lifeline) with the Hub.
	sendCommand(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId: zwaveHubNodeId))

	// Check every 30 minutes to see if the device is responding.
	if (enablePing)
	{
		logInfo("Periodic health check is enabled.")
		runEvery30Minutes(refreshDevice)
	}
	else logInfo("Periodic health check is disabled.")
}


/*
	parse
    
	Processes messages from the Z-Wave side of the Iris Smart Plug.
*/
def parse(String description)
{
	logDebug("Parsing ${description}")

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




/*
	POWER LEVEL REPORT RESPONSE
*/
def zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelReport cmd)
{
	def pwrLevel = (cmd.powerLevel > 0) ? "${cmd.powerLevel}dBm." : "NOMINAL"
	logDebug("POWER LEVEL REPORT V1: Transmit power for ${device.displayName} is ${pwrLevel}.")

	def dateTime = new Date()
	sendEvent(name: "powerLevel", value: pwrLevel, displayed: false)
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": POWER LEVEL REPORT V1", displayed: false)
	updateStatus()
 
	if (state.interrogationPhase == 1)
	{
		state.interrogationPhase = 2
		state.interrogationRetries = 0
	}
}


/*
	VERSION REPORT RESPONSE
*/
def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd)
{
	def fwVersion = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	logDebug("VERSION REPORT V1: ${device.displayName} is running firmware version: ${fwVersion}, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")

	def dateTime = new Date()
	sendEvent(name: "deviceVersion", value: fwVersion, displayed: false)
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": VERSION REPORT V1", displayed: false)
	updateStatus()
    
	if (state.interrogationPhase == 2)
	{
		state.interrogationPhase = 3
		state.interrogationRetries = 0
	}
}


/*
	FIRMWARE REPORT RESPONSE
    
	Note: Not currently used
*/
def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd)
{
	// Credit to Z-Wave Tweaker!!
	def firmwareIdDisp = String.format("%04X",cmd.firmwareId)
	def checksumDisp = String.format("%04X",cmd.checksum)

	def dateTime = new Date()
	logDebug("FIRMWARE METADATA REPORT V1: ${device.displayName} is running firmware ID: ${firmwareIdDisp} with checksum: ${checksumDisp}")
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": FIRMWARE METADATA REPORT V1", displayed: false)
	updateStatus()
    
	if (state.interrogationPhase == 3)
	{
		state.interrogationPhase = 4
		state.interrogationRetries = 0
	}
}


/*
	MANUFACTURER SPECIFIC RESPONSE

	Note: Used for device up/down checks.
*/
def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd)
{
	// Credit to Z-Wave Tweaker!!
	def manufacturerIdDisp = String.format("%04X",cmd.manufacturerId)
	def productIdDisp = String.format("%04X",cmd.productId)
	def productTypeIdDisp = String.format("%04X",cmd.productTypeId)
    
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)
	updateDataValue("manufacturer", cmd.manufacturerName)

	def dateTime = new Date()
	logDebug("MANUFACTURER SPECIFIC V2: Manufacturer ID: ${manufacturerIdDisp}, Manufacturer Name: ${cmd.manufacturerName}, Product Type ID: ${productTypeIdDisp}, Product ID: ${productIdDisp}")
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": MANUFACTURER SPECIFIC V2", displayed: false)
    
	sendEvent(name: "deviceMSR", value: msr, displayed: false)
	updateStatus()


	if (state.manualRefresh == 1)
	{
		sendEvent(name: "lastTest", value: "Device is responding!", displayed: false)
		state.manualRefresh = 0
		unschedule(refresh)
	}

	if (state.interrogationPhase == 4)
	{
		state.interrogationPhase = 5
		state.interrogationRetries = 0
	}
}


/*
	ASSOCIATION GROUP NAME RESPONSE
    
	Note: Not currently used
*/
def zwaveEvent(physicalgraph.zwave.commands.associationgrpinfov1.AssociationGroupNameReport cmd)
{
	state.assocGrpName = new String(cmd.name as byte[])

	def dateTime = new Date()
	logDebug("ASSOCIATION GROUP NAME REPORT V1: ${device.displayName} belongs to group ${state.assocGrpName}")
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": ASSOCIATION GROUP NAME REPORT V1", displayed: false)
	updateStatus()

	if (state.interrogationPhase == 5)
	{
		state.interrogationPhase = 6
		state.interrogationRetries = 0
	}
}


/*
	ASSOCIATION GROUP INFO RESPONSE
    
	Note: Not currently used
*/
def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd)
{
	def assocNodes
	def dateTime = new Date()
	logDebug("ASSOCIATION GROUP REPORT V2: ${device.displayName} in Group #${cmd.groupingIdentifier} with nodes: ${cmd.nodeId}")
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": ASSOCIATION GROUP REPORT V2", displayed: false)

	updateStatus()

	if (cmd.nodeId.any { it == zwaveHubNodeId })
	{
		assocNodes = "hub"
		sendEvent(name: "assocGroup", value: "${state.assocGrpName} (#${cmd.groupingIdentifier}) with ${assocNodes}.", displayed: false)
	}
	else
	{
		sendEvent(name: "assocGroup", value: "FAIL: Group 1 not found, configure device!", displayed: false)
	}

	if (state.interrogationPhase == 6)
	{
		state.interrogationPhase = 7
		state.interrogationRetries = 0
	}
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
	logDebug("statusOfOperation: ${cmd.statusOfOperation}")

	def testResults = ""
	switch (cmd.statusOfOperation)
	{
		case 0:
			testResults = "FAILED\n${cmd.testFrameCount} of 30 frames received."
			unschedule(getCommunicationTestResults)
			break;
		case 1:
			testResults = "SUCCESS\n${cmd.testFrameCount} of 30 frames received."
			unschedule(getCommunicationTestResults)
			break;
		case 2:
			testResults = "IN PROGRESS\n${cmd.testFrameCount} of 30 frames received."
			break;
	}
	if (state.testRefreshCount > 3)
	{
		testResults = "FAILED due to TIMEOUT."
		unschedule(getCommunicationTestResults)
	}
	sendEvent(name: "lastTest", value: testResults, displayed: false)

	def dateTime = new Date()
	logDebug(testResults)
	sendEvent(name: "lastMsgRcvd", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone) + ": POWER LEVEL NODE TEST REPORT V1", displayed: false)
	updateStatus()
}


/*
	updateStatus
    
	Sets the device state to online, but only if previously offline.
*/
def updateStatus()
{
	Map deviceState =
	[
		name: "status",
		isStateChange: (device.currentValue('status') != 'online' ? true : false),
		displayed: (device.currentValue('status') != 'online' ? true : false),
		value: 'online',
		descriptionText: "$device.displayName is online"
	]

	state.failedPings = 0
	logInfo("Device is online")
	sendEvent(deviceState)
}


/*
	testCommunication
    
	Executes a Z-Wave powerlevel test with 10 transmission frames. 

	Note: Useful for testing Z-Wave signal reliability
*/
def testCommunication()
{
	// Test 30 frames at nominal power
	sendHubCommand(new physicalgraph.device.HubAction(zwave.powerlevelV1.powerlevelTestNodeSet(powerLevel: 0, testFrameCount: 30, testNodeid: 1).format()))
	runIn(10, getCommunicationTestResults)
	state.testRefreshCount = 0
    
	sendEvent(name: "lastMsgRcvd", value: "", displayed: false)
	logTrace("Sending 30 test frames to device...")
	sendEvent(name: "lastTest", value: "Sending 30 test frames to device...", displayed: false)
}


/*
	getCommunicationTestResults
    
	Requests results from device of test initiated by testCommunication() 

	Note: Attempts to retreive results at 10 second intervals, up to 3 times before timing out.
*/
def getCommunicationTestResults()
{
	logTrace("Requesting test results from device ${state.testRefreshCount} of 3...")

	sendHubCommand(new physicalgraph.device.HubAction(zwave.powerlevelV1.powerlevelTestNodeGet().format()))
	state.testRefreshCount = state.testRefreshCount + 1
	sendEvent(name: "lastTest", value: "Asking device for results (${state.testRefreshCount}/3)...", displayed: false)
    
	if (state.testRefreshCount <= 3) runIn(10, getCommunicationTestResults)
	else
	{
		unschedule(getCommunicationTestResults)
		sendEvent(name: "lastTest", value: "FAILED - Device unreachable.", displayed: false)
	}
}


/*
	interrogateDevice
    
	Schedules request for power level, device version, and other details from device.

	Note: Iris SmartPlugs can be slow to respond to Z-Wave commands; it's not unusual for one not be handled by the device.
*/
def interrogateDevice()
{
	logTrace("Interrogating device...")
	sendEvent(name: "lastMsgRcvd", value: "", displayed: false)
	sendEvent(name: "powerLevel", value: "--", displayed: false)
	sendEvent(name: "deviceVersion", value: "--", displayed: false)
	sendEvent(name: "deviceMSR", value: "--", displayed: false)
	sendEvent(name: "assocGroup", value: "--", displayed: false)

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
		logInfo("Device is offline")
	}
	state.failedPings = state.failedPings + 1

	state.interrogationPhase = 1
	state.interrogationRetries = 0
	doDeviceInterrogation()
}


/*
	doDeviceInterrogation
    
	Manages requests power level, device version, and other details from device.

	Note: Since delayBetween is currently broken, we'll use runIn and space the commands out by a few seconds.
*/
def doDeviceInterrogation()
{
	state.interrogationRetries = state.interrogationRetries + 1
	if (state.interrogationRetries > 3)
	{
		sendEvent(name: "lastTest", value: "FAILED - Device unreachable.", displayed: false)
		unschedule(doDeviceInterrogation)
		return
	}

	switch (state.interrogationPhase)
	{
		case 1:		// Power Level
			sendCommand(zwave.powerlevelV1.powerlevelGet())
			sendEvent(name: "lastTest", value: "Phase ${state.interrogationPhase} of 6\nAsking for Power Level (${state.interrogationRetries}/3)...", displayed: false)
			break;
		case 2:		// Version
			sendCommand(zwave.versionV1.versionGet())
			sendEvent(name: "lastTest", value: "Phase ${state.interrogationPhase} of 6\nAsking for Version (${state.interrogationRetries}/3)...", displayed: false)
			break;
		case 3:		// Firmware
			sendCommand(zwave.firmwareUpdateMdV2.firmwareMdGet())
			sendEvent(name: "lastTest", value: "Phase ${state.interrogationPhase} of 6\nAsking for Firmware (${state.interrogationRetries}/3)...", displayed: false)
			break;
		case 4:		// Manufacturer-Specific
			sendCommand(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
			sendEvent(name: "lastTest", value: "Phase ${state.interrogationPhase} of 6\nAsking for Mfr Data (${state.interrogationRetries}/3)...", displayed: false)
			break;
		case 5:		// Association Group #1 Name
			sendCommand(zwave.associationGrpInfoV1.associationGroupNameGet(groupingIdentifier: 1))
			sendEvent(name: "lastTest", value: "Phase ${state.interrogationPhase} of 6\nAsking for Grp #1 Name (${state.interrogationRetries}/3)...", displayed: false)
			break;
		case 6:		// Association Group #1 Nodes
			sendCommand(zwave.associationV2.associationGet(groupingIdentifier: 1))
			sendEvent(name: "lastTest", value: "Phase ${state.interrogationPhase} of 6\nAsking for Grp #1 Nodes (${state.interrogationRetries}/3)...", displayed: false)
			break;
		case 7:		// Complete
			sendEvent(name: "lastTest", value: "Interrogation complete!", displayed: false)
			state.interrogationPhase = 0
			break;
	}
    
	if (state.interrogationPhase > 0 ) runIn(5, doDeviceInterrogation)
	logTrace("Device Interrogation: Phase ${state.interrogationPhase} of 6; Attempt ${state.interrogationRetries} of 3...")
}


/*
	refresh
    
	Refreshes the device by requesting manufacturer-specific information.

	Note: This is called from the refresh capbility
*/
def refresh()
{
	if (state.manualRefresh == 1)
	{
		sendEvent(name: "lastTest", value: "FAILED due to TIMEOUT.", displayed: false)
		state.manualRefresh = 0
		return
	}

	state.manualRefresh = 1
	logTrace("Checking to see if device is online...")
	sendEvent(name: "lastTest", value: "Attempting to contact device...", displayed: false)
	refreshDevice()
    
	runIn(15, refresh)
}


/*
	refreshDevice
    
	Refreshes the device by requesting manufacturer-specific information.

	Note: Three missed communication attempts will cause the device to be declared offline.
*/
def refreshDevice()
{
	def dateTime = new Date()
	sendEvent(name: "lastMsgRcvd", value: "", displayed: false)
	sendEvent(name: "lastRefresh", value: dateTime.format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: false)

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
		logInfo("Device is offline")
	}
	state.failedPings = state.failedPings + 1

	logDebug("Querying device for manufacturerSpecificV2 information...")
	sendCommand(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
}


/*
	poll
    
	Sets the device state to online, but only if previously offline.

	Note: This has *NEVER* been called by the hub in testing, but is included for use with 3rd party SmartApps
*/
def poll()
{
	logDebug("Poll called...")
	refresh()
}


/*
	zwaveEvent
    
	Helper function, nothing to see here.
*/
def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	createEvent(descriptionText: "Log: $device.displayName: $cmd", isStateChange: true)
}


/*
	sendCommand
    
	Helper function, nothing to see here.
*/
private sendCommand(physicalgraph.zwave.Command cmd)
{
	sendHubCommand(new physicalgraph.device.HubAction(cmd.format()))
}


/*
	logDebug
    
	Displays debug output to IDE logs based on user preference.
*/
private logDebug(msgOut)
{
	if (settings.enableDebug)
	{
		log.debug msgOut
	}
}


/*
	logTrace
    
	Displays trace output to IDE logs based on user preference.
*/
private logTrace(msgOut)
{
	if (settings.enableTrace)
	{
		log.trace msgOut
	}
}


/*
	logInfo
    
	Displays informational output to IDE logs.
*/
private logInfo(msgOut)
{
	log.info msgOut
}