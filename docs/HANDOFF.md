# Pocket Lock — handoff do nowej sesji

Stan na: 2026-09-26, po testach buildów 1.2.1–1.2.8 (versionCode 53→61).
Cel dokumentu: przekazać pełny kontekst, żeby nowa sesja mogła kontynuować bez czytania historii czatu.

---

## 1. Projekt

**Pocket Lock** — własny ekran blokady dla konsol Retroid (Android 13, 4:3).
Odblokowanie: 3 kliknięcia dowolnym przyciskiem (lub dotyk). Kotlin, bez zewnętrznych zależności,
minSdk 27, targetSdk 34, compileSdk 35. Package: `pl.iskri.pocketlock`.

**Główne urządzenia:**
- **Retroid Pocket Nova** — docelowe, podłączone do ADB (serial `d46c9df0`).
- **Retroid Pocket Classic** — urządzenie do debugowania; tam widać mignięcie przy RetroArchu.

**Aplikacje, na których testowano:**
- RetroArch `com.retroarch.aarch64` (v1.16.0_GIT, versionCode 1696272784) — sterownik **Vulkan** (Adreno).
- Mupen64Plus AE v3 `org.mupen64plusae.v3.fzurita` — pauzuje **tylko w `onStop`**.
- Dolphin `org.dolphinemu.dolphinemu` — pauzuje w `onPause` (fragment), ale wznawia się po odtworzeniu surface.
- Launcher Retroid: `com.radikal.gamelauncher` (startuje gry, RetroArch dostaje wtedy `QUITFOCUS`).

**Zasady repo (z `AGENTS.md`):**
- **Nigdy nie pushować na GitHub ani nie tworzyć/modyfikować Releases bez zgody użytkownika.**
- Lokalnie można: edycja, build, `git add`, lokalne commity, instalacja przez ADB.
- `keystore/`, `keystore.properties`, `dist/` są gitignored.
- Każdy kolejny build instalowany na konsoli musi mieć **wyższy `versionCode`** (konsola blokuje downgrade).

---

## 2. Build i instalacja

```powershell
$env:JAVA_HOME="$env:LOCALAPPDATA\Android\jdk17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
& "$env:LOCALAPPDATA\Android\gradle-8.11.1\bin\gradle.bat" assembleRelease --no-daemon
```

Wynik: `app\build\outputs\apk\release\app-release.apk` → kopiowany do
`dist\PocketLock-<wersja>.apk` i `dist\PocketLock.apk`.

