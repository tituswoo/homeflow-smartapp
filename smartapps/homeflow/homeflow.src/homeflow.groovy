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
    name: "homeflow",
    namespace: "homeflow",
    author: "Titus Woo",
    description: "Simpler automation",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
    page(name: "deviceSelectionPage", title: "Device Selection", nextPage: "authorizationPage", uninstall: true) {
        section("Control these devices...") {
            input "switches", "capability.switch", title: "Lights", multiple: true, required: false
            input "dimmers", "capability.switchLevel", title: "Dimmers", multiple: true, required: false
            input "presence", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: false
            input "contacts", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: false
            input "motion", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
        }
    }

    page(name: "authorizationPage", title: "Connect your SmartThings account", install: true, uninstall: true)
}

def authorizationPage() {
    def accountCode = authAccessToken()
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
	unsubscribe()
	initialize()
}

def initialize() {
    subscribeToAll()
}

def authAccessToken() {
    if (!state.accessToken) {
        state.accessToken = createAccessToken()
    }
    def json = new groovy.json.JsonBuilder([
        accessToken: state.accessToken,
        hubId: location.hubs[0].id
    ])
    def params = [
        uri: "http://homeflow.io:3001/api/auth/smartthings/token",
        body: json
    ]

    try {
        httpPostJson(params) { resp -> 
            log.info "here 1"
            return resp.data.accountCode
        }
    } catch (e) {
      log.error "authAccessToken: $e"
    }
}

def subscribeToAll() {
    subscribe(lights, "switch.on", eventHandler)
	subscribe(lights, "switch.off", eventHandler)
    subscribe(dimmers, "level", eventHandler)
    subscribe(dimmers, "switch", eventHandler)
    subscribe(contacts, "contact", eventHandler)
    subscribe(presence, "presence", eventHandler)
    subscribe(motion, "motion", eventHandler)
    subscribe(power, "power", eventHandler)
}

def getAllDevices() {
	def devices = []
    for (String key : settings.keySet()) {
    	settings["$key"].each { device ->
            devices << device
        }
    }

	return devices
}

def getAllDevicesAndMassage() {
  def devices = getAllDevices()
  def devicesList = []

  devices.each { device ->

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

def eventHandlerHandler () {
    def devices = getAllDevicesAndMassage()
    def json = groovy.json.JsonOutput.toJson(devices)
    def data = [
        devices: json,
        hubId: location.hubs[0].id
    ]

    def params = [
      uri: "http://homeflow.io:3001/api/smartthings/update",
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

def getDevice(id) {
	def devices = getAllDevices()
    def device = devices.find { it.id == id }
    if (!device) {
    	return false
    }
    return device
}

def eventHandler(event) {
    eventHandlerHandler()
}

def getDevicesList() {
    def devices = getAllDevicesAndMassage()
    def json = groovy.json.JsonOutput.toJson(devices)
    render data: json, status: 200
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
