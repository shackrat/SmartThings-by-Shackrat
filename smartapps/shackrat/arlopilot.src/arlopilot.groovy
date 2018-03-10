/**
 * ArloPilot Service Manager
 *
 * Copyright 2018 Steve White
 *
 * ArloPilot for SmartThings is a software package created and licensed by Retail Media Concepts LLC.
 * ArloPilot, along with associated elements, including but not limited to online and/or electronic documentation are
 * protected by international laws and treaties governing intellectual property rights.
 *
 * This software has been licensed to you. All rights are reserved. You may use and/or modify the software.
 * You may not sublicense or distribute this software or any modifications to third parties in any way.
 *
 * By downloading, installing, and/or executing this software you hereby agree to the terms and conditions set forh in the ArloPilot license agreement.
 * https://storage.googleapis.com/arlopilot/ArloPilot_License.html
 * 
 * Arlo is the trademark and intellectual property of Netgear Inc. Retail Media Concepts LLC has no affiliation or relationship with Netgear.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 */
import groovy.json.JsonSlurper
definition(
	name: "ArloPilot",
	namespace: "shackrat",
	author: "Steve White",
	description: "Enhanced Arlo System Integration.",
	category: "My Apps",
	iconUrl: "https://storage.googleapis.com/arlopilot/arlo-small.png",
	iconX2Url: "https://storage.googleapis.com/arlopilot/arlo-med.png",
	iconX3Url: "https://storage.googleapis.com/arlopilot/arlo-large.png"
)


preferences
{
	page (name: "mainPage", title: "ArloPilot")
	page (name: "arloCredentials", title: "Arlo Credentials")
	page (name: "manageArloDevices", title: "Manage Arlo Devices")
	page (name: "updateArloDevice", title: "Update Arlo Device")
	page (name: "manageArloCustomModes", title: "Custom Arlo Mode Management")
	page (name: "modeManagement", title: "Arlo Mode Management")
	page (name: "changeArloModeList", title: "Arlo Mode Change Device List")
	page (name: "changeArloMode", title: "Arlo Mode Change")
	page (name: "setActiveArloMode", title: "Set Arlo Mode")
	page (name: "configureSHMMapping", title: "SHM to Arlo Mapping")
	page (name: "generalSettings", title: "ArloPilot General Settings")
	page (name: "about", title: "About ArloPilot")
	page (name: "deviceAutomationManagement", title: "Arlo Device Automation Management")
	page (name: "configureSHMDeviceActions", title: "Configure Arlo Device Actions for SHM")
	page (name: "cameraPage", title: "Show Available Cameras")

	// 1.4 External Features
	page (name: "externalAccess", title: "Configure External Access")
	page (name: "virtualCameraTiles", title: "Configure Virtual Camera Tiles")
	page (name: "confirmTokenReset", title: "Reset oAuth Security Token")
	page (name: "resetSecurityToken", title: "Security Token Action")
	page (name: "cameraEndpointDetails", title: "External Camera Access Details")
}


/*
	mappings

	Maps enpoint URI's to ArloPilot functions.

	Added: v1.4
*/
mappings
{
	path("/latestimage/:deviceid") {action: [GET: "getLatestStill"]}
}


/*
	installed

	Doesn't do much other call initialize().
*/
def installed()
{
	logInfo "ArloPilot Installed"

	state.installed = true

 	initialize()
}


/*
	updated

	Re-initializes app after making changes.
*/
def updated()
{
	logInfo "ArloPilot Updated"
	unsubscribe()
	initialize()
}


/*
	initialize

	Creates SHM event subscription if configured.

	Updated: v1.4
*/
def initialize()
{
	logDebug "Initialize: There are ${childApps.size()} automations."

	state.modeAutomations = 0
	state.deviceAutomations = 0
	childApps.each {child ->
		logDebug "   ... automation: ${child.label}"
		if (child.name == "ArloPilot Mode Automation") state.modeAutomations = state.modeAutomations + 1
		if (child.name == "ArloPilot Device Automation") state.deviceAutomations = state.deviceAutomations + 1
	}

	childDevicess.each {child ->
		log.debug "   ... virtual device: ${child.label}"
	}

	// 1.4 Remove dead virtual devices
	getChildDevices()?.each
	{childDevice ->
		if (settings.virtualCameraTiles.find{"ArloPilot_${it}" == childDevice.deviceNetworkId})
		{
			logTrace "initialize: Found active device: ${childDevice.label}."
		}
		else
		{
			logTrace "initialize: Removing unused device: ${childDevice.label}."
			deleteChildDevice(childDevice.deviceNetworkId)
		}
	}

	// 1.4 Create virtual camera tile devices
	settings.virtualCameraTiles.each
	{deviceId->
		if (!getChildDevices()?.find{it.deviceNetworkId == "ArloPilot_${deviceId}"})
		{
			def arloCamera = getArloDevice(deviceId)
			if (arloCamera)
			{
				logDebug "initialize: Creating virtual camera device for ${arloCamera.deviceName}."
				addChildDevice("shackrat", "arloPilotCameraTile", "ArloPilot_${deviceId}", location.hubs[0].id, [
					"name": "${arloCamera.deviceName} - ArloPilot",
					"label": "${arloCamera.deviceName} - ArloPilot",
					"completedSetup": true, 
					"data": [
						"arloDeviceId": deviceId
				]])
			}
			else logError "initialize: Could not create virtual camera tile; unabel to find Arlo camera with ID: ${deviceId}."
		}
	}

	// Subscribe to SHM events
	if (isSHMConnected)
	{
		logTrace "initialize: Subscribing to SHM Events..."
		subscribe(location, "alarmSystemStatus", SHMStateHandler)
	}

	logInfo "ArloPilot Initialized."
}


