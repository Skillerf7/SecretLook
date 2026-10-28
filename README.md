# Secret Lock

Native Android-App (Kotlin, Jetpack Compose) für das **Samsung Galaxy S20 FE 5G (Android 13)**.
Sie sperrt von dir ausgewählte Apps nach einem frei einstellbaren Zeitplan und gibt sie danach automatisch frei.
Alles läuft lokal: keine Cloud, keine Werbung, keine Analyse-SDKs, kein Datenbackup.

> **Stand dieser Lieferung (ehrlich):** Quellcode und Gradle-Wrapper sind vollständig.
> Es gibt einen GitHub-Actions-Workflow (`.github/workflows/build-apk.yml`), der auf `main`
> Unit-Tests und `assembleDebug` ausführt und die Debug-APK als Artefakt **secretlock-debug-apk** hochlädt.
> In der lokalen Erstellungsumgebung konnte wegen begrenztem RAM kein vollständiger Build
> abgeschlossen werden. Die App wurde **nicht auf einem Gerät getestet**.

## Bauen und installieren

Voraussetzung: Android Studio (Koala oder neuer) mit JDK 17 und installiertem Android SDK (API 34).

1. Ordner `SecretLock` in Android Studio öffnen („Open“), Gradle-Sync abwarten (lädt Gradle 8.7 laut `gradle-wrapper.properties`).
2. Handy: Entwickleroptionen → USB-Debugging an, per Kabel verbinden.
3. Run ▶ (Variante `debug`) oder per Konsole `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`, dann `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Der Wrapper ist vollständig (`gradle/wrapper/gradle-wrapper.jar` + Skripte). Ohne Android SDK schlägt der Build mit „SDK location not found“ fehl – das ist erwartetes Verhalten.

## CI: Debug-APK per GitHub Actions

Workflow: `.github/workflows/build-apk.yml`

- Startet bei Push auf `main` und manuell (`workflow_dispatch`).
- JDK 17, Android SDK Platform 34 + Build-Tools 34.0.0, vorhandener Gradle-Wrapper.
- Ablauf: `./gradlew testDebugUnitTest` → `./gradlew assembleDebug`.
- Artefakt-Name: **secretlock-debug-apk** (`app/build/outputs/apk/debug/*.apk`).
- Bei Fehler: Artefakt `test-and-build-reports`.

Nach dem Commit der Workflow-Datei: Actions-Tab → Lauf öffnen → Artefakte herunterladen.

## Einrichtung auf dem Handy

1. **Bedienungshilfe einschalten:** Einstellungen → Bedienungshilfe → Installierte Apps → *Secret Lock Sperre* → Ein.
   Bei einer per APK installierten App ist der Schalter unter Android 13 zunächst ausgegraut:
   Einstellungen → Apps → Secret Lock → ⋮ (oben rechts) → **„Eingeschränkte Einstellungen zulassen“**, danach erneut versuchen.
2. **Regel anlegen:** Verwalten → Neue Regel (Name, Start, Endzeit *oder* Dauer, Wochentage, Apps).
   Beispiel: YouTube/TikTok/Instagram, 22:00–07:00, täglich.
3. Optional: Exakte Alarme erlauben (Startseite → Berechtigungen) für minutengenaue Wechsel.
4. **Aktivieren:** Wiederherstellungsweg einrichten/bestätigen, dann „Aktivieren und Symbol ausblenden“.

## Funktionsweise

| Baustein | Aufgabe |
|---|---|
| `domain/ScheduleCalculator` | Zentrale, Android-freie Berechnung (Mitternacht, Wochentage, Zeitzone, Sommerzeit, mehrere Regeln, Sofortsperre, Notfall-Aufhebung). |
| `domain/ClockGuard` | Erkennt verstellte Uhr (monotone `elapsedRealtime`-Uhr). Eine Regel gilt als aktiv, wenn Wanduhr **oder** vertrauenswürdige Zeit sie aktiv sehen. |
| `engine/LockEngine` | Berechnet den Zustand bei jedem Aufruf neu (nie nur aus einem Timer), plant den nächsten Wechsel per `AlarmManager`. |
| `engine/SystemEventReceiver` | Boot, App-Update, `TIME_SET`, `TIMEZONE_CHANGED`, Änderung der Alarm-Berechtigung → neu berechnen. |
| `engine/RefreshWorker` | WorkManager alle ≥15 Min. als Sicherheitsnetz. **Kein** minutengenauer Timer. |
| `service/LockAccessibilityService` | Ereignisgesteuert (kein Loop): erkennt Vordergrund-App; gesperrt → Home + `BlockActivity` mit Restzeit. Bei Sperrbeginn wird die zuletzt gesehene Vordergrund-App sofort geprüft. |
| `ui/BlockActivity` | Sperrseite mit Countdown, schließt sich selbst bei Ablauf. |

Fehlt die Bedienungshilfe, zeigt die Startseite „Sperre fällig – NICHT durchgesetzt“ bzw. „Einrichtung unvollständig“ – nie fälschlich „aktiv“.

## Schutz vor versehentlichem Abbruch

Während einer aktiven Sperre sind bestehende Regeln schreibgeschützt (kein Ausschalten, Bearbeiten, Löschen); neue, zusätzliche Sperren sind erlaubt.
Einziger vorgesehener Verwaltungsweg zum vorzeitigen Beenden: **Verwalten → Notfall-Aufhebung** (60 s Wartezeit + Eingabe „ENTSPERREN“).
Sie beendet nur die gerade laufenden Fenster; spätere Sperrzeiten bleiben bestehen.

## Launcher-Symbol ausblenden und Wiederherstellung

Ausgeblendet wird ausschließlich der Launcher-Eintrag (`activity-alias` `LauncherAlias`) per `PackageManager.setComponentEnabledSetting`.
Die App bleibt in Einstellungen → Apps und unter den Bedienungshilfen sichtbar; sie verschleiert weder Existenz noch Berechtigungen.

Wiederherstellungswege (mindestens einer muss vor dem Ausblenden bestätigt sein, sonst verweigert die App):

1. **Bedienungshilfe-Einstellungen:** Einstellungen → Bedienungshilfe → Installierte Apps → Secret Lock Sperre (Einstellungs-Zugang über `settingsActivity`).
2. **Telefon-Code** `*#*#75625#*#*` in der Telefon-App.
3. **Schnelleinstellungs-Kachel** „Secret Lock“ (unter Android 13 per System-Dialog hinzufügbar; das Ergebnis bestätigt die App automatisch).

Alle drei Wege sind **nicht auf einem Gerät getestet**. Bestätige einen Weg erst, wenn du ihn selbst ausprobiert hast.
Immer möglich: Symbol über die Verwaltung wieder einblenden; App in Einstellungen → Apps deinstallieren.

## Berechtigungen

| Berechtigung | Warum |
|---|---|
| Bedienungshilfe (`BIND_ACCESSIBILITY_SERVICE`) | Nur Fenster-Wechsel-Ereignisse (Paketname der Vordergrund-App). `canRetrieveWindowContent=false`: keine Bildschirminhalte, keine Eingaben. |
| `RECEIVE_BOOT_COMPLETED` | Zeitpläne nach Neustart wiederherstellen. |
| `SCHEDULE_EXACT_ALARM` | Sperrbeginn/-ende minutengenau auslösen. Ohne: ungenauer Alarm, die Durchsetzung läuft trotzdem über den Service. |
| `<queries>` (Launcher/Home) | Sichtbarkeit installierter Apps ohne `QUERY_ALL_PACKAGES`. |

Nicht verwendet: Geräteadministrator, Root, Internet, Nutzungsstatistik, Overlay-Berechtigung.
Es werden keine Zugangsdaten, App-Nutzungsdaten oder persönlichen Daten gespeichert oder protokolliert (nur Regeln, Uhr-Anker, bestätigte Wiederherstellungswege).

## Bekannte Grenzen

- Das ist eine **Annäherung**, keine systemweite Kontrolle: Wer die Bedienungshilfe ausschaltet, die App deinstalliert, sie „Beenden erzwingt“ oder das Gerät zurücksetzt, hebt die Sperre auf.
- **Auffindbarkeit in der Suche bei ausgeblendetem Symbol ist launcherabhängig** und kann auf One UI fehlen. Das ist nicht geprüft. Verlass dich auf die Wiederherstellungswege.
- Uhrzeit: Verstellen der Uhr **innerhalb eines Boots** wird erkannt und verkürzt die Sperre nicht. Ein Vorstellen der Uhr **und anschließender Neustart** ist technisch nicht erkennbar; „Automatische Uhrzeit“ aktiv lassen (die App warnt, wenn sie aus ist).
- Eine bereits geöffnete Ziel-App wird erkannt, sobald der Dienst ein Fenster-Ereignis dieser App gesehen hat; direkt nach frischem Dienststart/Boot greift die Sperre erst beim nächsten Fenster-Wechsel.
- Hintergrundaudio/PiP einer Ziel-App kann je nach App kurz weiterlaufen; Home wird beim Sperren ausgelöst.
- Der Telefon-Code kann auf neueren One-UI-Versionen oder durch Hintergrund-Start-Beschränkungen nicht funktionieren.
- Sommer-/Winterzeit: Fenster werden in lokaler Wanduhrzeit berechnet (z. B. 22:00–07:00 bleibt 22:00–07:00; die Nacht ist dann 8 bzw. 10 Stunden lang).

## Tests und Teststatus

| Test | Datei | Status |
|---|---|---|
| Sperrbeginn/-ende, Mitternacht, Wochentage, 24 h, Zeitzone, Sommer-/Winterzeit, mehrere Regeln, Sofortsperre, Notfall-Aufhebung, nächster Wechsel | `ScheduleCalculatorTest` | **geschrieben, nicht ausgeführt** (kein Android SDK → Gradle kann Android-Plugin nicht auflösen). Domain-Logik ist pure JVM; Algorithmus wurde manuell gegen die Testfälle geprüft. |
| Uhr vor-/zurückstellen, Neustart | `ClockGuardTest` | **geschrieben, nicht ausgeführt** |
| Speichern/Laden, Wiederherstellung nach Neustart, defekte Daten | `RuleJsonTest` | **geschrieben, nicht ausgeführt** |
| Fehlende Berechtigungen (nie „aktiv“ behaupten), Ausblend-Voraussetzungen | `EnforcementTest` | **geschrieben, nicht ausgeführt** |
| Launcher-Eintrag aus-/einblenden, Verwaltung bei ausgeblendetem Symbol erreichbar, Ausblenden ohne Wiederherstellungsweg verweigert | `androidTest/…InstrumentedTest` | **nicht getestet** (braucht Gerät) |
| Sperre auf dem S20 FE, Boot-Wiederherstellung, Doze, Wiederherstellungswege, Suche | – | **nicht getestet** (nur manuell auf dem Gerät möglich) |

Ausführen (mit Android SDK): `./gradlew testDebugUnitTest` (JVM-Unit-Tests) bzw. `./gradlew connectedDebugAndroidTest` (Gerät).

## Manuelle Checkliste für das Gerät

1. Regel 2 Minuten in der Zukunft anlegen → Ziel-App öffnen → Sperrseite mit Restzeit; nach Ablauf wieder nutzbar.
2. Ziel-App öffnen, dann Sofortsperre starten → App wird sofort gesperrt.
3. Neustart während einer Sperre → Sperre danach weiter aktiv.
4. Uhrzeit von Hand verstellen (automatische Zeit aus) → Warnhinweis, Sperre bleibt.
5. Alle drei Wiederherstellungswege ausprobieren, **bevor** das Symbol ausgeblendet wird.
