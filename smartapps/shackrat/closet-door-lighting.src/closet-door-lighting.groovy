/**
 * Closet Door Lighting
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
	name: "Closet Door Lighting",
	namespace: "shackrat",
	author: "Steve White",
	description: "Turns on lights when any contact sensor is open, but don't turn off until all are closed.",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/smartlights@2x.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/smartlights@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/smartlights@3x.png"
)

preferences
{
	section("Contact Sensors")
	{
		input "contacts", "capability.contactSensor", title: "Select Doors/Windows:", required: true, multiple: true
	}
    
	section("Lights & Switches")
	{
		input "switches", "capability.switch", title: "Select Lights and Switches", required: true, multiple: true
	}
}


def installed()
{
	log.info "Closet Door Lighting Installed"
	initialize()
}


def updated()
{
	log.info "Closet Door Lighting Updated"
	unsubscribe()
	initialize()
}


def initialize()
{
	log.info "Closet Door Lighting Installed"
	subscribe(contacts, "contact", handleContactEvent)
}


def handleContactEvent(evt)
{
	if (evt?.value == "open")
	{
		def openContacts = contacts.findAll
        { 
			it?.latestValue("contact") == "open"
    	}

		if (openContacts.size() == 1)
        {
			// At least one is open, turn the lights on
			switches?.on()
        }
	}
	else
	{
		def openContacts = contacts.findAll
        {
			it?.latestValue("contact") == "open"
    	}

		if (openContacts.size() == 0)
        {
			// All closed, lights off
			switches?.off()
        }
	}
}