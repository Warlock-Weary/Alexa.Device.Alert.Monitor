/**
 * ==========================  Alexa Device Alert Monitor 3.3.6 ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2025
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
 * Changelog:
 * 1.0.0 (2025-10-15) - Initial release
 *                     - Uses Virtual Contact Sensors (open/closed) instead of switches for better Alexa compatibility
 *                     - Supports Group and Individual monitors
 *                     - Master Status Check feature - creates separate contacts per device type (Contacts, Locks, Lights)
 *                     - Advanced option to remove all virtual devices for troubleshooting
 * 2.0.0 (2025-10-15) - Fix master status check to handle missing contacts and stagger closure scheduling
 * 2.0.1 (2025-10-15) - Improved logging in closeAllMasterContacts for clarity when contacts are already closed
 * 2.1.0 (2025-10-16) - Added "Repeat Alert Until Resolved" option for group and individual monitors
 *                     - Reuses delay setting for repeat interval, disabled for zero-delay monitors
 *                     - Reopens virtual contact for 10 seconds if alert persists, stops when resolved
 * 2.2.0 (2025-10-16) - Added configurable repeat limits (1-10, default 3) to prevent alert fatigue
 *                     - Shows repeat count in monitor descriptions
 *                     - Tracks repeat progress in debug logs
 * 2.2.1 (2025-10-16) - Removed automatic deletion of all virtual devices in initialize() to preserve device IDs for Alexa compatibility
 * 3.3.4 (2025-10-18) - Added separate repeat interval for instant alerts (delay=0)
 * 3.3.5 (2025-10-18) - Added configurable virtual device open duration (1-10 sec, default 5)
                       - Made group name required with no auto-naming
                       - Updated logs to reflect dynamic open duration
 */

definition(
   name: "Alexa Device Alert Monitor",
   namespace: "ADAM 3.3.6 - Warlock-Weary + Claude + Grok",
   author: "Warlock-Weary + Claude + Grok",
   description: "Monitor devices and control virtual contact sensors for Alexa announcements",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
)

preferences {
   page name: "pageMain"
   page name: "pageGroupMonitor"
   page name: "pageIndividualMonitor"
   page name: "pageRemoveGroupMonitor"
   page name: "pageRemoveIndividualMonitor"
}

Map pageMain() {
   state.remove("removedMonitor")
   state.remove("removeMonitor")
   dynamicPage(name: "pageMain", title: "<b>Alexa Device Alert Monitor Setup</b>", uninstall: true, install: true) {
      
      section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>📦 Group Monitors:</b>") {
         paragraph "Monitor multiple devices together - if ANY device is in alert state, the virtual contact sensor opens."
         
         state.groupMonitors?.each { Integer monitorNum ->
            String customName = settings."groupMonitor_${monitorNum}_name"
            String type = settings."groupMonitor_${monitorNum}_type"
            String autoName = type ? "Any ${getTypeDisplayName(type)} Open" : "Group Monitor ${monitorNum}"
            String monitorName = customName ?: autoName
            Boolean isEnabled = settings."groupMonitor_${monitorNum}_enabled" != false
            String desc = getGroupMonitorDescription(monitorNum)
            String statusIcon = isEnabled ? "✅" : "⏸️"
            href(name: "pageGroupMonitor${monitorNum}Href",
               page: "pageGroupMonitor",
               params: [monitorNumber: monitorNum],
               title: "${statusIcon} ${monitorName}",
               description: desc ?: "Click/tap to configure...",
               state: desc ? "complete" : null)
         }
         input name: "btnNewGroupMonitor", type: "button", title: "➕ Add New Group Monitor"
      }
      
      section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>🔧 Individual Monitors:</b>") {
         paragraph "Monitor single devices - one device per virtual contact sensor alert."
         
         state.individualMonitors?.each { Integer monitorNum ->
            com.hubitat.app.DeviceWrapper dev = settings."individualMonitor_${monitorNum}_device"
            String monitorName = dev?.displayName ?: "Individual Monitor ${monitorNum}"
            Boolean isEnabled = settings."individualMonitor_${monitorNum}_enabled" != false
            String desc = getIndividualMonitorDescription(monitorNum)
            String statusIcon = isEnabled ? "✅" : "⏸️"
            href(name: "pageIndividualMonitor${monitorNum}Href",
               page: "pageIndividualMonitor",
               params: [monitorNumber: monitorNum],
               title: "${statusIcon} ${monitorName}",
               description: desc ?: "Click/tap to configure...",
               state: desc ? "complete" : null)
         }
         input name: "btnNewIndividualMonitor", type: "button", title: "➕ Add New Individual Monitor"
      }

      section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>🔘 Master Status Check:</b>") {
         paragraph "Trigger a full status check of all monitors with a button press. Creates separate virtual contacts for each device type you're monitoring."
         input name: "masterStatusCheckEnabled", type: "bool", title: "Enable Master Status Check", 
            defaultValue: false, submitOnChange: true
         
         if (settings["masterStatusCheckEnabled"]) {
            input name: "masterStatusCheckButton", type: "capability.pushableButton", 
               title: "Button Device:", submitOnChange: true
            
            if (settings["masterStatusCheckButton"]) {
               input name: "masterStatusCheckButtonNumber", type: "number", 
                  title: "Button Number:", range: "1..16", defaultValue: 1, required: true
            }
            
            paragraph "<b>Virtual Contact Sensors Created:</b>"
            paragraph "<small>The app will automatically create contacts only for device types you're monitoring:</small>"
            paragraph "• <b>V-ADAM - Master Status Check - Contacts</b> (opens if any contacts open)"
            paragraph "• <b>V-ADAM - Master Status Check - Locks</b> (opens if any locks unlocked)"
            paragraph "• <b>V-ADAM - Master Status Check - Lights</b> (opens if any lights on)"
            paragraph "<small>When button ${settings['masterStatusCheckButtonNumber'] ?: '1'} is pushed, each contact will open briefly if that device type has alerts, then close after a few seconds.</small>"
         }
      }

