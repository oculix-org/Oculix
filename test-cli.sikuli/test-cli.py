# Smoke test for the -l (preload) and -e (auto-execute) CLI flags on the IDE.
#
# Run from a Windows cmd / PowerShell:
#   java -jar oculixide-3.0.3-rc4-win.jar -l "C:\path\to\Oculix\test-cli.sikuli"
#     -> IDE opens with this script preloaded. No auto-run.
#
#   java -jar oculixide-3.0.3-rc4-win.jar -l "C:\path\to\Oculix\test-cli.sikuli" -e
#     -> IDE opens, script preloaded, Run pressed automatically. You should
#        see "bonjour" in the IDE message panel.
#
#   java -jar oculixide-3.0.3-rc4-win.jar -c -l "C:\path\to\Oculix\test-cli.sikuli" -e
#     -> Same as above but messages go to the launching terminal (cmd) instead
#        of the IDE message panel. You should see "bonjour" in cmd.
#        This is the combination micves reported as broken on #224.
print("bonjour")
