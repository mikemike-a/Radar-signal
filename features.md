# Radar & Hunt App - Fonctionnalités et Feuille de Route

Ce document dresse la liste des fonctionnalités actuellement présentes dans l'application, ainsi que des idées d'améliorations et de nouvelles fonctionnalités à développer pour enrichir l'outil.

## 🛠 Fonctionnalités Actuelles

- **Détection Multi-sources d'Appareils (Scanners) :**
  - **Bluetooth LE (BLE) :** Détection continue et estimation de distance.
  - **Wi-Fi Scan :** Recherche des réseaux et points d'accès à proximité.
  - **Réseau Local (mDNS / Bonjour / UPnP) :** Découverte des services réseau actifs (AirPlay, Chromecast, Spotify Connect, etc.) même si le Bluetooth est désactivé.
- **Visualisation Radar & Radar Plein Écran :**
  - Interface graphique avec balayage angulaire et baguettes d'échelle en mètres (1m, 3m, 5m, 10m, 15m).
  - Mode Plein Écran interactif permettant de cliquer sur n'importe quelle puce du radar pour afficher l'identité, le type, la distance et engager la traque.
- **Suivi en Arrière-plan & Notifications :**
  - **Service Premier Plan (Foreground Service) :** Analyse continue du spectre BLE/Wi-Fi/mDNS avec notification statutaire permanente et bouton d'arrêt rapide.
  - **Notifications d'Arrivée & Alertes Anti-oubli :** Envoi d'alertes push hautement prioritaires (son/vibration) lorsqu'un appareil connu entre dans le champ ou quitte la zone de détection.
  - **Panneau de Test des Notifications :** Boutons dédiés dans les paramètres pour vérifier le bon fonctionnement des alertes.
- **Mode Chasse (Hunt / Tracker) :** 
  - Interface immersive "chaud/froid" pour retrouver un appareil physique.
  - Indicateur visuel à impulsion (pulsing ring) et échelle radiale de proximité.
  - Estimation de la distance en mètres via la force du signal (RSSI).
  - Tendance du signal (approche ou éloignement).
  - **Heatmap (Carte thermique locale) :** Construction d'une carte 2D de la force du signal en se déplaçant pour mieux repérer la cible.
- **Altimètre & Baromètre Intégré :** 
  - Estimation de l'altitude relative et détection de l'étage (RDC, Étage 1, Sous-sol, etc.) grâce au capteur de pression physique.
- **Balise de Détresse SOS (Sauvetage en décombres) :**
  - **Déclenchement Multiple :** Immédiat, différé par compte à rebours, ou automatique en cas d'inactivité prolongée (détection de non-mouvement par l'accéléromètre).
  - **Émissions Multi-canaux :** Activation d'une sirène sonore à 100% de volume, clignotement du flash d'appareil photo en code Morse SOS (`... --- ...`), et émission radio active par paquets Bluetooth LE (BLE) SOS spécifiques.
  - **Détection active croisée :** N'importe quel autre smartphone équipé de l'application détecte instantanément cette balise SOS avec une alerte rouge clignotante prioritaire sur son radar.
- **Gestion des Appareils Connus :** 
  - Sauvegarde d'appareils avec un nom personnalisé (alias).
  - Assignation d'un "étage" de référence à un appareil pour comparer facilement sa position verticale avec celle du téléphone.
  - Filtres rapides (Connus, BLE, Wi-Fi, mDNS) pour identifier les appareils familiers parmi tous les signaux ambiants.
- **Historique & Journal des Événements :** Suivi horodaté et géolocalisé des arrivées et départs d'appareils.

---

## 💡 Idées de Nouvelles Fonctionnalités (Roadmap)

Voici une liste d'idées pour transformer cet outil en un véritable couteau suisse de la détection et du tracking :

### 1. Réalité Augmentée (AR) & Boussole
- **AR Finder :** Utiliser la caméra du téléphone via ARCore pour afficher des particules flottantes ou un pointeur 3D là où le signal semble le plus fort.
- **Boussole Magnétique :** Combiner l'accéléromètre, le gyroscope et le magnétomètre pour orienter l'utilisateur (si l'appareil ciblé supporte la radiogoniométrie / Bluetooth Direction Finding 5.1+).

### 2. Retours Haptiques & Auditifs Avancés (Geiger Counter)
- **Radar Sonore & Bip Haptique :** Émettre un son ou une vibration dont la fréquence et l'intensité augmentent proportionnellement au RSSI, permettant de chasser un appareil "à l'aveugle" (pratique pour chercher sous les meubles ou dans le noir).

### 3. Cartographie & Triangulation
- **Triangulation collaborative :** Si plusieurs personnes utilisent l'application dans la même zone, fusionner leurs données RSSI en temps réel pour trianguler la position exacte de la cible sur une carte.

### 4. Partage & Cloud
- **Recherche Communautaire (Crowd-finding) :** Si un appareil est marqué comme "Perdu", d'autres utilisateurs de l'application pourraient le détecter silencieusement en passant à côté et mettre à jour sa position sur les serveurs.
- **Synchronisation des Appareils Connus :** Sauvegarder ses trackers et alias sur un compte cloud pour les retrouver sur un autre téléphone (Export/Import JSON local dans un premier temps).

### 5. Analyse des Signaux & Sécurité
- **Détection de "Stalkers" :** Analyser l'historique pour alerter l'utilisateur si un appareil inconnu (comme un AirTag ou un tracker BLE) le suit sur de longues distances.
- **Outil de diagnostic Wi-Fi :** Analyser l'encombrement des canaux Wi-Fi pour aider l'utilisateur à optimiser le placement de son routeur à la maison.