section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>⚙️ Advanced Options:</b>") {
    // Virtual Device Open Duration - FIRST
    paragraph "<b>🧭 Set Virtual Device Open Duration:</b>"
    paragraph "Set how long the virtual device stays open (1-10 seconds, default 5)"
    input name: "virtualDeviceOpenDuration", type: "number", 
        title: "Virtual Device Open Duration (seconds):", 
        range: "1..10", 
        defaultValue: 5, 
        required: false, 
        width: 6
    
    // Debug Logging Options - SECOND
    paragraph "<hr>"
    paragraph "<b>🔍 Debug Logging:</b>"
    input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
    if (settings["debugLogging"]) {
        input name: "debugLoggingAutoDisable", type: "bool", title: "Automatically disable debug logging after 30 minutes", 
            defaultValue: true, width: 8
    }
    
    // Danger Zone - LAST
    paragraph "<hr>"
    paragraph "<b>⚠️ Danger Zone:</b>"
    input name: "btnRemoveAllDevices", type: "button", title: "🗑️ Remove All Virtual Devices", submitOnChange: true
    paragraph "<i>This will delete ALL virtual contact sensors created by this app. Your monitor settings will be preserved, but devices will need to be recreated by clicking Done.</i>"
      }
   }
}

Map pageGroupMonitor(Map params) {
   Integer monitorNum
   if (params?.monitorNumber != null) {
      state.currMonitorNum = params.monitorNumber
      monitorNum = params.monitorNumber
   }
   else {
      monitorNum = state.currMonitorNum
   }
   
   dynamicPage(name: "pageGroupMonitor", title: "<b>Group Monitor Configuration</b>", uninstall: false, install: false, nextPage: "pageMain") {
      section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Group Settings:</b>") {
         input name: "groupMonitor_${monitorNum}_enabled", type: "bool", title: "Enable this monitor", 
            defaultValue: true, submitOnChange: true
         
         input name: "groupMonitor_${monitorNum}_name", type: "text", title: "Custom Group Name (required):", 
            required: true, submitOnChange: true,
            description: "Enter a custom name to identify this group."
         
         input name: "groupMonitor_${monitorNum}_type", type: "enum", title: "Device Type:", 
            options: ["contact": "Contact Sensor", "lock": "Lock", "switch": "Switch/Light"],
            required: true, submitOnChange: true
         
         if (settings."groupMonitor_${monitorNum}_type") {
            String devType = settings."groupMonitor_${monitorNum}_type"
            String capability = getCapabilityForType(devType)
            input name: "groupMonitor_${monitorNum}_devices", type: "capability.${capability}", 
               title: "Select Devices:", multiple: true, required: true, submitOnChange: true
         }
         
         input name: "groupMonitor_${monitorNum}_delay", type: "number", title: "Initial Delay (minutes, 0=immediate):", 
            range: "0..60", defaultValue: 0, required: true, width: 6
         
         input name: "groupMonitor_${monitorNum}_repeat", type: "bool", title: "Repeat Alert Until Resolved", 
            defaultValue: false, submitOnChange: true
         
         if (settings."groupMonitor_${monitorNum}_repeat") {
            input name: "groupMonitor_${monitorNum}_maxRepeats", type: "number", 
               title: "Maximum number of repeats:", 
               range: "1..10", defaultValue: 3, required: true, width: 6
            input name: "groupMonitor_${monitorNum}_repeatInterval", type: "number", 
               title: "Repeat Interval (minutes):", 
               range: "1..60", defaultValue: 5, required: true, width: 6
         }
         
         input name: "groupMonitor_${monitorNum}_modes", type: "mode", title: "Only alert in these modes (optional):", 
            multiple: true, required: false
         
         if (settings."groupMonitor_${monitorNum}_type" && settings."groupMonitor_${monitorNum}_devices" && settings."groupMonitor_${monitorNum}_name") {
            String displayName = settings."groupMonitor_${monitorNum}_name"
            paragraph "<b>Virtual Contact Sensor:</b> V-ADAM - ${displayName} Alert"
         }
      }
      
      section() {
         href(name: "hrefRemoveGroupMonitor",
            page: "pageRemoveGroupMonitor",
            title: "🗑️ Remove This Group Monitor",
            description: "Delete this monitor and its virtual contact sensor",
            params: [monitorNumber: monitorNum]
         )
      }
   }
}

Map pageIndividualMonitor(Map params) {
   Integer monitorNum
   if (params?.monitorNumber != null) {
      state.currMonitorNum = params.monitorNumber
      monitorNum = params.monitorNumber
   }
   else {
      monitorNum = state.currMonitorNum
   }
   
   dynamicPage(name: "pageIndividualMonitor", title: "<b>Individual Monitor Configuration</b>", uninstall: false, install: false, nextPage: "pageMain") {
      section("<hr style='background-color:#000000; height: 2px; border: 0;'><b>Monitor Settings:</b>") {
         input name: "individualMonitor_${monitorNum}_enabled", type: "bool", title: "Enable this monitor", 
            defaultValue: true, submitOnChange: true
         
         input name: "individualMonitor_${monitorNum}_type", type: "enum", title: "Device Type:", 
            options: ["contact": "Contact Sensor", "lock": "Lock", "switch": "Switch/Light"],
            required: true, submitOnChange: true
         
         if (settings."individualMonitor_${monitorNum}_type") {
            String devType = settings."individualMonitor_${monitorNum}_type"
            String capability = getCapabilityForType(devType)
            input name: "individualMonitor_${monitorNum}_device", type: "capability.${capability}", 
               title: "Select Device:", multiple: false, required: true, submitOnChange: true
         }
         
         input name: "individualMonitor_${monitorNum}_delay", type: "number", title: "Initial Delay (minutes, 0=immediate):", 
            range: "0..60", defaultValue: 0, required: true, width: 6
         
         input name: "individualMonitor_${monitorNum}_repeat", type: "bool", title: "Repeat Alert Until Resolved", 
            defaultValue: false, submitOnChange: true
         
         if (settings."individualMonitor_${monitorNum}_repeat") {
            input name: "individualMonitor_${monitorNum}_maxRepeats", type: "number", 
               title: "Maximum number of repeats:", 
               range: "1..10", defaultValue: 3, required: true, width: 6
            input name: "individualMonitor_${monitorNum}_repeatInterval", type: "number", 
               title: "Repeat Interval (minutes):", 
               range: "1..60", defaultValue: 5, required: true, width: 6
         }
         
         input name: "individualMonitor_${monitorNum}_modes", type: "mode", title: "Only alert in these modes (optional):", 
            multiple: true, required: false
         
         if (settings."individualMonitor_${monitorNum}_device") {
            com.hubitat.app.DeviceWrapper dev = settings."individualMonitor_${monitorNum}_device"
            String devType = settings."individualMonitor_${monitorNum}_type"
            String stateName = getStateNameForType(devType)
            String switchName = "V-ADAM - ${getTypeDisplayName(devType)} ${stateName} - ${dev.displayName} Alert"
            paragraph "<b>Virtual Contact Sensor Name:</b> ${switchName}"
         }
      }
      
      section() {
         href(name: "hrefRemoveIndividualMonitor",
            page: "pageRemoveIndividualMonitor",
            title: "🗑️ Remove This Individual Monitor",
            description: "Delete this monitor and its virtual contact sensor",
            params: [monitorNumber: monitorNum]
         )
      }
   }
}