/*
	mainPage

	UI Page: Main menu for the app.
*/
def mainPage()
{
	def authValid = false

	// Update app counts
	state.modeAutomations = 0
	state.deviceAutomations = 0
	childApps.each {child ->
		if (child.name == "ArloPilot Mode Automation") state.modeAutomations = state.modeAutomations + 1
		if (child.name == "ArloPilot Device Automation") state.deviceAutomations = state.deviceAutomations + 1
	}

	// Testing connection?
	if (settings.arloEmail != null && settings.arloPassword != null)
	{
		authValid = testArloLogin()
	}

	// Clean install?
	if (!state.installed && state.selectedDevices == null)
	{
		state.selectedDevices = []
	}

	dynamicPage(name: "mainPage", title: "ArloPilot Arlo Main Menu", uninstall: true, install: true)
	{
		section()
		{
			paragraph image: "https://storage.googleapis.com/arlopilot/arlo-small.png", "Enhanced Arlo Integration v${appVersion}"
			if (versionCheck) paragraph title: "Update Available!", "An update to ArloPilot v${latestVersion} is available.", required: true
		}
		section("Connect")
		{
			href "arloCredentials", title: "Connect to Arlo", description: authValid ? "Connected to Arlo!" : "Enter your Arlo credentials to connect...", state: authValid ? "complete" : null
		}
		if (authValid)
		{
			section("Arlo Devices & Custom Modes")
			{
				href "manageArloDevices", title: "Manage Arlo Devices & Modes", description: "Connect Arlo devices and manage user-defined Arlo modes.", state: state.selectedDevices.size() ? "complete" : null
				if (state.selectedDevices.size() > 0)
				{
					href "cameraPage", title: "Arlo Cameras", description: "Show discovered Arlo cameras."
					href "changeArloModeList", title: "Change Arlo Mode", description: "Manually set the Arlo mode on a device."
				}
			}

			if (state.selectedDevices.size() > 0)
			{
				section("Smart Home Monitor (" + (isSHMConnected ? "Connected" : "Not Connected") + ")")
				{
					if (settings.enableSHM == null || settings.enableSHM) href "configureSHMMapping", title: isSHMConnected ? "Connected to SHM!" : "Connect to SHM Alarm", description: "Change Arlo mode based on Smart Home Monitor alarm state.", state: isSHMConnected ? "complete" : null
					else paragraph "SHM integration is disabled."
				}
				section("Mode Automation Management (" + (state.modeAutomations > 0 ? state.modeAutomations : "Not") + " Configured)")
				{
					if (settings.enableModes == null || settings.enableModes) href "modeManagement", title: "Synchronize Modes", description: "Configure SmartThings modes to change Arlo modes.", state: state.modeAutomations ? "complete" : null
					else paragraph "SmartThings mode event integration is disabled."
				}
				section("Device Automation Management (" + (state.deviceAutomations > 0 ? state.deviceAutomations : "Not") + " Configured)")
				{
					if (settings.enableDevices == null || settings.enableDevices) href "deviceAutomationManagement", title: "Trigger Arlo Modes", description: "Configure SmartThings devices to trigger Arlo modes.", state: state.deviceAutomations ? "complete" : null
					else paragraph "SmartThings device event integration is disabled."
				}

			}
		}

		section()
		{
			href "generalSettings", title: "ArloPilot Settings", description: "Configure external access, camera tiles, debug and trace logging..."
			href "about", title: "Like ArloPilot?", description: "Support the project...  Consider making a small contribution today!"
		}
	}
}


/*
	generalSettings

	UI Page: Enable/Disable core features and debug logging.
*/
def generalSettings()
{
	dynamicPage(name: "generalSettings", title: "ArloPilot Settings", uninstall: false, install: false)
	{
		section()
		{
			if (getActiveArloCameras()?.size()) href "virtualCameraTiles", title: "Create Virtual Camera Tiles", description: "Create virtual devices to allow viewing of latest Arlo still images.", state: settings.virtualCameraTiles ? "complete" : null
			href "externalAccess", title: "Configure External Access", description: "Enable web access to specific ArloPilot features like still images.", state: isEndpointEnabled ? "complete" : null
		}

		section("Feature Control")
		{
			paragraph "Use these \"Master Switches\" as a way to turn off ArloPilot features such as event handling."
			input "enableSHM", "bool", title: "Enable Smart Home Monitor?", description: "Synchronize Arlo modes with Smart Home Monitor alarm states.", defaultValue: true, required: true, multiple: false
			input "enableModes", "bool", title: "Enable Mode Events?", description: "Enable mapping of SmartThings modes to Arlo modes.", defaultValue: true, required: true, multiple: false
			input "enableDevices", "bool", title: "Enable Device Events?", description: "Enable SmartThings devices to trigger Arlo modes.", defaultValue: true, required: true, multiple: false
		}

		section("Debug Logging")
		{
			paragraph "By default, only errors and warnings are reported in the IDE.  If you are experiencing an issue with this app, is recommended that debug and trace logging be enabled."
			input "enableTrace", "bool", title: "Enable trace logging?", description: "Indicates high-level activities during the execution of the app.", defaultValue: false, required: true, multiple: false
			input "enableDebug", "bool", title: "Enable debug logging?", description: "Shows detailed output from data structures and Arlo cloud responses.", defaultValue: false, required: true, multiple: false
		}
	}
}


/*
	arloCredentials

	UI Page: Requests Arlo credentials and tests the connection to the cloud.
*/
def arloCredentials()
{
	def authValid = false

	// Testing connection?
	if (settings.arloEmail && settings.arloPassword)
	{
		authValid = testArloLogin()
	}

	dynamicPage(name: "arloCredentials", title: "Connect to Arlo", uninstall: false, install: false)
 	{
		section("Arlo Credentials")
		{
			input name: "arloEmail", type: "email", title: "E-Mail", description: "E-mail address used for your Arlo account.", required: true, multiple: false, submitOnChange: true, state: authValid ? "complete" : null, capitalization: "none"
			if (arloEmail != null) input name: "arloPassword", type: "password", title: "Password", description: "Enter the password of your Arlo account.", required: true, multiple: false, submitOnChange: true,  state: authValid ? "complete" : null
		}

		if (settings.arloEmail != null && settings.arloPassword != null)
		{
			section(authValid ? "Connected!" : "Connecting...")
			{
				if (authValid) paragraph title:"Login Successful!", "ArloPilot was able to connect to the Arlo cloud!", state: "complete"
				else paragraph title:"Login Error", required: true, "ArloPilot was unable to login to the Arlo cloud.  Please check your credentials and try again."
			}
		}
	}
}


/*
	updateArloDevice

	UI Page: Performs the Addition/Removal of an Arlo base and displays result.
*/
def updateArloDevice(params)
{
	def actionText = "No Changes!"
	def arloBase = [:]
	if (params?.addDeviceId)
	{
		if (state.selectedDevices.contains(params.addDeviceId) == false) state.selectedDevices << params.addDeviceId
		actionText = "Base Station Added!"

		arloBase = getArloDevice(params?.addDeviceId)
	}
	else if (params?.remDeviceId)
	{
		state.selectedDevices.removeAll{ it == params.remDeviceId}
		actionText = "Base Station Removed!"
	}
	else return manageArloDevices()

	logDebug "Active Base Stations: ${state.selectedDevices}"
 
	dynamicPage(name: "updateArloDevice", title: "ArloPilot Device Management", install: false, uninstall: false)
	{
		section()
		{
			paragraph actionText
			if (params?.addDeviceId)
			{
				href "manageArloCustomModes", title: "Setup User-defined Modes", description: "Configure ArloPilot to use any user-defined Arlo modes for ${arloBase.deviceName}.", params: [deviceId: arloBase.deviceId]
			}
		}

		section()
		{
			href "manageArloDevices", title: "Manage Devices", description: "Return to Arlo device management..."
			href "mainPage", title: "Home", description: "Return to ArloPilot setup..."
		}
	}
}


/*
	manageArloDevices

	UI Page: Device management menu; connects Arlo devices and configures custom modes.
*/
def manageArloDevices()
{
	def arloModeCapableDevices = arloDevices("basestation") + arloDevices("arloq")

	dynamicPage(name: "manageArloDevices", title: "ArloPilot Device Management", install: false, uninstall: false)
	{
		if (arloModeCapableDevices?.size())
		{
			section("Discovered Arlo Base Stations")
			{
				paragraph "Select each Arlo base station to use with SmartThings."
			}

			arloModeCapableDevices.each
			{dev->
				section(dev.deviceName)
				{
					def activeDevice = state.selectedDevices.contains(dev.deviceId)
					href "updateArloDevice", title: activeDevice ? "Connected!" : "Not Connected", params: [addDeviceId: activeDevice ? 0 : dev.deviceId, remDeviceId: activeDevice ? dev.deviceId : 0, ts: now()], description: activeDevice ? "Tap to disconnect..." : "Tap to connect...", state: activeDevice ? "complete" : null, submitOnChange: true   
					if (activeDevice)
					{
						href "manageArloCustomModes", title: "Configure User-created Modes", description: "Configure user-created Arlo modes for ${dev.deviceName}.", params: [deviceId: dev.deviceId], state: getUserDefinedModesConfigured(dev.deviceId) ? "complete" : null
					}	
				}
			}
		}
	}
}


