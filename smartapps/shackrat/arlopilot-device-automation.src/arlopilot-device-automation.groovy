/**
 * ArloPilot Device Automation
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
 * Arlo is the trademark and intellectual property of Netgear Inc.
 * Retail Media Concepts LLC and ArloPilot are not affiliated nor have any relationship with Netgear.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 */
definition(
	name: "ArloPilot Device Automation",
	namespace: "shackrat",
	author: "Steve White",
	description: "Automate Arlo modes based on device events.",
	category: "My Apps",
	iconUrl: "https://storage.googleapis.com/arlopilot/arlo-small.png",
	iconX2Url: "https://storage.googleapis.com/arlopilot/arlo-med.png",
	iconX3Url: "https://storage.googleapis.com/arlopilot/arlo-large.png"
)

preferences
{
	page (name: "mainPage", title: "Main Menu")
	page (name: "runTimePage", title: "Automation Time Restrictions")
	page (name: "schedulePage", title: "Automation Execution Restrictions")
	page (name: "generalSettings", title: "Automation General Settings")
	page (name: "namePage", title: "Name Page")
	page (name: "configureDeviceActions", title: "Configure Device Actions")
}


/*
	installed

	Doesn't do much other set an installed flag.
*/
def installed()
{
	logInfo "ArloPilot Device Automation ${app.label} Installed"
	state.isInstalled = true
}


/*
	updated

	Re-initializes app after making changes.
*/
def updated()
{
	logInfo "ArloPilot Device Automation ${app.label} Updated"
	unsubscribe()
	initialize()
}


/*
	initialize

	Creates event subscription.
*/
def initialize()
{
	// if the user did not override the label, set the label to the default
	if (!overrideLabel)
	{
		app.updateLabel(getAppLabel())
	}


	// Switch ON automation?
	if (settings.stSwitchOn)
	{
		logTrace "Subscribing to switch on events for ${settings.stSwitchOn}"
		subscribe(settings.stSwitchOn, "switch.on", switchEvent)
	}

	// Switch OFF automation?
	else if (settings.stSwitchOff)
	{
		logTrace "Subscribing to switch off events for ${settings.stSwitchOff}"
		subscribe(settings.stSwitchOff, "switch.off", switchEvent)
	}

	// Presence arrival automation?
	else if (settings.stPresenceArrive)
	{
		logTrace "Subscribing to presence arrival events for ${settings.stPresenceArrive}"
		subscribe(settings.stPresenceArrive, "presence.present", presenceArriveEvent)
	}

	// Presence departure automation?
	else if (settings.stPresenceDepart)
	{
		logTrace "Subscribing to presence departure events for ${settings.stPresenceDepart}"
		subscribe(settings.stPresenceDepart, "presence.not present", presenceDepartEvent)
	}

	// Push button automation?
	else if (settings.pushButton)
	{
		logTrace "Subscribing to button events for ${settings.pushButton}"
		subscribe(settings.pushButton, "button.pushed", buttonEvent)
	}

	logInfo "ArloPilot Device Automation ${app.label} Initialized"
}