Map pageRemoveGroupMonitor(Map params) {
   dynamicPage(name: "pageRemoveGroupMonitor", title: "Remove Group Monitor", uninstall: false, install: false, nextPage: "pageMain") {
      if (params?.monitorNumber != null) state.removeMonitor = params.monitorNumber
      section("") {
         if (state.removeMonitor != null && state.removedMonitor != true) {
            String customName = settings."groupMonitor_${state.removeMonitor}_name"
            String type = settings."groupMonitor_${state.removeMonitor}_type"
            String autoName = type ? "Any ${getTypeDisplayName(type)} Open" : "Group Monitor"
            String switchName = customName ?: autoName
            paragraph "⚠️ This will delete the virtual contact sensor 'V-ADAM - ${switchName} Alert' and all settings for this monitor."
            paragraph "Any Alexa routines using this sensor will stop working."
            paragraph "Press the button below to confirm removal, or press 'Next' to cancel."
            input name: "btnRemoveGroupMonitor.${state.removeMonitor}", type: "button", title: "🗑️ Confirm Removal", submitOnChange: true
         }
         else {
            if (state.removedMonitor) paragraph "✅ Monitor removed. Press 'Next' to continue."
            else paragraph "Unknown removal status. Try again if needed. Press 'Next' to continue."
         }
      }
   }
}

Map pageRemoveIndividualMonitor(Map params) {
   dynamicPage(name: "pageRemoveIndividualMonitor", title: "Remove Individual Monitor", uninstall: false, install: false, nextPage: "pageMain") {
      if (params?.monitorNumber != null) state.removeMonitor = params.monitorNumber
      section("") {
         if (state.removeMonitor != null && state.removedMonitor != true) {
            com.hubitat.app.DeviceWrapper dev = settings."individualMonitor_${state.removeMonitor}_device"
            String devType = settings."individualMonitor_${state.removeMonitor}_type"
            String stateName = devType ? getStateNameForType(devType) : "Alert"
            String typeDisplay = devType ? getTypeDisplayName(devType) : "Device"
            String switchName = dev ? "V-ADAM - ${typeDisplay} ${stateName} - ${dev.displayName} Alert" : "Monitor ${state.removeMonitor}"
            paragraph "⚠️ This will delete the virtual contact sensor '${switchName}' and all settings for this monitor."
            paragraph "Any Alexa routines using this sensor will stop working."
            paragraph "Press the button below to confirm removal, or press 'Next' to cancel."
            input name: "btnRemoveIndividualMonitor.${state.removeMonitor}", type: "button", title: "🗑️ Confirm Removal", submitOnChange: true
         }
         else {
            if (state.removedMonitor) paragraph "✅ Monitor removed. Press 'Next' to continue."
            else paragraph "Unknown removal status. Try again if needed. Press 'Next' to continue."
         }
      }
   }
}

String getGroupMonitorDescription(Integer monitorNum) {
   String desc = ""
   List devices = settings."groupMonitor_${monitorNum}_devices"
   String type = settings."groupMonitor_${monitorNum}_type"
   Integer delay = settings."groupMonitor_${monitorNum}_delay"
   Boolean isEnabled = settings."groupMonitor_${monitorNum}_enabled" != false
   Boolean repeatEnabled = settings."groupMonitor_${monitorNum}_repeat" != false
   
   if (devices && type) {
      desc = "${devices.size()} ${getTypeDisplayName(type)}(s)"
      if (delay != null) {
         desc += delay == 0 ? " • Immediate" : " • ${delay} min delay"
      }
      if (repeatEnabled) {
         Integer repeatInterval = settings."groupMonitor_${monitorNum}_repeatInterval" ?: 5
         Integer maxRepeats = settings."groupMonitor_${monitorNum}_maxRepeats" ?: 3
         desc += " • Repeat every ${repeatInterval} min (${maxRepeats}x)"
      }
      if (!isEnabled) {
         desc += " • DISABLED"
      }
   }
   return desc
}

String getIndividualMonitorDescription(Integer monitorNum) {
   String desc = ""
   com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
   String type = settings."individualMonitor_${monitorNum}_type"
   Integer delay = settings."individualMonitor_${monitorNum}_delay"
   Boolean isEnabled = settings."individualMonitor_${monitorNum}_enabled" != false
   Boolean repeatEnabled = settings."individualMonitor_${monitorNum}_repeat" != false
   
   if (device && type) {
      desc = getTypeDisplayName(type)
      if (delay != null) {
         desc += delay == 0 ? " • Immediate" : " • ${delay} min delay"
      }
      if (repeatEnabled) {
         Integer repeatInterval = settings."individualMonitor_${monitorNum}_repeatInterval" ?: 5
         Integer maxRepeats = settings."individualMonitor_${monitorNum}_maxRepeats" ?: 3
         desc += " • Repeat every ${repeatInterval} min (${maxRepeats}x)"
      }
      if (!isEnabled) {
         desc += " • DISABLED"
      }
   }
   return desc
}

