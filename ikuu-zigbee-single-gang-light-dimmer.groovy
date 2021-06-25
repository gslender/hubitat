/**
 * =======================================================================================
 *  Ikuu Zigbee Single Gang Light Dimmer
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
 *  Last modified: 2021-06-25
 *
 *  Portions of this code was borrowed from Christos Psaroudis 
 *  https://github.com/C-PS/Hubitat/blob/main/drivers/DimmerSwitchDriver.txt
 */ 
 
metadata {
    definition (name: "Ikuu Zigbee Single Gang Light Dimmer", namespace: "gslender", author: "Grant Slender", importUrl: 
                "https://raw.githubusercontent.com/gslender/hubitat/main/ikuu-zigbee-single-gang-light-dimmer.groovy") {
    
    capability "Refresh"
    capability "Configuration"

    capability "Switch"
    capability "Switch Level"

    fingerprint profileId: "0104", inClusters: "0000,0004,0005,EF00", outClusters: "0019,000A", manufacturer: "_TZE200_9i9dt8is", model: "TS0601", deviceJoinName: "Ikuu Zigbee Single Gang Light Dimmer"
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
   device.setName("IKUU-SINGLE-LIGHT-DIMMER")
   updated();
   configure()
   unschedule()
   if (enableDebug) runIn(1800,logsOff)
}

// Parse incoming device messages to generate events
def parse(String description) {
    if (enableDebug) log.debug "parse() description: ${description}"

    if (description?.startsWith('catchall:')) {
        msg = zigbee.parseDescriptionAsMap(description)
        if (enableDebug) log.debug "parseDescriptionAsMap() msg: ${msg}"
        switch(msg.clusterId) {
            case "EF00": 
                def attribute = getAttribute(msg.data)
                def value = getAttributeValue(msg.data)
            
                switch (attribute) {
                    case "switch": 
                        switch(value) {
                            case 0:
                                if (enableDebug) log.debug "sendEvent(switch,off)"
                                sendEvent(name: "switch", value: "off")
                            break;

                            case 1:
                                if (enableDebug) log.debug "sendEvent(switch,on)"
                                sendEvent(name: "switch", value: "on")
                            break;
                        }     
                    break;
                
                    case "level": 
                        if (enableDebug) log.debug "sendEvent(level,${value / 10})"
                        sendEvent(name: "level", value: value / 10)
                    break;
                }
        
            break;
        }
    }       
}

private String getAttribute(ArrayList _data) {
    String retValue = ""
    if (_data.size() >= 5) {
        if (_data[2] == "01" && _data[3] == "01" && _data[4] == "00") {
            retValue = "switch"
        }
        else if (_data[2] == "02" && _data[3] == "02" && _data[4] == "00") {
            retValue = "level"
        }
    }
    
    return retValue
}

private int getAttributeValue(ArrayList _data) {
    int retValue = 0
    
    if (_data.size() >= 6) {
        int dataLength = _data[5] as Integer
        int power = 1;
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[i+5])
            power = power * 256
        }
    }
    
    return retValue
}

def off() {
    if (enableDebug) log.debug "off()"
    zigbee.command(0xEF00, 0x0, "00010101000100")
}

def on() {
    if (enableDebug) log.debug "on()"
    zigbee.command(0xEF00, 0x0, "00010101000101")
}

def setLevel(value) {
    if (enableDebug) log.debug "setLevel() value:${value}"
    if (value >= 0 && value <= 100) {
        Map commandParams = [:]
        String commandPayload = "0001020200040000" + zigbee.convertToHexString((value * 10) as Integer, 4)
        zigbee.command(0xEF00, 0x0, commandPayload)
    }
}

def refresh() {
    if (enableDebug) log.debug "refresh()"
    zigbee.command(0xEF00, 0x0, "00020100")
}

def configure() {
    if (enableDebug) log.debug "configure()"
    zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.onOffRefresh() + zigbee.levelRefresh()
}
