import groovy.transform.Field
import groovy.json.JsonOutput

/**
 * =======================================================================================
 *  iZone Universal Parent
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
 *  Last modified: 2021-06-26
 *
 */ 

@Field Map getFanLevel = ["low": 25 ,"medium": 50,"high": 100]      
@Field Map getZoneModeLevel = ["open": 1 ,"close": 2,"auto": 3,"override": 4,"constant": 5]
@Field Map getModeLevel = ["cool": 1 ,"heat": 2,"vent": 3,"dry": 4,"auto": 5,"exhuast": 6,"pump": 7]


metadata {
    definition (name: "iZone Universal Parent", namespace: "gslender", author: "Grant Slender", importUrl: "https://raw.githubusercontent.com/gslender/hubitat/main/izone-universal-parent.groovy") {

        capability "Refresh"
        capability "Configuration"
        capability "Switch"
        capability "FanControl"
        capability "Thermostat"
          
        attribute "firmware", "STRING"
        attribute "switch", "STRING"
        attribute "speed", "STRING"
        attribute "temperature", "NUMBER"
        attribute "thermostatSetpoint", "NUMBER"
        attribute "thermostatMode", "STRING"
        attribute "ctrlZone", "STRING"
        attribute "supportedFanSpeeds", "JSON_OBJECT"
        attribute "supportedThermostatFanModes", "JSON_OBJECT"
        attribute "supportedThermostatModes", "JSON_OBJECT"
        
        command "setSpeed", [[name: "Fan speed*",type:"ENUM", description:"Fan speed to set", constraints: getFanLevel.collect {k,v -> k}]]
        
        command "setThermostatMode", [[name: "AC mode*",type:"ENUM", description:"Cooling/heating mode to set", constraints: getModeLevel.collect {k,v -> k}]]
        
        command "devSendCmd"
    }
    
    preferences {
        def refreshEnum = [:]
		    refreshEnum << ["1 min" : "Refresh every minute"]
		    refreshEnum << ["5 min" : "Refresh every 5 minutes"]
		    refreshEnum << ["15 min" : "Refresh every 15 minutes"]
		    refreshEnum << ["30 min" : "Refresh every 30 minutes"]
        input name: "removeChildren", type: "bool", title: "Remove children on Configure", defaultValue: true
        input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "ip", type: "string", title:"Bridge IP", defaultValue:"192.168.1.50" , required: true
        input name: "refresh_Rate", type: "enum", title: "Polling Refresh Rate", options:refreshEnum, defaultValue: "1 min"
        
        input name: "devcmdbody", type: "string", title:"DEV Only Send Cmd", defaultValue:"{\"SysOn\":1}" 
    }
}

@Field static boolean enableRespDebug = false

/* ======== driver built-in callback methods ======== */
/* https://docs.hubitat.com/index.php?title=Device_Code */

void installed(){
   log.info "installed..."
   device.updateSetting("removeChildren",[type:"bool", value: true])
   device.updateSetting("enableDebug",[type:"bool", value: true])
   device.updateSetting("enableDesc",[type:"bool", value: true])
   device.updateSetting("refresh_Rate",[type:"enum", value: "1 min"])
   initialize()
}

void uninstalled(){
   log.info "uninstalled..."
   unschedule()
}

void updated() {
    log.info "updated..."
    unschedule()
    log.warn "debug logging is: ${enableDebug == true}"
    log.warn "description logging is: ${enableDesc == true}"
    switch(refresh_Rate) {
		case "1 min" :
			runEvery1Minute(refresh)
			break
		case "5 min" :
			runEvery5Minutes(refresh)
			break
		case "15 min" :
			runEvery15Minutes(refresh)
			break
		default:
			runEvery30Minutes(refresh)
	}
	if (enableDebug) log.debug "runEvery${refresh_Rate}Minute(s) refresh() "
    
    
   //if (enableDebug) runIn(1800,logsOff)
}

void initialize() {
   log.info "initialize..." 
   updated()
}

void parse(String description) {
    if (enableDebug) log.debug "parse() description: ${description}"
}

/* ======== capability commands ======== */

def devSendCmd() {
    if (enableDebug) log.debug "devSendCmd() devcmdbody: $devcmdbody"
    sendSimpleiZoneCmd(devcmdbody) 
    
    runInMillis(500, 'refresh')
}