String getCapabilityForType(String type) {
   switch(type) {
      case "contact": return "contactSensor"
      case "lock": return "lock"
      case "switch": return "switch"
      default: return "sensor"
   }
}

String getTypeDisplayName(String type) {
   switch(type) {
      case "contact": return "Garage Door"
      case "lock": return "Lock"
      case "switch": return "Light"
      default: return "Device"
   }
}

String getStateNameForType(String type) {
   switch(type) {
      case "contact": return "Open"
      case "lock": return "Open"
      case "switch": return "On"
      default: return "Alert"
   }
}

String getAttributeForType(String type) {
   switch(type) {
      case "contact": return "contact"
      case "lock": return "lock"
      case "switch": return "switch"
      default: return "state"
   }
}

String getAlertValueForType(String type) {
   switch(type) {
      case "contact": return "open"
      case "lock": return "unlocked"
      case "switch": return "on"
      default: return "active"
   }
}

void removeGroupMonitor(Integer monitorNum) {
   logDebug "removeGroupMonitor($monitorNum)"
   
   // Delete virtual contact sensor
   String customName = settings."groupMonitor_${monitorNum}_name"
   String type = settings."groupMonitor_${monitorNum}_type"
   if (type) {
      String autoName = "Any ${getTypeDisplayName(type)} Open"
      String switchName = customName ?: autoName
      deleteVirtualSwitch("V-ADAM - ${switchName} Alert")
   }
   
   // Remove all settings for this monitor
   def settingNamesToRemove = settings?.keySet()?.findAll { it.startsWith("groupMonitor_${monitorNum}_") }
   logDebug "  Settings to remove: $settingNamesToRemove"
   settingNamesToRemove.each { settingName ->
      app.removeSetting(settingName)
   }
   
   state.groupMonitors.removeElement(monitorNum as Integer)
   state.remove('removeMonitor')
   state.removedMonitor = true
   logDebug "Finished removing group monitor $monitorNum"
}

void removeIndividualMonitor(Integer monitorNum) {
   logDebug "removeIndividualMonitor($monitorNum)"
   
   // Delete virtual contact sensor
   com.hubitat.app.DeviceWrapper dev = settings."individualMonitor_${monitorNum}_device"
   String devType = settings."individualMonitor_${monitorNum}_type"
   if (dev && devType) {
      String stateName = getStateNameForType(devType)
      String switchName = "V-ADAM - ${getTypeDisplayName(devType)} ${stateName} - ${dev.displayName} Alert"
      deleteVirtualSwitch(switchName)
   }
   
   // Remove all settings for this monitor
   def settingNamesToRemove = settings?.keySet()?.findAll { it.startsWith("individualMonitor_${monitorNum}_") }
   logDebug "  Settings to remove: $settingNamesToRemove"
   settingNamesToRemove.each { settingName ->
      app.removeSetting(settingName)
   }
   
   state.individualMonitors.removeElement(monitorNum as Integer)
   state.remove('removeMonitor')
   state.removedMonitor = true
   logDebug "Finished removing individual monitor $monitorNum"
}

void appButtonHandler(btn) {
   switch (btn) {
      case "btnNewGroupMonitor":
         if (state.groupMonitors == null) state.groupMonitors = []
         Integer newNum = (state.groupMonitors?.size() > 0) ? ((state.groupMonitors[-1] as Integer) + 1) : 0
         state.groupMonitors << newNum
         break
         
      case "btnNewIndividualMonitor":
         if (state.individualMonitors == null) state.individualMonitors = []
         Integer newNum = (state.individualMonitors?.size() > 0) ? ((state.individualMonitors[-1] as Integer) + 1) : 0
         state.individualMonitors << newNum
         break
         
      case { it.startsWith("btnRemoveGroupMonitor.") }:
         Integer monitorNum = (btn - "btnRemoveGroupMonitor.") as Integer
         removeGroupMonitor(monitorNum)
         break
         
      case { it.startsWith("btnRemoveIndividualMonitor.") }:
         Integer monitorNum = (btn - "btnRemoveIndividualMonitor.") as Integer
         removeIndividualMonitor(monitorNum)
         break
      
      case "btnRemoveAllDevices":
         removeAllVirtualDevices()
         break
         
      default:
         log.debug "Unhandled button press: $btn"
   }
}

com.hubitat.app.DeviceWrapper createVirtualSwitch(String switchName) {
   logDebug "Creating virtual contact sensor: ${switchName}"
   try {
      com.hubitat.app.DeviceWrapper existingDevice = getChildDevice("AlexaAlert_${app.id}_${switchName}")
      if (existingDevice) {
         logDebug "Virtual contact sensor already exists: ${switchName}"
         return existingDevice
      }
      
      com.hubitat.app.DeviceWrapper device = addChildDevice(
         "hubitat",
         "Virtual Contact Sensor",
         "AlexaAlert_${app.id}_${switchName}",
         [
            name: switchName,
            label: switchName
         ]
      )
      logDebug "Created virtual contact sensor: ${switchName}"
      return device
   }
   catch (Exception e) {
      log.error "Error creating virtual contact sensor ${switchName}: ${e.message}"
      return null
   }
}

void deleteVirtualSwitch(String switchName) {
   logDebug "Deleting virtual contact sensor: ${switchName}"
   try {
      com.hubitat.app.DeviceWrapper device = getChildDevice("AlexaAlert_${app.id}_${switchName}")
      if (device) {
         deleteChildDevice("AlexaAlert_${app.id}_${switchName}")
         logDebug "Deleted virtual contact sensor: ${switchName}"
      }
   }
   catch (Exception e) {
      log.error "Error deleting virtual contact sensor ${switchName}: ${e.message}"
   }
}

