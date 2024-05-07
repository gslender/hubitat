import groovy.transform.Field
import groovy.json.JsonOutput
import hubitat.helper.ColorUtils

/**
 * =======================================================================================
 *  iZone iLights
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
 *  Last modified: 2021-07-15
 *
 */ 

@Field Map getAccel = ["very slow": 0 ,"slow": 1,"medium": 2, "fast": 3]
@Field Map getFx = ["none": 0 ,"reading": 1,"relaxing": 2, "candle": 3, "holiday": 4, "rotate": 5, "circadian": 6, "musinc": 7, "auto brightness": 8]

metadata {
    definition (name: "iZone iLights Universal Parent", namespace: "gslender", author: "Grant Slender", importUrl: "https://raw.githubusercontent.com/gslender/hubitat/main/izone-ilights-universal-parent.groovy") {

        capability "Refresh"
        capability "Configuration"
        
        attribute "firmware", "STRING"
        attribute "lastCheckin", "STRING"
        
        command "discoverBridge"
        command "devSendCmd"
    }
    
    preferences {
        def refreshEnum = [:]
		    refreshEnum << ["1 min" : "Refresh every minute"]
		    refreshEnum << ["5 min" : "Refresh every 5 minutes"]
		    refreshEnum << ["15 min" : "Refresh every 15 minutes"]
		    refreshEnum << ["30 min" : "Refresh every 30 minutes"]
		    refreshEnum << ["never" : "Never Refresh"]
        input name: "removeChildren", type: "bool", title: "Remove Children on Configure", defaultValue: true
        input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "ip", type: "string", title:"Bridge IP" , required: true
        input name: "refresh_Rate", type: "enum", title: "Polling Refresh Rate", options:refreshEnum, defaultValue: "5 min"

        input name: "devcmdbody", type: "string", title:"DEV Only Send Cmd", defaultValue:'{"LiName":{"No": x,"Name":"string"}}'
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
   device.updateSetting("refresh_Rate",[type:"enum", value: "5 min"])
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
		case "30 min" :
			runEvery30Minutes(refresh)
			break
		default:
            unschedule()
	}
	if (enableDebug) log.debug "runEvery${refresh_Rate}Minute(s) refresh() "
    refresh()
    
   if (enableDebug) runIn(1800,logsOff)
}

void initialize() {
    log.info "initialize..." 
    discoverBridge()
    state.sysinfo = [:]
    state.lights = [:]
    state.firmware = null
}

void parse(description) {
    if (enableDebug) log.debug "parse() description: ${description}"
    
    try {
        def map = parseDescriptionAsMap(description)
        def list = hexToASCII(map.get("payload")).tokenize(',')
        def ip_addr = null
        list.each {
            if (it.startsWith("IP_")) {
                ip_addr = it.reverse().take(it.length()-3).reverse()
            }
        }    
        device.updateSetting("ip",[type:"string", value: ip_addr])
    } catch (e) {
        if (enableDebug) log.debug "parse() error $e"
    }
}


/* ======== capability commands ======== */


