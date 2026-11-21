EDITOR EXIF ANDROID - PROGETTO 1.1

Progetto Android nativo Kotlin + Jetpack Compose, con layout adattivo e pannello EXIF su tablet.

Funzioni incluse nel codice:
- Selezione cartella tramite Storage Access Framework
- Miniature con nome e dimensione; selezione multipla
- Ordinamento per nome, dimensione ed estensione (crescente/decrescente)
- Lettura EXIF: data, esposizione, diaframma, ISO, focale, focale equivalente, modello obiettivo, marca/modello e flash
- Importazione XLSX (prima scheda, prime quattro colonne: modello, focale, focale equivalente, diaframma)
- Scrittura EXIF per obiettivo, focale, focale equivalente e diaframma su file supportati
- Spostamento dei file selezionati in una cartella scelta
- Eliminazione dei file selezionati
- Visualizzatore a schermo intero con pinch zoom e scorrimento orizzontale
- Esportazione JPG qualità 70, lato massimo 2000 pixel, proporzioni mantenute
- Rinomina progressiva con prefisso e numero iniziale

NOTE IMPORTANTI
- La scrittura EXIF è pensata soprattutto per JPEG. Il supporto alla scrittura dipende dal formato e dal provider di archiviazione.
- L'importazione XLSX legge la prima scheda e assume intestazioni nella prima riga e dati nelle colonne A-D.
- I RAW possono essere visualizzati solo se Android/il dispositivo dispone di un decoder compatibile.
- La cancellazione usa il provider SAF e può essere definitiva (non garantisce il Cestino Android).
- L'esportazione usa la cartella selezionata nel selettore destinazione; verificare sempre l'esito prima di cancellare gli originali.
- L'app richiede test su dispositivo reale: questo ambiente non dispone di Android SDK/Gradle e non può produrre o verificare l'APK.

BUILD APK SU WINDOWS
1. Installa Android Studio recente e Android SDK Platform 35.
2. Apri questa cartella in Android Studio.
3. Attendi la sincronizzazione Gradle (serve connessione al primo avvio).
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. File: app/build/outputs/apk/debug/app-debug.apk

Requisiti: JDK 17. Min SDK 26 (Android 8.0).


GENERAZIONE AUTOMATICA APK SU WINDOWS
1. Installa Android Studio dal sito ufficiale Android Developer.
2. Durante la configurazione lascia installare Android SDK.
3. Apri Android Studio > More Actions > SDK Manager > SDK Tools.
4. Verifica che "Android SDK Command-line Tools (latest)" sia installato.
5. Chiudi Android Studio.
6. Fai doppio clic su CREA_APK.bat.
7. Al primo avvio lo script scarica Gradle 8.9 e i componenti Android mancanti.
8. Al termine trovi APK\EditorEXIF.apk.

Serve una connessione internet al primo avvio. Lo script non richiede di modificare il codice.
Se la compilazione fallisce, conserva il testo completo della finestra nera e invialo per diagnosticare l'errore.