void removeAllVirtualDevices() {
   log.warn "Removing ALL virtual devices created by this app..."
   Integer deletedCount = 0
   
   try {
      List<com.hubitat.app.DeviceWrapper> childDevices = getChildDevices()
      childDevices.each { device ->
         try {
            String deviceName = device.label ?: device.name
            deleteChildDevice(device.deviceNetworkId)
            log.info "Deleted: ${deviceName}"
            deletedCount++
         }
         catch (Exception e) {
            log.error "Error deleting device ${device.deviceNetworkId}: ${e.message}"
         }
      }
      
      log.warn "Removed ${deletedCount} virtual device(s). Click Done to recreate them based on current monitor settings."
   }
   catch (Exception e) {
      log.error "Error removing devices: ${e.message}"
   }
}

void deviceEventHandler(evt) {
   logDebug "Device event: ${evt.device.displayName} ${evt.name} = ${evt.value}"
   
   // Check all group monitors
   state.groupMonitors?.each { Integer monitorNum ->
      List devices = settings."groupMonitor_${monitorNum}_devices"
      if (devices?.find { it.id == evt.device.id }) {
         handleGroupMonitorEvent(monitorNum)
      }
   }
   
   // Check all individual monitors
   state.individualMonitors?.each { Integer monitorNum ->
      com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
      if (device?.id == evt.device.id) {
         handleIndividualMonitorEvent(monitorNum)
      }
   }
}

void buttonEventHandler(evt) {
   logDebug "Button event: ${evt.device.displayName} button ${evt.value} pushed"
   
   if (settings["masterStatusCheckEnabled"]) {
      Integer buttonNum = settings["masterStatusCheckButtonNumber"] ?: 1
      Integer pushedButton = evt.value as Integer
      
      if (pushedButton == buttonNum) {
         logDebug "Master status check triggered by button ${buttonNum}"
         performMasterStatusCheck()
      }
   }
}

void performMasterStatusCheck() {
   logDebug "Performing master status check..."
   
   Map<String, Boolean> typeAlerts = [contact: false, lock: false, switch: false]
   Set<String> monitoredTypes = []
   
   state.groupMonitors?.each { Integer monitorNum ->
      if (settings."groupMonitor_${monitorNum}_enabled" != false) {
         String type = settings."groupMonitor_${monitorNum}_type"
         if (type) monitoredTypes.add(type)
         List devices = settings."groupMonitor_${monitorNum}_devices"
         if (devices && type) {
            String attribute = getAttributeForType(type)
            String alertValue = getAlertValueForType(type)
            if (devices.any { it.currentValue(attribute) == alertValue }) {
               typeAlerts[type] = true
            }
         }
      }
   }
   
   state.individualMonitors?.each { Integer monitorNum ->
      if (settings."individualMonitor_${monitorNum}_enabled" != false) {
         String type = settings."individualMonitor_${monitorNum}_type"
         if (type) monitoredTypes.add(type)
         com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
         if (device && type) {
            String attribute = getAttributeForType(type)
            String alertValue = getAlertValueForType(type)
            if (device.currentValue(attribute) == alertValue) {
               typeAlerts[type] = true
            }
         }
      }
   }
   
   logDebug "Master status check results - Contacts: ${typeAlerts.contact}, Locks: ${typeAlerts.lock}, Lights: ${typeAlerts.switch}"
   
   // Open contacts immediately
   if (monitoredTypes.contains("contact")) {
      handleMasterContactByType("Contacts", typeAlerts.contact)
      pauseExecution(1000) // 1-second pause
   }
   if (monitoredTypes.contains("lock")) {
      handleMasterContactByType("Locks", typeAlerts.lock)
      pauseExecution(1000) // 1-second pause
   }
   if (monitoredTypes.contains("switch")) {
      handleMasterContactByType("Lights", typeAlerts.switch)
   }
   
   // Schedule a single closure for all contacts
   runIn(10, "closeAllMasterContacts", [data: [monitoredTypes: monitoredTypes]])
}

void handleMasterContactByType(String typeName, Boolean hasAlert) {
   String contactName = "V-ADAM - Master Status Check - ${typeName}"
   com.hubitat.app.DeviceWrapper contact = getChildDevice("AlexaAlert_${app.id}_${contactName}")
   
   if (!contact) {
      logDebug "Master contact for ${typeName} not found (may not be monitoring this type)"
      return
   }
   
   if (hasAlert) {
      if (contact.currentValue("contact") != "open") {
         contact.open()
         logDebug "Opened master contact: ${contactName}"
      }
   } else {
      if (contact.currentValue("contact") == "open") {
         contact.close()
         logDebug "Closed master contact: ${contactName} (no alerts)"
      }
   }
}

void closeAllMasterContacts(Map data) {
   Set<String> monitoredTypes = data.monitoredTypes
   logDebug "Closing all master contacts"
   
   monitoredTypes.each { type ->
      String typeName = type == "contact" ? "Contacts" : (type == "lock" ? "Locks" : "Lights")
      String contactName = "V-ADAM - Master Status Check - ${typeName}"
      com.hubitat.app.DeviceWrapper contact = getChildDevice("AlexaAlert_${app.id}_${contactName}")
      
      if (!contact) {
         logDebug "Failed to close master contact: ${contactName} (device not found)"
         return
      }
      if (contact.currentValue("contact") != "open") {
         logDebug "Master contact: ${contactName} is already closed"
         return
      }
      contact.close()
      logDebug "Closed master contact: ${contactName}"
      pauseExecution(7000) // 7-second pause
      if (contact) {
         logDebug "Verification: ${contactName} state is ${contact.currentValue('contact')}"
      } else {
         logDebug "Verification failed: ${contactName} not found"
      }
   }
}

void verifyClosure(Map data) {
   String contactName = data.contactName
   com.hubitat.app.DeviceWrapper contact = getChildDevice("AlexaAlert_${app.id}_${contactName}")
   if (contact) {
      logDebug "Verification: ${contactName} state is ${contact.currentValue('contact')}"
   } else {
      logDebug "Verification failed: ${contactName} not found"
   }
}