/*
	manageArloCustomModes

	UI Page: Configures user-defined modes for a specific Arlo base station or Arlo-Q camera.
*/
def manageArloCustomModes(params)
{
	if (params?.deviceId == null) return mainPage()

	def baseId = params.deviceId
	def arloBase = getArloDevice(baseId)

	dynamicPage(name: "manageArloCustomModes", title: "${arloBase.deviceName} Custom Mode Management", install: false, uninstall: false)
	{
		5.times
		{item->
			section("Configure Custom Arlo #${item+1}")
			{
				input name: "cMode_"+baseId+"_Id_"+item, type: "text", title: "Mode ID", description: "Arlo mode ID (i.e. 'mode3'.", multiple: false, required: false, submitOnChange: false, capitalization: "none"
				input name: "cMode_"+baseId+"_Name_"+item, type: "text", title: "Name", description: "Friendly name for this mode.", multiple: false, required: false, submitOnChange: false
			}
		}
	}
}


/*
	modeManagement

	UI Page: Create and manage child apps to sync SmartThings modes to an Arlo mode.
*/
def modeManagement()
{
	dynamicPage(name: "modeManagement", title: "Arlo Mode Management", uninstall: false, install: false)
	{
		section("Add Automation")
		{
			app(name: "arloMode", appName: "ArloPilot Mode Automation", namespace: "shackrat", title: "Add Arlo Mode Synchronization Automation", multiple: true)
		}
	}
}


/*
	deviceAutomationManagement

	UI Page: Create and manage child apps to trigger Arlo mode changes on SmartThings device events.
*/
def deviceAutomationManagement()
{
	dynamicPage(name: "deviceAutomationManagement", title: "Arlo Device Automation Management", uninstall: false, install: false)
	{
		section("Add Automation")
		{
			app(name: "arloDevice", appName: "ArloPilot Device Automation", namespace: "shackrat", title: "Add SmartThings Arlo Trigger Automation", multiple: true)
		}
	}
}


/*
	changeArloModeList

	UI Page: Lists connected Arlo base stations and Arlo-Q cameras eligible for a mode change.
*/
def changeArloModeList()
{
	def arloModeCapableDevices = arloDevices("basestation") + arloDevices("arloq")

	dynamicPage(name: "changeArloModeList", title: "Change Arlo Mode", install: false, uninstall: false)
	{
		if (arloModeCapableDevices?.size())
		{
			section()
			{
				paragraph "Using this page you can manually change the current Arlo mode on any base station or Arlo-Q camera."
			}
			section("Connected Devices")
			{
				arloModeCapableDevices.each
				{dev->
					def activeDevice = state.selectedDevices.contains(dev.deviceId)
					if (activeDevice)
					{
						href "changeArloMode", title: "${dev.deviceName}", description: "Manually change the Arlo mode for ${dev.deviceName}.", params: [deviceId: dev.deviceId]
					}	
				}
			}
		}
	}
}


/*
	changeArloMode

	UI Page: Displays a list of available modes for the base station or Arlo-Q passed in [params].
*/
def changeArloMode(params)
{
	if (params?.deviceId == null) return mainPage()

	def baseId = params.deviceId
	def arloBase = getArloDevice(baseId)
	def arloModes = getArloModes(baseId)

	dynamicPage(name: "changeArloMode", title: "Change Arlo Mode", install: false, uninstall: false)
	{
		section()
		{
			paragraph "Select an available Arlo mode for ${arloBase.deviceName}..."
		}

		section("Available Modes")
		{
			arloModes.each
			{mode->
				href "setActiveArloMode", title: "${mode.value}", description: "Set the Arlo mode for ${arloBase.deviceName} to ${mode.value}.", params: [deviceId: baseId, mode: mode.key]
			}
		}
	}
}


