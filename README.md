# Psí tlumočník

Android appka, která poslouchá štěkot psa a odhaduje, v jaké situaci štěká (cizí člověk, hra, samota, strach…). Učí se na konkrétním psovi z nahrávek, které majitel označí.

**Verze:** 1.00 · **Package:** `io.github.marciano.psitlumocnik` · minSdk 26 · Kotlin + Jetpack Compose (Material 3)

## Co umí

- **Víc psů**, každý má vlastní nahrávky a vlastní naučený model.
- **Nahrávání** z mikrofonu (zdroj UNPROCESSED, bez automatického zesílení) nebo **import souboru** (mp3, m4a, ogg/opus, wav, video). Nahrávku jde do appky i **sdílet** z WhatsAppu a podobně.
- **Rozbor** každé nahrávky:
  - detekce jednotlivých štěknutí podle obálky hlasitosti,
  - výška (YIN), čistota hlasu / chraplavost, spektrální těžiště a plochost, délka, tempo a pravidelnost série,
  - **YAMNet** (Google, AudioSet, 521 tříd) přes LiteRT: ověří, že jde o psa (štěkot, vrčení, kňučení, vytí…), a dodá „otisk“ zvuku.
- **Výklad** = mix obecných pravidel (Mortonova motivačně-strukturní pravidla, výzkum Pongrácz / Molnár) a k-NN modelu naučeného z označených nahrávek psa. Čím víc označených, tím větší váha naučeného modelu.
- **Označování**: situace + řeč těla + poznámka.
- **Hlídání**: služba na popředí nechá mikrofon běžet, každé štěkání sama nahraje (2 s před, max 60 s), rozebere a uloží. Nahrávky jsou předvyplněné jako „Samota“ a čekají na potvrzení.
- **Statistiky**: úspěšnost modelu (leave-one-out), „slovník“ psa po situacích, co ještě dotočit.
- **Export/import profilu psa** jako soubor `.psi` (ZIP s JSONem, volitelně se zvuky): poslat kámošovi, zálohovat.
- Světlý/tmavý režim, všechno offline, nic se neposílá na internet.

## Build

Android Studio (aktuální verze s podporou AGP 9.4) nebo příkazová řádka:

```
./gradlew assembleDebug        # testovací APK
./gradlew testDebugUnitTest    # unit testy analýzy
./gradlew assembleRelease      # podepsané APK (viz níže)
```

Verze: AGP 9.4.1, Gradle 9.8.0, Kotlin 2.4.20, Compose BOM 2026.09.00, LiteRT 2.2.0, compileSdk/targetSdk 37.

`android.uniquePackageNames=false` v `gradle.properties` je nutné: LiteRT 2.x používá stejný namespace ve dvou knihovnách a AGP 9 by to jinak odmítl.

### Podepisování

Release se podepisuje vlastním klíčem (ne debug, kvůli Play Protect). Údaje se berou z proměnných prostředí `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, nebo lokálně ze souboru `keystore.properties` v kořeni (je v `.gitignore`). Bez klíče se release podepíše debug klíčem.

### GitHub Actions

`.github/workflows/build.yml` při každém pushi spustí testy a sestaví release APK (artefakt). Při pushi tagu `v*` (např. `v1.00`) vytvoří GitHub Release s APK. Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Struktura

```
analysis/   Dsp (YIN, FFT), BarkAnalysis (detekce + vlastnosti), YamNet, Analyzer, Interpretation (pravidla + k-NN)
audio/      mikrofon, dekodér souborů, WAV, přehrávač
data/       modely, úložiště (data.json + audio/*.wav), nastavení
service/    WatchService – hlídání
transfer/   export/import profilu
ui/         Compose obrazovky, téma, ViewModel
```

## Poctivě

Překladač psí řeči to není. Spolehlivě jde odhadnout emoční ladění a situaci. Výzkum na neznámých psech dosahuje kolem 40 % u šesti situací (náhoda 17 %); na jednom psovi s vlastními daty to jde výrazně líp. Rozhodující je kvalita označení: označuj, co se dělo, ne co si myslíš, že pes chtěl říct.

## Licence modelu

`app/src/main/assets/yamnet.tflite` a `yamnet_class_map.csv`: YAMNet, © Google, Apache License 2.0.
