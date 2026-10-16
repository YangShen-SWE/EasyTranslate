package com.easytranslate.service.selection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class WindowsSelectedTextService implements SelectedTextService {

  // Package-visible for offline script-contract checks; tests never capture the desktop.
  static final String SCRIPT = """
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
                        if (-not (Test-Foreground $node)) { break }
                        try {
                            $pattern = $null
                            $supported = $node.TryGetCurrentPattern(
                                [System.Windows.Automation.TextPattern]::Pattern,
                                [ref]$pattern
                            )
                            if ($supported) {
                                $ranges = $pattern.GetSelection()
                                if ($null -ne $ranges -and $ranges.Length -gt 1) {
                                    # Disjoint selections are not supported; do not send only the first range.
                                    [Console]::Write('UNKNOWN')
                                    exit
                                }
                                if ($null -ne $ranges -and $ranges.Length -eq 0) {
                                    $sawEmptySelection = $true
                                }
                                if ($null -ne $ranges -and $ranges.Length -eq 1) {
                                    # Probe one extra UTF-16 unit; never translate a silent prefix.
                                    $text = $ranges[0].GetText(%d)
                                    if ($null -ne $text -and $text.Length -gt %d) {
                                        $foundText = $text
                                        break
                                    } elseif ([string]::IsNullOrWhiteSpace($text)) {
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

                # Reject a detected foreground change during this capture. This is not a keypress snapshot.
                if ([ForegroundNative]::GetForegroundWindow().ToInt64() -ne $foreground) {
                    [Console]::Write('UNKNOWN')
                    exit
                }

                if ($null -ne $foundText -and $foundText.Length -gt %d) {
                    [Console]::Write('TOO_LONG')
                } elseif ($null -ne $foundText) {
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
            """.formatted(SelectionResult.MAX_TEXT_LENGTH + 1,
                SelectionResult.MAX_TEXT_LENGTH, SelectionResult.MAX_TEXT_LENGTH);

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

      return parseResponse(new String(process.getInputStream().readAllBytes(),
          StandardCharsets.UTF_8));

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

  /** Decode only the local capture protocol; no process, focus or credentials are accessed here. */
  static SelectionResult parseResponse(String response) {
    if (response == null) return SelectionResult.unknown();
    response = response.strip();
    if (response.equals("NONE")) return SelectionResult.none();
    if (response.equals("TOO_LONG")) return SelectionResult.tooLong();
    if (!response.startsWith("FOUND:")) return SelectionResult.unknown();
    try {
      byte[] bytes = Base64.getDecoder().decode(response.substring("FOUND:".length()));
      String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
      if (text.length() > SelectionResult.MAX_TEXT_LENGTH) return SelectionResult.tooLong();
      return text.isBlank() ? SelectionResult.none() : SelectionResult.found(text);
    } catch (IllegalArgumentException | CharacterCodingException error) {
      return SelectionResult.unknown();
    }
  }
}
