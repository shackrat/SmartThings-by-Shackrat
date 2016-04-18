/**
 *  Control GE Wall Switch Indicators by Mode
 *
 *  Copyright 2016 Steve White
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
definition(
	name: "Turn Off GE Switch Indicators by Mode",
	namespace: "shackrat",
	author: "Steve White",
	description: "Turns Off the LED indicators on GE wall switches and fan controls based on system mode.",
	category: "My Apps",
	iconUrl: "http://cdn.device-icons.smartthings.com/indicators/never-lit.png",
	iconX2Url: "http://cdn.device-icons.smartthings.com/indicators/never-lit@2x.png",
	iconX3Url: "http://cdn.device-icons.smartthings.com/indicators/never-lit@3x.png")

preferences {
	section("Configure Switches and Mode(s)") {
		input name: "switches", title: "Turn off LED on switches", type: "capability.indicator", multiple: true
		input "modes", "mode", title: "When mode is", multiple: true, required: true
		input name:"defaultMode", title: "Otherwise, set to", type: "enum", metadata:[ values: ['Lit when on', 'Lit when off'] ]
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(location, "mode", modeChangeEvent)
}

def modeChangeEvent(evt) {
	if (modes.find { it == location.mode } != null) {
		log.debug "Killing LED Indicators for ${location.mode}"
		switches.indicatorNever()
	}
	else {
		log.debug "Restoring LED Indicators for ${location.mode}"
		if (defaultMode == "Lit when on") {
			switches.indicatorWhenOn()
		}
		else {
			switches.indicatorWhenOff()
		}
	}
}




