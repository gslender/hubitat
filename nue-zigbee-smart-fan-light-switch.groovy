/**
 * =======================================================================================
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
 *  Last modified: 2021-05-09
 *
 */ 
 
metadata {
   definition (name: "Nue ZigBee Smart Fan Light Switch", namespace: "gslender", author: "Grant Slender", importUrl: "https://raw.githubusercontent.com/gslender/hubitat/main/nue-zigbee-smart-fan-light-switch.groovy") {
      
      capability "Refresh"
      capability "Configuration"
       
      command "lightOn"
      command "lightOff"
      command "fanLowOn"
      command "fanMedOn"
      command "fanHighOn"
      command "fanOff"
      attribute "fan", "STRING"
      attribute "light", "STRING"
      attribute "lastFan", "STRING"

      fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,1000", outClusters: "1000", manufacturer: "3A Smart Home DE", model: "LXN56-1S27LX1.2", deviceJoinName: "Nue Smart Fan Light Switch"
       
   }
      
preferences {
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}
/*
private getCLUSTER_LIGHT_SWITCH() { 0x0001 }
private getCLUSTER_FAN_HIGH_SWITCH() { 0x0002 }
private getCLUSTER_FAN_MED_SWITCH() { 0x0003 }
private getCLUSTER_FAN_LOW_SWITCH() { 0x0004 }
private getCOMMAND_OFF() { 0x00 }
private getCOMMAND_ON() { 0x01 }
*/
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
   initialize()
}

void initialize() {
   if (enableDebug) log.debug "Initializing"    
   log.warn "debug logging is: ${enableDebug == true}"
   log.warn "description logging is: ${enableDesc == true}"
   device.setName("NUE-ZBFLB")
   configure()
   unschedule()
//    if (enableDebug) runIn(1800,logsOff)
}

void parse(String description) {
    if (enableDebug) log.debug "parse description: ${description}"
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    //  send event for heartbeat    
    sendEvent(name: "lastCheckin", value: now)
    if (description.startsWith("catchall")) return
    
    def descMap = zigbee.parseDescriptionAsMap(description) 
//    log.debug descMap
    def ssMap = zigbee.parse(description)
    def descriptionText
    def rawValue = Integer.parseInt(descMap.value,16)
    def value = rawValue == 1 ? "on" : "off"
    def name = "switch"
    
    //log.debug "descMap =: ${descMap}"
    //log.debug "ssMap =: ${ssMap}"
    
    if (ssMap.sourceEndpoint == 0x01) {
        name = "light"
        getChildDeviceParse("${device.deviceNetworkId}-Light","switch",value)
    } else {
        name = "fan"
        getChildDeviceParse("${device.deviceNetworkId}-Fan","switch",value) 
        if (ssMap.sourceEndpoint == 0x02) {    
            value = "high"       
        }
        if (ssMap.sourceEndpoint == 0x03) {
            value = "med"
        }
        if (ssMap.sourceEndpoint == 0x04) {  
            value = "low"
        }
        if (rawValue == 0) value = "off"
        
        sendEvent(name: "lastFan", value: "${device.currentValue("fan",true)}")
    }
    
    descriptionText = "${device.displayName} was turned ${value}"         
    if (enableDesc) log.info "${descriptionText}"
    sendEvent(name:name,value:value,descriptionText:descriptionText)
    
}

/* ======== capability commands ======== */

def refresh() {
   if (enableDebug) log.debug "refresh()"
    
   [
       "he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0", "delay 20", // light
       "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0", "delay 20", // fan-high
       "he rattr 0x${device.deviceNetworkId} 0x03 0x0006 0x0", "delay 20", // fan-med
       "he rattr 0x${device.deviceNetworkId} 0x04 0x0006 0x0", "delay 20", // fan-low
   ]
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
    return
}

/* ======== custom commands and methods ======== */

def addChildType(String label, String type) {
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

def getChildDeviceParse(child,_name,_value) {
    if (enableDebug) log.debug "getChildDeviceParse(${child},${_name},${_value})"  
    def cd = getChildDevice(child)
    if (cd) {
        cd.parse([[name:_name, value:_value, descriptionText:"${cd.displayName} was turned ${_value}"]])
    } else log.warn "no child found ?"
}

def lightOn() {
    if (enableDebug) log.debug "lightOn()"
    sendEvent(name: "light", value: "on")
    getChildDeviceParse("${device.deviceNetworkId}-Light","switch","on")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def lightOff() {
    if (enableDebug) log.debug "lightOff()"
    sendEvent(name: "light", value: "off")
    getChildDeviceParse("${device.deviceNetworkId}-Light","switch","off")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanOff(){
    if (enableDebug) log.debug "fanOff()"  
    sendEvent(name: "fan", value: "off")
    
    getChildDeviceParse("${device.deviceNetworkId}-Fan","fan","off")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x04 0x0006 0x0 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanHighOn(){
    if (enableDebug) log.debug "fanHighOn()"  
    sendEvent(name: "fan", value: "high")
    getChildDeviceParse("${device.deviceNetworkId}-Fan","fan","high")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanMedOn(){
    if (enableDebug) log.debug "fanMedOn()"
    sendEvent(name: "fan", value: "med")
    getChildDeviceParse("${device.deviceNetworkId}-Fan","fan","med")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x03 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}

def fanLowOn(){
    if (enableDebug) log.debug "fanLowOn()"
    sendEvent(name: "fan", value: "low")
    getChildDeviceParse("${device.deviceNetworkId}-Fan","fan","low")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x04 0x0006 0x1 {}", hubitat.device.Protocol.ZIGBEE))
}


/* ======== child device methods ======== */

void componentRefresh(cd){
    if (enableDebug) log.debug "componentRefresh ${cd.displayName}"
}

void componentOn(cd){
    if (enableDebug) log.debug "componentOn ${cd.displayName}"
    
    if (cd.displayName.endsWith("-Light")) lightOn()
    if (cd.displayName.endsWith("-Fan")) fanHighOn()
    if (cd.displayName.endsWith("-Fan")) fanMedOn()
    if (cd.displayName.endsWith("-Fan")) fanLowOn()
}

void componentOff(cd){
    if (enableDebug) log.debug "componentOff ${cd.displayName}"
    
    if (cd.displayName.endsWith("-Light")) lightOff()
    else fanOff()
}

void componentSetSpeed(cd, speed){
    if (enableDebug) log.debug "componentSetSpeed ${cd.displayName} ${speed}"
    
    if (speed == "off") fanOff()
}


void componentSetLevel(cd, level){
    if (enableDebug) log.debug "componentSetLevel ${cd.displayName} ${level}"
    
    if (level < 1) fanOff()
    if (level > 0 && level < 34) fanLowOn()
    if (level > 33 && level < 67) fanMedOn()
    if (level > 66) fanHighOn()
}


def doSendEvent(Map eventData, Boolean forceStateChange=false) {
   if (enableDebug) log.debug("doSendEvent(${eventData}...")
   String descriptionText = "${device.displayName} ${eventData.name} is ${eventData.value}${eventData.unit ?: ''}"
   if (enableDesc && (device.currentValue(eventData.name) != eventData.value || eventData.isStateChange)) log.info(descriptionText)
   Map eventProperties = [name: eventData.name, value: eventData.value, descriptionText: descriptionText,
      unit: eventData.unit, phyiscal: eventData.physical, digital: eventData.digital,
      isStateChange: eventData.isStateChange]
   if (forceStateChange) eventProperties["isStateChange"] = true
   sendEvent(eventProperties)
}