def off() {
    if (enableDebug) log.debug "off()"
    sendSimpleiZoneCmd("SysOn",0) // - is the on/off setting, 0 = stop air con, 1 = run air con
    
    runInMillis(500, 'refresh')
}

def on() {
    if (enableDebug) log.debug "on()"
    sendSimpleiZoneCmd("SysOn",1) // - is the on/off setting, 0 = stop air con, 1 = run air con
    
    runInMillis(500, 'refresh')
}

def setSpeed(_speed) {
    if (enableDebug) log.debug "setSpeed() speed: $_speed"
    
    def speed = 0
    
    switch (_speed) {
       case "off":
       case "low":
          speed = 1
       break
       case "medium-low":
       case "medium":
       case "medium-high":
          speed = 2
       break
       case "high":
       case "on":
       case "auto":
          speed = 3
       break
    }
    
    sendSimpleiZoneCmd("SysFan",speed) // - is the new fan speed 1-4
    
    runInMillis(500, 'refresh')
}

def cycleSpeed() {
    if (enableDebug) log.debug "cycleSpeed()"
    
    String currentSpeed = device.currentValue("speed") ?: "off"
    
    switch (currentSpeed) {
       case "low":
           sendSimpleiZoneCmd("SysFan",2)
       break
       case "medium-low":
       case "medium":
       case "medium-high":
           sendSimpleiZoneCmd("SysFan",3)
       break
       case "high":
       case "off":
       case "on":
       case "auto":
           sendSimpleiZoneCmd("SysFan",1)
       break
    }
}

def auto() {
    if (enableDebug) log.debug "auto()"
    
    setThermostatMode("auto") 
}

def cool() {
    if (enableDebug) log.debug "cool()"
    
    setThermostatMode("cool") 
}

def emergencyHeat() {
    log.warn "emergencyHeat() - not supported"
}

def fanAuto() {
    log.warn "fanAuto() - not supported"
}

def fanCirculate() {
    log.warn "fanCirculate() - not supported"
}

def fanOn() {
    log.warn "fanCirculate() - not supported"
}

def heat() {
    if (enableDebug) log.debug "heat()"
    
    setThermostatMode("heat") 
}

def setCoolingSetpoint(temperature) {
    if (enableDebug) log.debug "setCoolingSetpoint()"
    setSetpoint(temperature)
}

def setHeatingSetpoint(temperature) {
    if (enableDebug) log.debug "setHeatingSetpoint()"
    setSetpoint(temperature)
}

private setSetpoint(temperature) {
    if (state.sysinfo.RAS != 3) {         
        sendSimpleiZoneCmd("SysSetpoint",temperature*100) 
    
        runInMillis(500, 'refresh')
    } else {
        log.warn "setSetpoint($temperature) - not available when RAS=Zones"
    }    
}

def setSchedule(json) {
    log.warn "setSchedule() - not supported"
}

def setThermostatFanMode(fanmode) {
    if (enableDebug) log.debug "setThermostatFanMode()"
    setSpeed(fanmode) 
}

def setThermostatMode(thermostatmode) {
    if (enableDebug) log.debug "setMode() mode: $thermostatmode"
    
    def mode = getModeLevel[thermostatmode]    
    sendSimpleiZoneCmd("SysMode",mode) 
    
    runInMillis(500, 'refresh')
}

def refresh() {
    if (enableDebug) log.debug "refresh()"
    
    // connect and getSystemInfo 
    def result = getSystemInfo()
    
    if (result.status == "ok") {
        def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
        sendEvent(name: "lastCheckin", value: now)
        state.sysinfo = result.SystemV2
        sendiZoneEvents()
        
        // getZonesInfo
        result.SystemV2.NoOfZones.times {
            def resultZone = getZonesInfo(it)
            if (resultZone.status == "ok") {    
                def zonename = "zone${it+1}"
                if (state.zones[zonename]) state.zones[zonename] = resultZone.ZonesV2
                def hubitatThermoAttrib = convertiZoneToHubitat(resultZone.ZonesV2)
                getChildDeviceParse("${device.getDeviceNetworkId()}-$zonename",hubitatThermoAttrib)
            }  
        }
        
        //getFirmwareList
        def resultFmw = getFirmwareList()
        if (resultFmw.status == "ok") {
            sendEvent(name: "firmware", value: resultFmw.Fmw)
        }    
        
    } else {
        sendEvent(name: "lastCheckin", value: "unknown")
        sendEvent(name: "help", value: "check IP is correct")
    }
    sendEvent(name: "status", value: result.status)
}