/*
	setActiveArloMode

	UI Page: Changes the Arlo mode on the base station or Arlo-Q as passed in [params] then displays the results of the action.
*/
def setActiveArloMode(params)
{
	if (params?.deviceId == null || params?.mode == null) return mainPage()

	def baseId = params.deviceId
	def modeId = params.mode

	def arloBase = getArloDevice(baseId)
	def modeName = getArloModes(baseId)?.find{it.key == modeId}.value

	// Set the mode
	logDebug "setActiveArloMode: Attempting to set mode on ${arloBase.deviceName} to ${modeName}."
	def result = setArloMode(baseId, modeId)

	dynamicPage(name: "setActiveArloMode", title: "Processing Arlo Mode Change", install: false, uninstall: false)
	{
		section()
		{
			if (result)
				paragraph "The Arlo mode for ${arloBase.deviceName} was successfully changed to ${modeName}."
			else
				paragraph "The Arlo mode for ${arloBase.deviceName} could not be changed!", state: "error", required: true
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}


/*
	configureSHMMapping

	UI Page: Configures Arlo mode to change when selected Smart Home Monitor alarm modes are activated.
*/
def configureSHMMapping()
{
	def arloModeCapableDevices = arloDevices("basestation") + arloDevices("arloq")

	dynamicPage(name: "configureSHMMapping", title: "ArloPilot Smart Home Monitor Alarm Configuration", nextPage: "mainPage", install: false, uninstall: false)
	{
		if (arloModeCapableDevices?.size())
		{
			section()
			{
				if (settings.enableSHM == null || settings.enableSHM)
					paragraph "Using this page you can configure Arlo modes to automatically change based on Smart Home Monitor alarm state."
				else
					paragraph "Smart Home Monitor features are currently disabled in ArloPilot settings!", required: true
			}
			section("SHM Armed - Stay")
			{
				arloModeCapableDevices.each
				{dev->
					def activeDevice = state.selectedDevices.contains(dev.deviceId)
					if (activeDevice)
					{
						def arloModes = getArloModes(dev.deviceId)
						input "SHMArmStay_"+dev.deviceId, "enum", title: "${dev.deviceName}", description: "Set the Arlo mode to...", options: arloModes, multiple: false, required: false, submitOnChange: true
					}	
				}
				if (shmStayDevices)
				{
					input name: "SHMArmStay_notifySendPush", title: "Send a push notification confirming these action?", type: "bool", required: false
				}
			}
			section()
			{
				href "configureSHMDeviceActions", title: "Camera Settings - Armed/Stay", description: "Change device properties like camera on/off and night vision control.", params: [shmMode: "stay"], state: isSHMStayConfigured ? "complete" : null
			}

			section("SHM Armed - Away")
			{
				arloModeCapableDevices.each
				{dev->
					def activeDevice = state.selectedDevices.contains(dev.deviceId)
					if (activeDevice)
					{
						def arloModes = getArloModes(dev.deviceId)
						input "SHMArmAway_"+dev.deviceId, "enum", title: "${dev.deviceName}", description: "Set the Arlo mode to...", options: arloModes, multiple: false, required: false, submitOnChange: true
					}	
				}
				if (shmAwayDevices)
				{
					input name: "SHMArmAway_notifySendPush", title: "Send a push notification confirming this action?", type: "bool", required: false
				}
			}
			section()
			{
				href "configureSHMDeviceActions", title: "Camera Settings - Armed/Away", description: "Change device properties like camera on/off and night vision control.", params: [shmMode: "away"], state: isSHMAwayConfigured ? "complete" : null
			}

			section("SHM Disarmed")
			{
				arloModeCapableDevices.each
				{dev->
					def activeDevice = state.selectedDevices.contains(dev.deviceId)
					if (activeDevice)
					{
						def arloModes = getArloModes(dev.deviceId)
						input "SHMArmOff_"+dev.deviceId, "enum", title: "${dev.deviceName}", description: "Set the Arlo mode to...", options: arloModes, multiple: false, required: false, submitOnChange: true
					}	
				}
				if (shmStayDevices)
				{
					input name: "SHMArmOff_notifySendPush", title: "Send a push notification confirming this action?", type: "bool", required: false
				}
			}
			section()
			{
				href "configureSHMDeviceActions", title: "Camera Settings - Disarmed", description: "Change device properties like camera on/off and night vision control.", params: [shmMode: "off"], state: isSHMOffConfigured ? "complete" : null
			}
		}
	}
}


/*
	configureSHMDevice

	UI Page: Configures a specific device for a specific SHM mode.
*/
def configureSHMDeviceActions(params)
{
	def arloCameraCapableDevices = arloDevices("camera") + arloDevices("arloq")
	if (params.shmMode) state.configureSHMDevice_shmMode = params.shmMode.capitalize()

	Map arloCameras = [:]
	arloCameraCapableDevices?.each {
		arloCameras << [(it.deviceId): it.deviceName]
	}

	Map switchOnCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_CamOff")
	Map switchOffCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_CamOn")
	Map nightVisionOnCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_NightVisionOff")
	Map nightVisionOffCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_NightVisionOn")

	Map powerSaveLowCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_PowerSaveOptimal", settings."SHMArm${state.configureSHMDevice_shmMode}_PowerSaveHigh")
	Map powerSaveOptimalCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_PowerSaveLow", settings."SHMArm${state.configureSHMDevice_shmMode}_PowerSaveHigh")
	Map powerSaveHighCameras = deviceXORFilter(arloCameras, settings."SHMArm${state.configureSHMDevice_shmMode}_PowerSaveLow", settings."SHMArm${state.configureSHMDevice_shmMode}_PowerSaveOptimal")

	dynamicPage(name: "configureSHMDeviceActions", title: "ArloPilot Smart Home Monitor Alarm Configuration - ${state.configureSHMDevice_shmMode} Device Actions", nextPage: "configureSHMMapping", install: false, uninstall: false)
	{
		section()
		{
			if (settings.enableSHM == null || settings.enableSHM)
				paragraph "Using this page you can configure specific device properties when Smart Home Monitor alarm state changes to ${state.configureSHMDevice_shmMode}."
			else
				paragraph "Smart Home Monitor features are currently disabled in ArloPilot settings!", required: true
		}

		section("Camera Control")
		{
			input "SHMArm${state.configureSHMDevice_shmMode}_CamOn", "enum", title: "Enable Recording", description: "Disable privacy mode on these cameras...", options: switchOnCameras, multiple: true, required: false, submitOnChange: true
			input "SHMArm${state.configureSHMDevice_shmMode}_CamOff", "enum", title: "Disable Recording", description: "Enable privacy mode on these cameras...", options: switchOffCameras, multiple: true, required: false, submitOnChange: true
		}
		section("Night Vision Control")
		{
			input "SHMArm${state.configureSHMDevice_shmMode}_NightVisionOn", "enum", title: "Enable Night Vision", description: "Enable night vision on these cameras...", options: nightVisionOnCameras, multiple: true, required: false, submitOnChange: true
			input "SHMArm${state.configureSHMDevice_shmMode}_NightVisionOff", "enum", title: "Disable Night Vision", description: "Disable night vision on these cameras...", options: nightVisionOffCameras, multiple: true, required: false, submitOnChange: true
		}
		section("Power Saver Control")
		{
			input "SHMArm${state.configureSHMDevice_shmMode}_PowerSaveLow", "enum", title: "Best Video", description: "Set these cameras to the highest video quality...", options: powerSaveLowCameras, multiple: true, required: false, submitOnChange: true
			input "SHMArm${state.configureSHMDevice_shmMode}_PowerSaveOptimal", "enum", title: "Optimal", description: "Set these cameras an optimal balance of video quality and battery life...", options: powerSaveOptimalCameras, multiple: true, required: false, submitOnChange: true
			input "SHMArm${state.configureSHMDevice_shmMode}_PowerSaveHigh", "enum", title: "Best Battery Life", description: "Set these cameras to optimize battery life...", options: powerSaveHighCameras, multiple: true, required: false, submitOnChange: true
		}
	}
}


/*
	cameraPage

	UI Page: Displays a list of the available cameras with last captured image.
*/
def cameraPage()
{
	def arloCameraDevices = arloDevices("camera")
	def arloQCameraDevices = arloDevices("arloq")
	def activeCameras = getActiveArloCameras()
	def arloBase

	dynamicPage(name: "cameraPage", title: "ArloPilot - Discovered Cameras", nextPage: "mainPage", install: false, uninstall: false)
	{
		section()
		{
			if (arloCameraDevices?.size() || arloQCameraDevices?.size())
				paragraph "Using this page you can view the available Arlo cameras.\n\nNote: Images may not be available for all cameras.\n\nTap any camera to view the latest image."
			else
				paragraph "No Arlo cameras could be found!", required: true
		}

		section("Arlo Pro / Pro 2 Cameras")
		{
			arloCameraDevices.each
			{
				if (activeCameras.find{cam -> cam.deviceId == it.deviceId})
				{
					arloBase = getArloDevice(it.parentId)

					if (isEndpointEnabled && settings.virtualCameraTiles.find{dev-> dev == it.deviceId})
					{
						href "cameraEndpointDetails", title: it.deviceName, description: "${arloBase?.deviceName}\nTap to view link to this camera.", image: it.presignedLastImageUrl, params: [deviceId: it.deviceId]
					}
					else
					{
						href title: it.deviceName, description: "${arloBase?.deviceName}", image: it.presignedLastImageUrl, url: it.presignedLastImageUrl
					}
				}
			}
		}
		section("Arlo-Q / Arlo-Q2 Cameras")
		{
			arloQCameraDevices.each
			{
				if (activeCameras.find{cam -> cam.deviceId == it.deviceId})
				{
					if (isEndpointEnabled && settings.virtualCameraTiles.find{dev-> dev == it.deviceId})
					{
						href "cameraEndpointDetails", title: it.deviceName, description: "Tap to view link to this camera.", image: it.presignedLastImageUrl, params: [deviceId: it.deviceId]
					}
					else
					{
						href title: it.deviceName, image: it.presignedLastImageUrl, url: it.presignedLastImageUrl
					}
				}
			}
		}
	}
}


/*
	externalAccess

	UI Page: Configures external access (oAuth).

	Added: v1.4
*/
def externalAccess()
{
	if (!state.installed) createArloPilotEndpoint()

	dynamicPage(name: "externalAccess", title: "ArloPilot External Access", uninstall: false, install: false)
	{
		section()
		{
			if (isEndpointEnabled)
			{
				paragraph "ArloPilot External Access is Enabled!"
				href "confirmTokenReset", title: "Reset Security Token", description: "Reset the ArloPilot oAuth security token..."
			}
			else paragraph "To enable external access to specific ArloPilot features, visit the ArloPilot app in the SmartApps section in the SmartThings IDE.  Locate and click \"Edit Properties\" then scroll down to the section titled \"OAuth\" and click \"Enable OAuth in Smart App\". Click \"Update\" then Done to finish.", title: "Enable OAuth for external access to ArloPilot", required: true, state: null
		}
		if (isEndpointEnabled || createArloPilotEndpoint())
		{
			section()
			{
				input "enableEndpoint", "bool", title: "Enable External Access?", description: "Enable SmartThings endpoint for external access to specific features.", defaultValue: true, required: true, multiple: false
			}
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}


/*
	confirmTokenReset

	UI Page: Warns and prompt the user to reset the oAuth token.

	Added: v1.4
*/
def confirmTokenReset()
{
	dynamicPage(name: "confirmTokenReset", title: "ArloPilot External Access - Confirm Token Reset", uninstall: false, install: false)
	{
		section()
		{
			paragraph "Resetting the security break all external links!  These will need to be re-created after the token has been reset.", title: "WARNING...  Action cannot be undone!", required: true
			href "resetSecurityToken", title: "", description: "Proceed to Reset Security Token", required: true
		}
		section("Navigation")
		{
			href "externalAccess", title: "External Access", description: "Return to External Access menu..."
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}


/*
	resetSecurityToken

	UI Page: Resets the oAuth token.

	Added: v1.4
*/
def resetSecurityToken()
{
	state.stEndpoint = null

	def endPoint = createArloPilotEndpoint()
	logDebug "New ArloPilot endpoint created: ${endPoint}"

	dynamicPage(name: "resetSecurityToken", title: "ArloPilot External Access - Security Token Reset!", uninstall: false, install: false)
	{
		section()
		{
			paragraph "The ArloPilot security token has been reset; all external links will need to be updated."
		}
		section("Navigation")
		{
			href "externalAccess", title: "External Access", description: "Return to External Access menu..."
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}





/*
	cameraEndpointDetails

	UI Page: Provides external endpoint URL's on devices enabled for external access.

	Added: v1.4
*/
def cameraEndpointDetails(params)
{
	if (params?.deviceId == null) return cameraPage()
	def arloDevice = getArloDevice(params.deviceId)
	if (!arloDevice) return cameraPage()

	def imageURL = "${state.stEndpoint}latestimage/${params.deviceId}"

	dynamicPage(name: "cameraEndpointDetails", title: "ArloPilot External Access - ${arloDevice.deviceName}", uninstall: false, install: false)
	{
		section()
		{
			if (isEndpointEnabled && settings.virtualCameraTiles.find{it == params.deviceId})
			{
				logInfo "URL for \"${arloDevice.deviceName}\" latest still: ${imageURL}"
				paragraph "This Arlo camera has been enabled for external web access to still images.  The link is displayed below, it is also displayed in the IDE logs."
				paragraph imageURL, title: arloDevice.deviceName
				href title: "Open in Browser", description: "View this link in an external browser.", url: imageURL, style: "external"
			}
			else paragraph "External access has not been enabled.  Please check ArloPilot settings to enable web access to this device.", title: "External Access Disabled!", required: true
		}
		section("Navigation")
		{
			href "cameraPage", title: "Arlo Cameras", description: "Show discovered Arlo cameras."
			href "externalAccess", title: "External Access", description: "Return to External Access menu..."
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}


/*
	virtualCameraTiles

	UI Page: Configures external access (oAuth).

	Added: v1.4
*/
def virtualCameraTiles()
{
	def arloCameras = getActiveArloCamerasKV()

	dynamicPage(name: "virtualCameraTiles", title: "ArloPilot Virtual Camera Tiles", uninstall: false, install: false)
	{
		section("Camera Tiles")
		{
			input "virtualCameraTiles", "enum", title: "Create Camera Tiles", options: arloCameras, description: "Create a virtual camera device with a carousel of latest stills.  This device can also be accessed through the web if external access is enabled.", multiple: true, required: false
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}


/*
	about

	UI Page: Displays the about page with credits & donate link.
*/
def about()
{
	dynamicPage(name: "about", title: "About ArloPilot", uninstall: false, install: false)
	{
		section()
		{
			paragraph image: "https://storage.googleapis.com/arlopilot/arlo-small.png", "Enhanced Arlo Integration v${appVersion}\nCopyright 2018, Steve White"
			if (versionCheck) paragraph title: "Update Available!", "An update to ArloPilot v${latestVersion} is available.", required: true
		}
		section()
		{
			paragraph "ArloPilot is provided free for personal and non-commercial use.  Countless hours went into the development and testing of this SmartApp.  If you like it and would like to see it succeed, or see more apps like this in the future, please consider making a small donation to the cause."
			href "donate", style:"embedded", title: "Consider making a \$5 or \$10 donation today.", image: "https://storage.googleapis.com/arlopilot/donate-icon.png", url: "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=RV866THNUMJQC"
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to ArloPilot main menu..."
		}
	}
}


/*
	SHMStateHandler

	Handler for SMart Home Monitor alarm state changes.

	Note: Valid event values are: away, stay, off
*/
def SHMStateHandler(evt)
{
	Map arloDevices = [:]
	Map deviceActions = [:]
	def notifyPush = false

	if (settings.enableSHM != null && !settings.enableSHM)
	{
		logWarn "Smart Home Monitor features are currently disabled!"
		return false
	}

	logTrace "SHMStateHandler: Alarm Event ${evt.value}"

	switch (evt.value)
	{
		case "away":
			arloDevices = shmAwayDevices
			deviceActions = shmAwayDeviceActions
			notifyPush = settings.SHMArmAway_notifySendPush
			break
		case "stay":
			arloDevices = shmStayDevices
			deviceActions = shmStayDeviceActions
			notifyPush = settings.SHMArmStay_notifySendPush
			break
		case "off":
			arloDevices = shmOffDevices
			deviceActions = shmOffDeviceActions
			notifyPush = settings.SHMArmOff_notifySendPush
			break
		default:
			logWarn "Ignoring unexpected SHM alarm mode."
			break
	}

	logDebug "SHMStateHandler: Devices for ${evt.value} = ${arloDevices}"

	def result = true
	arloDevices.each
	{deviceId, modeId ->
		if (!setArloMode(deviceId, modeId)) result = false
		pause(500)
	}

	if (notifyPush)
	{
		sendPush("Arlo SHM changed to \"${evt.value}\"; ${result ? "all Arlo mode(s) changed." : "one or more Arlo modes failed to change!"} .")
	}


	// Process device actions
	if (deviceActions.size())
	{
		logTrace "SHMStateHandler: Processing device actions for SHM event - ${evt.value}..."
		deviceActions.each
		{propCmd, deviceIdList ->
			deviceIdList?.each
			{
				"${propCmd}"(it)
				pause(500)
			}
		}
	}

}


/*
	testArloLogin

	Tests the supplied Arlo credentials by logging into the Arlo cloud.
*/
private testArloLogin()
{
	def login = arloLogin(false)
	if (login == [:])
	{
		logWarn "testArloLogin() returned false"
		return false
	}
	return true
}


/*
	arloLogin

	Performs HTTP POST to to login to the Arlo cloud.  Caches and returns the session cookies for future calls.
*/
private Map arloLogin(useCache = true)
{
	// Cached login valid?
	if (useCache && state.arloSessionTS > 0 && state.arloSessionTS > now() - 300000) 
	{
		logTrace "arloLogin: Using cached session: ${state.arloSession}."
		return  state.arloSession
	}

	// Response Map
	Map mapAuth =
	[
		Authorization:	"",
		Cookie:			[]
	]

	// Attempt to create an Arlo session
	try
	{
		httpPostJson(
			[
				uri:  		"https://arlo.netgear.com",
				path: 		"/hmsweb/login",
				tlsVersion: "TLSv1.2",
				body:
				[
					email:		settings.arloEmail,
					password:	settings.arloPassword
				]
			]
		)
		{jsonResponse ->
			if (jsonResponse.data.success == true)
			{
				mapAuth.Authorization = jsonResponse.data.data.token
				jsonResponse.headers.each
				{
					if (it.name == "Set-Cookie")
					{
						mapAuth.Cookie << it.value.split(";").getAt(0)
					}
				}
				logDebug "Retrieved authentication token, \"${mapAuth.Authorization}\""
			}
			else
			{
				logError "Failed to login to the Arlo cloud.  The response received was: ${objResponse.data}."
				return [:]
 			}
		}
	}
	catch (errorException)
	{
		logError "Failed to login to the Arlo cloud - ${errorException}.  The response received was: ${objResponse}."
		return [:]
	}

	logTrace "Login returned [${mapAuth}]."
 
	// Cache logins for 5 minutes   
	state.arloSession = mapAuth
	state.arloSessionTS = now()
	state.arloDeviceCacheTS = 0
	return mapAuth
}


/*
	arloDevices

	Returns a list of arlo devices for an optional device type either from the cloud (or cache).
*/
List arloDevices(filterDeviceType = "", parentDeviceId = "")
{
	def arloCloudDevices = []

	// Cached device list valid?
	if (state.arloDevices?.size() == 0 || state.arloSessionTS == 0 || (state.arloDeviceCacheTS + 300000) < now()) 
	{
		logTrace "arloDevices: Requesting updated device list from the Arlo cloud..."

		try
		{
			httpGet(
				[
					uri:		"https://arlo.netgear.com",
					path:		"/hmsweb/users/devices",
					tlsVersion: "TLSv1.2",
					headers: 	arloLogin()
				]
			){jsonResponse ->
 				if (jsonResponse.data.success == true)
				{
					// Cache devices for 5 minutes   
					state.arloDevices = jsonResponse.data.data
					state.arloSessionTS = now()
					state.arloDeviceCacheTS = now()
				}
				else
				{
					// Cache devices for 5 minutes   
					state.arloDevices = []
					state.arloSessionTS = 0

					logError "Failed to retrieve device list from the Arlo cloud.  The response received was: ${jsonResponse.data}."
					return false
				}
			}
		}
		catch (errorException)
		{
			logError "Caught exception [${errorException}] while attempting to retreive the device list."
		}
	}
	else logTrace "arloDevices: Using cached device list..."



	if (filterDeviceType == "")
	{
		if (parentDeviceId == "") arloCloudDevices = state.arloDevices.findAll {it.displayOrder > 0}
		else arloCloudDevices = state.arloDevices.findAll {it?.parentId == parentDeviceId && it.displayOrder > 0}
	}
	else
	{
		if (parentDeviceId == "") arloCloudDevices = state.arloDevices.findAll {it.deviceType == filterDeviceType && it.displayOrder > 0}
		else arloCloudDevices = state.arloDevices.findAll {it.deviceType == filterDeviceType && it.parentId == parentDeviceId && it.displayOrder > 0}
	}

	logTrace "Devices returned [${arloCloudDevices}]."
	return arloCloudDevices
}


/*
	setArloMode

	Sets the current Arlo mode to [modeId] on the base station or Arlo-Q [deviceId]
*/
def setArloMode(deviceId, modeId)
{
	// Get base station details
	def arloBase = getArloDevice(deviceId)

	logDebug "setArloMode: Attempting to set Arlo mode on ${arloBase.deviceId} to ${modeId}..."

	// Attempt to create an Arlo session
	try
	{
		httpPostJson(
			[
				uri:  		"https://arlo.netgear.com",
				path:		"/hmsweb/users/devices/notify/${arloBase.deviceId}",
				tlsVersion: "TLSv1.2",
				headers: [
					xcloudId: arloBase.xCloudId
				] + arloLogin(),
				body:
				[
					action:				"set",
					from:				"ArloPilot",
					properties: [
						active:				(modeId == "schedule" ? true : modeId)
					],
					active:				(modeId == "schedule" ? "active" : modeId),
					publishResponse:	(modeId == "schedule" ? true : false),
					resource:			(modeId == "schedule" ? "schedule" : "modes"),
					responseUrl:		"",
					to:					arloBase.deviceId,
					transId:			""
				]
			]
		)
		{jsonResponse ->
			if (jsonResponse.data.success == true)
			{
				logTrace "Arlo Mode for ${arloBase.deviceName} changed to ${modeId}."
			}
			else
			{
				logError "Failed to change the mode for ${arloBase.deviceName} to ${modeId}.  The response received was: ${objResponse.data}."
				return false
 			}
		}
	}
	catch (errorException)
	{
		logError "Caught exception [${errorException}] while attempting to set mode."
	}

	return true
}


/*
	setArloDeviceProperty

	Sets the a [deviceProperty] on Arlo [deviceId] to [propertyValue] on an Arlo device.
*/
def setArloDeviceProperty(deviceId, deviceProperty, propertyValue)
{
	// Get device details
	def arloDevice = getArloDevice(deviceId)
	def arloBase = getArloDevice(arloDevice?.parentId)
	if (!arloDevice || !arloBase)
	{
		logError "setArloDeviceProperty: Unable to locate the requested Arlo device (deviceId) or parent (${arloDevice?.parentId})."
		return false
	}

	logDebug "setArloDeviceProperty: Attempting to set \"${deviceProperty}\" on ${arloDevice.deviceName} (${arloBase.deviceName}) to ${propertyValue}..."

	// Attempt to create an Arlo session
	try
	{
		httpPostJson(
			[
				uri:  		"https://arlo.netgear.com",
				path:		"/hmsweb/users/devices/notify/${arloBase.deviceId}",
				tlsVersion: "TLSv1.2",
				headers: [
					xcloudId: arloBase.xCloudId
				] + arloLogin(),
				body: 
				[
					action:				"set",
					from:				"ArloPilot",
					properties: [
						(deviceProperty):		propertyValue
					],
					publishResponse:	true,
					resource:			"cameras/" + arloDevice.deviceId,
					transId:			""
				]

			]
		)
		{jsonResponse ->
			if (jsonResponse.data.success == true)
			{
				logTrace "Arlo property \"${deviceProperty}\" for ${arloDevice.deviceName} changed to ${propertyValue}."
			}
			else
			{
				logError "Failed to set property \"${deviceProperty}\" for ${arloDevice.deviceName} to ${propertyValue}.  The response received was: ${objResponse.data}."
				return false
 			}
		}
	}
	catch (errorException)
	{
		logError "Caught exception [${errorException}] while attempting to set device property."
	}

	return true
}


/*
	cameraOn

	Turns on the [deviceId] camera by setting the privacyActive property to false.
*/
def cameraOn(deviceId)
{
	return setArloDeviceProperty(deviceId, "privacyActive", false)
}


/*
	cameraOff

	Turns off the [deviceId] camera by setting the privacyActive property to true.
*/
def cameraOff(deviceId)
{
	return setArloDeviceProperty(deviceId, "privacyActive", true)
}


/*
	nightVisionOn

	Turns on [deviceId] cameras night vision by setting the nightVisionMode property to 1.
*/
def nightVisionOn(deviceId)
{
	return setArloDeviceProperty(deviceId, "nightVisionMode", 1)
}


/*
	nightVisionOff

	Turns off [deviceId] cameras night vision by setting the nightVisionMode property to 0.
*/
def nightVisionOff(deviceId)
{
	return setArloDeviceProperty(deviceId, "nightVisionMode", 2)
}


/*
	powerSaveLow

	Sets [deviceId] cameras power saver to the lowest setting.  (Best Video)

*/
def powerSaveLow(deviceId)
{
	return setArloDeviceProperty(deviceId, "powerSaveMode", 3)
}


/*
	powerSaveOptimal

	Sets [deviceId] cameras power saver to the optimized setting.  (Balanced battery life vs video quality)

*/
def powerSaveOptimal(deviceId)
{
	return setArloDeviceProperty(deviceId, "powerSaveMode", 2)
}


/*
	powerSaveHigh

	Sets [deviceId] cameras power saver to the highest setting.  (Best Battery Life)

*/
def powerSaveHigh(deviceId)
{
	return setArloDeviceProperty(deviceId, "powerSaveMode", 1)
}


/*
	getArloModes

	Returns a map containing default Arlo modes combined with user-defined modes.
*/
Map getArloModes(baseStationId)
{
	// Get custom modes
	def arloModes = [mode1: "Armed", mode0: "Disarmed", schedule: "Schedule"]

	5.times
	{
		if (settings."cMode_${baseStationId}_Id_${it}" && settings."cMode_${baseStationId}_Name_${it}")
		{
			arloModes << [(settings."cMode_${baseStationId}_Id_${it}"): settings."cMode_${baseStationId}_Name_${it}"]
		}
	}
	return arloModes
}


/*
	getUserDefinedModesConfigured

	Returns true if any user-defined modes are configured; false if not.
*/
private getUserDefinedModesConfigured(baseStationId)
{
	def result = false
	5.times
	{
		if (settings."cMode_${baseStationId}_Id_${it}" && settings."cMode_${baseStationId}_Name_${it}")
		{
			result = true
		}
	}
	return result
}


/*
	getSelectedArloBases

	Returns a map [deviceId: deviceName] of connected Arlo base stations and/or Arlo-Q cameras.
*/
Map getSelectedArloBases()
{
	def arloModeCapableDevices = arloDevices()
	def arloDevicesKV = [:]

	state.selectedDevices.each
	{deviceId->
		arloDevicesKV << ["${deviceId}": arloModeCapableDevices.find{ it.deviceId == deviceId}?.deviceName ]
	}
	logDebug "Returning ${arloDevicesKV} to child."

	return arloDevicesKV
}


/*
	getActiveArloCameras

	Returns a list of active Arlo and/or Arlo-Q cameras.

	Added: v1.4
*/
public List getActiveArloCameras()
{
	List arloCameraCapableDevices = []

	state.selectedDevices.each
	{deviceId->
		def arloBase = getArloDevice(deviceId)
		arloCameraCapableDevices = arloCameraCapableDevices +  arloDevices("", arloBase.deviceId)
	}
	arloCameraCapableDevices?.removeAll{it.deviceType == "basestation" || it.deviceType == "siren"}

	return arloCameraCapableDevices
}


/*
	getActiveArloCamerasKV

	Returns a key/value Map [deviceId: deviceName] of Arlo and/or Arlo-Q cameras for use in preferences.

	Added: v1.4
*/
public Map getActiveArloCamerasKV()
{
	List arloCameraCapableDevices = getActiveArloCameras()

	Map arloCamerasKV = [:]

	arloCameraCapableDevices?.each
	{device->
		arloCamerasKV << ["${device.deviceId}": device.deviceName]
	}
	logDebug "Returning ${arloCamerasKV} to child."

	return arloCamerasKV
}


/*
	getArloDevice

	Returns an object containing the full Arlo device details as returned from the cloud (or cache).
*/
private getArloDevice(deviceId)
{
	def arloModeCapableDevices = arloDevices()
	return arloModeCapableDevices.find{ it.deviceId == deviceId}
}


/*
	getIsSHMConnected

	Returns true if Smart Home Monitor alarm modes are linked to Arlo; false if not.
*/
private getIsSHMConnected()
{
	return checkConfigSet("SHMArm", "notifySendPush")
}


/*
	getIsSHMAwayConfigured

	Returns true if Smart Home Monitor alarm state (Away) device actions are configured.
*/
private getIsSHMAwayConfigured()
{
	return checkConfigSet("SHMArmAway", "notifySendPush")
}


/*
	getIsSHMStayConfigured

	Returns true if Smart Home Monitor alarm state (Stay) device actions are configured.
*/
private getIsSHMStayConfigured()
{
	return checkConfigSet("SHMArmStay", "notifySendPush")
}


/*
	getIsSHMOffConfigured

	Returns true if Smart Home Monitor alarm state (Off) device actions are configured.
*/
private getIsSHMOffConfigured()
{
	return checkConfigSet("SHMArmOff", "notifySendPush")
}


/*
	checkConfigSet

	Returns true if specific app configuration values are set based on keys beginning with [cfgPrefix] and optionally NOT ending with [cfgExcludeSuffix].
*/
private checkConfigSet(cfgPrefix, cfgExcludeSuffix=false)
{
	def cfgSet = false

	for (String key : settings.keySet())
	{
		if (key.startsWith(cfgPrefix) && (cfgExcludeSuffix == false || !key.endsWith(cfgExcludeSuffix)))
		{
			cfgSet = true
		}
	}

	return cfgSet
}


/*
	getIsSTModeChangeEnabled

	Returns true if mode change automations are enabled; false if not.
*/
public getIsSTModeChangeEnabled()
{
	return (settings.enableModes != null && settings.enableModes) ? true : false
}


/*
	getIsDeviceEventsEnabled

	Returns true if device event automations are enabled; false if not
*/
public getIsDeviceEventsEnabled()
{
	return (settings.enableDevices != null && settings.enableDevices) ? true : false
}


/*
	getShmOffDevices

	Returns a map of Arlo base stations with mode changes that are triggered when SHM is set to disarmed.
*/
Map getShmOffDevices()
{
	Map alarmDevices = [:]

	state.selectedDevices.each
	{deviceId->
		if ( settings."SHMArmOff_${deviceId}" != null ) alarmDevices[deviceId] = settings."SHMArmOff_${deviceId}"
	}
	return alarmDevices
}


/*
	getShmStayDevices

	Returns a map of Arlo base stations with mode changes that are triggered when SHM is set to Arm (stay).
*/
Map getShmStayDevices()
{
	Map alarmDevices = [:]

	state.selectedDevices.each
	{deviceId->
		if ( settings."SHMArmStay_${deviceId}" != null ) alarmDevices[deviceId] = settings."SHMArmStay_${deviceId}"
	}
	return alarmDevices
}


/*
	getShmAwayDevices

	Returns a map of Arlo base stations with mode changes that are triggered when SHM is set to Arm (away).
*/
Map getShmAwayDevices()
{
	Map alarmDevices = [:]

	state.selectedDevices.each
	{deviceId->
		if ( settings."SHMArmAway_${deviceId}" != null ) alarmDevices[deviceId] = settings."SHMArmAway_${deviceId}"
	}
	return alarmDevices
}


/*
	getShmOffDeviceActions

	Returns a map of device actions that should be triggered when SHM is set to disarmed.
*/
Map getShmOffDeviceActions()
{
	return getShmDeviceActions("Off")
}


/*
	getShmStayDeviceActions

	Returns a map of device actions that should be triggered when SHM is set to Arm (stay).
*/
Map getShmStayDeviceActions()
{
	return getShmDeviceActions("Stay")
}


/*
	getShmAwayDeviceActions

	Returns a map of device actions that should be triggered when SHM is set to Arm (away).
*/
Map getShmAwayDeviceActions()
{
	return getShmDeviceActions("Away")
}


/*
	getShmDeviceActions

	Returns a map of device actions to take when SHM is set to disarmed.
*/
private getShmDeviceActions(shmMode)
{
	Map deviceActions = [:]

	if (settings."SHMArm${shmMode}_CamOn") deviceActions << [("cameraOn"): settings."SHMArm${shmMode}_CamOn"]
	if (settings."SHMArm${shmMode}_CamOff") deviceActions << [("cameraOff"): settings."SHMArm${shmMode}_CamOff"]
	if (settings."SHMArm${shmMode}_NightVisionOn") deviceActions << [("nightVisionOn"): settings."SHMArm${shmMode}_NightVisionOn"]
	if (settings."SHMArm${shmMode}_NightVisionOff") deviceActions << [("nightVisionOff"): settings."SHMArm${shmMode}_NightVisionOff"]
	if (settings."SHMArm${shmMode}_PowerSaveLow") deviceActions << [("powerSaveLow"): settings."SHMArm${shmMode}_PowerSaveLow"]
	if (settings."SHMArm${shmMode}_PowerSaveOptimal") deviceActions << [("powerSaveOptimal"): settings."SHMArm${shmMode}_PowerSaveOptimal"]
	if (settings."SHMArm${shmMode}_PowerSaveHigh") deviceActions << [("powerSaveHigh"): settings."SHMArm${shmMode}_PowerSaveHigh"]

	return deviceActions
}


/*
	getIsEndpointEnabled

	Returns true if the oAuth endpoint is active, false if not.

	Added: v1.4
*/
def getIsEndpointEnabled()
{
	return (settings?.enableEndpoint != null && settings?.enableEndpoint) && state?.stEndpoint
}


/*
	createArloPilotEndpoint

	Creates an access token and enables the endpoint features (virtual camera tiles, etc.)

	Added: v1.4
*/
private createArloPilotEndpoint()
{
	if (!state.stEndpoint)
	{
		try
		{
			def accessToken = createAccessToken()
			if (accessToken)
			{
				state.stEndpoint = apiServerUrl("/api/token/${accessToken}/smartapps/installations/${app.id}/")
				logTrace "createArloPilotEndpoint: Access Token [${errorException}] successfulyl created."
			}
		}
		catch(errorException)
		{
			state.stEndpoint = null
			logError "createArloPilotEndpoint: Failed to create endpoint; ${errorException}."
		}
	}
	return state.stEndpoint
}


/*
	getLatestStill

	Outputs the latest saved Arlo still image as a binary stream for the camera specified by [params.deviceid].

	Added: v1.4
*/
def getLatestStill()
{
	def deviceId = params.deviceid
	ByteArrayInputStream imageBytes

	if (deviceId)
	{
		imageBytes = getChildDevice("ArloPilot_${deviceId}")?.getLastImageBytes()
	}

	if (!imageBytes)
	{
		return false
	} 
	render contentType: "image/jpeg", data: imageBytes
}


/*
	getLastImageURL

	Returns the a pre-signed URL to the Arlo cloud for the latest captured still image.

	Note: Called by a the child device.

	Added: v1.4
*/
public def getLastImageURL(deviceId)
{
	if (deviceId)
	{
		// Invalidate the cache
		state.arloDeviceCacheTS = 0

		def arloDevice = getArloDevice(deviceId)
		if (arloDevice)
		{
			logDebug "getLastImageURL: Returning pre-signed image URL ${arloDevice.presignedLastImageUrl}"
			return arloDevice.presignedLastImageUrl
		}
	}

	logError "getLastImageURL: Unable to fetch the latest image URL."
	return false
}


/*
	deviceXORFilter

	Helper function to filter the list of camera devices.
*/
Map deviceXORFilter(deviceList, filterList, filterList2 = null)
{
	Map copyMap = [:]
	copyMap.putAll(deviceList)
	if (filterList) copyMap.keySet().removeAll(filterList)
	if (filterList2 != null) copyMap.keySet().removeAll(filterList2)
	return copyMap
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


/*
	logWarn

	Displays warning output to IDE logs.
*/
private logWarn(msgOut)
{
	log.warn msgOut
}


/*
	logError

	Displays error output to IDE logs.
*/
private logError(msgOut)
{
	log.error msgOut
}

private getAppVersion(){1.5}

private getVersionCheck()
{
	return latestVersion > appVersion ? true : false
}

private getLatestVersion()
{
	if (state.lastVersionCheck && state.lastVersionCheck + 172800000 > now())
	{
		logDebug "Version check returned from cache ${state.latestVersion}."
		return state.latestVersion.toDouble()
	}

	httpGet(
		[
			uri:	"https://storage.googleapis.com",
			path:	"/arlopilot/arlopilot_latest_version.json"
		]
	){jsonResponse ->
		if (jsonResponse.responseData?.version)
		{
			logDebug "Version check returned ${jsonResponse.responseData.version}."
			state.latestVersion = jsonResponse.responseData.version
			state.lastVersionCheck = now()
		}
	}
	return state.latestVersion.toDouble()
}