void cleanupOpenContacts() {
   logDebug "Running cleanup for open master contacts"
   ["Contacts", "Locks", "Lights"].each { typeName ->
      String contactName = "V-ADAM - Master Status Check - ${typeName}"
      com.hubitat.app.DeviceWrapper contact = getChildDevice("AlexaAlert_${app.id}_${contactName}")
      if (contact && contact.currentValue("contact") == "open") {
         contact.close()
         logDebug "Cleanup: Closed master contact: ${contactName}"
      }
   }
}

void handleGroupMonitorEvent(Integer monitorNum) {
   // Check if monitor is enabled
   Boolean isEnabled = settings."groupMonitor_${monitorNum}_enabled" != false
   if (!isEnabled) {
      logDebug "Group monitor ${monitorNum} is disabled, skipping"
      return
   }
   
   String type = settings."groupMonitor_${monitorNum}_type"
   List devices = settings."groupMonitor_${monitorNum}_devices"
   Integer delay = settings."groupMonitor_${monitorNum}_delay" ?: 0
   Boolean repeatEnabled = settings."groupMonitor_${monitorNum}_repeat" != false
   List modes = settings."groupMonitor_${monitorNum}_modes"
   String customName = settings."groupMonitor_${monitorNum}_name"
   
   if (!devices || !type) return
   
   // Auto-generate name if not provided
   String autoName = devices.collect { it.displayName }.join(", ")
   String switchName = customName ?: autoName
   String fullSwitchName = "V-ADAM - ${switchName} Alert"
   
   // Check if we're in allowed mode
   if (modes && !modes.contains(location.mode)) {
      logDebug "Group monitor ${monitorNum}: Not in allowed mode, skipping"
      return
   }
   
   String attribute = getAttributeForType(type)
   String alertValue = getAlertValueForType(type)
   
   // Check if ANY device is in alert state
   Boolean anyInAlertState = devices.any { device ->
      device.currentValue(attribute) == alertValue
   }
   
   com.hubitat.app.DeviceWrapper virtualSwitch = getChildDevice("AlexaAlert_${app.id}_${fullSwitchName}")
   
   if (anyInAlertState) {
      logDebug "Group monitor ${monitorNum}: Device(s) in alert state"
      if (delay > 0) {
         logDebug "Scheduling switch ON in ${delay} minutes"
         runIn(delay * 60, "turnOnGroupSwitch", [data: [monitorNum: monitorNum]])
      } else {
         turnOnGroupSwitch([monitorNum: monitorNum])
      }
      // Schedule repeat if enabled
      if (repeatEnabled) {
         Integer maxRepeats = settings."groupMonitor_${monitorNum}_maxRepeats" ?: 3
         Integer repeatInterval = settings."groupMonitor_${monitorNum}_repeatInterval" ?: 5
         logDebug "Scheduling repeat alert for group monitor ${monitorNum} every ${repeatInterval} minutes (max ${maxRepeats}x)"
         runIn((repeatInterval * 60) + 10, "repeatGroupAlert", [data: [monitorNum: monitorNum, repeatCount: 0]])
      }
   } else {
      logDebug "Group monitor ${monitorNum}: All devices OK"
      unschedule("turnOnGroupSwitch")
      unschedule("repeatGroupAlert")
      if (virtualSwitch?.currentValue("contact") == "open") {
         virtualSwitch.close()
         logDebug "Closed contact: ${fullSwitchName}"
      }
   }
}

void turnOnGroupSwitch(Map data) {
   Integer monitorNum = data.monitorNum
   String type = settings."groupMonitor_${monitorNum}_type"
   List devices = settings."groupMonitor_${monitorNum}_devices"
   String customName = settings."groupMonitor_${monitorNum}_name"
   
   if (!devices || !type) return
   
   // Auto-generate name if not provided
   String autoName = devices.collect { it.displayName }.join(", ")
   String switchName = customName ?: autoName
   String fullSwitchName = "V-ADAM - ${switchName} Alert"
   
   String attribute = getAttributeForType(type)
   String alertValue = getAlertValueForType(type)
   
   // Double-check devices are still in alert state
   Boolean anyInAlertState = devices.any { device ->
      device.currentValue(attribute) == alertValue
   }
   
   if (anyInAlertState) {
      com.hubitat.app.DeviceWrapper virtualSwitch = getChildDevice("AlexaAlert_${app.id}_${fullSwitchName}")
      if (virtualSwitch && virtualSwitch.currentValue("contact") != "open") {
         virtualSwitch.open()
         logDebug "Opened contact: ${fullSwitchName}"
         Integer duration = settings.virtualDeviceOpenDuration ?: 5
         runIn(duration, "closeGroupSwitch", [data: [monitorNum: monitorNum]])
      }
   }
}

void turnOnIndividualSwitch(Map data) {
   Integer monitorNum = data.monitorNum
   String type = settings."individualMonitor_${monitorNum}_type"
   com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
   
   if (!device || !type) return
   
   String attribute = getAttributeForType(type)
   String alertValue = getAlertValueForType(type)
   String stateName = getStateNameForType(type)
   String switchName = "V-ADAM - ${getTypeDisplayName(type)} ${stateName} - ${device.displayName} Alert"
   
   // Double-check device is still in alert state
   if (device.currentValue(attribute) == alertValue) {
      com.hubitat.app.DeviceWrapper virtualSwitch = getChildDevice("AlexaAlert_${app.id}_${switchName}")
      if (virtualSwitch && virtualSwitch.currentValue("contact") != "open") {
         virtualSwitch.open()
         logDebug "Opened contact: ${switchName}"
         Integer duration = settings.virtualDeviceOpenDuration ?: 5
         runIn(duration, "closeIndividualSwitch", [data: [monitorNum: monitorNum]])
      }
   }
}