def List<String> configure() {
    if (enableDebug) log.debug "configure()"
    
    // clean stuff up !!
    state.clear() 
    state.sysinfo = [:]
    state.zones = [:]
    if (removeChildren) {
        getChildDevices().each { 
            if (enableDebug) log.debug "deleteChildDevice ${it.deviceNetworkId}"
            deleteChildDevice("${it.deviceNetworkId}") 
        }
    }
    
    // connect and getSystemInfo 
    def result = getSystemInfo()
    
    if (result.status == "ok") {
        def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
        sendEvent(name: "lastCheckin", value: now)
        state.sysinfo = result.SystemV2
        sendiZoneEvents()
        
        // getZonesInfo
        result.SystemV2.NoOfZones.times {
            def resultZone = getZonesInfo(it)
            if (resultZone.status == "ok") {
                def zonename = "zone${it+1}"
                addChildThermostat(zonename,resultZone.ZonesV2.Name)
                state.zones.put(zonename, resultZone.ZonesV2)
                def hubitatThermoAttrib = convertiZoneToHubitat(resultZone.ZonesV2)
                getChildDeviceParse("${device.getDeviceNetworkId()}-zone${it+1}",hubitatThermoAttrib)
            }  
        }
        
        // getACUnitFaults
        getACUnitFaults()
        
        //getFirmwareList
        def resultFmw = getFirmwareList()
        if (resultFmw.status == "ok") {
            sendEvent(name: "firmware", value: resultFmw.Fmw)
        }    
        
    } else {
        sendEvent(name: "lastCheckin", value: "unknown")
        sendEvent(name: "help", value: "check IP is correct")
    }
    sendEvent(name: "status", value: result.status)
    
    return
}

/* ======== custom commands and methods ======== */
private sendiZoneEvents() {
/*
thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
*/    
    sendEvent(name: "temperature", value: state.sysinfo.Temp/100.0)
    sendEvent(name: "thermostatSetpoint", value: state.sysinfo.Setpoint/100.0)
    sendEvent(name: "coolingSetpoint", value: state.sysinfo.Setpoint/100.0)
    sendEvent(name: "heatingSetpoint", value: state.sysinfo.Setpoint/100.0)
    sendEvent(name: "thermostatMode", value: getModeLevel.find { it.value == state.sysinfo.SysMode }?.key)
    sendEvent(name: "supportedFanSpeeds", value: getFanLevel.collect {k,v -> k})
    sendEvent(name: "supportedThermostatFanModes", value: getFanLevel.collect {k,v -> k})
    sendEvent(name: "supportedThermostatModes", value: getModeLevel.collect {k,v -> k})
    
    def ctrlZone = state.zones["zone${state.sysinfo.CtrlZone+1}"]
    if (ctrlZone != null) sendEvent(name: "ctrlZone", value: ctrlZone.Name)
    
    if (state.sysinfo.SysOn == 0) {
        sendEvent(name: "switch", value: "off")
        sendEvent(name: "speed", value: "off")
        sendEvent(name: "thermostatFanMode", value: "off")        
        sendEvent(name: "thermostatMode", value: "off")
    } else {
        sendEvent(name: "switch", value: "on")
        sendEvent(name: "speed", value: getFanMode())
        sendEvent(name: "thermostatFanMode", value: getFanMode())
    }
}

private String getFanMode() {
    switch (state.sysinfo.SysFan) {
        case 1: // SysFan_Low
            return "low"
            break
        case 2: // SysFan_Med
            return "medium"
            break
        case 3: // SysFan_High
            return "high"
            break
        case 4: // SysFan_Auto
            return "auto"
            break
        default:
            break
    }
}

