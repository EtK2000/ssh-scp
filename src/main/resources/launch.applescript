#!/usr/bin/osascript

on run argv
	tell application "Terminal"
		set t to do script
		set w to first window of (every window whose tabs contains t)
		activate w
		do script argv in t
	end tell
end run