def refresh() {
    if (enableDebug) log.debug "refresh()"
    
    // connect and getSystemInfo 
    def result = getSystemInfo()
    
    if (result.status == "ok") {
        def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
        sendEvent(name: "lastCheckin", value: now)
        state.sysinfo = result.iLightSystem
        
        // getLightsInfo 
        state.lights.clear()
        result.iLightSystem.LiNext.times {
            def resultLights = getLightInfo(it)
            if (resultLights.status == "ok") {    
                def indexL = "L${resultLights.iLight.Index}"
                state.lights.put(indexL, resultLights.iLight)
                def hubitatiLightAttrib = convertiLightToHubitat(resultLights.iLight)
                childSendEvent("${device.getDeviceNetworkId()}-$indexL",hubitatiLightAttrib)
            }  
        }
        
        //getFirmwareList
        if (state.firmware == null) {
            def resultFmw = getFirmwareList()
            if (resultFmw.status == "ok") {
                state.firmware = resultFmw.Fmw
            }   
        }
        sendEvent(name: "firmware", value: state.firmware)
        
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
    state.lights = [:]
    state.firmware = null
    if (removeChildren) {
        getChildDevices().each { 
            if (enableDebug) log.debug "deleteChildDevice ${it.deviceNetworkId}"
            deleteChildDevice("${it.deviceNetworkId}") 
        }
    }
    
    // connect and refresh
    refresh()
    
    state.lights.eachWithIndex { indexL, lightData, index ->
        addChildLight(indexL,lightData.Name,lightData.Type)
    }
    
    // populate the children
    refresh()
}
    

/* ======== custom commands and methods ======== */

def discoverBridge() {
    if (enableDebug) log.debug "discoverBridge()"   
    def myHubAction = new hubitat.device.HubAction("IASD", hubitat.device.Protocol.LAN,[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,destinationAddress: "255.255.255.255:12107"])
    sendHubCommand(myHubAction)
}

def devSendCmd() {
    if (enableDebug) log.debug "devSendCmd() devcmdbody: $devcmdbody"
    sendSimpleiLightCmd(devcmdbody) 
    
    runInMillis(500, 'refresh')
} 

private parseDescriptionAsMap(description) {
    try {
        def descMap = description.split(",").inject([:]) { map, param ->
            def nameAndValue = param.split(":")
            if (nameAndValue.length == 2){
                map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
            } else {
                map += [(nameAndValue[0].trim()):""]
            }
        }
        return descMap
    } catch (e) {
        return [:]
    }
}

private String hexToASCII(String hexValue) {
    def output = new StringBuilder("")
    for (int i = 0; i < hexValue.length(); i += 2) {
        def str = hexValue.substring(i, i + 2)
        output.append((char) Integer.parseInt(str, 16))
    }
    return output.toString()
}

private Map convertiLightToHubitat(_iLight) {
    def map = [:]

    map.put("_acceleration", getAccel.find { it.value == _iLight.Acce }?.key)
    
    map.put("switch", (_iLight.On == 1) ? 'on' : 'off')
    
    if (_iLight.Type == "CL5") {       
        map.put("level", _iLight.Brig )
        map.put("effectName", getFx.find { it.value == _iLight.Fx }?.key)
        map.put("lightEffects",getFx.collect {k,v -> k})
        map.put("colorMode", (_iLight.Rgb == 1) ? 'RGB' : 'CT')
        def hsv = ColorUtils.rgbToHSV([_iLight.R, _iLight.G, _iLight.B])
        map.put("RGB", _iLight.R+","+_iLight.G+","+_iLight.B )
        map.put("hue", hsv[0])
        map.put("saturation", hsv[1])
        map.put("colorName", getGenericColorName(hsv))
        map.put("color", ColorUtils.rgbToHEX([_iLight.R, _iLight.G, _iLight.B]))
    }
    return map
}

private getGenericColorName(hsv) {
    
    if (hsv[2] < 17) return "Black"
    if (hsv[1] < 17) return "White"
    
    switch (hsv[0].toInteger()) {
        case 0..2: return "Red"
        case 3..10: return "Orange"
        case 11..16: return "Yellow"
        case 17..37: return "Green"
        case 38..48: return "Cyan"
        case 49..68: return "Blue"
        case 69..73: return "Violet"
        case 74..84: return "Magenta"
        case 85..94: return "Pink"
        case 95..100: return "Red"
    }
    
    return "unknown"
}

private childSendEvent(_childname,_attrib,_value) {
    childSendEvent(_childname,[_attrib:_value])
}

private childSendEvent(_childname,_map) {
    if (enableDebug) log.debug "childSendEvent(${_childname},${_map})"  
    
    def child = getChildDevice(_childname)
    if (child) {
        _map.each {
            def data = [name:it.key, value:it.value, descriptionText:"${child.displayName} ${it.key} value was set to ${it.value}"]
            child.sendEvent(data)
        }
    } else log.warn "no child found ?"
}


private Map sendSimpleiLightCmd(cmd,value) {
    sendSimpleiLightCmd(JsonOutput.toJson(["$cmd":value]))
}

private Map sendSimpleiLightCmd(cmdbody) {
    def respData
    def params = [:]
    params.uri = "http://${ip}:80/iLightCommand"
    params.body = cmdbody
       
    if (enableDebug) log.debug "sendSimpleiLightCmd() params: $params"
    
    try {
        
        httpPost(params) { resp -> 
            
            resp.headers.each {
                if (enableDebug && enableRespDebug) log.warn "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.data.text
        } 
    } catch (e) {
        if (enableDebug) log.debug "sendSimpleiLightCmd() ERROR: $e"
        return [status:"failed: $e"]
    }
                
    if (enableDebug && enableRespDebug) log.warn "resp.data.text: ${respData}"
    
    if (!respData.startsWith("{OK}")) {
        log.warn "$cmdbody returned $respData"
    }
    
    return [status:"$respData"]
}

private Map getSystemInfo() {
    if (enableDebug) log.debug "getSystemInfo()"
    def respData = [:]
    
    def uri = "http://${ip}:80/iLightRequest"
    def mapBody = ["LiRequest":["Type": 2,"No": 0,"No1": 0]] as Map
    
    try {
        respData.status = "failed"
        httpPostJson(uri,mapBody) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.warn "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.warn "resp.data: ${respData.toString()}"
            if (respData.containsKey("iLightSystem")) respData.status = "ok"
        }
    } catch (e) {
        if (enableDebug) log.debug "getSystemInfo() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

private Map getLightInfo(int indexL) { 
    if (enableDebug) log.debug "getLightInfo() indexL: $indexL"
    def respData = [:]
    
    def uri = "http://${ip}:80/iLightRequest"
    def mapBody = ["LiRequest":["Type": 1,"No": indexL,"No1": 0]] as Map

    try {
        respData.status = "failed"
        httpPostJson(uri,mapBody) {
            resp -> resp.headers.each {
                if (enableDebug && enableRespDebug) log.warn "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.warn "resp.data: ${respData.toString()}"
            if (respData.containsKey("iLight")) respData.status = "ok"
        } 
    } catch (e) {
        if (enableDebug) log.debug "getLightInfo() ERROR: $e"
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
                if (enableDebug && enableRespDebug) log.warn "resp.headers: ${it.name} : ${it.value}"
            }
            
            respData = resp.getData() as Map
            if (enableDebug && enableRespDebug) log.warn "resp.data: ${respData.toString()}"
            if (respData.containsKey("Fmw")) respData.status = "ok"
        } 
    } catch (e) {
        if (enableDebug) log.debug "getFirmwareList() ERROR: $e"
        respData.status = "failed: $e"
    }
    return respData
}

def addChildLight(String indexL, String label, String type) {
    if (enableDebug) log.debug "addChildLight() indexL: $indexL label: $label type: $type" 
    def devLabel = device.label
    if (devLabel == null) devLabel = device.name
    
    def childname = "${device.getDeviceNetworkId()}-${indexL}"
    if (!getChildDevice(childname)) {
        def deviceString = ""
        switch (type) {
            
		case "CL5" :    
            deviceString = "Generic Component RGBW Light Effects"
            break
            
		case "CGPO" :
        default:
            deviceString = "Generic Component Switch"
            break
        }
        
        addChildDevice("hubitat",deviceString, childname, [label: "${devLabel}-${label}",name: "${indexL}-${label}", isComponent: true])
    } else {
        if (enableDebug) log.debug "child exists !! $childname not created !!" 
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("enableDebug",[value:"false",type:"bool"])
}


/* ======== child Light device methods ======== */

private int getIndexL(cd) {
    return (cd.getDeviceNetworkId().drop(cd.getDeviceNetworkId().size() - 1).toInteger())
}

def componentOn(cd) {
    if (enableDebug) log.debug "componentOn ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    sendSimpleiLightCmd("LiOn",["No":indexL,"On":1]) // Turn light on/off 0 - Off, 1 - On
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}

def componentOff(cd) {
    if (enableDebug) log.debug "componentOff ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    sendSimpleiLightCmd("LiOn",["No":indexL,"On":0]) // Turn light on/off 0 - Off, 1 - On
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}

def componentSetLevel(cd, level, duration=-1) {
    if (enableDebug) log.debug "setLevel ${cd.displayName}"
    def indexL = getIndexL(cd)

    if (duration > 0) {
        setAccel(indexL,duration)
    }
    
    setBright(indexL,level.toInteger())
    runInMillis(500, 'refreshLightIndex',[data: indexL])    
}

private setAccel(indexL,duration) {
    // Accel (0 - slowest to 3 - fastest)
    def accel = 3
    if (duration > 1) accel = 2
    if (duration > 3) accel = 1
    if (duration > 6) accel = 0    
    sendSimpleiLightCmd("LiAccel",["No":indexL,"Accel": accel.toInteger()]) // Set acceleration
}

private setBright(indexL,bright) {
    sendSimpleiLightCmd("LiBright",["No":indexL,"Bright": bright]) // Set Brightness/level
}

def componentSetColor(cd, colormap) {
    if (enableDebug) log.debug "componentSetColor ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    def iLight = state.lights["L${getIndexL(cd)}"]
    def hsv = ColorUtils.rgbToHSV([iLight.R, iLight.G, iLight.B])
    
    if (colormap.hue != null && colormap.hue != "NaN") hsv[0] = colormap.hue
    if (colormap.saturation != null && colormap.saturation != "NaN") hsv[1] = colormap.saturation
    if (colormap.level != null && colormap.level != "NaN") hsv[2] = colormap.level
    
    setRGB(indexL,ColorUtils.hsvToRGB(hsv))
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}

private setRGB(indexL,rgb) {
    def r = rgb[0].toInteger()
    def g = rgb[1].toInteger()
    def b = rgb[2].toInteger()
    sendSimpleiLightCmd("LiRgb",["No":indexL,"R": r,"G": g, "B": b]) // Set RGB color
}

def componentSetHue(cd, hue) {
    if (enableDebug) log.debug "componentSetHue ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    def iLight = state.lights["L${getIndexL(cd)}"]
    def hsv = ColorUtils.rgbToHSV([iLight.R, iLight.G, iLight.B])
    def rgb = ColorUtils.hsvToRGB([hue, hsv[1], hsv[2]])
    
    setRGB(indexL,rgb)
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}

def componentSetSaturation(cd, saturation) {
    if (enableDebug) log.debug "componentSetSaturation ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    def iLight = state.lights["L${getIndexL(cd)}"]
    def hsv = ColorUtils.rgbToHSV([iLight.R, iLight.G, iLight.B])
    def rgb = ColorUtils.hsvToRGB([hsv[0], saturation, hsv[2]])
    
    setRGB(indexL,rgb)
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}

def componentSetEffect(cd, effectnumber) {
    if (enableDebug) log.debug "componentSetEffect ${cd.displayName} $effectnumber"
    
    if (effectnumber < 0 || effectnumber > getFx.size()) {
        log.warn "effectnumber $effectnumber outside range"
        return
    }
    
    def indexL = getIndexL(cd)
    sendSimpleiLightCmd("LiFx",["No":indexL,"Fx": effectnumber]) // Set FX type
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}


def componentSetNextEffect(cd) {
    if (enableDebug) log.debug "componentSetNextEffect ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    def iLight = state.lights["L${getIndexL(cd)}"]
    
    def nextFx = iLight.Fx + 1
    if (nextFx > getFx.size()-1) nextFx = 0
    componentSetEffect(cd, nextFx) 
}

def componentSetPreviousEffect(cd) {
    if (enableDebug) log.debug "componentSetPreviousEffect ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    def iLight = state.lights["L${getIndexL(cd)}"]
    
    def nextFx = iLight.Fx - 1
    if (nextFx < 0) nextFx = getFx.size()-1
    componentSetEffect(cd, nextFx) 
}

def componentSetColorTemperature(cd, colortemperature, level=-1, duration=-1) {
    if (enableDebug) log.debug "componentSetColorTemperature ${cd.displayName}"
    
    def indexL = getIndexL(cd)
    if (duration > 0) {
        setAccel(indexL,duration)
    }    
    
    if (level > 0) {
        setBright(indexL,level.toInteger())    
    }
    setRGB(indexL,tempToRGB(colortemperature))  
    runInMillis(500, 'refreshLightIndex',[data: indexL])
}

private tempToRGB(temp) {
    
    temp = temp / 100
    def red, blue, green

    if (temp <= 66) {
        red = 255
    } else {
        red = temp - 60
        red = 329.698727466 * Math.pow(red, -0.1332047592)
        if (red < 0) red = 0
        if (red > 255) red = 255
    }
    
    if (temp <= 66) {
        green = temp
        green = 99.4708025861 * Math.log(green) - 161.1195681661
    } else {
        green = temp - 60
        green = 288.1221695283 * Math.pow(green, -0.0755148492)
    }
    if (green < 0) green = 0
    if (green > 255) green = 255

    if (temp >= 66) {
        blue = 255
    } else {
        if (temp <= 19) {
            blue = 0
        } else {
            blue = temp - 10
            blue = 138.5177312231 * Math.log(blue) - 305.0447927307
            if (blue < 0) blue = 0
            if (blue > 255) blue = 255
        }
    }
    
    def out = []
    out[0] = Math.floor(red).toInteger()
    out[1] = Math.floor(green).toInteger()
    out[2] = Math.floor(blue).toInteger()
    return out
} 

def componentRefresh(cd) {
    if (enableDebug) log.debug "componentRefresh ${cd.displayName}"
    
    refreshLightIndex(getIndexL(cd))
}

private refreshLightIndex(indexL) {
    def resultLights = getLightInfo(indexL)
    if (resultLights.status == "ok") {    
        def indexLStr = "L${resultLights.iLight.Index}"
        state.lights.put(indexLStr, resultLights.iLight)
        def hubitatiLightAttrib = convertiLightToHubitat(resultLights.iLight)
        childSendEvent("${device.getDeviceNetworkId()}-$indexLStr",hubitatiLightAttrib)  
    } 
}