private Map convertiZoneToHubitat(_izone) {
/*
thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
*/
    def map = [:]
    map.put("temperature",_izone.Temp/100.0)
    def setPoint = _izone.Setpoint 
    
    map.put("supportedThermostatModes", ["open", "close", "auto"])
    map.put("supportedThermostatFanModes", ["open", "close", "auto"])
    map.put("thermostatFanMode",getFanMode())
    
    switch (_izone.Mode) {
        case 1: // ZoneMode_Open
            map.put("thermostatMode","open")
            setPoint = state.sysinfo.Setpoint
            break
        case 2: // ZoneMode_Close
            map.put("thermostatMode","close")
            setPoint = state.sysinfo.Setpoint
            break
        case 3: // ZoneMode_Auto
            map.put("thermostatMode","auto")
        /*
            if (_izone.Setpoint > _izone.Temp) {
                map.put("thermostatMode","heat")
            } else {
                map.put("thermostatMode","cool")
            }
        */
            break
        case 4: // ZoneMode_Override
        case 5: // ZoneMode_Constant
        default:
            break
    }

    map.put("coolingSetpoint",setPoint/100.0)
    map.put("heatingSetpoint",setPoint/100.0)
    map.put("thermostatSetpoint",setPoint/100.0)

    return map
}

private getChildDeviceParse(_childname,_attrib,_value) {
    getChildDeviceParse(_childname,[_attrib:_value])
}

private getChildDeviceParse(_childname,_map) {
    if (enableDebug) log.debug "getChildDeviceParse(${_childname},${_map})"  
    
    def child = getChildDevice(_childname)
    if (child) {
        _map.each {
            def data = [[name:it.key, value:it.value, descriptionText:"${child.displayName} ${it.key} value was set to ${it.value}"]]
            //log.warn "$child - $data"
            child.parse(data)
        }
    } else log.warn "no child found ?"
}


private Map sendSimpleiZoneCmd(cmd,value) {
    sendSimpleiZoneCmd(JsonOutput.toJson(["$cmd":value]))
}

