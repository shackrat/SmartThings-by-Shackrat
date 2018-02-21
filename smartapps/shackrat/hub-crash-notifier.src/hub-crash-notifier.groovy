/**
 * Hub Crash Notifier
 *
 * Copyright 2018 Steve White
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
	name: "Hub Crash Notifier",
	namespace: "shackrat",
	author: "Steve White",
	description: "Notifies when specific hub events occur.",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-Panic.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-Panic@2x.png",
)

preferences
{

}


def installed()
{
	log.info "Hub Crash Notifier Installed"
	initialize()
}


def updated()
{
	log.info "Hub Crash Notifier Updated"
	unsubscribe()
	initialize()
}


def initialize()
{
	log.info "Hub Crash Notifier Installed"
	def hub = location.hubs[0] 
	subscribe(hub, "hubStatus", hubStatusHandler)
}


def hubStatusHandler(evt)
{
	logInfo ("Hub Event! ${evt.displayName} - ${evt.value}.")


	// Z-Wave radio event
	if (evt.value == "zw_radio_on")
	{
		sendPush("${evt.displayName} has an event that caused a Z-Wave restart.")
	}

	// Zigbee radio event
	if (evt.value == "zb_radio_on")
	{
		sendPush("${evt.displayName} has an event that caused a Zigbee restart.")
	}

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