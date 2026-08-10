# Radar & Hunt App - Fonctionnalités et Feuille de Route

Ce document dresse la liste des fonctionnalités actuellement présentes dans l'application, ainsi que des idées d'améliorations et de nouvelles fonctionnalités à développer pour enrichir l'outil.

## 🛠 Fonctionnalités Actuelles

- **Détection d'Appareils (Scanners) :** Recherche et identification des appareils Bluetooth (BLE) et Wi-Fi environnants.
- **Visualisation Radar :** Interface graphique permettant de visualiser la densité et la proximité des signaux autour de l'utilisateur.
- **Mode Chasse (Hunt / Tracker) :** 
  - Interface immersive "chaud/froid" pour retrouver un appareil physique.
  - Indicateur visuel à impulsion (pulsing ring) dont la vitesse s'adapte à la distance.
  - Estimation de la distance en mètres via la force du signal (RSSI).
  - Tendance du signal (approche ou éloignement).
- **Altimètre & Baromètre Intégré :** 
  - Estimation de l'altitude relative et détection de l'étage (RDC, Étage 1, Sous-sol, etc.) grâce au capteur de pression.
  - Mode simulateur de pression pour les téléphones dépourvus de capteur barométrique matériel.
- **Gestion des Appareils Connus :** 
  - Sauvegarde d'appareils avec un nom personnalisé (alias).
  - Assignation d'un "étage" de référence à un appareil pour comparer facilement sa position verticale avec celle du téléphone.
  - Filtre rapide pour identifier les appareils familiers parmi tous les signaux ambiants.
- **Historique & Paramètres :** Suivi des détections passées et personnalisation de l'application (écrans prévus dans la navigation).

---

## 💡 Idées de Nouvelles Fonctionnalités (Roadmap)

Voici une liste d'idées pour transformer cet outil en un véritable couteau suisse de la détection et du tracking :

### 1. Suivi en Arrière-plan & Notifications
- **Alertes d'entrée/sortie :** Recevoir une notification push lorsqu'un appareil "Connu" entre dans le champ de détection (ex: "Le sac à dos est à portée") ou lorsqu'il le quitte (alarme anti-oubli/anti-vol).
- **Scan passif :** Continuer à enregistrer l'historique des appareils rencontrés avec leur position GPS approximative sans avoir besoin de garder l'écran allumé.

### 2. Réalité Augmentée (AR) & Boussole
- **AR Finder :** Utiliser la caméra du téléphone via ARCore pour afficher des particules flottantes ou un pointeur 3D là où le signal semble le plus fort.
- **Boussole Magnétique :** Combiner l'accéléromètre, le gyroscope et le magnétomètre pour orienter l'utilisateur (si l'appareil ciblé supporte la radiogoniométrie / Bluetooth Direction Finding 5.1+).

### 3. Cartographie & Triangulation
- **Heatmap (Carte thermique locale) :** Demander à l'utilisateur de se déplacer dans la pièce pour construire une carte 2D de la force du signal, permettant de "voir" les ondes à travers les murs.
- **Triangulation collaborative :** Si plusieurs personnes utilisent l'application dans la même zone, fusionner leurs données RSSI en temps réel pour trianguler la position exacte de la cible sur une carte.

### 4. Partage & Cloud
- **Recherche Communautaire (Crowd-finding) :** Si un appareil est marqué comme "Perdu", d'autres utilisateurs de l'application pourraient le détecter silencieusement en passant à côté et mettre à jour sa position sur les serveurs.
- **Synchronisation des Appareils Connus :** Sauvegarder ses trackers et alias sur un compte cloud pour les retrouver sur un autre téléphone (Export/Import JSON local dans un premier temps).

### 5. Retours Haptiques & Auditifs Avancés
- **Radar Sonore (Geiger Counter) :** Émettre un son ou une vibration (retour haptique) dont la fréquence et l'intensité augmentent proportionnellement au RSSI, permettant de chasser un appareil "à l'aveugle" (pratique pour chercher sous les meubles ou dans le noir).

### 6. Analyse des Signaux & Sécurité
- **Détection de "Stalkers" :** Analyser l'historique pour alerter l'utilisateur si un appareil inconnu (comme un AirTag ou un tracker BLE) le suit sur de longues distances.
- **Outil de diagnostic Wi-Fi :** Analyser l'encombrement des canaux Wi-Fi pour aider l'utilisateur à optimiser le placement de son routeur à la maison.
