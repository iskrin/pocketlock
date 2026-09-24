# Pocket Lock

Ekran blokady dla **Retroid Pocket Nova** (Android 13) w stylu Switcha: po wybudzeniu
konsoli pojawia się czarny ekran z 3 kropkami. Odblokowanie = 3 naciśnięcia dowolnego
przycisku lub 3 dotknięcia ekranu.

## Pliki wynikowe

- `dist/PocketLock.apk` – gotowy do instalacji (release, podpisany)
- `dist/PocketLock-debug.apk` – wersja debug (zapasowa)

## Instalacja

### Sposób 1: przez USB (adb)

1. Na konsoli włącz opcje programisty: Ustawienia → Informacje → 7x klik w „Numer kompilacji”.
2. W opcjach programisty włącz „Debugowanie USB”.
3. Podłącz konsolę kablem USB i uruchom na PC:

```
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r dist\PocketLock.apk
```

### Sposób 2: bez kabla

1. Skopiuj `PocketLock.apk` na konsolę (pendrive / karta microSD / chmura).
2. Otwórz plik menedżerem plików i zezwól na instalację z nieznanych źródeł.

## Konfiguracja konsoli (jednorazowo)

1. Otwórz aplikację **Pocket Lock**.
2. Nadaj uprawnienie **Nakładki** („Nakładki i inne okna” / „Display over other apps”).
3. Ustawienia → Zabezpieczenia → **Blokada ekranu** → ustaw **„Przesuń”** (chroni przed
   przyciskiem Home) albo „Brak”.
4. Włącz przełącznik **„Blokada włączona”** (uruchomi usługę + autostart po restarcie).
5. Wyłącz **optymalizację baterii** dla tej aplikacji.
6. Kliknij **„Przetestuj ekran blokady”** – powinien pojawić się czarny ekran z 3 kropkami.

## Jak to działa

- Usługa pierwszoplanowa nasłuchuje `ACTION_SCREEN_ON` i po każdym wybudzeniu ekranu
  pokazuje ekran blokady (aktywność z `showWhenLocked` + `turnScreenOn`).
- 3 kropki zapalają się po kolei; licznik się kumuluje (nie resetuje się).
- Po 3. kliknięciu aplikacja zdejmuje systemową blokadę i wraca do gry.
- Przyciski Home/Back nie zdejmują blokady (Back jest ignorowany, po Home ekran wraca).
- Diagnostyka: w ekranie ustawień widać ostatnio odebrany klawisz (keycode) oraz wersję Androida.

## Budowanie ze źródeł

Wymagania: JDK 17, Android SDK (platform 35, build-tools 35.0.0), Gradle 8.11.1.

```
$env:JAVA_HOME="$env:LOCALAPPDATA\Android\jdk17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:LOCALAPPDATA\Android\gradle-8.11.1\bin\gradle.bat assembleRelease
```

albo przez wrapper: `.\gradlew.bat assembleRelease`.

## Uwagi

- To zamek „konsolowy” (jak na Switchu), nie zabezpieczenie klasy bankowej – da się go
  obejść przez ADB/recovery.
- `keystore/` i `keystore.properties` nie są w repo (klucz podpisu). Bez nich zbuduje się
  tylko wersja debug. Zachowaj je, jeśli chcesz aktualizować zainstalowaną aplikację.
- Po pierwszym uruchomieniu system może chwilę pokazać własny ekran blokady zanim
  pojawi się Pocket Lock (normalne przy starcie z tła).