Instalacja: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\PocketLock-<wersja>.apk`

Weryfikacja podpisu: `apksigner verify dist\PocketLock-<wersja>.apk`.

**Aktualna wersja w kodzie:** `versionCode = 61`, `versionName = "1.2.8-test"`.

---

## 3. Architektura kodu (stan bieżący)

| Plik | Rola |
|---|---|
| `LockService.kt` | Foreground service; receiver `SCREEN_ON/OFF`; overlay; mute; audio polling; pauza/re-stop |
| `LockActivity.kt` | Aktywność blokady (keyguard path oraz w overlay mode jako „pause holder”) |
| `PauseActivity.kt` | **Nowa (1.2.8)**: pusta, nieprzezroczysta aktywność uruchamiana na żądanie, żeby zatrzymać aplikacje ignorujące `onPause` |
| `LockOverlayView.kt` | Widok overlay (kropki, licznik kliknięć, animacje wyjścia) |
| `LockAppearance.kt` | Tło/kropki/kolory |
| `ScreenTimeout.kt` | Wyłączanie ekranu po czasie przez device admin (`dpm.lockNow()`) |
| `Prefs.kt` | SharedPreferences (m.in. `media_muted` — stan wyciszenia do odtworzenia po crashu) |
| `styles.xml` | `LockTheme` = **translucent**, `PauseTheme` = opaque |

**Kluczowe zachowania w `LockService`:**
- `SCREEN_OFF` → `armLock()`: overlay + `requestAudioFocus` + `muteMedia()` + **natychmiastowy** `LockActivity.launch()`.
- `SCREEN_ON` (bez keyguarda) → start `audioCheckRunnable` (co 400 ms, start po 400 ms).
- `audioCheckRunnable`: jeśli `AudioManager.isMusicActive` prawdziwe 2× z rzędu → `PauseActivity.launch()`
  (zatrzymuje aplikację przez `onStop`).
- `onOverlayPress()` (1. kliknięcie): jeśli `PauseActivity` działa → `finish()` (early resume, żeby reinit
  schował się za overlayem).
- `performUnlock()` (3. kliknięcie): finish `PauseActivity` + `LockActivity`, animacja, `detachOverlay(unmuteMedia = true)`.
- `muteMedia()` / `unmuteMedia()`: wycisza strumień `STREAM_MUSIC` na czas blokady; stan zapisany w prefsach.
- Dźwięk kliknięć blokady: `USAGE_ASSISTANCE_SONIFICATION` (strumień systemowy), więc mute go nie dotyczy.

---

## 4. Historia problemów i ustaleń (ważne!)

### 4.1 Mignięcie na Classicu (RetroArch)
- Objaw: po odblokowaniu krótkie czarno-białe mignięcie, potem gra.
- Przyczyna: nieprzezroczysty `LockActivity` → `onStop` → zniszczenie surface/GL → przy wznowieniu
  RetroArch odtwarza kontekst i to widać, gdy overlay zjeżdża.
- Rozwiązanie: **translucentny `LockTheme`** — aplikacja pod spodem jest tylko pauzowana (`onPause`),
  surface żyje, przy odblokowaniu nie ma reinitu → brak mignięcia. Potwierdzone przez użytkownika.

### 4.2 Crash RetroArch (najważniejsze!)
- Objaw: „nasza aplikacja czasem wyłącza grę; po odblokowaniu launcher zamiast gry”.
- To **crash RetroArch**, nie zamknięcie przez naszą appkę. Dowód z logów (Nova):
  ```
  signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)
  pid: ..., tid: ..., name: Thread-3  >>> com.retroarch.aarch64 <<<
  #00 pc 00000000000d2000  [anon:.bss]
  #01 ... libretroarch-activity.so
  #02 ... libretroarch-activity.so (video_driver_frame+3756)
  #03 ... (runloop_iterate+4536)
  ```
- Mechanizm: RetroArch 1.16.0 (Vulkan/Adreno) crashuje przy **wielokrotnych, szybkich**
  inicjalizacjach kontekstu. W logach przed crashem widać 2–3× `AdrenoVK-0 ... Application Name: RetroArch`
  w ~1,5 s (resume przy screen-on → zatrzymanie przez nasz opaque `LockActivity` → early resume).
- Kluczowe: użytkownik **zapala ekran ~39–180 ms po zgaszeniu** (szybkie power-off/on). Start aktywności
  trwa ~70 ms, więc opaque `LockActivity` przegrywa wyścig → app wstaje przy screen-on, potem jest
  zatrzymywana i wznawiana ponownie → 2 inity → crash.
- Rozwiązanie: **nie zatrzymywać RetroArcha** (translucent). Wtedy ma dokładnie 1 stop/resume na blokadę
  (jak stockowa blokada) → brak crasha. Potwierdzone przez użytkownika: „brak crasha na retroarchu”.

### 4.3 Mupen64 chodzi w tle
- Źródło (potwierdzone w źródłach Mupen64Plus AE, `GameFragment.java`):
  ```java
  onStop()  { mIsResumed = false; if (!mShuttingDown && hasServiceStarted()) tryPausing(); ... }
  onPause() { /* nic */ }
  onResume(){ if (!mIsResumed) { mIsResumed = true; tryRunning(); ... } }
  isSafeToRender() = mIsResumed && mIsSurface && !mShuttingDown
  ```
  Czyli **Mupen64 pauzuje wyłącznie w `onStop`**. Przy translucentnej blokadzie dostaje tylko `onPause`
  → emulacja chodzi dalej (użytkownik: „chodzi, bez dźwięku”).
- `CoreService` Mupena jest związany lokalnym binderem, **brak eksportowanej akcji pauzy** — nie da się
  go zapauzować intentem/broadcastem.
- Próby:
  - Opaque `LockActivity` (1.2.7) → Mupen staje, ale RetroArch crashuje (patrz 4.2).
  - **1.2.8 (obecny)**: adaptive — translucent baza + wykrywanie `isMusicActive` + `PauseActivity`.
    Użytkownik zgłasza: **Mupen nadal nie pauzuje** (RetroArch OK, brak crasha).

### 4.4 QUITFOCUS w RetroArch (do świadomości)
- RetroArch Android: `quitfocus = getIntent().hasExtra("QUITFOCUS")`; w `onStop()`:
  `if (quitfocus) System.exit(0);`
- Launchery (Retroid/dawniej Daijishō) potrafią przekazywać `QUITFOCUS`, przez co RetroArch sam się zamyka
  przy utracie widoczności. Jeśli użytkownik zgłasza „gra się zamyka”, warto sprawdzić, jak odpala grę.
- Uwaga: to zachowanie RetroArcha, nie nasze.

---

## 5. Otwarty problem: dlaczego Mupen nie pauzuje w 1.2.8

Hipotezy do sprawdzenia (kolejność sugerowana):

1. **`isMusicActive` zwraca `false`** mimo działającej emulacji (mute strumienia nie powinien zatrzymywać
   aktywnej ścieżki, ale trzeba to potwierdzić). Diagnostyka: zalogować wynik w `audioCheckRunnable`
   (np. co poll do logcatu, albo licznik do `Prefs.lastKey`).
2. **Gałąź keyguarda**: `onScreenOn()` uruchamia polling tylko w `else` (brak zablokowanego keyguarda).
   Jeśli na urządzeniu keyguard jest zablokowany (np. po `dpm.lockNow()` z `ScreenTimeout`), polling w ogóle
   nie startuje. Diagnostyka: log `km.isKeyguardLocked` w `onScreenOn`.
3. **`PauseActivity.launch()` nie startuje** (restrykcje background activity start? wyjątek?).
   Diagnostyka: log w `PauseActivity.launch` + `adb shell dumpsys activity activities | grep -i pocketlock`.
4. **Audio jest przerywane** (np. tylko efekty, cisza w grze) — 2 kolejne polle mogą się nie trafić.
   Rozważyć: wymagać 1 trafienia na dłuższym oknie, albo użyć `OnAudioFocusChangeListener`.
5. **Mupen pauzuje audio, ale nie emulację** (np. ścieżka audio zatrzymana przy utracie focusu, a rdzeń
   chodzi) → `isMusicActive` false, a gra działa. Wtedy potrzebny inny sygnał (patrz niżej).

**Inne sygnały/mechanizmy do rozważenia:**
- `AudioManager.registerAudioPlaybackCallback` / `getActivePlaybackConfigurations` — wymagają
  `MODIFY_AUDIO_ROUTING` (privileged) → niedostępne.
- `UsageStatsManager` z `PACKAGE_USAGE_STATS` (specjalny dostęp) — precyzyjna detekcja foregroundu,
  ale wymaga zgody użytkownika w ustawieniach; pozwoliłoby też zrobić listę per-app.
- `AccessibilityService` — ciężki, wymaga włączenia w ustawieniach.
- `ActivityManager.getRunningAppProcesses/getRunningTasks` — od Androida 8 zwracają tylko własne procesy.
- **Nie zatrzymywać niczego** (translucent) i zaakceptować, że Mupen chodzi — najbezpieczniejsze dla RetroArcha.
- **RetroArch po stronie użytkownika**: aktualizacja RetroArcha (1.16.0 jest stary) albo zmiana
  `Settings → Video → Output → Video Driver` z `vulkan` na `gl`. Wtedy opaque-pauza dla wszystkiego
  przestaje crashować i problem znika u źródła.

---

## 6. Diagnostyka (ADB)

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb logcat -d -s PocketLock            # nasze logi (tag PocketLock)
& $adb logcat -d -v time | Out-File $env:TEMP\logcat.txt -Encoding utf8   # pełny log do analizy
& $adb shell dumpsys dropbox --print      # pełne tombstone'y crashów (szukać "retroarch")
& $adb shell dumpsys activity activities | Select-String pocketlock
```

