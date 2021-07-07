import groovy.transform.Field
import groovy.json.JsonOutput

/**
 * =======================================================================================
 *  SMA Webconnect
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
 *  Last modified: 2021-07-08
 *
 */ 

@Field Map users = ["user": "usr" ,"installer": "istl"]
@Field String sma_urlLogin                    = "dyn/login.json"
@Field String sma_urlLogout                   = "dyn/logout.json"
@Field String sma_urlValues                   = "dyn/getValues.json"

@Field String sma_status                      = "6180_08214800" 
@Field String sma_metering_power_supplied     = "6100_40463600" // inverter pushes this in grid in W
@Field String sma_pv_power                    = "6100_0046C200" // pv generates this in W
@Field String sma_metering_power_absorbed     = "6100_40463700" // grid sends me this in W

metadata {
    definition (name: "SMA Webconnect", namespace: "gslender", author: "Grant Slender", importUrl: "https://raw.githubusercontent.com/gslender/hubitat/main/sma-webconnect.groovy") {

        capability "Refresh"
        capability "Configuration"
    	capability "Energy Meter"
		capability "Power Meter"
        capability "Sensor"
        
        command "startPolling"
        command "stopPolling"
          
        attribute "firmware", "STRING"
        attribute "authenticated", "STRING" // SID
    }
    
    preferences {
        if(showLogin != false) {
            section('SMA Webconnect') {
                input name: 'usergrp', type: 'text',     title: 'User Group',   required: true,  displayDuringSetup: true, description: '<em>SMA User Group</em>'
                input name: 'password', type: 'password', title: 'Password',   required: true,  displayDuringSetup: true, description: '<em>SMA Password</em>'
                input name: 'ip', type: 'text',     title: 'IP Address', required: true, displayDuringSetup: true, description: '<em>SMA Device IP Address</em>'
            }
        }
        section('Configuration') {
            def refreshEnum = [:]
		        refreshEnum << [1 : "Refresh every 1 second"]
		        refreshEnum << [2 : "Refresh every 2 seconds"]
		        refreshEnum << [5 : "Refresh every 5 seconds"]
		        refreshEnum << [15 : "Refresh every 15 seconds"]
		        refreshEnum << [30 : "Refresh every 30 seconds"]
		        refreshEnum << [60 : "Refresh every 1 minute"]
		        refreshEnum << [120 : "Refresh every 2 minutes"]
		        refreshEnum << [300 : "Refresh every 5 minutes"]
            input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
            input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
            input name: "refresh_Rate", type: "enum", title: "Polling Refresh Rate", options:refreshEnum, defaultValue: 5
            input name: 'showLogin', type: 'bool', title: 'Show SMA Webconnect Login', defaultValue: true, submitOnChange: true
        }
    }
}

@Field static boolean enableRespDebug = false

/* ======== driver built-in callback methods ======== */
/* https://docs.hubitat.com/index.php?title=Device_Code */

void installed(){
   log.info "installed..."
   device.updateSetting("showLogin",[type:"bool", value: true])
   device.updateSetting("enableDebug",[type:"bool", value: true])
   device.updateSetting("enableDesc",[type:"bool", value: true])
   device.updateSetting("refresh_Rate",[type:"enum", value: 5])
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
    state.clear()
    state.polling = false
    
    if (enableDebug) runIn(1800,logsOff)
}

void initialize() {
   log.info "initialize..." 
   updated()
}

void parse(String description) {
    if (enableDebug) log.debug "parse() description: ${description}"
}

/* ======== capability commands ======== */

def refresh() {
    if (enableDebug) log.debug "refresh()"
    
    if (!state.sid) {    
        state.sid = sma_login()
    }
        
    def loginOk = false
    
    if (state.sid) {
        def status = sma_getValues(state.sid,sma_status)
        if (getNestedVal(status,sma_status) != null) loginOk = true
    }

    if (!loginOk) {
        sendEvent(name: "lastCheckin", value: "unknown")
        sendEvent(name: "status", value: "ERROR [check IP / UsrGrp / Pwd]")
        state.sid = null
    } else {
        def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
        sendEvent(name: "lastCheckin", value: now)
        sendEvent(name: "status", value: "connected")
        
        def nestedMap = sma_getValues(state.sid,[sma_metering_power_supplied,sma_pv_power,sma_metering_power_absorbed])
        
        def metering_power_supplied = getNestedVal(nestedMap,sma_metering_power_supplied) 
        def pv_power = getNestedVal(nestedMap,sma_pv_power) 
        def metering_power_absorbed = getNestedVal(nestedMap,sma_metering_power_absorbed) 
        
        if (pv_power == null) pv_power = 0
        if (metering_power_supplied == null) metering_power_supplied = 0
        if (metering_power_absorbed == null) metering_power_absorbed = 0
        
        def power_consumption = pv_power - metering_power_supplied + metering_power_absorbed
        
        sendEvent(name: "power", value: pv_power)
        sendEvent(name: "metering_power_supplied", value: metering_power_supplied)
        sendEvent(name: "metering_power_absorbed", value: metering_power_absorbed)
        sendEvent(name: "power_consumption", value: power_consumption)
        sendEvent(name: "energy", value: power_consumption/1000.0)
    } 
    
    if (state.polling == true) { 
        runInMillis(refresh_Rate.toInteger() * 1000, 'refresh')
    } else {
        sma_logout()        
    }
}

