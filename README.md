# Pocket Lock

Ekran blokady dla **Retroid Pocket Nova** (Android 13) w stylu Switcha: po wybudzeniu
konsoli pojawia się ekran z 3 kropkami na tle (domyślnie wbudowany obrazek, można ustawić
własne zdjęcie). Odblokowanie = 3 naciśnięcia dowolnego przycisku lub 3 dotknięcia ekranu.
Interfejs aplikacji jest w języku angielskim.

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
2. W zakładce **Permissions**: nadaj uprawnienie **Nakładki** („Display over other apps”)
   i wyłącz **optymalizację baterii**.
3. Ustawienia → Zabezpieczenia → **Blokada ekranu** → ustaw **„Brak”** (zalecane, patrz
   „Tryby działania”) albo „Przesuń”.
4. W zakładce **Options** włącz przełącznik **„Lock enabled”** (uruchomi usługę + autostart
   po restarcie); tam też są **Click sound**, **Vibration** i przycisk **Appearance…**.

Pełna instrukcja jest pod ikonką **(i)** w prawym górnym rogu ekranu aplikacji.

## Tryby działania

- **„Brak” + uprawnienie Nakładki = tryb nakładki (zalecany).** Czarna nakładka jest dodawana
  już przy gaszeniu ekranu, więc po naciśnięciu power pierwsza klatka to od razu ekran blokady –
  bez mignięcia gry i bez animacji „wjeżdżania”. Po 3 kliknięciach nakładka zjeżdża w dół
  i gra jest od razu tam, gdzie ją zostawiłeś.
- **„Przesuń” = tryb aktywności (zapasowy).** Systemowa blokada jest widoczna nad nakładką,
  więc aplikacja pokazuje ekran blokady jako aktywność nad nią, z wyłączoną animacją wejścia.
  Może wystąpić krótkie mignięcie obrazu.
- Wskazówka: w Opcjach programisty można wyłączyć „Skala animacji okna / przejścia / animatora”,
  co dodatkowo wygładza przejścia w całym systemie.

## Wygląd (Appearance)

Przycisk **Appearance…** w ustawieniach otwiera ekran z podglądem blokady 1:1 (stały u góry)
i trzema zakładkami (podgląd jest cały czas widoczny podczas regulacji):

- **Background** – trzy stany tła: **Choose image** (własne zdjęcie, kopiowane do aplikacji
  w oryginalnej rozdzielczości — limit 4096 px, zapis bezstratny: WebP lossless na Androidzie 11+,
  PNG na starszych, korekta obrotu EXIF), **Default image** (wbudowany obrazek
  `res/drawable-nodpi/default_background.png`, używany też domyślnie) oraz **Black background**
  (czarne tło). Do tego zoom i pozycja tła oraz przycisk **Reset** dla tych suwaków.
  Tło jest zawsze kadrowane tak, aby wypełniać ekran; suwakami X/Y przesuwasz kadr w zakresie
  zdjęcia (np. 16:9 na 4:3 — lewo/prawo, bez czarnych pasów). Przy zoomie mniejszym niż
  dopasowanie zdjęcie jest centrowane.
- **Dots** – rozmiar, odstęp i pozycja kropek + przycisk **Reset** dla tych suwaków.
- **Colors** – kolory kropek (aktywnej i nieaktywnej): barwa, nasycenie, jasność, krycie,
  oraz **Reset defaults**.

Gesty na podglądzie (przeciąganie/pinch) działają na warstwę wybranej zakładki.

Pozycje i przesunięcia zapisywane są jako ułamki rozmiaru ekranu, a kropki w dp × skala,
więc ten sam wygląd działa poprawnie na 16:9, 4:3 i innych rozdzielczościach.

## Jak to działa

- Usługa pierwszoplanowa nasłuchuje `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`.
- Bez systemowej blokady: przy gaszeniu ekranu dodawana jest czarna nakładka
  (`TYPE_APPLICATION_OVERLAY`) – jest gotowa, zanim wybudzisz ekran, więc nie ma mignięć.
- Z systemową blokadą („Przesuń”): pokazywana jest aktywność `showWhenLocked` + `turnScreenOn`
  z wyłączoną animacją wejścia (`FLAG_ACTIVITY_NO_ANIMATION`).
- 3 kropki zapalają się po kolei; licznik się kumuluje (nie resetuje się).
- Każde kliknięcie odtwarza dźwięk (`app/src/main/res/raw/press_click.ogg`) i wibruje;
  jedno i drugie można wyłączyć w sekcji Options.
- Po 3. kliknięciu ekran blokady dynamicznie zjeżdża w dół (350 ms, z przyspieszeniem),
  odkrywając grę/emulator dokładnie tam, gdzie została przerwana.
- Przyciski Home/Back nie zdejmują blokady.
- Diagnostyka: w ekranie ustawień widać ostatnio odebrany klawisz (keycode), status dźwięku
  i wersję Androida.

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
