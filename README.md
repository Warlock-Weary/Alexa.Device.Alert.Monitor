# Alexa.Device.Alert.Monitor


Amazon Echo devices
Amazon Echo Skill app configured in Hubitat
Virtual devices created by the app (automatic)
:sparkles: Key Features
Individual Device Monitors
Monitor specific devices with custom delays

Added Repeat Feature - Allows app to repeat notifications for alerts.
Each device gets its own virtual contact sensor
Perfect for specific announcements - "Front door is unlocked" or "Garage door 2 is open"
Added Separate Repeat Interval - Introduced a "Repeat Interval" input (1-60 mins, default 5) for individual monitors, allowing repeats independent of the initial delay (0 for instant alerts).
Enabled Repeats for Zero Delay - Modified repeat logic to work with delay = 0, scheduling repeats based on the new "Repeat Interval" instead of the initial delay.
Group Monitors
Monitor multiple devices as one group

Example: 3 garage doors → One alert if ANY is open
Simplifies monitoring similar devices
Added Repeat Feature - Allows app to repeat notifications for alerts.
Added Group Name - Allows you to configure the group name.
Added Separate Repeat Interval - Introduced a "Repeat Interval" input (1-60 mins, default 5) for group monitors, allowing repeats independent of the initial delay (0 for instant alerts).
Enabled Repeats for Zero Delay - Modified repeat logic to work with delay = 0, scheduling repeats based on the new "Repeat Interval" instead of the initial delay.
Smart Delays (0-60 minutes)
0 minutes = Immediate alert when device changes state
30 minutes = Wait before alerting (great for garage doors)
Auto-cancellation = If you fix it before the delay expires, no alert!
Added Repeat Feature = Allows app to repeat notifications for alerts.
Master Status Check
Press a button to check ALL monitored devices at once

Creates separate contacts for each device type (Contacts/Locks/Lights)
Perfect for bedtime or "leaving house" routines
Each type opens only if that category has issues
Enable/Disable Monitors
Pause monitors without deleting settings

Great for seasonal use (disable garage alerts in winter)
Easy testing and troubleshooting
Auto-Naming & Customization
Auto-generates sensible device names
All devices prefixed with "V-ADAM" for easy identification
Customize names when you need multiple groups
Mode Restrictions
Only alert in specific modes (Away, Night, etc.)

