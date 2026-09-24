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
3. Ustawienia → Zabezpieczenia → **Blokada ekranu** → ustaw **„Brak”** (zalecane, patrz
   „Tryby działania”) albo „Przesuń”.
4. Włącz przełącznik **„Blokada włączona”** (uruchomi usługę + autostart po restarcie).
5. Wyłącz **optymalizację baterii** dla tej aplikacji.
6. Kliknij **„Przetestuj ekran blokady”** – powinien pojawić się czarny ekran z 3 kropkami.

## Tryby działania

- **„Brak” + uprawnienie Nakładki = tryb nakładki (zalecany).** Czarna nakładka jest dodawana
  już przy gaszeniu ekranu, więc po naciśnięciu power pierwsza klatka to od razu ekran blokady –
  bez mignięcia gry i bez animacji „wjeżdżania”. Po 3 kliknięciach nakładka znika i gra jest
  od razu tam, gdzie ją zostawiłeś.
- **„Przesuń” = tryb aktywności (zapasowy).** Systemowa blokada jest widoczna nad nakładką,
  więc aplikacja pokazuje ekran blokady jako aktywność nad nią, z wyłączoną animacją wejścia.
  Może wystąpić krótkie mignięcie obrazu.
- Wskazówka: w Opcjach programisty można wyłączyć „Skala animacji okna / przejścia / animatora”,
  co dodatkowo wygładza przejścia w całym systemie.

## Jak to działa

- Usługa pierwszoplanowa nasłuchuje `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`.
- Bez systemowej blokady: przy gaszeniu ekranu dodawana jest czarna nakładka
  (`TYPE_APPLICATION_OVERLAY`) – jest gotowa, zanim wybudzisz ekran, więc nie ma mignięć.
- Z systemową blokadą („Przesuń”): pokazywana jest aktywność `showWhenLocked` + `turnScreenOn`
  z wyłączoną animacją wejścia (`FLAG_ACTIVITY_NO_ANIMATION`).
- 3 kropki zapalają się po kolei; licznik się kumuluje (nie resetuje się).
- Każde kliknięcie odtwarza krótki dźwięk (`app/src/main/res/raw/press_click.mp3`).
- Po 3. kliknięciu ekran blokady dynamicznie zjeżdża w dół (350 ms, z przyspieszeniem),
  odkrywając grę/emulator dokładnie tam, gdzie została przerwana.
- Przyciski Home/Back nie zdejmują blokady.
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
