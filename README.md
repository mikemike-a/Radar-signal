# Presence Radar 📡

Presence Radar est une application Android native complète, performante et économe en batterie, conçue pour détecter en continu et de manière **100% locale** (sans aucun serveur ni envoi de données) la présence d'appareils Bluetooth Low Energy (BLE), Wi-Fi et services réseau mDNS.

🚀 **Fonctionnalités Clés :**
- **Visualisation Radar Interactive** : Un balayage en temps réel avec estimation de distance (RSSI) et placement spatial des balises (BLE, Wi-Fi, mDNS).
- **Mode Chasse (Hunt)** : Retrouvez vos objets perdus avec une interface "chaud/froid", une carte thermique (Heatmap) et un **effet Geiger sonore et haptique**.
- **Balise de Secours SOS** : Transformez votre téléphone en balise de détresse (sirène, flash morse, émission radio BLE SOS) détectable par d'autres utilisateurs en cas d'urgence.
- **Détection de Stalkers** : Surveillance intelligente de l'historique pour détecter les trackers (AirTags, etc.) qui vous suivraient de manière suspecte.
- **Diagnostic Réseau & Étages** : Analyse de l'encombrement Wi-Fi et détection de l'étage (RDC, Étage 1) via le baromètre physique du téléphone.

## 🛠 Choix d'Architecture

L'application est structurée selon les principes de l'architecture **MVVM (Model-View-ViewModel)** et du **Clean Architecture**, répartis dans les packages suivants :

1. **`com.example.data`** (Données & Persistance) :
   - `KnownDevice` (Entité) : Représente un appareil de confiance enregistré par l'utilisateur (via son adresse MAC, BSSID ou nom d'annonce Bluetooth/Wi-Fi).
   - `HistoryEntry` (Entité) : Enregistre de manière immuable les événements d'arrivées (`ARRIVED`) et de départs (`DEPARTED`) avec horodatage.
   - `DeviceDao` & `AppDatabase` : Gérés via **Room (SQLite)** de manière asynchrone avec des requêtes réactives renvoyant des `Flow<List<T>>` pour mettre à jour l'interface automatiquement en temps réel.

2. **`com.example.scanning`** (Moteur de Détection) :
   - `PresenceScanner` : Centralise et synchronise les scans BLE (via `BluetoothLeScanner`), Wi-Fi (via `WifiManager`) et services réseau mDNS (via `NsdManager`).
   - Il calcule en continu l'estimation de distance via le RSSI (en utilisant un modèle logarithmique d'affaiblissement du signal) et gère le filtrage de sensibilité dynamique.
   - Il maintient un dictionnaire d'appareils actifs (`ConcurrentHashMap`) mis à jour en arrière-plan de façon thread-safe.

3. **`com.example.service`** (Opération en Arrière-plan) :
   - `PresenceService` : Un **Foreground Service (Service au premier plan)** hautement optimisé qui tourne de manière persistante sur Android Oreo (API 26) jusqu'à Android 15+. 
   - Il maintient une notification discrète dans la barre d'état et déclare le bon `foregroundServiceType="connectedDevice"` sur Android 14+ pour un respect strict du cycle de vie du système sans risquer d'être tué par l'OS.

4. **`com.example.ui` & `MainActivity`** (Interface Homme-Machine) :
   - Une interface utilisateur moderne, construite entièrement en **Jetpack Compose** et respectant la charte Material Design 3.
   - Elle propose un thème **Obsidian Dark Tech** très soigné, avec un widget Canvas interactif simulant un radar de balayage circulaire qui place les appareils émetteurs sous forme de balises lumineuses selon leur puissance de signal (RSSI).

---

## 🔋 Optimisations de la Batterie

Le scan permanent d'ondes radio est l'une des tâches les plus énergivores sur mobile. Notre solution applique des stratégies drastiques pour minimiser la décharge de la batterie :

- **Duty Cycle intelligent** : Au lieu d'avoir un scan BLE ouvert à 100% du temps (qui viderait la batterie en quelques heures), le service utilise des cycles alternés de scan et de repos (par défaut : 15 secondes d'activité suivies d'une pause, configurable via le mode économie).
- **Scan Settings optimisés** :
  - **Mode normal** : Utilise le mode `SCAN_MODE_LOW_LATENCY` pour une réactivité maximale quand l'utilisateur configure ses appareils.
  - **Mode économie de batterie** : Bascule automatiquement en `SCAN_MODE_LOW_POWER` pour minimiser le temps d'écoute radio.
- **Cache Wi-Fi passif** : Au lieu de forcer continuellement des requêtes d'envoi de sondes Wi-Fi actives, l'application lit périodiquement les résultats de scan mis en cache par l'OS Android, évitant ainsi de réveiller inutilement le contrôleur matériel Wi-Fi du téléphone.

---

## 🛑 Limites Techniques des Scans sous Android Récent

Il est crucial de comprendre les restrictions imposées par les versions récentes d'Android lors de l'utilisation de l'application :

1. **Randomisation des Adresses MAC** :
   - *Problématique* : Par mesure de sécurité et de confidentialité, les smartphones récents (iOS et Android) modifient constamment (toutes les quelques minutes) l'adresse MAC Bluetooth qu'ils annoncent publiquement.
   - *Solution Presence Radar* : Vous pouvez enregistrer un appareil connu non seulement par son adresse MAC, mais également par son **nom d'annonce public (SSID ou Nom Bluetooth)**. Si le téléphone annonce "iPhone de Marc", l'application le reconnaîtra de manière stable, peu importe la rotation de son adresse MAC physique.

2. **Bridage (Throttling) du Scan Wi-Fi** :
   - *Problématique* : Android limite sévèrement le nombre de scans Wi-Fi pour préserver la batterie :
     - Les applications au premier plan ne peuvent scanner que **4 fois toutes les 2 minutes**.
     - Les applications en arrière-plan sont bridées à **1 seul scan toutes les 30 minutes**.
   - *Solution Presence Radar* : L'application s'appuie principalement sur le scan Bluetooth Low Energy (BLE) en arrière-plan (qui n'est pas bridé de cette manière) pour la détection de présence en temps réel, tout en utilisant le Wi-Fi comme indicateur secondaire lors des interactions de premier plan.

---

## 🧪 Comment Tester l'Application

### Prérequis physiques
Pour tester pleinement le comportement de balayage et de détection, il est **fortement recommandé** d'utiliser un **appareil Android physique** :
1. Activez le Bluetooth et le Wi-Fi sur votre appareil de test.
2. Démarrez l'application et accordez les autorisations à l'écran d'accueil (Localisation, Bluetooth et Notifications).
3. Activez l'interrupteur "Scan" en haut à droite. Des dizaines d'appareils émetteurs (écouteurs, montres, balises, réseaux Wi-Fi) vont apparaître en temps réel sur le radar graphique et dans la liste.
4. Cliquez sur le bouton `+` à côté d'un appareil détecté pour lui attribuer un alias (ex : "Mes Écouteurs").
5. Éteignez ou éloignez l'appareil ciblé. Après le délai d'absence configuré (dans les paramètres), vous verrez l'appareil disparaître de la liste "Présent" et une entrée de départ s'ajoutera automatiquement dans l'onglet **Historique**.

### Exécution des Tests Unitaires et de Non-Régression
Vous pouvez exécuter les tests automatisés locaux dans la JVM de votre machine de développement :

- **Exécuter les tests unitaires et de cycle de vie** :
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Vérifier l'intégrité visuelle (Tests de capture d'écran Roborazzi)** :
  ```bash
  gradle :app:verifyRoborazziDebug
  ```
