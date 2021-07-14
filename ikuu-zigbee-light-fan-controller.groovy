import groovy.transform.Field
/**
 * =======================================================================================
 *  Ikuu Zigbee Light Fan Controller
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
 *  Last modified: 2021-07-14
 *
 */ 

@Field static final List supportedFanSpeeds = ["low", "medium", "high", "off"]
 
metadata {
      definition (name: "Ikuu Zigbee Light Fan Controller", namespace: "gslender", author: "Grant Slender", importUrl: "https://raw.githubusercontent.com/gslender/hubitat/main/ikuu-zigbee-light-fan-controller.groovy") {

      capability "Refresh"
      capability "Configuration"
       
      command "lightOn"
      command "lightOff"
      command "fanLowOn"
      command "fanMediumOn"
      command "fanHighOn"
      command "fanOff"
      attribute "fan", "STRING"
      attribute "light", "STRING"

  	  fingerprint profileId: "0104", inClusters: "0000, 0004, 0005, 0006, 0003, 0202, EF00", outClusters: "0019, 000A", manufacturer: "_TZ3210_lzqq3u4r", model: "TS0501", deviceJoinName: "Ikuu Zigbee Light Fan Switch"
       
   }
      
preferences {
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}


/* ======== driver built-in callback methods ======== */
/* https://docs.hubitat.com/index.php?title=Device_Code */

void installed(){
    log.info "installed..."
    device.updateSetting("enableDebug",[type:"bool", value: true])
    device.updateSetting("enableDesc",[type:"bool", value: true])
    initialize()
}

void uninstalled(){
    log.info "uninstalled..."
}

void updated() { 
    log.info "updated..."
    log.warn "debug logging is: ${enableDebug == true}"
    log.warn "description logging is: ${enableDesc == true}"
}

void initialize() {   
    log.info "initialize..."    
    device.setName("IKUU-FAN-LIGHT")
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
    if (description.startsWith("catchall")) return

    def event = zigbee.getEvent(description)
    if (event) {
      event.name = "light"
      sendEvent(event)
      if (enableDebug) log.debug "sendEvent( ${event} )"
      getChildDeviceParse("${device.deviceNetworkId}-Light","switch",event.value)
    } else {
    	if (description?.startsWith("read attr -")) {
    		def descMap = zigbee.parseDescriptionAsMap(description)
    		if (descMap.cluster == "0202" && descMap.attrId == "0000") {
    			event.name = "fan"
                switch (descMap.value.toInteger()) {
                    case 1:
                    event.value = "low"
                    break
                    
                    case 2:
                    event.value = "medium"
                    break
                    
                    case 3:
                    case 4:
                    event.value = "high"
                    break
       
                    case 0:
                    default :
                    event.value = "off"                
                }
                sendEvent(event)
                if (enableDebug) log.debug "sendEvent( ${event} )"
                
                runInMillis(100, 'calcChildFanSpeed')
    		}
    	}
    }
    
    if (event) {
        if (enableDesc) log.info "${device.displayName} ${event.name} was set to ${event.value}"
    }
    
}

/* ======== capability commands ======== */

def refresh() {
    if (enableDebug) log.debug "refresh()"
   
    return zigbee.onOffRefresh() + zigbee.readAttribute(0x0202, 0x0000)
}


def List<String> configure() {
    if (enableDebug) log.debug "configure()"
    
    // clean stuff up !!
    state.clear()
    
    getChildDevices().each { 
        if (enableDebug) log.debug "deleteChildDevice ${it.deviceNetworkId}"
        deleteChildDevice("${it.deviceNetworkId}") 
    }
    
    addChildType("Light","Switch")
    addChildType("Fan","Fan")
    state.comments = "Child devices created!" 
    return zigbee.configureReporting(0x0006, 0x0000, 0x10, 1, 600, null) +
	zigbee.configureReporting(0x0202, 0x0000, 0x30, 1, 600, null)
}

/* ======== custom commands and methods ======== */

def calcChildFanSpeed() {
    if (enableDebug) log.debug "calcChildFanSpeed()"
    
    def fanspeed = "off"
    def fanswitch = "off"
    
    
    if (device.currentValue("fan") == "high") {
        fanspeed = "high"
        fanswitch = "on"
    }
    if (device.currentValue("fan") == "medium") {
        fanspeed = "medium"
        fanswitch = "on"
    }
    if (device.currentValue("fan") == "low") {
        fanspeed = "low"
        fanswitch = "on"
    }
    getChildDeviceParse("${device.deviceNetworkId}-Fan","speed",fanspeed) 
    getChildDeviceParse("${device.deviceNetworkId}-Fan","switch",fanswitch) 
    getChildDeviceParse("${device.deviceNetworkId}-Fan","supportedFanSpeeds",["low", "medium", "high", "off"])
}

def getChildDeviceParse(_childname,_attrib,_value) {
    if (enableDebug) log.debug "getChildDeviceParse(${_childname},${_attrib},${_value})"  
    def child = getChildDevice(_childname)
    if (child) {
        child.sendEvent(name:_attrib, value:_value)
    } else log.warn "no child found ?"
}

def addChildType(String label, String type) {
    if (enableDebug) log.debug "addChildType(${label},${type})" 
    def devLabel = device.label
    if (devLabel == null) devLabel = device.name
    
    switch (type) {
        case "Switch":
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

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("enableDebug",[value:"false",type:"bool"])
}

def lightOn() {
    if (enableDebug) log.debug "lightOn()"
    getChildDeviceParse("${device.deviceNetworkId}-Light","switch","on")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x1 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def lightOff() {
    if (enableDebug) log.debug "lightOff()"
    getChildDeviceParse("${device.deviceNetworkId}-Light","switch","off")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x1 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanOff(){
    if (enableDebug) log.debug "fanOff()"      
    getChildDeviceParse("${device.deviceNetworkId}-Fan","speed","off")
    sendHubCommand(new hubitat.device.HubAction("he wattr 0x${device.deviceNetworkId} 0x01 0x0202 0x0000 0x30 {00}", hubitat.device.Protocol.ZIGBEE))
}

def fanHighOn(){
    if (enableDebug) log.debug "fanHighOn()"  
    getChildDeviceParse("${device.deviceNetworkId}-Fan","speed","high")
    sendHubCommand(new hubitat.device.HubAction("he wattr 0x${device.deviceNetworkId} 0x01 0x0202 0x0000 0x30 {03}", hubitat.device.Protocol.ZIGBEE))   
}

def fanMediumOn(){
    if (enableDebug) log.debug "fanMediumOn()"
    getChildDeviceParse("${device.deviceNetworkId}-Fan","speed","medium")
    sendHubCommand(new hubitat.device.HubAction("he wattr 0x${device.deviceNetworkId} 0x01 0x0202 0x0000 0x30 {02}", hubitat.device.Protocol.ZIGBEE))
}

def fanLowOn(){
    if (enableDebug) log.debug "fanLowOn()"
    getChildDeviceParse("${device.deviceNetworkId}-Fan","speed","low")
    sendHubCommand(new hubitat.device.HubAction("he wattr 0x${device.deviceNetworkId} 0x01 0x0202 0x0000 0x30 {01}", hubitat.device.Protocol.ZIGBEE))
}


/* ======== child device methods ======== */

void componentRefresh(cd){
    if (enableDebug) log.debug "componentRefresh ${cd.displayName}"
}

void componentOn(cd){
    if (enableDebug) log.debug "componentOn ${cd.displayName}"
    
    if (cd.displayName.endsWith("-Light")) lightOn()
}

void componentOff(cd){
    if (enableDebug) log.debug "componentOff ${cd.displayName}"
    
    if (cd.displayName.endsWith("-Light")) lightOff()
    else fanOff()
}

String componentSetSpeed(cd, speed){
    if (enableDebug) log.debug "componentSetSpeed (${cd.displayName}, ${speed})"
    
    switch (speed) {
       case "off":
          fanOff()
       break
       case "on":
          if (cd && cd.currentValue("speed") != "off") break
       case "low":
          fanLowOn()
       break
       case "medium-low":
       case "medium":
       case "medium-high":
          fanMediumOn()
       break
       case "high":
          fanHighOn()
       break
    }
}

String componentCycleSpeed(cd){
    if (enableDebug) log.debug "componentCycleSpeed (${cd.displayName})"
    
    String currentSpeed = "off"
    
    if (cd) currentSpeed = cd.currentValue("speed") ?: "off"
    
    switch (currentSpeed) {
       case "off":
          return fanLowOn()
       break
       case "low":
          return fanMediumOn()
       break
       case "medium-low":
       case "medium":
       case "medium-high":
          return fanHighOn()
       break
       case "high":
          return fanOff()
       break
    }
}

String componentSetLevel(cd, level){
    if (enableDebug) log.debug "componentSetLevel (${cd.displayName}, ${level})"
    
    if (level < 1) fanOff()
    if (level > 0 && level < 34) fanLowOn()
    if (level > 33 && level < 67) fanMediumOn()
    if (level > 66) fanHighOn()
}
