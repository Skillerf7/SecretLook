# Testbericht Launcher-Sichtbarkeit (v1.0.1)

## Analyse (am Quellcode geprüft)
- `AndroidManifest.xml`: `MainActivity` hat keinen Launcher-Filter und ist `exported`. Der Launcher-Eintrag ist der Alias `.LauncherAlias` (Ziel `.ui.MainActivity`).
- `LauncherVisibility.kt`: schaltet ausschliesslich den Alias per `setComponentEnabledSetting(..., DONT_KILL_APP)`. `MainActivity` wird nirgends deaktiviert, Start-Intents (Kachel, Telefon-Code, Bedienungshilfen-Zahnrad) zeigen direkt auf `MainActivity`.
- Folge: Anforderung "Start-Activity bleibt aktiv" war schon erfüllt. Die Ursache des toten Suchtreffers ist sehr wahrscheinlich ein Treffer, der auf den abgeschalteten Alias zeigt. Android kann eine deaktivierte Komponente nicht starten. (Schlussfolgerung aus dem Code, am Gerät nicht gemessen.)

## Geht "aus App-Drawer weg, in Samsung-Suche da, Treffer öffnet"?
- Nicht belegt. Mir ist keine dokumentierte Android-Methode bekannt, einen Launcher-Eintrag nur aus dem Drawer zu entfernen und gleichzeitig für die Suche zu behalten.
- Die Samsung-eigene Funktion "Apps ausblenden" lässt den Launcher-Eintrag aktiv. Zu ihrem Suchverhalten widersprechen sich Quellen: manche Anleitungen sagen, ausgeblendete Apps bleiben suchbar, ein Samsung-Community-Beitrag berichtet das Gegenteil nach einem Update. Beides ist hier ungeprüft.

## Umgesetzt
1. Zweiter Weg "Samsung-eigenes Ausblenden" im Aktivieren-Screen: schaltet den Alias wieder ein, öffnet die Home-Einstellungen (Rückfall: allgemeine Einstellungen), erklärt die Schritte.
2. Diagnose-Karte: Zustand von Alias und MainActivity, "Verwaltung startbar", Anzahl Launcher-Einträge.
3. Ehrliche Texte (Dialog, Hinweis, Info-Seite).
4. Fester Debug-Schlüssel (`app/debug.keystore`), damit APK-Updates über die installierte App möglich sind und Regeln/Einstellungen erhalten bleiben. Einmalig nötig: alte Version deinstallieren (Daten gehen dabei verloren), weil sie mit einem zufälligen Schlüssel signiert wurde.
5. Instrumentierte Tests ergänzt.

## Teststatus
| Test | Status |
|---|---|
| Code kompiliert | NICHT geprüft (kein Android SDK hier), GitHub-Build entscheidet |
| Unit-Tests | vorhanden, hier nicht ausgeführt |
| Instrumentierte Tests (Alias aus, MainActivity aktiv, 0/1 Launcher-Einträge) | geschrieben, NICHT ausgeführt |
| Symbol aus Drawer/Home nach Ausblenden | NICHT am Gerät getestet |
| Samsung-Suche findet App / Treffer öffnet | NICHT getestet, Ergebnis offen |
| Samsung "Apps ausblenden" + Suche | NICHT getestet |
| Intent zu Samsung-Home-Einstellungen | NICHT getestet |
| Regeln bleiben nach Ausblenden | Code fasst nur den Alias an (DONT_KILL_APP), am Gerät nicht getestet |

## Test-Reihenfolge am Handy
1. Einblenden. Aktivieren → "Alternative" → Samsung "Apps ausblenden" → Secret Lock wählen.
2. Home-Screen/Drawer: weg? Finder: Secret Lock suchen, Treffer antippen.
3. Klappt es nicht: "Samsung-Ausblenden" rückgängig, stattdessen "Aktivieren und Symbol ausblenden" und über Kachel/Telefon-Code öffnen.