void repeatGroupAlert(Map data) {
   Integer monitorNum = data.monitorNum
   Integer repeatCount = data.repeatCount ?: 0
   Boolean isEnabled = settings."groupMonitor_${monitorNum}_enabled" != false
   Boolean repeatEnabled = settings."groupMonitor_${monitorNum}_repeat" != false
   String type = settings."groupMonitor_${monitorNum}_type"
   List devices = settings."groupMonitor_${monitorNum}_devices"
   Integer repeatInterval = settings."groupMonitor_${monitorNum}_repeatInterval" ?: 5
   Integer maxRepeats = settings."groupMonitor_${monitorNum}_maxRepeats" ?: 3
   List modes = settings."groupMonitor_${monitorNum}_modes"
   
   // Ensure maxRepeats is within valid range
   if (maxRepeats < 1) maxRepeats = 1
   if (maxRepeats > 10) maxRepeats = 10
   
   // Check if we've hit the limit
   if (repeatCount >= maxRepeats) {
      logDebug "Group monitor ${monitorNum}: Completed ${maxRepeats} repeats, stopping"
      return
   }
   
   if (!isEnabled || !repeatEnabled || !devices || !type) {
      logDebug "Repeat stopped for group monitor ${monitorNum}: disabled or invalid settings"
      return
   }
   
   if (modes && !modes.contains(location.mode)) {
      logDebug "Group monitor ${monitorNum}: Not in allowed mode, skipping repeat"
      return
   }
   
   String attribute = getAttributeForType(type)
   String alertValue = getAlertValueForType(type)
   
   Boolean anyInAlertState = devices.any { device ->
      device.currentValue(attribute) == alertValue
   }
   
   if (anyInAlertState) {
      logDebug "Repeat ${repeatCount + 1}/${maxRepeats} for group monitor ${monitorNum}"
      turnOnGroupSwitch([monitorNum: monitorNum])
      Integer duration = settings.virtualDeviceOpenDuration ?: 5
      runIn((repeatInterval * 60) + duration, "repeatGroupAlert", [data: [monitorNum: monitorNum, repeatCount: repeatCount + 1]])
   } else {
      logDebug "Group monitor ${monitorNum}: Alert resolved after ${repeatCount} repeat(s)"
   }
}

void handleIndividualMonitorEvent(Integer monitorNum) {
   // Check if monitor is enabled
   Boolean isEnabled = settings."individualMonitor_${monitorNum}_enabled" != false
   if (!isEnabled) {
      logDebug "Individual monitor ${monitorNum} is disabled, skipping"
      return
   }
   
   String type = settings."individualMonitor_${monitorNum}_type"
   com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
   Integer delay = settings."individualMonitor_${monitorNum}_delay" ?: 0
   Boolean repeatEnabled = settings."individualMonitor_${monitorNum}_repeat" != false
   List modes = settings."individualMonitor_${monitorNum}_modes"
   
   if (!device || !type) return
   
   // Check if we're in allowed mode
   if (modes && !modes.contains(location.mode)) {
      logDebug "Individual monitor ${monitorNum}: Not in allowed mode, skipping"
      return
   }
   
   String attribute = getAttributeForType(type)
   String alertValue = getAlertValueForType(type)
   String stateName = getStateNameForType(type)
   String switchName = "V-ADAM - ${getTypeDisplayName(type)} ${stateName} - ${device.displayName} Alert"
   
   com.hubitat.app.DeviceWrapper virtualSwitch = getChildDevice("AlexaAlert_${app.id}_${switchName}")
   
   if (device.currentValue(attribute) == alertValue) {
      logDebug "Individual monitor ${monitorNum}: Device in alert state"
      if (delay > 0) {
         logDebug "Scheduling switch ON in ${delay} minutes"
         runIn(delay * 60, "turnOnIndividualSwitch", [data: [monitorNum: monitorNum]])
      } else {
         turnOnIndividualSwitch([monitorNum: monitorNum])
      }
      // Schedule repeat if enabled
      if (repeatEnabled) {
         Integer maxRepeats = settings."individualMonitor_${monitorNum}_maxRepeats" ?: 3
         Integer repeatInterval = settings."individualMonitor_${monitorNum}_repeatInterval" ?: 5
         logDebug "Scheduling repeat alert for individual monitor ${monitorNum} every ${repeatInterval} minutes (max ${maxRepeats}x)"
         runIn((repeatInterval * 60) + 10, "repeatIndividualAlert", [data: [monitorNum: monitorNum, repeatCount: 0]])
      }
   } else {
      logDebug "Individual monitor ${monitorNum}: Device OK"
      unschedule("turnOnIndividualSwitch")
      unschedule("repeatIndividualAlert")
      if (virtualSwitch?.currentValue("contact") == "open") {
         virtualSwitch.close()
         logDebug "Closed contact: ${switchName}"
      }
   }
}

void closeIndividualSwitch(Map data) {
   Integer monitorNum = data.monitorNum
   String type = settings."individualMonitor_${monitorNum}_type"
   com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
   
   if (!device || !type) return
   
   String stateName = getStateNameForType(type)
   String switchName = "V-ADAM - ${getTypeDisplayName(type)} ${stateName} - ${device.displayName} Alert"
   
   com.hubitat.app.DeviceWrapper virtualSwitch = getChildDevice("AlexaAlert_${app.id}_${switchName}")
   if (virtualSwitch && virtualSwitch.currentValue("contact") == "open") {
      virtualSwitch.close()
Integer duration = settings.virtualDeviceOpenDuration ?: 5
logDebug "Closed contact: ${switchName} after ${duration} seconds"
   }
}