/*
	mainPage

	UI Page: Selects ST mode, Arlo base, and Arlo mode to create an automation.
*/
def mainPage()
{
	def deviceOptions = parent.getSelectedArloBases()

	dynamicPage(name: "mainPage", title: "Create a new ArloPilot Device Automation", uninstall: true, install: false, nextPage: "namePage")
	{
		section()
		{
			paragraph "ArloPilot device automation allows the execution of Arlo modes when a SmartThings device event occurs."
			if (!automationEnabled && (app.label != null && app.label != app.name))
				paragraph title: "Automation Disabled!", required: true, "This automation has been disabled.  It can be enabled using the switch below."
		}

		section("SmartThings Device Triggers")
		{
			paragraph "Configure any one of the following triggers..."
			if (!stPresenceDepart && !stSwitchOn && !pushButton && !stSwitchOff)
				input "stPresenceArrive", "capability.presenceSensor", title: "Someone Arrives", description: "When any of these presence sensors arrives...", multiple: true, required: false, submitOnChange: true
			if (!stPresenceArrive && !stSwitchOn && !pushButton && !stSwitchOff)
				input "stPresenceDepart", "capability.presenceSensor", title: "Everyone Leaves", description: "When all of these presence sensors depart...", multiple: true, required: false, submitOnChange: true
			if (!stPresenceArrive && !stPresenceDepart && !stSwitchOn && !stSwitchOff)
				input "pushButton", "capability.button", title: "Button Pressed", description: "When this button is pressed...", multiple: false, required: false, submitOnChange: true
			if (!stPresenceArrive && !stPresenceDepart && !pushButton && !stSwitchOff)
				input "stSwitchOn", "capability.switch", title: "Switch Turns On", description: "When this switch turns on...", multiple: false, required: false, submitOnChange: true
			if (!stPresenceArrive && !stPresenceDepart && !pushButton && !stSwitchOn)
				input "stSwitchOff", "capability.switch", title: "Switch Turns Off", description: "When this switch turns off...", multiple: false, required: false, submitOnChange: true
		}

		if (stPresenceArrive || stPresenceDepart || stSwitchOn || pushButton || stSwitchOff)
		{
			section("Arlo Devices")
			{
				deviceOptions.each
				{dev->
					def arloModes = parent.getArloModes(dev.key)
					input "Arlo_"+dev.key, "enum", title: "${dev.value}", description: "Set the Arlo mode to...", options: arloModes, multiple: false, required: false, submitOnChange: true
				}
			}

			section("Device Properties")
			{
				href "configureDeviceActions", title: "Change these camera settings...", description: "Change device properties like camera on/off and night vision control.", state: checkConfigSet("devAction") ? "complete" : null
			}

			if (isArloSelected || checkConfigSet("devAction"))
			{
				section("Options")
				{
					href "schedulePage", title: "Automation Time Restrictions", description: "Restrict this mode synchronization to specific days/times...", state: (startingAt || runDays) ? "complete" : null
					href "generalSettings", title: "ArloPilot Automation Settings", description: "Configure optional debug and trace logging...", state: (enableTrace || enableDebug) ? "complete" : null
					input "automationEnabled", "bool", title: "Enable Automation?", description: "Enable this automation to run?", required: true, defaultValue: true, submitOnChange: true
				}
			}
		}
	}
}


/*
	schedulePage

	UI Page: Configure app to run only on specific days of the week. Time-bar execution with link to page optional start & end times.
*/
def schedulePage()
{
	dynamicPage(name: "schedulePage", title: "Rule Schedule", uninstall: false, install: false, nextPage: "mainPage")
	{
		section("Only allow this mode synchronization to run... ")
		{
			href "runTimePage", title: "During specific times:", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null
			input "runDays", "enum", title: "On certain days of the week:", multiple: true, required: false, submitOnChange: true,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], state: runDays?.size() ? "complete" : null
		}
	}
}


/*
	runTimePage

	UI Page: Time-bar execution with optional start & end times.
*/
def runTimePage()
{
	def startTimeComplete = (startingAt in ["Sunrise", "Sunset"] || (startingAt == "A specific time" && startingTime != null))

	dynamicPage(name:"runTimePage", title: "Automation Time Range", install: false, uninstall: false)
	{
		section("Allow this automation from...")
		{
			input "startingAt", "enum", title: "Starting at...", options: ["A specific time", "Sunrise", "Sunset"], required: false, submitOnChange: true, state: startTimeComplete ? "complete" : null
			if (startingAt == "A specific time")
				input "startingTime", "time", title: "Start time", required: true, submitOnChange: true
			else
			{
				if (startingAt == "Sunrise")
					input "startSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
				else if (startingAt == "Sunset")
					input "startSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
			}
		}
		
		if (startTimeComplete)
		{
			section("Allow this automation through...")
			{
				input "endingAt", "enum", title: "Ending at...", options: ["A specific time", "Sunrise", "Sunset"], required: true, submitOnChange: true
				if (endingAt == "A specific time")
					input "endingTime", "time", title: "End time", required: true
				else
				{
					if (endingAt == "Sunrise")
						input "endSunriseOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
					else if (endingAt == "Sunset")
						input "endSunsetOffset", "number", range: "*..*", title: "Offset in minutes (+/-)", required: false
				}
			}
		}
	}
}


