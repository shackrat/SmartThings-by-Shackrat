/**
 * IrisUsers.com Power Logging & Graphing
 *
 * Copyright 2016 Steve White
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 */

definition(
	name: "IrisUsers.com Power Logging & Graphing",
	namespace: "shackrat",
	author: "Steve White",
	description: "Log power usage data to IrisUsers.com",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences
{
	section("Power Devices to Log")
	{
		input "powerDevices", "capability.powerMeter", title: "Select power devices:", required: true, multiple: true
	}
    
	section("IrisUsers API")
	{
	    input "apiKey", "text", title: "IrisUsers.com API Key:", required: true, defaultValue: "12345"
	    input "logInterval", "enum", title: "Interval in which to send data (minutes):", options: ["0", "1", "5", "10"], defaultValue: "1", required: true, multiple: false
	}
    
	section("Debug Settings")
	{
		input "resetLogger", "bool", title:"Reset the logger and dump queue?", required: false, defaultValue: false
		input "disableLogging", "bool", title: "Disable uploading?", required: false, defaultValue: false
		input "enableDebug", "bool", title: "Enable debug output?", required: false, defaultValue: false
	}
}


def installed()
{
	log.info "IrisUsers.com Power Logging & Graphing Installed"
	resetStateVals()
	initialize()
}


def updated()
{
	log.info "IrisUsers.com Power Logging & Graphing Updated"
	unsubscribe()
	initialize()
	
	if (settings.powerDevices.size() > 0)
	{
		saveDevices()
	}

	if (settings.resetLogger) 
	{
		resetStateVals()
		settings.resetLogger = false
	}
}


def initialize()
{
	log.info "IrisUsers.com Power Logging & Graphing Installed"
	
	subscribe(powerDevices, "power", handlePowerEvent)
}


def resetStateVals()
{
	log.debug "Set original state"
	
	unschedule()
	
	state.queue = [:]
	state.failureCount = 0
	state.scheduled = false
	state.lastSchedule = 0
}


def handlePowerEvent(evt)
{
	if (settings.logInterval.toInteger() > 0)
	{
		queueValue(evt)
	}
	else
	{
		sendValue(evt)
	}
}


private sendValue(evt)
{
	if (disableLogging)
	{
		if (enableDebug) log.debug "Skipping power reading, upload disabled."
		return
	}

    if (enableDebug) log.debug "Posting single power event for ${evt.displayName} - ${evt.value}W."

    def query = [a: "ad", t: apiKey, "d[${evt.device.id}][]": evt.date.format('yyyy-MM-dd HH:mm:ss', location.timeZone) + "|${evt.value}"] 
	def getParams = [
		uri:	"http://irisusers.com",
		path:	"/logger/logger.php",
		query:	query
	]

	httpGet(getParams)
	{ response ->
		log.debug(response.status)
		if (response.status != 200 )
		{
			if (enableDebug) log.debug "Posting power event data failed with status of ${response.status}"
		}
	}
}


private queueValue(evt)
{
	checkAndProcessQueue()
	if (evt?.value)
	{
		if (enableDebug) log.debug "Queueing single power event for ${evt.displayName} - ${evt.value}W."
		state.queue.put("d[${evt.device.id}][${evt.id}]", evt.date.format('yyyy-MM-dd HH:mm:ss', location.timeZone) + "|${evt.value}")
		scheduleQueue()
	}
}


private checkAndProcessQueue()
{
	// runIn not processing the queue?
    if (state.scheduled && ((now() - state.lastSchedule) > (logInterval.toInteger()*120000)))
    {
		sendEvent(name: "scheduleFailure", value: now())
		unschedule()
		processQueue()
    }
}


def scheduleQueue()
{
	if (state.failureCount >= 3)
	{
		if (enableDebug) log.debug "Excessive schedule failures, resetting state and flushing queue."
	    sendEvent(name: "queueFailure", value: now())
	    resetState()
	}

	if (state.scheduled == false)
	{
		if (enableDebug) log.debug "Scheduling queue to run."
	    runIn (logInterval.toInteger() * 60, processQueue)
	    state.scheduled=true
	    state.lastSchedule=now()
	} 
}


private resetState()
{
    state.queue = [:]
    state.failureCount=0
    state.scheduled=false
}


def processQueue()
{
	state.scheduled = false
	if (enableDebug) log.debug "Processing " + state.queue.size() + " Queued Power Readings"

	if (state.queue != [:])
	{
	    def query = [a: "ad", t: apiKey] + state.queue

	    if (enableDebug) log.debug "Uploading queued batch of power events."
	    
		def getParams = [
			uri:	"http://irisusers.com",
			path:	"/logger/logger.php",
			body:	query
		]

		httpPost(getParams)
		{ response ->
			log.debug(response.status)
			if (response.status != 200 )
			{
				if (enableDebug) log.debug "Posting power event data failed with status of ${response.status}"
				state.failureCount = state.failureCount+1
				scheduleQueue()
			}
			else
			{
				resetState()
				state.queue = [:]
				state.failureCount = 0
				state.scheduled = false
			}
		}
	}
}


def saveDevices()
{
	if (powerDevices != [:])
	{
	    def query = [a: "ud", t: apiKey]
	    for (e in powerDevices)
	    {
	    	query << ["d[${e.id}]": e.displayName]
		}

	    if (enableDebug) log.debug "Uploading device list."
	    
		def getParams = [
			uri:	"http://irisusers.com",
			path:	"/logger/logger.php",
			query:	query
		]

		httpGet(getParams)
		{ response ->
			log.debug(response.status)
			if (response.status != 200 )
			{
				if (enableDebug) log.debug "Upload device list failed with status of ${response.status}"
			}
		}
	}
}