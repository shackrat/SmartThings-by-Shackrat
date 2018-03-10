/**
 * ArloPilot Camera Device Tile
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
metadata
{
	definition (name: "arloPilotCameraTile", namespace: "shackrat", author: "Steve White")
	{
		capability "Image Capture"
		capability "Actuator"
		capability "refresh"
	}

	preferences
	{
		section("Polling Options")
		{
			paragraph "Due to an incompatibility between the Arlo cloud and the SmartThings cloud, ArloPilot is unable to receive notifications from the Arlo cloud.\nTo refresh images, ArloPilot must periodically poll for changes."
			input "pollInterval", "enum", title: "Polling Interval", description: "Time in minutes in which to check for updated still images. (Set to 0 to disable)", options: [0,5,10,15,30], multiple: false, required: false, defaultValue: 10			
		}
		section("Debug Logging")
		{
			paragraph "By default, only errors and warnings are reported in the IDE.  If you are experiencing an issue with this device, is recommended that debug and trace logging be enabled."
			input "enableTrace", "bool", title: "Enable trace logging?", description: "Indicates high-level activities during the execution of the app.", defaultValue: false, required: true, multiple: false
			input "enableDebug", "bool", title: "Enable debug logging?", description: "Shows detailed output from data structures and Arlo cloud responses.", defaultValue: false, required: true, multiple: false
		}
	}

	// Virtual Camera Tile
	tiles(scale: 2)
	{  
		carouselTile("cameraDetails", "device.image", width: 6, height: 6) { }

		standardTile("refresh", "device.refresh", width: 2, height: 2, decoration: "flat")
		{
			state "default", label: "", action: "refresh", icon:"st.secondary.refresh"
		}

		standardTile("image", "device.image", width: 2, height: 2, decoration: "flat")
		{
			state "default", label: "", icon:"st.camera.take-photo"
		}

		valueTile("lastRefresh", "lastRefresh", width: 4, height: 2, inactiveLabel: false, decoration: "flat")
		{
			state "default", label:'Last Refresh\n${currentValue}'
		}

		main "image"
		details (["cameraDetails", "lastRefresh", "refresh"])
	}
}


/*
	installed

	Doesn't do much other call initialize().
*/
def installed()
{
	logInfo "ArloPilot - Virtual Camera Tile Installed"
	initialize()
	refresh()
}


/*
	updated

	Re-initializes app after making changes.
*/
def updated()
{
	logInfo "ArloPilot - Virtual Camera Tile Updated"
	initialize()
}


/*
	initialize

	Resets virtual device state, nothing special.
*/
def initialize()
{
	state.lastImage = ""
	state.lastImageUrl = ""
	state.lastImageMd5 = ""
	state.lastRefresh = 0

	// Schedule polling of the Arlo cloud
	def pollingInterval = settings.pollInterval ? settings.pollInterval : 10
	logTrace "initialize: Setting polling interval to ${pollingInterval}."
	switch (pollingInterval.toInteger())
	{
		case 0:
			unschedule()
			break
		case 5:
			runEvery5Minutes(refresh)
			break
		case 10:
			runEvery10Minutes(refresh)
			break
		case 15:
			runEvery15Minutes(refresh)
			break
		case 30:
			runEvery30Minutes(refresh)
			break
	}

	logInfo "ArloPilot - Virtual Camera Tile Initialized."
}


/*
	refresh

	Retreives the latest snapshot image URL from the Arlo cloud for the device.
*/
def refresh()
{
	def imgUrl = parent.getLastImageURL(getDataValue("arloDeviceId"))
	if (!imgUrl)
	{
		logError "refresh: Could not execute getLastImageURL for Arlo device " + getDataValue("arloDeviceId")
		state.lastImage = ""
		return
	}

	// Check for URL changes
	if (imgUrl == state.lastImageUrl)
	{
		logTrace "refresh: Nothing to refresh. The Arlo cloud returned no new image."
		return
	}

	// Fetch the latest image and store it
	try
	{
		httpGet(imgUrl)
		{ response ->
			if (response.status == 200 && response.headers.'Content-Type'.contains("image/jpeg"))
			{
				def imageBytes = response.data
				if (imageBytes)
				{
					def imageName = getImageName()
					try
					{
						// Only store the image if it's changed
						def imageMD5 = getImageMD5(imageBytes)
 						if (state.lastImageMd5 != imageMD5)
						{
							logTrace "refresh: Storing updated image from ArloCloud.  MD5: ${imageMD5}"

							storeImage(imageName, imageBytes)
							state.lastImage = imageName
							state.lastImageUrl = imgUrl
							state.lastImageMd5 = imageMD5
							state.lastRefresh = now()
							def timeString = new Date().format("MM-dd-yy h:mm:ss a", location.timeZone)
							sendEvent(name: "lastRefresh", value: timeString, displayed: false)
						}
						else logTrace "refresh: Image from ArloCloud has not changed.  MD5: ${imageMD5}"
					}
					catch (errorException)
					{
						logError "refresh: Error storing image ${imageName}: ${errorException}"
					}
				}
			}
			else
			{
				logError "refresh: Could not get image; image not available or not a valid jpeg response"
			}
		}
	}
	catch (errorException)
	{
		logDebug "refresh: Error making request; ${errorException}"
	}
}


/*
	getLastImageBytes

	Retreives the latest snapshot image from the SmartThings cloud and returns it as a ByteArrayInputStream.

	Note: This function is called from the ArloPilot service manager. 
*/
ByteArrayInputStream getLastImageBytes()
{
	if (state.lastImage == "")
	{
		refresh()
	}

	 return state.lastImage == "" ? false : getImage(state.lastImage)
}


/*
	getImageName

	Generates a unique image name for use with storeImage().
*/
private getImageName()
{
	return java.util.UUID.randomUUID().toString().replaceAll('-', '')
}


/*
	getImageMD5

	Generates a unique MD5 hash for ByteArrayInputStream [imageBytes]
*/
import java.security.MessageDigest;
private getImageMD5(ByteArrayInputStream imageBytes)
{
	MessageDigest md5Digest = MessageDigest.getInstance("MD5")
	byte[] byteBuffer = new byte[1024]
	int bytesRead = 0

	while (bytesRead != -1)
	{
		bytesRead = imageBytes.read(byteBuffer)
		if (bytesRead > 0)
		{
			md5Digest.update(byteBuffer, 0, bytesRead)
		}
	}

	imageBytes.reset();
	imageBytes.skip(0);

	def md5 = new BigInteger(1, md5Digest.digest()).toString(16).padLeft(32, '0')
    return md5.toString()
}


/*
	logDebug

	Displays debug output to IDE logs based on user preference.
*/
private logDebug(msgOut)
{
//	if (settings.enableDebug)
//	{
		log.debug msgOut
//	}
}


/*
	logTrace

	Displays trace output to IDE logs based on user preference.
*/
private logTrace(msgOut)
{
//	if (settings.enableTrace)
//	{
		log.trace msgOut
//	}
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