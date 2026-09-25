package com.easytranslate.service.selection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class WindowsSelectedTextService implements SelectedTextService {

  private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

            try {
                Add-Type -AssemblyName UIAutomationClient
                Add-Type -AssemblyName UIAutomationTypes
                Add-Type -AssemblyName System.Windows.Forms
                Add-Type -AssemblyName WindowsBase

                Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class ForegroundNative { [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow(); }'

                $foreground = [ForegroundNative]::GetForegroundWindow().ToInt64()
                if ($foreground -eq 0) {
                    [Console]::Write('UNKNOWN')
                    exit
                }

                $walker = [System.Windows.Automation.TreeWalker]::RawViewWalker

                function Test-Foreground($element) {
                    $node = $element
                    for ($depth = 0; $depth -lt 128 -and $null -ne $node; $depth++) {
                        try {
                            if ($node.Current.NativeWindowHandle -eq $foreground) {
                                return $true
                            }
                            $node = $walker.GetParent($node)
                        } catch {
                            return $false
                        }
                    }
                    return $false
                }

                $focused = [System.Windows.Automation.AutomationElement]::FocusedElement
                $hovered = $null
                try {
                    $mouse = [System.Windows.Forms.Cursor]::Position
                    $point = [System.Windows.Point]::new($mouse.X, $mouse.Y)
                    $hovered = [System.Windows.Automation.AutomationElement]::FromPoint($point)
                } catch {
                    $hovered = $null
                }

                $candidates = @()
                if ($null -ne $focused -and (Test-Foreground $focused)) {
                    $candidates += $focused
                }
                if ($null -ne $hovered -and (Test-Foreground $hovered)) {
                    $candidates += $hovered
                }

                $foundText = $null
                $sawEmptySelection = $false

                foreach ($candidate in $candidates) {
                    $node = $candidate
                    for ($level = 0; $level -lt 8 -and $null -ne $node; $level++) {
                        try {
                            $pattern = $null
                            $supported = $node.TryGetCurrentPattern(
                                [System.Windows.Automation.TextPattern]::Pattern,
                                [ref]$pattern
                            )
                            if ($supported) {
                                $ranges = $pattern.GetSelection()
                                if ($null -ne $ranges -and $ranges.Length -gt 0) {
                                    $text = $ranges[0].GetText(1000)
                                    if ([string]::IsNullOrWhiteSpace($text)) {
                                        $sawEmptySelection = $true
                                    } else {
                                        $foundText = $text
                                        break
                                    }
                                }
                            }
                        } catch {
                            # This control failed; continue with its parent.
                        }
                        try {
                            $node = $walker.GetParent($node)
                        } catch {
                            $node = $null
                        }
                    }
                    if ($null -ne $foundText) {
                        break
                    }
                }

                if ($null -ne $foundText) {
                    $bytes = [System.Text.Encoding]::UTF8.GetBytes($foundText)
                    [Console]::Write('FOUND:' + [Convert]::ToBase64String($bytes))
                } elseif ($sawEmptySelection) {
                    [Console]::Write('NONE')
                } else {
                    [Console]::Write('UNKNOWN')
                }
            } catch {
                [Console]::Write('UNKNOWN')
            }
            """;

  @Override
  public SelectionResult getSelectedText() {
    Process process = null;

    try {
      String encodedScript = Base64.getEncoder().encodeToString(
          SCRIPT.getBytes(StandardCharsets.UTF_16LE)
      );
      process = new ProcessBuilder(
          "powershell.exe",
          "-NoProfile",
          "-NonInteractive",
          "-WindowStyle", "Hidden",
          "-EncodedCommand", encodedScript
      ).start();

      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return SelectionResult.unknown();
      }

      if (process.exitValue() != 0) {
        return SelectionResult.unknown();
      }

      String response = new String(
          process.getInputStream().readAllBytes(),
          StandardCharsets.UTF_8
      ).trim();

      if (response.equals("NONE")) {
        return SelectionResult.none();
      }

      if (response.startsWith("FOUND:")) {
        byte[] bytes = Base64.getDecoder().decode(
            response.substring("FOUND:".length())
        );
        String text = new String(bytes, StandardCharsets.UTF_8);

        if (!text.isBlank()) {
          return SelectionResult.found(text);
        }
      }

      return SelectionResult.unknown();

    } catch (InterruptedException e) {
      if (process != null) {
        process.destroyForcibly();
      }
      Thread.currentThread().interrupt();
      return SelectionResult.unknown();
    } catch (IOException | IllegalArgumentException e) {
      return SelectionResult.unknown();
    }
  }
}
