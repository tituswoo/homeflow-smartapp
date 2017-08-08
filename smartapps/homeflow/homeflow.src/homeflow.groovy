    import groovy.transform.Field
    import groovy.json.*

    /**
    *  homeflow 1.0.0
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
                input "Actuator", "capability.actuator", multiple: true, title: "Which actuators", required: false
                input "Sensor", "capability.sensor", multiple: true, title: "Which sensors", required: false
            }

            section ("Input") {
                CAPABILITY_MAP.each { key, capability ->
                    input key, capability["capability"], title: capability["name"], multiple: true, required: false
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
        log.debug "hubId: ${location.hubs[0].id}"

        def attributes = []

        settings.each { key, devicesInInput ->
            devicesInInput.each { device ->
                device.supportedAttributes.each { attribute ->
                    attributes.push(attribute.getName())
                }
            }
        }
        
        attributes.each { attribute ->
            subscribe(Actuator, attribute, eventHandler)
            subscribe(Sensor, attribute, eventHandler)
        }
    }

    def getFormattedDevices() {
        log.debug "getFormattedDevices: getting formatted devices"
        def devices = getDevices()
        def deviceList = []

        // TODO: refactor nesting
        devices.each { device ->
            // each capability represents a device
            device.capabilities.each { capability ->
                def subDevice = [:]
                def formattedCapabilityName = toCamelCase(capability.name)

                subDevice.name = device.name
                subDevice.id = "${device.id}_${formattedCapabilityName}"
                subDevice.deviceId = device.id
                subDevice.displayName = device.displayName
                subDevice.capability = formattedCapabilityName
                subDevice.deviceData = [:]
                
                // capability exists in capability map (blacklist: healthCheck, sensor, threeAxis)
                if (CAPABILITY_MAP[formattedCapabilityName]) {
                    CAPABILITY_MAP[formattedCapabilityName].attributes.each { attribute -> 
                        def currentAttributeName = "current${attribute.capitalize()}"
                        subDevice.deviceData[attribute] = device[currentAttributeName]
                    }
                    deviceList.push(subDevice)
                }
            }
        }

        return deviceList
    }

    // gets devices from SmartThings without modification
    def getDevices() {
        def devices = []
        for (String key : settings.keySet()) {
            settings[key].each { device ->
                devices << device
            }
        }

        return devices
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
        log.debug "eventHandler: event occurred"
        def devices = getFormattedDevices()
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
        def devices = getFormattedDevices()
        def devicesJSON = groovy.json.JsonOutput.toJson(devices)
        render data: devicesJSON, status: 200
    }

    def updateDevice() {
        log.debug "updateDevice: updating device"
        log.debug request.JSON
        log.debug params

        def deviceId = params.deviceId
        def capability = request.JSON?.capability
        def attribute = request.JSON?.attribute
        def value = request.JSON?.value

        if (!deviceId) {
        	httpError(400, "deviceId parameter is required.")
        }

        def device = getDevice(deviceId)

        def action = CAPABILITY_MAP[capability].action

        "$action"(device, attribute, value)

        // log.debug request.JSON
        // def deviceId = params.id
        // def command = request.JSON?.command



        // def device = getDevice(deviceId)

        // if (!device) {
        // 	httpError(500, "Could not find device with id ${deviceId}")
        // }

        // try {
        //   switch (command) {
        //     case "setLevel":
        //       def level = request.JSON?.level
        //       device.on()
        //       device.setLevel(level)
        //       break
        //     case "setHue":
        //       def hue = request.JSON?.hue
        //       device.on()
        //       device.setHue(hue)
        //       break
        //     default:
        //       device."$command"()
        //   }
        // } catch (e) {
        // 	httpError(500, "Could not execute command '${command}' to device with id ${deviceId}")
        // }
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

    def toCamelCase(str) {
        def strWithNoSpaces = str.replaceAll("\\s","")
        return strWithNoSpaces[0].toLowerCase() + strWithNoSpaces.substring(1)
    }

    mappings {
        path("/devices") {
            action: [
                GET: "getDevicesList",
            ]
        }
        path("/device/:deviceId") {
            action: [
                PUT: "updateDevice"
            ]
        }
    }

    // device lookup tree
    @Field CAPABILITY_MAP = [
        "accelerationSensor": [
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
        "contactSensor": [
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
        "garageDoor": [
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
        "level": [
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
        "motionSensor": [
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
        "powerMeter": [
            name: "Power Meter",
            capability: "capability.powerMeter",
            attributes: [
                "power"
            ]
        ],
        "presenceSensor": [
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
        "shockSensor": [
            name: "Shock Sensor",
            capability: "capability.shockSensor",
            attributes: [
                "shock"
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
        "switch": [
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
        "temperatureMeasurement": [
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
        "waterSensor": [
            name: "Water Sensor",
            capability: "capability.waterSensor",
            attributes: [
                "water"
            ]
        ],
        "windowShade": [
            name: "Window Shade",
            capability: "capability.windowShade",
            attributes: [
                "windowShade"
            ],
            action: "actionOpenClosed"
        ],
        // "relaySwitch": [
        //     name: "Relay Switch",
        //     capability: "capability.relaySwitch",
        //     attributes: [
        //         "switch"
        //     ],
        //     action: "actionOnOff"
        // ],
        // "imageCapture": [
        //     name: "Image Capture",
        //     capability: "capability.imageCapture",
        //     attributes: [
        //         "image"
        //     ]
        // ],
        // "signalStrength": [
        //     name: "Signal Strength",
        //     capability: "capability.signalStrength",
        //     attributes: [
        //         "lqi",
        //         "rssi"
        //     ]
        // ],
        // TODO: custom
        // "stepSensor": [
        //     name: "Step Sensor",
        //     capability: "capability.stepSensor",
        //     attributes: [
        //         "steps",
        //         "goal"
        //     ]
        // ],
        // "threeAxis": [
        //     name: "Three Axis",
        //     capability: "capability.threeAxis",
        //     attributes: [
        //         "threeAxis"
        //     ]
        // ],
    ]

    def actionAlarm(device, attribute, value) {
        switch (value) {
            case "strobe":
                device.strobe()
            break
            case "siren":
                device.siren()
            break
            case "off":
                device.off()
            break
            case "both":
                device.both()
            break
        }
    }

    def actionColor(device, attribute, value) {
        switch (attribute) {
            case "hue":
                device.setHue(value as float)
            break
            case "saturation":
                device.setSaturation(value as float)
            break
            case "color":
                def values = value.split(',')
                def colormap = ["hue": values[0] as float, "saturation": values[1] as float]
                device.setColor(colormap)
            break
        }
    }

    def actionOpenClosed(device, attribute, value) {
        if (value == "open") {
            device.open()
        } else if (value == "closed") {
            device.close()
        }
    }

    def actionOnOff(device, attribute, value) {
        if (value == "off") {
            device.off()
        } else if (value == "on") {
            device.on()
        }
    }

    def actionActiveInactive(device, attribute, value) {
        if (value == "active") {
            device.active()
        } else if (value == "inactive") {
            device.inactive()
        }
    }

    def actionThermostat(device, attribute, value) {
        switch(attribute) {
            case "heatingSetpoint":
                device.setHeatingSetpoint(value)
            break
            case "coolingSetpoint":
                device.setCoolingSetpoint(value)
            break
            case "thermostatMode":
                device.setThermostatMode(value)
            break
            case "thermostatFanMode":
                device.setThermostatFanMode(value)
            break
        }
    }

    def actionMusicPlayer(device, attribute, value) {
        switch(attribute) {
            case "level":
                device.setLevel(value)
            break
            case "mute":
                if (value == "muted") {
                    device.mute()
                } else if (value == "unmuted") {
                    device.unmute()
                }
            break
            case "status":
                if (device.getSupportedCommands().any {it.name == "setStatus"}) {
                    device.setStatus(value)
                }
            break
        }
    }

    def actionColorTemperature(device, attribute, value) {
        device.setColorTemperature(value as int)
    }

    def actionLevel(device, attribute, value) {
        device.setLevel(value as int)
    }

    def actionPresence(device, attribute, value) {
        if (value == "present") {
            device.arrived();
        }
        else if (value == "not present") {
            device.departed();
        }
    }

    def actionConsumable(device, attribute, value) {
        device.setConsumableStatus(value)
    }

    def actionLock(device, attribute, value) {
        if (value == "locked") {
            device.lock()
        } else if (value == "unlocked") {
            device.unlock()
        }
    }

    def actionCoolingThermostat(device, attribute, value) {
        device.setCoolingSetpoint(value)
    }

    def actionThermostatFan(device, attribute, value) {
        device.setThermostatFanMode(value)
    }

    def actionHeatingThermostat(device, attribute, value) {
        device.setHeatingSetpoint(value)
    }

    def actionThermostatMode(device, attribute, value) {
        device.setThermostatMode(value)
    }

    def actionTimedSession(device, attribute, value) {
        if (attribute == "timeRemaining") {
            device.setTimeRemaining(value)
        }
    }