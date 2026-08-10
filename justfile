# justfile - Commandes de build, test et déploiement pour le projet Android

# Affiche la liste complète de toutes les commandes disponibles (comportement par défaut)
default:
    @just --list

# Compile l'application complète en mode Debug et génère le fichier APK
build:
    gradle :app:assembleDebug

# Compile rapidement le code Kotlin du projet pour vérifier l'absence d'erreurs de syntaxe ou de type
compile:
    gradle :app:compileDebugKotlin

# Exécute tous les tests unitaires locaux (JUnit & Robolectric) sur la JVM
test:
    gradle :app:testDebugUnitTest

# Vérifie l'interface graphique contre les régressions visuelles (Roborazzi)
verify-screenshots:
    gradle :app:verifyRoborazziDebug

# Enregistre de nouvelles images de référence pour les tests d'interface graphique (Roborazzi)
record-screenshots:
    gradle :app:recordRoborazziDebug

# Nettoie le cache Gradle, les dossiers de build locaux et les fichiers temporaires
clean:
    gradle clean

# Installe l'APK de Debug compilé sur l'émulateur ou l'appareil physique connecté
install:
    gradle :app:installDebug