Ważne tagi/logi:
- `I/PocketLock`: `broadcast: ...`, `overlay attached/detached`, `media muted/unmuted`,
  `arming lock activity`, `early resume of the app behind the lock`, `app still playing behind the lock, stopping it`.
- Crash RetroArch: `F/DEBUG ... signal 11 ... video_driver_frame`.
- Zmiany stanu aktywności: `V/WindowManager: State movement ...`.

---

## 7. Buildy testowe w `dist/` (do porównań)

| Plik | versionCode | Co robi |
|---|---|---|
| `PocketLock-1.2.1-test.apk` | 54 | Bez pauzowania (overlay only) — brak mignięcia, ale gry chodzą w tle |
| `PocketLock-1.2.2-test.apk` | 55 | Opaque + opóźnienie animacji 250 ms — mignięcie wróciło, pauza za długa |
| `PocketLock-1.2.3-test.apk` | 56 | **Translucent** — brak mignięcia, dźwięk wrócił |
| `PocketLock-1.2.4-test.apk` | 57 | Translucent + mute + cykle pauzy |
| `PocketLock-1.2.5-test.apk` | 58 | Opaque + early resume + re-stop — **crash RetroArch** |
| `PocketLock-1.2.6-test.apk` | 59 | Translucent + mute — brak crasha, Mupen chodzi |
| `PocketLock-1.2.7-test.apk` | 60 | Opaque natychmiast — Mupen staje, RetroArch crashuje przy szybkim on/off |
| `PocketLock-1.2.8-test.apk` | 61 | **Obecny**: translucent + mute + adaptive `PauseActivity` (Mupen nie pauzuje) |

---

## 8. Stan gita

Ostatni commit: `eac2a66` (Release 1.2, versionCode 53). Wszystkie zmiany 1.2.x są **niezacommitowane**:

```
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/pl/iskri/pocketlock/LockOverlayView.kt
 M app/src/main/java/pl/iskri/pocketlock/LockService.kt
 M app/src/main/java/pl/iskri/pocketlock/Prefs.kt
 M app/src/main/res/values/styles.xml
?? app/src/main/java/pl/iskri/pocketlock/PauseActivity.kt
?? docs/HANDOFF.md
```

---

## 9. Sugerowany następny krok

1. Dodać logi diagnostyczne w `audioCheckRunnable` (`active=$active`, `count=$audioActiveCount`)
   oraz w `onScreenOn` (`keyguardLocked=...`) i w `PauseActivity.launch` (start/porażka).
2. Zbudować, zainstalować, poprosić użytkownika o test: Mupen w tle + blokada, potem `adb logcat -d -s PocketLock`.
3. Na podstawie logów wybrać dalszą drogę (naprawa detekcji vs. inny sygnał vs. usage stats vs. decyzja
   „nie zatrzymujemy” + po stronie RetroArcha zmiana sterownika na `gl`).
