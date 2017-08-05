import groovy.transform.Field
import groovy.json.*

/**
 *  homeflow
 *
 *  Copyright 2017 Titus Woo
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
    name: "homeflow-alpha",
    namespace: "homeflow",
    author: "Titus Woo",
    description: "Simpler automation",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

@Field String httpEndpoint = "http://d7231358.ngrok.io"

preferences {
    page(name: "deviceSelectionPage", title: "Device Selection", nextPage: "authorizationPage", uninstall: true) {
        section("Control these devices...") {
            input "switches", "capability.switch", title: "Lights", multiple: true, required: false
            input "colors", "capability.colorControl", title: "Color Controllers", multiple: true, required: false
            input "dimmers", "capability.switchLevel", title: "Dimmers", multiple: true, required: false
            input "presence", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: false
            input "contacts", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: false
            input "motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
        }

        section ("Control these devices...") {
            CAPABILITY_MAP.each { key, deviceType ->
                // input "alarm", "capability.alarm", title: "Alarm"
                input key, deviceType["capability"], title: deviceType["name"], multiple: true, required: false
            }
        } 
    }

    page(name: "authorizationPage", title: "Connect your SmartThings account", install: true, uninstall: true)
}

def authorizationPage() {
    def accountCode = getAccountCode()
    dynamicPage(name: "authorizationPage") {
        section("Get Setup") {
            paragraph image: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
                  title: "$accountCode",
                  required: false,
                  "Enter this code on your Homeflow dashboard."
        }
    }
}

def installed() {
	initialize()
}

def updated() {
    // unsubscrbe
	unsubscribe()
    // re-subscribe
	initialize()
}

def initialize() {
    subscribeAll()
}

def uninstalled() {
    handleUninstall()
}

def handleUninstall() {
    def payload = new groovy.json.JsonBuilder([
        hubId: location.hubs[0].id,
    ])

   fetch("smartthings/uninstall", payload)
}

def getAccountCode() {
    if (!state.accessToken) {
        state.accessToken = createAccessToken()
    }
    def payload = new groovy.json.JsonBuilder([
        accessToken: state.accessToken,
        hubId: location.hubs[0].id,
        appId: app.id,
    ])

    def resp = fetch("auth/smartthings/token", payload)
    return resp.accountCode
}

def subscribeAll() {
    log.debug("Subscribing to devices...")
    CAPABILITY_MAP.each { key, deviceType ->
        deviceType["attributes"].each { attribute ->
            subscribe(settings[key], attribute, eventHandler)
        }
    }

    getDevices()
    // subscribe(switches, "switch.on", eventHandler)
	// subscribe(switches, "switch.off", eventHandler)
    // subscribe(dimmers, "level", eventHandler)
    // subscribe(dimmers, "switch", eventHandler)
    // subscribe(contacts, "contact", eventHandler)
    // subscribe(presence, "presence", eventHandler)
    // subscribe(motion, "motion", eventHandler)
    // subscribe(power, "power", eventHandler)
}

def getDevices() {
    log.debug "getDevices: getting devices"
	def devices = []

     CAPABILITY_MAP.each { key, deviceType ->
        settings[key].each { device ->
            def deviceObject = [:]

            deviceObject.name = device.name
            deviceObject.id = device.id
            deviceObject.label = device.label
            deviceObject.displayName = device.displayName
            deviceObject.capability = key
            deviceObject.deviceData = [:]
            deviceType.attributes.each { attribute -> 
                def currentAttributeName = "current${attribute.capitalize()}"
                deviceObject.deviceData[attribute] = device[currentAttributeName]
            }

            // TODO: depracate
            deviceObject.capabilities = device.capabilities.collect { c -> c.name }

            devices.push(deviceObject)
        }
    }

	return devices
}

// TODO: REMOVE
def getDevicesAndFormat() {
    log.debug "getDevicesAndFormat: getting devices and formatting"
    def devices = getDevices()
    def devicesList = []

    devices.each { device ->
        log.debug "getDevicesAndFormat: device – $device"

        def deviceObject = [:]

        deviceObject.name = device.name
        deviceObject.id = device.id
        deviceObject.label = device.label
        deviceObject.displayName = device.displayName
        deviceObject.capabilities = device.capabilities.collect { c -> c.name }

        device.supportedAttributes.each { attribute ->
            def currentAttributeName = "current${attribute.name.capitalize()}"
            deviceObject["$attribute.name"] = device["$currentAttributeName"]
        }

        if (!devicesList.findAll { it.id == device.id }) {
            devicesList.push(deviceObject)
        }
    }

    return devicesList
}

def getDevice(id) {
	def devices = getDevices()
    def device = devices.find { it.id == id }
    if (!device) {
    	return false
    }
    return device
}

def eventHandler() {
    def devices = getDevices()
    def devicesJSON = groovy.json.JsonOutput.toJson(devices)
    def data = [
        devices: devicesJSON,
        hubId: location.hubs[0].id
    ]

    def params = [
      uri: "$httpEndpoint/api/smartthings/update",
      body: data
    ]

    try {
        httpPostJson(params) { resp -> 
            log.debug resp.data
        }
    } catch (e) {
      log.error "eventHandler: $e"
    }
}

def getDevicesList() {
    def devices = getDevices()
    def devicesJSON = groovy.json.JsonOutput.toJson(devices)
    render data: devicesJSON, status: 200
}

def updateDevice() {
	def deviceId = params.id
    def command = request.JSON?.command

    if (!deviceId) {
    	httpError(400, "deviceId parameter is required.")
    }

    def device = getDevice(deviceId)

    if (!device) {
    	httpError(500, "Could not find device with id ${deviceId}")
    }

    try {
      switch (command) {
        case "setLevel":
          def level = request.JSON?.level
          device.on()
          device.setLevel(level)
          break
        case "setHue":
          def hue = request.JSON?.hue
          device.on()
          device.setHue(hue)
          break
        default:
          device."$command"()
      }
    } catch (e) {
    	httpError(500, "Could not execute command '${command}' to device with id ${deviceId}")
    }
}

// HELPERS
def fetch(path, payload) {
    def params = [
        uri: "$httpEndpoint/api/$path",
        body: payload
    ]

    log.debug("Sending POST: ${params}")

    try {
        httpPostJson(params) { resp -> 
            return resp.data
        }
    } catch (e) {
        log.debug("fetch.error: $e")
    }
}

mappings {
	path("/devices") {
    	action: [
        	GET: "getDevicesList",
        ]
    }
    path("/device/:id") {
    	action: [
        	PUT: "updateDevice"
        ]
    }
}

// device lookup tree
@Field CAPABILITY_MAP = [
    "accelerationSensors": [
        name: "Acceleration Sensor",
        capability: "capability.accelerationSensor",
        attributes: [
            "acceleration"
        ]
    ],
    "alarm": [
        name: "Alarm",
        capability: "capability.alarm",
        attributes: [
            "alarm"
        ],
        action: "actionAlarm"
    ],
    "battery": [
        name: "Battery",
        capability: "capability.battery",
        attributes: [
            "battery"
        ]
    ],
    "beacon": [
        name: "Beacon",
        capability: "capability.beacon",
        attributes: [
            "presence"
        ]
    ],
    "button": [
        name: "Button",
        capability: "capability.button",
        attributes: [
            "button"
        ]
    ],
    "carbonDioxideMeasurement": [
        name: "Carbon Dioxide Measurement",
        capability: "capability.carbonDioxideMeasurement",
        attributes: [
            "carbonDioxide"
        ]
    ],
    "carbonMonoxideDetector": [
        name: "Carbon Monoxide Detector",
        capability: "capability.carbonMonoxideDetector",
        attributes: [
            "carbonMonoxide"
        ]
    ],
    "colorControl": [
        name: "Color Control",
        capability: "capability.colorControl",
        attributes: [
            "hue",
            "saturation",
            "color"
        ],
        action: "actionColor"
    ],
    "colorTemperature": [
        name: "Color Temperature",
        capability: "capability.colorTemperature",
        attributes: [
            "colorTemperature"
        ],
        action: "actionColorTemperature"
    ],
    "consumable": [
        name: "Consumable",
        capability: "capability.consumable",
        attributes: [
            "consumable"
        ],
        action: "actionConsumable"
    ],
    "contactSensors": [
        name: "Contact Sensor",
        capability: "capability.contactSensor",
        attributes: [
            "contact"
        ]
    ],
    "doorControl": [
        name: "Door Control",
        capability: "capability.doorControl",
        attributes: [
            "door"
        ],
        action: "actionOpenClosed"
    ],
    "energyMeter": [
        name: "Energy Meter",
        capability: "capability.energyMeter",
        attributes: [
            "energy"
        ]
    ],
    "garageDoors": [
        name: "Garage Door Control",
        capability: "capability.garageDoorControl",
        attributes: [
            "door"
        ],
        action: "actionOpenClosed"
    ],
    "illuminanceMeasurement": [
        name: "Illuminance Measurement",
        capability: "capability.illuminanceMeasurement",
        attributes: [
            "illuminance"
        ]
    ],
    "imageCapture": [
        name: "Image Capture",
        capability: "capability.imageCapture",
        attributes: [
            "image"
        ]
    ],
    "levels": [
        name: "Switch Level",
        capability: "capability.switchLevel",
        attributes: [
            "level"
        ],
        action: "actionLevel"
    ],
    "lock": [
        name: "Lock",
        capability: "capability.lock",
        attributes: [
            "lock"
        ],
        action: "actionLock"
    ],
    "mediaController": [
        name: "Media Controller",
        capability: "capability.mediaController",
        attributes: [
            "activities",
            "currentActivity"
        ]
    ],
    "motionSensors": [
        name: "Motion Sensor",
        capability: "capability.motionSensor",
        attributes: [
            "motion"
        ],
        action: "actionActiveInactive"
    ],
    "musicPlayer": [
        name: "Music Player",
        capability: "capability.musicPlayer",
        attributes: [
            "status",
            "level",
            "trackDescription",
            "trackData",
            "mute"
        ],
        action: "actionMusicPlayer"
    ],
    "pHMeasurement": [
        name: "pH Measurement",
        capability: "capability.pHMeasurement",
        attributes: [
            "pH"
        ]
    ],
    "powerMeters": [
        name: "Power Meter",
        capability: "capability.powerMeter",
        attributes: [
            "power"
        ]
    ],
    "presenceSensors": [
        name: "Presence Sensor",
        capability: "capability.presenceSensor",
        attributes: [
            "presence"
        ]
    ],
    "humiditySensors": [
        name: "Relative Humidity Measurement",
        capability: "capability.relativeHumidityMeasurement",
        attributes: [
            "humidity"
        ]
    ],
    "relaySwitch": [
        name: "Relay Switch",
        capability: "capability.relaySwitch",
        attributes: [
            "switch"
        ],
        action: "actionOnOff"
    ],
    "shockSensor": [
        name: "Shock Sensor",
        capability: "capability.shockSensor",
        attributes: [
            "shock"
        ]
    ],
    "signalStrength": [
        name: "Signal Strength",
        capability: "capability.signalStrength",
        attributes: [
            "lqi",
            "rssi"
        ]
    ],
    "sleepSensor": [
        name: "Sleep Sensor",
        capability: "capability.sleepSensor",
        attributes: [
            "sleeping"
        ]
    ],
    "smokeDetector": [
        name: "Smoke Detector",
        capability: "capability.smokeDetector",
        attributes: [
            "smoke"
        ]
    ],
    "soundSensor": [
        name: "Sound Sensor",
        capability: "capability.soundSensor",
        attributes: [
            "sound"
        ]
    ],
    "stepSensor": [
        name: "Step Sensor",
        capability: "capability.stepSensor",
        attributes: [
            "steps",
            "goal"
        ]
    ],
    "switches": [
        name: "Switch",
        capability: "capability.switch",
        attributes: [
            "switch"
        ],
        action: "actionOnOff"
    ],
    "soundPressureLevel": [
        name: "Sound Pressure Level",
        capability: "capability.soundPressureLevel",
        attributes: [
            "soundPressureLevel"
        ]
    ],
    "tamperAlert": [
        name: "Tamper Alert",
        capability: "capability.tamperAlert",
        attributes: [
            "tamper"
        ]
    ],
    "temperatureSensors": [
        name: "Temperature Measurement",
        capability: "capability.temperatureMeasurement",
        attributes: [
            "temperature"
        ]
    ],
    "thermostat": [
        name: "Thermostat",
        capability: "capability.thermostat",
        attributes: [
            "temperature",
            "heatingSetpoint",
            "coolingSetpoint",
            "thermostatSetpoint",
            "thermostatMode",
            "thermostatFanMode",
            "thermostatOperatingState"
        ],
        action: "actionThermostat"
    ],
    "thermostatCoolingSetpoint": [
        name: "Thermostat Cooling Setpoint",
        capability: "capability.thermostatCoolingSetpoint",
        attributes: [
            "coolingSetpoint"
        ],
        action: "actionCoolingThermostat"
    ],
    "thermostatFanMode": [
        name: "Thermostat Fan Mode",
        capability: "capability.thermostatFanMode",
        attributes: [
            "thermostatFanMode"
        ],
        action: "actionThermostatFan"
    ],
    "thermostatHeatingSetpoint": [
        name: "Thermostat Heating Setpoint",
        capability: "capability.thermostatHeatingSetpoint",
        attributes: [
            "heatingSetpoint"
        ],
        action: "actionHeatingThermostat"
    ],
    "thermostatMode": [
        name: "Thermostat Mode",
        capability: "capability.thermostatMode",
        attributes: [
            "thermostatMode"
        ],
        action: "actionThermostatMode"
    ],
    "thermostatOperatingState": [
        name: "Thermostat Operating State",
        capability: "capability.thermostatOperatingState",
        attributes: [
            "thermostatOperatingState"
        ]
    ],
    "thermostatSetpoint": [
        name: "Thermostat Setpoint",
        capability: "capability.thermostatSetpoint",
        attributes: [
            "thermostatSetpoint"
        ]
    ],
    "threeAxis": [
        name: "Three Axis",
        capability: "capability.threeAxis",
        attributes: [
            "threeAxis"
        ]
    ],
    "timedSession": [
        name: "Timed Session",
        capability: "capability.timedSession",
        attributes: [
            "timeRemaining",
            "sessionStatus"
        ],
        action: "actionTimedSession"
    ],
    "touchSensor": [
        name: "Touch Sensor",
        capability: "capability.touchSensor",
        attributes: [
            "touch"
        ]
    ],
    "valve": [
        name: "Valve",
        capability: "capability.valve",
        attributes: [
            "contact"
        ],
        action: "actionOpenClosed"
    ],
    "voltageMeasurement": [
        name: "Voltage Measurement",
        capability: "capability.voltageMeasurement",
        attributes: [
            "voltage"
        ]
    ],
    "waterSensors": [
        name: "Water Sensor",
        capability: "capability.waterSensor",
        attributes: [
            "water"
        ]
    ],
    "windowShades": [
        name: "Window Shade",
        capability: "capability.windowShade",
        attributes: [
            "windowShade"
        ],
        action: "actionOpenClosed"
    ]
]