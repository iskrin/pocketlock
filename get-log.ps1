# Pocket Lock - zbieranie logow z konsoli
# Uruchom:  powershell -ExecutionPolicy Bypass -File get-log.ps1

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$out = Join-Path $PSScriptRoot "pocketlock-log.txt"

if (-not (Test-Path $adb)) {
    Write-Host "BLAD: nie znaleziono adb pod: $adb"
    exit 1
}

Write-Host "Sprawdzam polaczenie z konsola..."
$list = & $adb devices
$list | ForEach-Object { Write-Host "  $_" }

if (-not ($list -match "\tdevice")) {
    Write-Host ""
    Write-Host "Konsola nie jest widoczna jako 'device'. Sprawdz:"
    Write-Host "  1. Na konsoli: Ustawienia -> System -> Opcje programisty -> 'Debugowanie USB' = WLACZONE"
    Write-Host "  2. Na konsoli zaakceptuj dialog 'Zezwol na debugowanie USB' (zaznacz 'Zawsze zezwalaj')"
    Write-Host "  3. Kabel USB musi byc kablem danych (nie tylko ladowania); sprobuj innego portu"
    exit 1
}

Write-Host ""
Write-Host "Czyszcze bufor logow..."
& $adb logcat -c | Out-Null

Write-Host ""
Write-Host "=== ZBIERAM LOGI ==="
Write-Host "Zrob teraz na konsoli:"
Write-Host "  1. wlacz gre/emulator"
Write-Host "  2. power (ekran gasnie)"
Write-Host "  3. power (ekran wraca)"
Write-Host "  4. nacisnij 3 razy, zeby odblokowac"
Write-Host ""
Write-Host "Po odtworzeniu problemu nacisnij tutaj Ctrl+C."
Write-Host ""

try {
    & $adb logcat -s PocketLock AndroidRuntime | Tee-Object -FilePath $out
} finally {
    Write-Host ""
    Write-Host "Log zapisany w pliku:"
    Write-Host "  $out"
    Write-Host "Wklej mi jego zawartosc (albo sciezke)."
}
