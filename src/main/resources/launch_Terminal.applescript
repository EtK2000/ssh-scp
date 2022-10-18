tell application "Terminal"
    set t to do script "${command}"
    set w to first window of (every window whose tabs contains t)
    activate w
end tell