/**
 *  Utilitech Glass Break Sensor
 *
 *  Copyright 2016 Steve White, based on the original DeviceType created by Adam Heinmiller
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
	definition (namespace: "shackrat", name: "Utilitech Glass Break Sensor", author: "Steve White") 
	{
		capability "Contact Sensor"
		capability "Battery"

		fingerprint deviceId:"0xA102", inClusters:"0x20, 0x9C, 0x80, 0x82, 0x84, 0x87, 0x85, 0x72, 0x86, 0x5A"
	}

	simulator 
	{
		status "Trigger Glass Break": "command: 9C02, payload: 26 00 FF 00 00"
		status "Reset Sensor": "command: 9C02, payload: 26 00 00 00 00"

		status "Battery Status 25%": "command: 8003, payload: 19"
		status "Battery Status 50%": "command: 8003, payload: 32"
		status "Battery Status 75%": "command: 8003, payload: 4B"
		status "Battery Status 100%": "command: 8003, payload: 64"
	}

	preferences
	{
		section {
			input title: "Device Bypass", description: "This feature allows you keep the glass break sensor online but do not use it to trigger alarms or other events.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input title: "Bypass this device?", displayDuringSetup: false, type: "bool", name: "bypassEnabled"
		}
		section {
			input title: "Device Debug", description: "This feature allows you log message handling to the device for troubleshooting purposes.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input title: "Enable debug messages?", displayDuringSetup: false, type: "bool", name: "debugEnabled"
		}
	}
   
	tiles 
	{
		standardTile("contact", "device.contact", width: 2, height: 2) 
		{
			state "closed", label: 'all ok', icon: "st.Home.home9", backgroundColor: "#ffffff"
			state "open", label: 'active', icon: "st.contact.contact.open", backgroundColor: "#FF0000"
			state "inactive", label: 'bypassed', icon: "st.Home.home9", backgroundColor:"#ffffff"
		}
        
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") 
		{
			state "battery", label:'${currentValue}% battery', unit:""
		}
		
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
        	state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
		main "contact"
		details(["contact", "battery", "refresh"])
	}
}


def installed()
{
	updated()
}


def updated()
{
	state.BatteryLevel = 100
	log.info "GBDevHandler Updated with settings ${settings}"
	if (bypassEnabled)
	{
		sendEvent(name: "contact", value: "inactive")
	}
	else
	{
		sendEvent(name: "contact", value: "closed")
	}
}


def getTimestamp() 
{
    return new Date().time
}


def parse(String description) 
{
	if (debugEnabled) log.debug "GBDevHandler Parse: $description / ${cmd}"
	
	def result = []
	def cmd = zwave.parse(description)
    
	if (cmd) 
	{
		result << zwaveEvent(cmd)
	}
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) 
{
	if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd}"
	
	def result = []
	result << response(zwave.wakeUpV2.wakeUpNoMoreInformation())
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) 
{
	if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd}"
    
    def result = []
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) 
{
	if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd}"
	
	def result = []
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd)
{
	if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd}"
	
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF)
	{
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery."
	}
	else
	{
		map.value = cmd.batteryLevel
	}
	createEvent(map)
}


def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd) 
{
	def result = [name: "contact"]
	if (bypassEnabled)
	{
		if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd} :: DEVICE IS BYPASSED"
		result += [value: "inactive", descriptionText: "${device.displayName} IS BYPASSED"]
	}
	else if (cmd.sensorState == 0)
	{
		if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd}"
		result += [value: "closed", descriptionText: "${device.displayName} has reset"]
	}
	else if (cmd.sensorState == 255)
	{
		if (debugEnabled) log.debug "GBDevHandler Device Command: ${cmd}"
		result += [value: "open", descriptionText: "${device.displayName} has detected glass breakage!!"]
	}
	return createEvent(result)
}


def zwaveEvent(physicalgraph.zwave.Command cmd) 
{
	if (debugEnabled) log.debug "GBDevHandler Unhandled Device Command: ${cmd}"
	
	return createEvent([descriptionText: "Unhandled event: ${device.displayName} :: ${cmd}", displayed: false])
}