/*
	namePage

	UI Page: Last page in automation setup where optional name is chosen.
*/
def namePage()
{
	def appLabel = getAppLabel()

	if (!overrideLabel)
	{
		app.updateLabel(appLabel)
		logTrace "Updating Label ${app.label} to ${appLabel}"
	}


	dynamicPage(name: "namePage", install: true)
	{
		if (overrideLabel)
		{
			section("ArloPilot Mode Automation name")
			{
				label title: "Enter custom name", defaultValue: appLabel, required: false
			}
		}
		else
		{
			section("ArloPilot Mode Automation name")
			{
				paragraph app.label
			}
		}
		section
		{
			input "overrideLabel", "bool", title: "Edit automation name", defaultValue: "false", required: "false", submitOnChange: true
		}
	}
}


/*
	generalSettings

	UI Page: Enable/Disable debug logging.
*/
def generalSettings()
{
	dynamicPage(name: "generalSettings", title: "ArloPilot Mode Automation Settings", uninstall: false, install: false)
	{
		section("Notification Actions")
		{
			input name: "notifySendPush", title: "Send a push notification when this automation runs?", type: "bool", required: false, submitOnChange: true
			if (notifySendPush)
			{
				input name: "notifyCustomText", title: "Custom notification text:", description: "Optional. Enter custom text or leave blank for default.", type: "text", required: false, submitOnChange: false
			}
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
	configureDeviceActions

	UI Page: Configures a specific device for a specific SmartThings mode.
*/
def configureDeviceActions()
{
	def arloCameraCapableDevices = parent.arloDevices()
	arloCameraCapableDevices.removeAll{it.deviceType == "basestation" || it.deviceType == "siren"}

	Map arloCameras = [:]
	arloCameraCapableDevices?.each {
		arloCameras << [(it.deviceId): it.deviceName]
	}

	Map switchOnCameras = parent.deviceXORFilter(arloCameras, settings.devAction_CamOff)
	Map switchOffCameras = parent.deviceXORFilter(arloCameras, settings.devAction_CamOn)
	Map nightVisionOnCameras = parent.deviceXORFilter(arloCameras, settings.devAction_NightVisionOff)
	Map nightVisionOffCameras = parent.deviceXORFilter(arloCameras, settings.devAction_NightVisionOn)

	Map powerSaveLowCameras = parent.deviceXORFilter(arloCameras, settings.devAction_PowerSaveOptimal, settings.devAction_PowerSaveHigh)
	Map powerSaveOptimalCameras = parent.deviceXORFilter(arloCameras, settings.devAction_PowerSaveLow, settings.devAction_PowerSaveHigh)
	Map powerSaveHighCameras = parent.deviceXORFilter(arloCameras, settings.devAction_PowerSaveLow, settings.devAction_PowerSaveOptimal)

	dynamicPage(name: "configureDeviceActions", title: "ArloPilot Device Actions", nextPage: "mainPage", install: false, uninstall: false)
	{
		section()
		{
			paragraph "ArloPilot Device Actions enable changing individual camera parameters when SmartThings mode changes occur."
			if (automationEnabled != null && !automationEnabled)
				paragraph title: "Automation Disabled!", required: true, "This automation has been disabled.  It can be enabled using the switch below."

		}

		section("Camera Control")
		{
			input "devAction_CamOn", "enum", title: "Enable Recording", description: "Disable privacy mode on these cameras...", options: switchOnCameras, multiple: true, required: false, submitOnChange: true
			input "devAction_CamOff", "enum", title: "Disable Recording", description: "Enable privacy mode on these cameras...", options: switchOffCameras, multiple: true, required: false, submitOnChange: true
		}
		section("Night Vision Control")
		{
			input "devAction_NightVisionOn", "enum", title: "Enable Night Vision", description: "Enable night vision on these cameras...", options: nightVisionOnCameras, multiple: true, required: false, submitOnChange: true
			input "devAction_NightVisionOff", "enum", title: "Disable Night Vision", description: "Disable night vision on these cameras...", options: nightVisionOffCameras, multiple: true, required: false, submitOnChange: true
		}
		section("Power Saver Control")
		{
			input "devAction_PowerSaveLow", "enum", title: "Best Video", description: "Set these cameras to the highest video quality...", options: powerSaveLowCameras, multiple: true, required: false, submitOnChange: true
			input "devAction_PowerSaveOptimal", "enum", title: "Optimal", description: "Set these cameras an optimal balance of video quality and battery life...", options: powerSaveOptimalCameras, multiple: true, required: false, submitOnChange: true
			input "devAction_PowerSaveHigh", "enum", title: "Best Battery Life", description: "Set these cameras to optimize battery life...", options: powerSaveHighCameras, multiple: true, required: false, submitOnChange: true
		}
	}
}


/*
	getIsArloSelected

	Returns true if any Arlo cameras are selected in preferences
*/
private getIsArloSelected()
{
	def ArloSelected = false
	settings.each
	{
		if (it.key.startsWith("Arlo_")) ArloSelected = true
	}

	return ArloSelected
}


/*
	getSelectedArloDeviceIds

	Returns a list of the configured Arlo devices.
*/
List getSelectedArloDeviceIds()
{
	List arloDevices = []
	settings.each
	{
		if (it.key.startsWith("Arlo_")) arloDevices << it.key.replace("Arlo_", "")
	}

	return arloDevices
}


/*
	switchEvent

	Handler function for switch events.
*/
def switchEvent(evt)
{
	// Even though we're subscribed only to on events it doesn't hurt to double-check
	if (isAutomationEnabled && (evt.value == "on" || evt.value == "off"))
	{
		if (executionAllowed)
		{
			doArloModeChange()
		}
	}
}


/*
	buttonEvent

	Handler function for button push events.
*/
def buttonEvent(evt)
{
	// Even though we're subscribed only to on events it doesn't hurt to double-check
	if (isAutomationEnabled && evt.value == "pushed")
	{
		if (executionAllowed)
		{
			doArloModeChange()
		}
	}
}


/*
	presenceArriveEvent

	Handler function for "someone arrived" presence events.
*/
def presenceArriveEvent(evt)
{
	logTrace "Processing arrival event..."
	def someoneArrived = settings.stPresenceArrive.find{it.currentPresence == "present"}

	// Even though we're subscribed only to on events it doesn't hurt to double-check
	if (isAutomationEnabled && evt.value == "present" && someoneArrived)
	{
		doArloModeChange()
	}
}


/*
	presenceDepartEvent

	Handler function for "everyone left" presence events.
*/
def presenceDepartEvent(evt)
{
	logTrace "Processing departure event..."
	def someoneHome = settings.stPresenceArrive.find{it.currentPresence == "present"}

	// Even though we're subscribed only to on events it doesn't hurt to double-check
	if (isAutomationEnabled && evt.value == "present" && !someoneHome)
	{
		doArloModeChange()
	}
}


/*
	doArloModeChange

	Performs the actual Arlo mdoe change for "someone arrived" & "everyone left" presence events.
*/
private doArloModeChange()
{
	Map devActions = deviceActions

	if (executionAllowed)
	{
		// Change the Arlo mode, if automations not diabled
		if (parent.getIsDeviceEventsEnabled)
		{
			def arloBases = selectedArloDeviceIds
			def result = true
			arloBases.each
			{
				logTrace "Setting ${parent.selectedArloBases.find{dev-> dev.key == it}?.value} to mode: " + settings."Arlo_${it}"
				if (parent.setArloMode(it, settings."Arlo_${it}") == false) result = false
			}

			// Notifications
			if (settings.notifySendPush)
			{
				sendPush(settings.notifyCustomText ? settings.notifyCustomText : "${app.label} has ${result ? "completed" : "FAILED!"} .")
			}

			// Process device actions
			if (devActions.size())
			{
				logTrace "doArloModeChange: Processing device actions for event - ${evt.value}..."
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
		else logWarn "Device Event Automations are Disabled!"
	}
}


/*
	getIsAutomationEnabled

	Returns true if device event automations are enabled; false if not
*/
public getIsAutomationEnabled()
{
	return (settings.automationEnabled != null && settings.automationEnabled) ? true : false
}


/*
	getAppLabel

	Creates a pretty label for the automation.
*/
private getAppLabel()
{
	List arloDevices = []
	selectedArloDeviceIds.each
	{deviceId->
		arloDevices << parent.selectedArloBases.find{ it.key == deviceId}?.value
	}
	def arloBases = arloDevices.join(", ")

	if (arloBases != "" && checkConfigSet("devAction")) arloBases += " mode and camera properties"
	else if (arloBases == "") arloBases = "camera properties"
	else arloBases += " mode"
 
	if (settings.stSwitchOn)
	{
		return "Change ${arloBases} when ${settings.stSwitchOn} turns on."
	}
	else if (settings.stSwitchOff)
	{
		return "Change ${arloBases} when ${settings.stSwitchOn} turns off."
	}
	else if (settings.stPresenceArrive)
	{
		return "Change ${arloBases} when someone arrives."
	}
	else if (settings.stPresenceDepart)
	{
		return "Change ${arloBases} when everyone leaves."
	}
	else if (settings.pushButton)
	{
		return "Change ${arloBases} when ${settings.pushButton} is pressed."
	}
	return ""
}


/*
	getExecutionAllowed

	Checks time-barring options to determine if app is allowed to run.
*/
private getExecutionAllowed()
{
	logDebug "getExecutionAllowed: Checking to see if app can run..."
	def result = dayOfWeekValid && startTimeValid && endTimeValid
	logDebug "getExecutionAllowed: Automation is ${result ? 'allowed' : 'not allowed'} to run."
	return result
}


/*
	getDayOfWeekValid

	Checks day of week time-barring.
*/
private getDayOfWeekValid()
{
	def result = true
	if (settings.runDays)
	{
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) df.setTimeZone(location.timeZone)
		else df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		def day = df.format(new Date())
		result = settings.runDays.contains(day)
	}
	logDebug "getExecutionAllowed: DayOfWeek is ${result ? 'valid' : 'not valid'}."
	return result 
}


/*
	getStartTimeValid

	Checks start of time range time-barring.
*/
private getStartTimeValid()
{
	def result = true
	def start = null

	// No time set?
	if (settings.startingAt != null)
	{
		if (settings.startingTime)
		{
			start = timeToday(settings.startingTime, location.timeZone).time
		}
		else if (settings.startingAt in ["Sunrise", "Sunset"])
		{
			def startSunTime = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: settings.startSunriseOffset, sunsetOffset: settings.startSunsetOffset)
			if (settings.startingAt == "Sunrise") start = startSunTime.sunrise.time
			else if (settings.startingAt == "Sunset") start = startSunTime.sunset.time
		}
		result = start < now()
	}
	logDebug "getExecutionAllowed: StartTime is ${result ? 'valid' : 'not valid'}."
	return result
}


/*
	getEndTimeValid

	Checks end of time range time-barring.
*/
private getEndTimeValid()
{
	def result = true
	def end = null

	// No time set?
	if (settings.startingAt != null && settings.endingAt == null)
	{
		if (settings.endingTime)
		{
			end = timeToday(settings.endingTime,location.timeZone).time
		}
		else if (settings.endingAt in ["Sunrise", "Sunset"])
		{
			def endSunTime = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: settings.endSunriseOffset, sunsetOffset: settings.endSunsetOffset)
			if (settings.endingAt == "Sunrise") end = endSunTime.sunrise.time
			else if (settings.endingAt == "Sunset") end = endSunTime.sunset.time
		}
		result = end > now()
	}
	logDebug "getExecutionAllowed: EndTime is ${result ? 'valid' : 'not valid'}."
	return result
}


/*
	getTimeLabel

	Creates a pretty UI label for time range time-barring.
*/
private getTimeLabel()
{
	def result = ""

	if (settings.startingAt == "Sunrise") result = settings.startingAt + offsetLabel(settings.startSunriseOffset)
	else if (settings.startingAt == "Sunset") result = settings.startingAt + offsetLabel(settings.startSunsetOffset)
	else if (settings.startingTime) result = hhmm(settings.startingTime)

	if (result.size())
	{ 
		result += " to "

		if (settings.endingAt == "Sunrise") result += settings.endingAt + offsetLabel(settings.endSunriseOffset)
		else if (settings.endingAt == "Sunset") result += settings.endingAt + offsetLabel(settings.endSunsetOffset)
		else if (settings.endingTime) result += hhmm(settings.endingTime, "h:mm a z")
	}   
}


/*
	offsetLabel

	Utility Function: Creates a pretty UI label for time range offset for time-barring.
*/
private offsetLabel(value)
{
	def result = value ? ((value > 0 ? "+" : "") + value + " min") : ""
}


/*
	hhmm

	Utility Function: Formats the time as [h:mm a] in the local time zone.
*/
private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
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