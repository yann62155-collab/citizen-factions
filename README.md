# CITIZEN Factions

Mod Minecraft Java **1.21.1** — **NeoForge 21.1.215** — Java 21.

Système de factions et de lien avec les dragons pour le serveur CITIZEN.

## Factions et grades

- **Dragonnier** : peut monter les dragons non corrompus.
- **Chasseur de dragon** : peut monter les dragons noirs, gris et cuivrés.
- Grades administrables : **Primodiau**, **Alpha**, **Confirmé**, **Petit**.
- Seuls les opérateurs niveau 2 peuvent créer les factions, ajouter/retirer/transférer des joueurs, consulter les listes et modifier les grades.
- Un joueur ne peut appartenir qu'à une seule faction à la fois.
- Le transfert conserve le grade du joueur.

## Lien avec le dragon

- +1 point par minute passée en selle, jusqu'à 100.
- -1 point par tranche complète de 10 minutes après avoir abandonné le dragon.
- La valeur est sauvegardée sur le dragon et par UUID de joueur : elle survit à la mort du joueur et au redémarrage du serveur.
- Barre de boss affichant le niveau du lien.
- Résistance légère et périodique lorsque le lien est faible.

## Commandes

Toutes les commandes de gestion exigent le niveau opérateur 2.

```
/faction create dragonnier
/faction create chasseur_de_dragon
/faction add <joueur> <dragonnier|chasseur_de_dragon>
/faction transfer <joueur> <dragonnier|chasseur_de_dragon>
/faction remove <joueur>
/faction grade <joueur> <primodiau|alpha|confirme|petit>
/faction list
/faction info
/faction info <joueur>
/faction members <dragonnier|chasseur_de_dragon>
```

Les factions doivent être créées avant d'y ajouter des joueurs.

## Dépendances du serveur et des clients

- Minecraft 1.21.1
- NeoForge 21.1.215 ou version compatible 21.1.x
- Ice and Fire Unofficial Port 2.1.13-beta.10
- Rampart 1.1.0

Ice and Fire et Rampart doivent être installés séparément. Le code du mod Factions détecte la classe de dragon par réflexion afin de ne pas embarquer les JAR des dépendances dans ce dépôt.

## Compilation

La compilation automatique est configurée dans GitHub Actions. Chaque push sur `main` lance `gradle build` avec Java 21 et Gradle 8.10.2. Le JAR est publié comme artefact du workflow.

Pour compiler localement avec Gradle 8.10.2 :

```bash
gradle build
```

Le fichier compilé se trouve dans `build/libs/`.

## État

Cette branche contient le code source et le workflow de compilation. Le mod doit encore être compilé avec le workflow GitHub Actions puis testé sur un serveur de test NeoForge avant installation sur le serveur CITIZEN de production.