void repeatIndividualAlert(Map data) {
   Integer monitorNum = data.monitorNum
   Integer repeatCount = data.repeatCount ?: 0
   Boolean isEnabled = settings."individualMonitor_${monitorNum}_enabled" != false
   Boolean repeatEnabled = settings."individualMonitor_${monitorNum}_repeat" != false
   String type = settings."individualMonitor_${monitorNum}_type"
   com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
   Integer repeatInterval = settings."individualMonitor_${monitorNum}_repeatInterval" ?: 5
   Integer maxRepeats = settings."individualMonitor_${monitorNum}_maxRepeats" ?: 3
   List modes = settings."individualMonitor_${monitorNum}_modes"
   
   // Ensure maxRepeats is within valid range
   if (maxRepeats < 1) maxRepeats = 1
   if (maxRepeats > 10) maxRepeats = 10
   
   // Check if we've hit the limit
   if (repeatCount >= maxRepeats) {
      logDebug "Individual monitor ${monitorNum}: Completed ${maxRepeats} repeats, stopping"
      return
   }
   
   if (!isEnabled || !repeatEnabled || !device || !type) {
      logDebug "Repeat stopped for individual monitor ${monitorNum}: disabled or invalid settings"
      return
   }
   
   if (modes && !modes.contains(location.mode)) {
      logDebug "Individual monitor ${monitorNum}: Not in allowed mode, skipping repeat"
      return
   }
   
   String attribute = getAttributeForType(type)
   String alertValue = getAlertValueForType(type)
   
   if (device.currentValue(attribute) == alertValue) {
      logDebug "Repeat ${repeatCount + 1}/${maxRepeats} for individual monitor ${monitorNum}"
      turnOnIndividualSwitch([monitorNum: monitorNum])
      Integer duration = settings.virtualDeviceOpenDuration ?: 5
      runIn((repeatInterval * 60) + duration, "repeatIndividualAlert", [data: [monitorNum: monitorNum, repeatCount: repeatCount + 1]])
   } else {
      logDebug "Individual monitor ${monitorNum}: Alert resolved after ${repeatCount} repeat(s)"
   }
}

void installed() {
   log.info "Installed"
   initialize()
}

void updated() {
   log.info "Updated"
   unsubscribe()
   unschedule()
   initialize()
   
   // Persist default virtual device open duration if not set
   if (settings.virtualDeviceOpenDuration == null) {
      app.updateSetting("virtualDeviceOpenDuration", [type: "number", value: 5])
      log.debug "Persisted default virtual device open duration: 5 seconds"
   }
}

void initialize() {
   log.info "Initializing Alexa Device Alert Monitor"
   
   if (settings["debugLogging"]) {
      if (settings["debugLoggingAutoDisable"] != false) {
         log.debug "Debug logging enabled for 30 minutes"
         runIn(1800, disableDebugLogging)
      } else {
         log.debug "Debug logging enabled (will not auto-disable)"
      }
   }
   
   // Create master status check contact sensors if enabled
   if (settings["masterStatusCheckEnabled"]) {
      Set<String> monitoredTypes = []
      
      state.groupMonitors?.each { Integer monitorNum ->
         Boolean isEnabled = settings."groupMonitor_${monitorNum}_enabled" != false
         if (isEnabled) {
            String type = settings."groupMonitor_${monitorNum}_type"
            if (type) monitoredTypes.add(type)
         }
      }
      
      state.individualMonitors?.each { Integer monitorNum ->
         Boolean isEnabled = settings."individualMonitor_${monitorNum}_enabled" != false
         if (isEnabled) {
            String type = settings."individualMonitor_${monitorNum}_type"
            if (type) monitoredTypes.add(type)
         }
      }
      
      // Create virtual contacts for each monitored type
      if (monitoredTypes.contains("contact")) {
         String contactName = "V-ADAM - Master Status Check - Contacts"
         createVirtualSwitch(contactName)
         logDebug "Created/verified master contact for Contacts"
      }
      
      if (monitoredTypes.contains("lock")) {
         String contactName = "V-ADAM - Master Status Check - Locks"
         createVirtualSwitch(contactName)
         logDebug "Created/verified master contact for Locks"
      }
      
      if (monitoredTypes.contains("switch")) {
         String contactName = "V-ADAM - Master Status Check - Lights"
         createVirtualSwitch(contactName)
         logDebug "Created/verified master contact for Lights"
      }
      
      // Subscribe to button events
      if (settings["masterStatusCheckButton"]) {
         subscribe(settings["masterStatusCheckButton"], "pushed", buttonEventHandler)
         logDebug "Subscribed to button: ${settings['masterStatusCheckButton'].displayName}"
      }
   }
   
   // Create/update virtual contact sensors for group monitors
   state.groupMonitors?.each { Integer monitorNum ->
      Boolean isEnabled = settings."groupMonitor_${monitorNum}_enabled" != false
      if (!isEnabled) {
         logDebug "Skipping disabled group monitor ${monitorNum}"
         return
      }
      
      String customName = settings."groupMonitor_${monitorNum}_name"
      List devices = settings."groupMonitor_${monitorNum}_devices"
      String type = settings."groupMonitor_${monitorNum}_type"
      
      if (devices && type) {
         String autoName = devices.collect { it.displayName }.join(", ")
         String switchName = customName ?: autoName
         String fullSwitchName = "V-ADAM - ${switchName} Alert"
         com.hubitat.app.DeviceWrapper virtualSwitch = createVirtualSwitch(fullSwitchName)
         
         String attribute = getAttributeForType(type)
         devices.each { device ->
            subscribe(device, attribute, deviceEventHandler)
         }
         
         handleGroupMonitorEvent(monitorNum)
      }
   }
   
   // Create/update virtual contact sensors for individual monitors
   state.individualMonitors?.each { Integer monitorNum ->
      Boolean isEnabled = settings."individualMonitor_${monitorNum}_enabled" != false
      if (!isEnabled) {
         logDebug "Skipping disabled individual monitor ${monitorNum}"
         return
      }
      
      com.hubitat.app.DeviceWrapper device = settings."individualMonitor_${monitorNum}_device"
      String type = settings."individualMonitor_${monitorNum}_type"
      
      if (device && type) {
         String stateName = getStateNameForType(type)
         String switchName = "V-ADAM - ${getTypeDisplayName(type)} ${stateName} - ${device.displayName} Alert"
         com.hubitat.app.DeviceWrapper virtualSwitch = createVirtualSwitch(switchName)
         
         String attribute = getAttributeForType(type)
         subscribe(device, attribute, deviceEventHandler)
         
         handleIndividualMonitorEvent(monitorNum)
      }
   }
   
   log.info "Initialization complete"
}

void disableDebugLogging() {
   log.info "Debug logging automatically disabled after 30 minutes"
   app.updateSetting("debugLogging", [type: "bool", value: false])
}

void logDebug(String msg) {
   if (settings["debugLogging"]) {
      log.debug msg
   }
}
