/**
 *  Wattvision Connector
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
	name: "Wattvision Power Connector",
	namespace: "shackrat",
	author: "Steve White",
	description: "Uploads SmartThings Power Meter data to Wattvision",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/wattvision.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/wattvision%402x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	section("Power Meter") {
		input "power", "capability.powerMeter", title: "Select Power Meter", required: false, multiple: true
	}
	section ("Wattvision API") {
		input "apiKey", "text", title: "Wattvision API Key", required:true
		input "apiId", "text", title: "Wattvision API ID", required:true
		input "sensorId", "text", title: "Wattvision Virtual Sensor ID", required:true
	}
	section ("General Settings") {
		input "sensorHysteresis", "number", title: "Average Readings over (n) Seconds", required:true, defaultValue: "20"
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	state.postInterval = sensorHysteresis.toInteger() * 1000
	state.lastUpdate = now()
	state.powerReadings = []
	state.energyReading = 0
	subscribe(power, "power", powerEvent)
	subscribe(power, "energy", energyEvent)
}

def powerEvent(evt) {
	sendEvent(evt)
}

def energyEvent(evt) {
	log.info "WPC logging energy reading of ${evt.value} watt-hours."
	state.energyReading = evt.value.toFloat() * 1000
}

private sendEvent(evt) {
	state.powerReadings << evt.value.toInteger()
	if ((state.lastUpdate + state.postInterval) > now()) {
		log.info "WPC logging power reading of ${evt.value} watts."
		return
	}
		
	def readings = state.powerReadings.size()
	def watts = Math.round(state.powerReadings.sum() / readings)
	def watthours = Math.round(state.energyReading)

	log.info "WPC uploading ${watts}W based on ${readings} samples, ${watthours} watt-hours lifetime."

	state.powerReadings = []	 
	state.lastUpdate = now()

	def reqParams = [
		uri: "https://www.wattvision.com/api/v0.2/elec",
		body: "{\"sensor_id\":\"${sensorId}\",\"api_id\":\"${apiId}\",\"api_key\":\"${apiKey}\",\"watts\":${watts},\"watthours\":${watthours}}",
		requestContentType: "application/x-www-form-urlencoded;charset=utf-8"
	]

	httpPost(reqParams) { response ->
		if (response.status != 200 ) {
			log.error "Wattvision Power Connector logging failed."
			response.headers.each {
				log.debug "${it.name} : ${it.value}"
			}
		}
	}
}