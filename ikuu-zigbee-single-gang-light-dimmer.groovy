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

import java.security.MessageDigest

metadata {
    definition (name: "Ikuu Zigbee Single Gang Light Dimmer", namespace: "gslender", author: "Grant Slender", importUrl:
                "https://raw.githubusercontent.com/gslender/hubitat/main/ikuu-zigbee-single-gang-light-dimmer.groovy") {
    
    capability "Configuration"
    capability "Initialize"

    capability "Switch"
    capability "Switch Level"

    fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0300,EF00", outClusters: "0019,000A", manufacturer: "_TZE200_swaamsoy", model: "TS0601", deviceJoinName: "Ikuu Zigbee Single Gang Light Dimmer"
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
   //device.setName("IKUU-SINGLE-LIGHT-DIMMER")
   updated();
   configure()
   unschedule()
   if (enableDebug) runIn(1800,logsOff)
}

// Parse incoming device messages to generate events
def parse(String description) {

    if (description?.startsWith('catchall:')) {
        msg = zigbee.parseDescriptionAsMap(description)
        switch(msg.clusterId) {
            case "EF00":
                def attribute = getAttribute(msg.data)
                def value = getAttributeValue(msg.data)

                switch (attribute) {
                    case "switch":
                        if (enableDebug) log.debug "known msg.command: ${msg.command}"
                        switch(value) {
                            case 0:
                                if (enableDebug) log.debug "sendEvent(switch,off) - $attribute:$value"
                                sendEvent(name: "switch", value: "off")
                            break;

                            case 1:
                                if (enableDebug) log.debug "sendEvent(switch,on) - $attribute:$value"
                                sendEvent(name: "switch", value: "on")
                            break;
                        }
                    break;

                    case "level":
                        if (enableDebug) log.debug "known msg.command: ${msg.command}"
                        if (enableDebug) log.debug "sendEvent(level,${value / 10}) - $attribute:$value"
                        sendEvent(name: "level", value: value / 10)
                    break;
                    
                    default:                        
                        if (enableDebug) log.debug "unknown msg.command: ${msg.command} ${msg.data}"
                }

            break;
            
        }
    } else { 
        if (enableDebug) log.debug "parse() description: ${description}" 
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

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("enableDebug",[value:"false",type:"bool"])
}

def off() {
    if (enableDebug) log.debug "off()"
    String seq = "00" + zigbee.convertToHexString(rand(256), 2)
    zigbee.command(0xEF00, 0x0, null, 500, seq+"0100000000")

}

def on() {
    if (enableDebug) log.debug "on()"
    String seq = "00" + zigbee.convertToHexString(rand(256), 2)
    zigbee.command(0xEF00, 0x0, null, 500, seq+"0100000001")
}


def setLevel(value, rate = 0) {
    if (value >= 0 && value <= 100) {      
        String seq = "00" + zigbee.convertToHexString(rand(256), 2)
        String commandPayload = seq + "020200040000" + zigbee.convertToHexString((value * 10) as Integer, 4)
        //String commandPayload = "2222020200040000" + zigbee.convertToHexString((value * 10) as Integer, 4)
        if (enableDebug) log.debug "setLevel() value:${value} - $commandPayload"        
        zigbee.command(0xEF00, 0x0, null, 200, commandPayload)  +  zigbee.command(0xEF00, 0x0, null, 500, "11110100000001")
    }
}


private rand(n) { return (new Random().nextInt(n))} 

def configure() {
    if (enableDebug) log.debug "configure() does nothing"
//    zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.onOffRefresh() + zigbee.levelRefresh() 
}