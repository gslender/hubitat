import groovy.transform.Field
/**
 * =======================================================================================
 *  Nue Zigbee Light Fan Controller
 *  Copyright 2021 Grant Slender
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
 * =======================================================================================
 *
 *  Last modified: 2021-11-20
 *
 */

@Field static final List supportedFanSpeeds = ["low", "medium", "high", "off"]

metadata {
    definition (name: "Nue Zigbee Light Fan Controller", namespace: "gslender", author: "Grant Slender", importUrl: "https://raw.githubusercontent.com/gslender/hubitat/main/nue-zigbee-light-fan-controller.groovy") {
        capability "Light"
        capability "FanControl"
        capability "Refresh"
        capability "Configuration"

        command "setSpeed", [[name: "Fan speed*", type: "ENUM", description: "Fan speed to set", constraints: supportedFanSpeeds]]

        attribute "fan", "STRING"
        attribute "switch", "STRING"

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0006, 0008, 0000", manufacturer: "3A Smart Home DE", model: "LXX60-FN27LX1.0", deviceJoinName: "Nue Fan Light Switch"
    }

    preferences {
        input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}


/* ======== driver built-in callback methods ======== */
/* https://docs.hubitat.com/index.php?title=Device_Code */

void installed() {
    log.info "installed..."
    device.updateSetting("enableDebug",[type:"bool", value: true])
    device.updateSetting("enableDesc",[type:"bool", value: true])
    initialize()
}

void uninstalled() {
    log.info "uninstalled..."
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${enableDebug == true}"
    log.warn "description logging is: ${enableDesc == true}"
}

void initialize() {
    log.info "initialize..."
    device.setName("NUE-FAN-LIGHT")
    updated();
    configure()
    unschedule()
    if (enableDebug) runIn(1800,logsOff)
}

void parse(String description) {
    if (enableDebug) log.debug "parse description: ${description}"

    //  send event for heartbeat
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)

    def event

    if (description.startsWith("catchall")) {
        def matches = description =~ /^catchall: 0104 0006 (\d+) \d+ \d+ \d+ [\dA-F]+ \d+ \d+ \d+ ([\dA-F]+) \d+ (\d+)$/

        if (matches.size() == 1) {
            def match = matches[0]

            if (match[2] == "0B") {
                switch (match[1]) {
                    case "01":
                        event = createEvent(name: "switch", value: match[3] == "0100" ? "on" : "off")
                        break

                    case "02":
                        event = createEvent(name: "fan", value: match[3] == "0100" ? "high" : "off")
                        break

                    case "03":
                        event = createEvent(name: "fan", value: match[3] == "0100" ? "medium" : "off")
                        break

                    case "04":
                        event = createEvent(name: "fan", value: match[3] == "0100" ? "low" : "off")
                        break
                }
            }
        }
    }

    if (event) {
        if (enableDesc) log.info "${device.displayName} ${event.name} was set to ${event.value}"

        sendEvent(event)
        if (enableDebug) log.debug "sendEvent( ${event} )"

        if (event.name == "switch") {
            sendChildEvent("${device.deviceNetworkId}-Light", "switch", event.value)
        } else if (event.name == "fan") {
            sendChildEvent("${device.deviceNetworkId}-Fan", "speed", event.value)
        }
    }
}

/* ======== capability commands ======== */

def refresh() {
    if (enableDebug) log.debug "refresh()"

    return zigbee.onOffRefresh() + zigbee.readAttribute(0x0006, 0x0000)
}


def List<String> configure() {
    if (enableDebug) log.debug "configure()"

    // clean stuff up !!
    state.clear()

    getChildDevices().each {
        if (enableDebug) log.debug "deleteChildDevice ${it.deviceNetworkId}"
        deleteChildDevice("${it.deviceNetworkId}")
    }

    addChildType("Light", "Light")
    addChildType("Fan", "Fan")
    state.comments = "Child devices created!"
    return zigbee.configureReporting(0x0006, 0x0000, 0x10, 1, 600, null)
}

/* ======== custom commands and methods ======== */