def List<String> configure() {
    if (enableDebug) log.debug "configure()"
    
    state.clear()     
    return
}

def startPolling() {
    if (enableDebug) log.debug "startPolling()"
    if (state.polling == false) runInMillis(1000, 'refresh')
    state.polling = true
}

def stopPolling() {
    if (enableDebug) log.debug "stopPolling()"
    state.polling = false
    sma_logout()     
}

/* ======== custom commands and methods ======== */

def getNestedVal(map,key) {
    
    def value = null
    
    try {
        def aKey = map.keySet().toArray()[0]
        def aValue = map.get(aKey)
        
        def bValue = aValue.get(key)
    
        def cKey = bValue.keySet().toArray()[0]
        def cValue = bValue.get(cKey)
    
        def dValue = cValue.toArray()[0]
        
        value = dValue.val
    } catch (e) {
        if (enableDebug) log.debug "getNestedVal() $e"
    }
    
    return value 
}

private String sma_login() {
    if (enableDebug) log.debug "sma_login()"
    def params = [:]
    params.uri = "https://${ip}:443/${sma_urlLogin}"
    params.body = ["right": "$usergrp" ,"pass": "$password"] as Map  
    params.ignoreSSLIssues = true
    
    if (enableDebug && enableRespDebug) log.warn "sma_login() params: $params"
    
    def sid = null
    
    try {
        httpPostJson(params) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.warn "sma_login() resp.headers: ${it.name} : ${it.value}"
            }
            
            def respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.warn "sma_login() resp.data: ${respData.toString()}"
            
            if (respData.containsKey("result") && respData.result.containsKey("sid")) sid = respData.result.sid
        } 
    } catch (e) {
        if (enableDebug) log.debug "sma_login() ERROR: $e"
    }
    if (enableDebug) log.debug "sma_login() sid=$sid"
    return sid
}

private sma_getValues(sid,_key) {
    if (enableDebug) log.debug "sma_getValues() sid=$sid"
    
    def key = ""
    if (_key instanceof String) key = '"' + _key + '"'
    
    if (_key instanceof List) {
        _key.each {
            key += '"' + it +'",'
        }
        key = key.substring(0, key.length() - 1)
    }
    
    def params = [:]
    params.uri = "https://${ip}:443/${sma_urlValues}?sid=$sid"
    params.body = '{"destDev":[],"keys":[' + key + ']}'
    params.ignoreSSLIssues = true
    
    if (enableDebug && enableRespDebug) log.warn "sma_getValues() params: $params"
    
    def value = null
    
    try {
        httpPostJson(params) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.warn "sma_getValues() resp.headers: ${it.name} : ${it.value}"
            }
            
            def respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.warn "sma_getValues() resp.data: ${respData.toString()}"
            
            if (respData.containsKey("result")) value = respData.result
        } 
    } catch (e) {
        if (enableDebug) log.debug "sma_getValues() ERROR: $e"
    }
    if (enableDebug) log.debug "sma_getValues() value=$value"
    return value
}

private sma_logout() {
    if (!state.sid) return
    if (enableDebug) log.debug "sma_logout() sid=${state.sid}"
    def params = [:]
    params.uri = "https://${ip}:443/${sma_urlLogout}?sid=${state.sid}"
    params.body = [] as Map  
    params.ignoreSSLIssues = true
    
    if (enableDebug && enableRespDebug) log.warn "sma_logout() params: $params"
    
    try {
        httpPostJson(params) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.warn "sma_logout() resp.headers: ${it.name} : ${it.value}"
            }
            
            def respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.warn "sma_logout() resp.data: ${respData.toString()}"
        } 
    } catch (e) {
        if (enableDebug) log.debug "sma_logout() ERROR: $e"
    }
    state.sid = null
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("enableDebug",[value:"false",type:"bool"])
}