private Map sendSimpleiZoneCmd(cmdbody) {
    def respData = [:]
    def params = [:]
    
    params.uri = "http://${ip}:80/iZoneCommandV2"
    params.body = cmdbody
    params.textParser = true
       
    if (enableDebug) log.debug "sendSimpleiZoneCmd() params: $params"
    
    try {
        
        httpPost(params) {
            resp -> resp.headers.each {
                //if (enableDebug && enableRespDebug) 
                log.debug "resp.headers: ${it.name} : ${it.value}"
            }
            
            if (resp.status == 200 && resp.data == "{OK}") 
                respData.status = "ok"
            else {
                respData.status = "failed: ${resp.status} data: ${resp.data}"
            }  
        } 
    } catch (e) {
        if (enableDebug) log.debug "sendSimpleiZoneCmd() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

private Map getSystemInfo() {
    if (enableDebug) log.debug "getSystemInfo()"
    def respData = [:]
    
    def uri = "http://${ip}:80/iZoneRequestV2"
    def mapBody = ["iZoneV2Request":["Type": 1,"No": 0,"No1": 0]] as Map

    try {
        respData.status = "failed"
        httpPostJson(uri,mapBody) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.debug "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.debug "resp.data: ${respData.toString()}"
            if (respData.containsKey("SystemV2")) respData.status = "ok"
        } 
    } catch (e) {
        if (enableDebug) log.debug "getSystemInfo() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

private Map getZonesInfo(int zone) { 
    if (enableDebug) log.debug "getZonesInfo() zone: $zone"
    def respData = [:]
    
    def uri = "http://${ip}:80/iZoneRequestV2"
    def mapBody = ["iZoneV2Request":["Type": 2,"No": zone,"No1": 0]] as Map

    try {
        respData.status = "failed"
        httpPostJson(uri,mapBody) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.debug "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.debug "resp.data: ${respData.toString()}"
            if (respData.containsKey("ZonesV2")) respData.status = "ok"
        } 
    } catch (e) {
        if (enableDebug) log.debug "getZonesInfo() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

private Map getACUnitFaults() { 
    if (enableDebug) log.debug "getACUnitFaults()"
    def respData = [:]
    
    def uri = "http://${ip}:80/iZoneRequestV2"
    def mapBody = ["iZoneV2Request":["Type": 4,"No": 0,"No1": 0]] as Map

    try {
        respData.status = "failed"
        httpPostJson(uri,mapBody) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.debug "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.debug "resp.data: ${respData.toString()}"
            if (respData.containsKey("AcUnitFaultHistV2")) respData.status = "ok"
        } 
    } catch (e) {
        if (enableDebug) log.debug "getACUnitFaults() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

private Map getFirmwareList() { 
    if (enableDebug) log.debug "getFirmwareList()"
    def respData = [:]
    
    def uri = "http://${ip}:80/iZoneRequestV2"
    def mapBody = ["iZoneV2Request":["Type": 6,"No": 0,"No1": 0]] as Map

    try {
        respData.status = "failed"
        httpPostJson(uri,mapBody) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.debug "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.debug "resp.data: ${respData.toString()}"
            if (respData.containsKey("Fmw")) respData.status = "ok"
        } 
    } catch (e) {
        if (enableDebug) log.debug "getFirmwareList() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

def addChildThermostat(String zone,String label) {
    if (enableDebug) log.debug "addChildThermostat() zone: $zone label: $label" 
    def devLabel = device.label
    if (devLabel == null) devLabel = device.name
    
    def childname = "${device.getDeviceNetworkId()}-${zone}"
    if (!getChildDevice(childname)) {
        addChildDevice("hubitat","Generic Component Thermostat", childname, 
            [label: "${devLabel}-${label}",name: "${zone}-${label}", isComponent: true])
    } else {
        if (enableDebug) log.debug "child exists !! $childname not created !!" 
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("enableDebug",[value:"false",type:"bool"])
}

/* ======== child Thermostat device methods ======== */

void componentRefresh(cd) {
    if (enableDebug) log.debug "componentRefresh ${cd.displayName}"
    
    def zoneIndex = (cd.getDeviceNetworkId().drop(cd.getDeviceNetworkId().size() - 1).toInteger())-1
    def resultZone = getZonesInfo(zoneIndex)
    if (resultZone.status == "ok") {    
        def zonename = "zone${zoneIndex+1}"
        if (state.zones[zonename]) state.zones[zonename] = resultZone.ZonesV2
        def hubitatThermoAttrib = convertiZoneToHubitat(resultZone.ZonesV2)
        getChildDeviceParse("${device.getDeviceNetworkId()}-$zonename",hubitatThermoAttrib)
    }  
}

void componentSetCoolingSetpoint(cd,temperature) {
    if (enableDebug) log.debug "componentSetCoolingSetpoint() ${cd.displayName}"
}

void componentSetHeatingSetpoint(cd,temperature) {
    if (enableDebug) log.debug "componentSetCoolingSetpoint() ${cd.displayName}"
}

void componentSetSchedule(cd) {
    log.warn "setSchedule() - not supported"
}

void componentSetThermostatFanMode(cd,mode) {
    if (enableDebug) log.debug "componentSetThermostatFanMode() ${cd.displayName}"
}

void componentSetThermostatMode(cd,thermostatmode) {
    if (enableDebug) log.debug "componentSetThermostatMode() ${cd.displayName},$thermostatmode"
    
    def zoneIndex = (cd.getDeviceNetworkId().drop(cd.getDeviceNetworkId().size() - 1).toInteger())-1
    def zoneMode = getZoneModeLevel[thermostatmode]    
    sendSimpleiZoneCmd("ZoneMode",["Index":zoneIndex,"Mode":zoneMode])
    
    componentRefresh(cd)
}

/*
void componentAuto(cd) { // set to ZoneMode = auto / climate
}

void componentOff(cd) { // set to ZoneMode = closed
    if (enableDebug) log.debug "componentOff() ${cd.displayName}"
}

void componentCool(cd) {
    if (enableDebug) log.debug "componentCool() ${cd.displayName}"
}

void componentHeat(cd) {
    if (enableDebug) log.debug "componentHeat() ${cd.displayName}"
}

void componentEmergencyHeat(cd) {
    log.warn "emergencyHeat() - not supported"
}

void componentFanAuto(cd) {
    log.warn "fanAuto() - not supported"
}

void componentFanCirculate(cd) {
    log.warn "fanCirculate() - not supported"
}

void componentFanOn(cd) {
    log.warn "fanCirculate() - not supported"
}
*/