def addChildType(String label, String type) {
    if (enableDebug) log.debug "addChildType(${label},${type})"
    def devLabel = device.label
    if (devLabel == null) devLabel = device.name

    switch (type) {
        case "Light":
            addChildDevice("hubitat","Generic Component Switch", "${device.deviceNetworkId}-${label}", [label: "${devLabel}-${label}",name: "${device.name}-${label}", isComponent: true])
            break

        case "Fan":
            addChildDevice("hubitat","Generic Component Fan Control", "${device.deviceNetworkId}-${label}", [label: "${devLabel}-${label}",name: "${device.name}-${label}", isComponent: true])
            break

        default :
             log.warn "addChildType incorrect type:${type}"
             break
        }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("enableDebug", [value: "false", type: "bool"])
}

def sendChildEvent(_childname, _attrib, _value) {
    if (enableDebug) log.debug "sendChildEvent(${_childname},${_attrib},${_value})"
    def child = getChildDevice(_childname)
    if (child) {
        child.sendEvent(name: _attrib, value: _value)
    } else {
        log.warn "no child found ?"
    }
}

def lightOn() {
    if (enableDebug) log.debug "lightOn()"
    sendChildEvent("${device.deviceNetworkId}-Light", "switch", "on")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x1 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def lightOff() {
    if (enableDebug) log.debug "lightOff()"
    sendChildEvent("${device.deviceNetworkId}-Light", "switch", "off")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x1 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanOff() {
    if (enableDebug) log.debug "fanOff()"
    sendChildEvent("${device.deviceNetworkId}-Fan", "speed", "off")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x04 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanHighOn() {
    if (enableDebug) log.debug "fanHighOn()"
    sendChildEvent("${device.deviceNetworkId}-Fan", "speed", "high")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanMediumOn() {
    if (enableDebug) log.debug "fanMediumOn()"
    sendChildEvent("${device.deviceNetworkId}-Fan", "speed", "medium")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanLowOn() {
    if (enableDebug) log.debug "fanLowOn()"
    sendChildEvent("${device.deviceNetworkId}-Fan", "speed", "low")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x04 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}


/* ======== child device methods ======== */

void componentRefresh(cd) {
    if (enableDebug) log.debug "componentRefresh ${cd.displayName}"
}

void on() {
    if (enableDebug) log.debug "on"

    lightOn()
}

void componentOn(cd) {
    if (enableDebug) log.debug "componentOn (${cd.displayName})"

    if (cd.displayName.endsWith("-Light")) lightOn()
    else fanMedium()
}

void off() {
    if (enableDebug) log.debug "off"

    lightOff()
}

void componentOff(cd) {
    if (enableDebug) log.debug "componentOff (${cd.displayName})"

    if (cd.displayName.endsWith("-Light")) lightOff()
    else fanOff()
}

String setSpeed(speed) {
    if (enableDebug) log.debug "setSpeed (${speed})"

    switch (speed) {
        case "off":
            fanOff()
            break
        case "low":
            fanLowOn()
            break
        case "medium-low":
        case "medium":
        case "medium-high":
        case "on":
            fanMediumOn()
            break
        case "high":
            fanHighOn()
            break
    }
}

String componentSetSpeed(cd, speed) {
    if (enableDebug) log.debug "componentSetSpeed (${cd.displayName}, ${speed})"

    setSpeed(speed)
}

String cycleSpeed() {
    if (enableDebug) log.debug "cycleSpeed"

    String currentSpeed = device.currentValue("fan") ?: "off"

    switch (currentSpeed) {
       case "off":
          return fanLowOn()
       break
       case "low":
          return fanMediumOn()
       break
       case "medium":
          return fanHighOn()
       break
       case "high":
          return fanOff()
       break
    }
}

String componentCycleSpeed(cd) {
    if (enableDebug) log.debug "componentCycleSpeed (${cd.displayName})"

    cycleSpeed()
}

String componentSetLevel(cd, level) {
    if (enableDebug) log.debug "componentSetLevel (${cd.displayName}, ${level})"

    if (level < 1) fanOff()
    if (level > 0 && level < 34) fanLowOn()
    if (level > 33 && level < 67) fanMediumOn()
    if (level > 66) fanHighOn()
}
