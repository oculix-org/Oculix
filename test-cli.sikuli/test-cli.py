# Smoke test for the -l (preload) and -e (auto-execute) CLI flags on the IDE.
#
# Run from a Windows cmd / PowerShell:
#   java -jar oculixide-3.0.3-rc4-win.jar -l "C:\path\to\Oculix\test-cli.sikuli"
#     -> IDE opens with this script preloaded. No auto-run.
#
#   java -jar oculixide-3.0.3-rc4-win.jar -l "C:\path\to\Oculix\test-cli.sikuli" -e
#     -> IDE opens, script preloaded, Run pressed automatically. A SikuliX
#        popup with "bonjour" should appear on screen.
#
#   java -jar oculixide-3.0.3-rc4-win.jar -c -l "C:\path\to\Oculix\test-cli.sikuli" -e
#     -> Same as above. The popup is the canonical proof the script ran:
#        it bypasses any stdout / message-panel routing question.
#        This is the combination micves reported as broken on #224.
popup("bonjour")
