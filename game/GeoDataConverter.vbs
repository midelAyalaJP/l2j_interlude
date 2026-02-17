Set WshShell = CreateObject("WScript.Shell")

' Caminho relativo ao .vbs
appPath = "..\libs\*;"
mainClass = "net.sf.l2j.geodataconverter.ui.GeoDataConverterUI"

cmd = "javaw -Xmx512m -cp " & appPath & " " & mainClass

WshShell.Run cmd, 0, False
