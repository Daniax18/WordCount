# Workflow détaillé du projet WordCount

Ce document explique **qui fait quoi, dans quel ordre**, depuis le
`docker compose up` jusqu'au fichier de résultat. Il suit l'exemple
[`data/input/sample.txt`](../data/input/sample.txt) du début à la fin.

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Les acteurs et leur rôle](#2-les-acteurs-et-leur-rôle)
3. [Définition de chaque fichier](#3-définition-de-chaque-fichier)
4. [Les fichiers de configuration, propriété par propriété](#4-les-fichiers-de-configuration-propriété-par-propriété)
5. [Le workflow pas à pas](#5-le-workflow-pas-à-pas)
6. [L'exemple suivi à chaque étape](#6-lexemple-suivi-à-chaque-étape)
7. [Ports et interfaces web](#7-ports-et-interfaces-web)
8. [Erreurs fréquentes](#8-erreurs-fréquentes)

---

## 1. Vue d'ensemble

```
 TON PC (Windows)                         DOCKER (réseau "hadoop")
 ─────────────────                        ──────────────────────────────────────────────
                                          ┌──────────── HDFS (stockage) ─────────────┐
 data/input/sample.txt ── monté sur ───►  │ namenode   : l'annuaire des fichiers     │
                          /data           │ datanode   : stocke les blocs            │
                                          └──────────────────────────────────────────┘
 mvn package                              ┌──────────── YARN (calcul) ───────────────┐
   └► target/wordcount.jar ─ monté sur ►  │ resourcemanager : reçoit et planifie     │
                             /jobs        │ nodemanager     : exécute map & reduce   │
                                          └──────────────────────────────────────────┘
 config/hadoop/*.xml ──── montés dans ─►  /opt/hadoop/etc/hadoop/  (les 4 conteneurs)
```

En une phrase : **Maven fabrique le jar sur ton PC, Docker le rend visible
dans les conteneurs, HDFS stocke l'entrée et la sortie, YARN exécute le
Mapper et le Reducer.**

Ordre global :

| # | Phase | Où | Commande |
|---|-------|----|----------|
| 0 | Démarrage du cluster | Docker | `docker compose up -d` |
| 1 | Compilation | PC | `mvn -f hadoop-mapreduce-lab/pom.xml clean package` |
| 2 | Chargement des données dans HDFS | namenode | `hdfs dfs -put ...` |
| 3 | Soumission et exécution du job | namenode → YARN | `hadoop jar /jobs/wordcount.jar ...` |
| 4 | Lecture du résultat | namenode | `hdfs dfs -cat ...` |

---

## 2. Les acteurs et leur rôle

### Côté PC

| Acteur | Rôle |
|--------|------|
| **Docker / Docker Compose** | Crée les 4 conteneurs Hadoop, le réseau qui les relie, et les "montages" (dossiers du PC visibles dans les conteneurs). |
| **Maven** | Télécharge la bibliothèque Hadoop, compile le Java et produit `wordcount.jar`. |
| **JDK 21** | Compilateur Java. Grâce au `pom.xml`, il produit du code compatible Java 8. |

### Côté cluster : HDFS (Hadoop Distributed File System)

HDFS est un système de fichiers **réparti** : un gros fichier est coupé en
**blocs** de 128 Mo, chaque bloc est stocké sur un ou plusieurs DataNodes.

| Acteur | Conteneur | Rôle |
|--------|-----------|------|
| **NameNode** | `namenode` | Le chef. Garde l'**annuaire** : quels fichiers existent, en quels blocs ils sont découpés, sur quel DataNode est chaque bloc. Ne stocke **aucune donnée** de fichier. |
| **DataNode** | `datanode` | L'entrepôt. Stocke réellement les blocs sur son disque. Envoie régulièrement un "heartbeat" au NameNode pour dire qu'il est vivant. |

### Côté cluster : YARN (Yet Another Resource Negotiator)

YARN est le **gestionnaire de ressources** : il décide quelle tâche tourne
où, avec combien de mémoire et de CPU.

| Acteur | Conteneur | Rôle |
|--------|-----------|------|
| **ResourceManager** | `resourcemanager` | Le chef. Reçoit les jobs, connaît la capacité de chaque NodeManager, attribue des "conteneurs YARN" (= une portion de mémoire + CPU). |
| **NodeManager** | `nodemanager` | L'ouvrier. Lance les processus Java demandés (ApplicationMaster, tâches map, tâches reduce) et héberge le **service de shuffle**. |
| **MRAppMaster** (ApplicationMaster) | créé à la volée dans le `nodemanager` | Le chef de chantier **d'un seul job**. Il demande au ResourceManager des conteneurs pour les maps puis les reduces, suit leur progression, relance une tâche qui échoue. Il disparaît à la fin du job. |
| **YarnChild** (tâche map / reduce) | créé à la volée dans le `nodemanager` | Le processus Java qui exécute **notre** code : `WordCountMapper.map()` ou `WordCountReducer.reduce()`. |

> Attention à la confusion de vocabulaire : un **conteneur Docker** (namenode,
> datanode…) n'a rien à voir avec un **conteneur YARN** (un processus Java
> lancé par le NodeManager avec une limite de mémoire).

### Notre code

| Classe | Rôle |
|--------|------|
| **WordCountDriver** | Le `main`. Décrit le job et le soumet. Ne lit aucune donnée. Tourne dans le **client** (ici le conteneur `namenode`). |
| **WordCountMapper** | Transforme chaque ligne en paires `(mot, 1)`. Tourne dans une tâche map. |
| **WordCountReducer** | Additionne les `1` de chaque mot. Tourne dans une tâche reduce. |

---

## 3. Définition de chaque fichier

```
WordCount/
├── .gitignore
├── README.md
├── docker-compose.yml
├── docs/
│   └── WORKFLOW.md
├── config/hadoop/
│   ├── core-site.xml
│   ├── hdfs-site.xml
│   ├── mapred-site.xml
│   └── yarn-site.xml
├── data/
│   ├── input/sample.txt
│   └── output/
└── hadoop-mapreduce-lab/
    ├── pom.xml
    ├── src/main/java/com/wordcount/
    │   ├── WordCountDriver.java
    │   ├── WordCountMapper.java
    │   └── WordCountReducer.java
    └── target/
        └── wordcount.jar
```

| Fichier | Définition |
|---------|------------|
| `.gitignore` | Liste ce que git ne doit pas versionner : `target/` (le jar se régénère), `data/output/` (résultats), fichiers d'IDE, logs. |
| `README.md` | Mode d'emploi rapide : démarrer, compiler, lancer, lire le résultat. |
| `docker-compose.yml` | Définit le cluster : les 4 services, l'image `apache/hadoop:3.4.1`, les ports exposés, les dossiers montés et le réseau `hadoop`. |
| `docs/WORKFLOW.md` | Ce document. |
| `config/hadoop/core-site.xml` | Config commune à tout Hadoop : **où est HDFS**. |
| `config/hadoop/hdfs-site.xml` | Config du stockage : nombre de copies, droits. |
| `config/hadoop/mapred-site.xml` | Config de MapReduce : **tourner sur YARN** et où trouver Hadoop. |
| `config/hadoop/yarn-site.xml` | Config de YARN : où est le ResourceManager, **service de shuffle**, mémoire. |
| `data/input/sample.txt` | Le texte à analyser. Visible dans les conteneurs sous `/data/input/sample.txt`. Ce n'est **pas** HDFS : c'est un disque local qu'on copie ensuite dans HDFS. |
| `data/output/` | Endroit où l'on rapatrie le résultat depuis HDFS (`getmerge`). |
| `hadoop-mapreduce-lab/pom.xml` | Recette Maven : dépendance `hadoop-client` 3.4.1, compilation Java 8, nom du jar, classe principale. |
| `WordCountDriver.java` | Point d'entrée : configure et soumet le job. |
| `WordCountMapper.java` | Logique map : ligne → `(mot, 1)`. |
| `WordCountReducer.java` | Logique reduce : `(mot, [1,1,…])` → `(mot, total)`. |
| `target/wordcount.jar` | Le programme compilé (≈ 5 Ko : il ne contient que nos 3 classes). Visible dans les conteneurs sous `/jobs/wordcount.jar`. |

### Les montages Docker (dossier PC → dossier conteneur)

| Sur ton PC | Dans chaque conteneur | Pourquoi |
|------------|-----------------------|----------|
| `config/hadoop/*.xml` | `/opt/hadoop/etc/hadoop/*.xml` (lecture seule) | Remplacer la config par défaut de l'image par la nôtre. |
| `data/` | `/data` | Échanger des fichiers texte entre le PC et le cluster. |
| `hadoop-mapreduce-lab/target/` | `/jobs` | Le jar fraîchement compilé est immédiatement utilisable, sans copie. |

---

## 4. Les fichiers de configuration, propriété par propriété

Au démarrage, **chaque** processus Hadoop (NameNode, DataNode,
ResourceManager, NodeManager, et aussi la commande `hadoop jar`) lit les
fichiers XML de `/opt/hadoop/etc/hadoop/`. Toute propriété absente prend
sa valeur par défaut.

### `docker-compose.yml`

| Élément | Valeur | Rôle |
|---------|--------|------|
| `x-hadoop-common` (`&hadoop-common`) | bloc commun | Évite de répéter image, réseau et montages 4 fois. Chaque service l'hérite avec `<<: *hadoop-common`. |
| `image` | `apache/hadoop:3.4.1` | Image officielle (CentOS + Java 8 + Hadoop). |
| `command` | `hdfs namenode`, `hdfs datanode`, `yarn resourcemanager`, `yarn nodemanager` | Le **même** binaire Hadoop, lancé dans 4 rôles différents. |
| `hostname` / `container_name` | `namenode`, … | Nom utilisé par les autres conteneurs pour se joindre (`hdfs://namenode:9000`). |
| `ENSURE_NAMENODE_DIR` | `/tmp/hadoop-hadoop/dfs/name` | Si ce dossier n'existe pas, le script de démarrage de l'image lance `hdfs namenode -format` : HDFS est créé vide au premier démarrage. |
| `depends_on` | | Ordre de **démarrage** : namenode d'abord, puis datanode et resourcemanager, enfin nodemanager. (Docker n'attend pas que le service soit *prêt*, seulement *lancé* ; Hadoop sait réessayer.) |
| `ports` | `9870`, `9000`, `9864`, `8088`, `8042` | Rendent les interfaces web accessibles depuis ton navigateur. |
| `networks: hadoop` | | Réseau privé où chaque conteneur est joignable par son nom. |

### `core-site.xml`

| Propriété | Valeur | Rôle | Sans elle |
|-----------|--------|------|-----------|
| `fs.defaultFS` | `hdfs://namenode:9000` | Système de fichiers par défaut. Un chemin `/user/hadoop/input` devient `hdfs://namenode:9000/user/hadoop/input`. Les DataNodes l'utilisent aussi pour trouver leur NameNode. | Défaut `file:///` : Hadoop lirait le disque local du conteneur, et le DataNode ne saurait pas où s'enregistrer. |

### `hdfs-site.xml`

| Propriété | Valeur | Rôle | Sans elle |
|-----------|--------|------|-----------|
| `dfs.replication` | `1` | Nombre de copies de chaque bloc. | Défaut `3` : avec un seul DataNode, les fichiers seraient marqués "sous-répliqués" en permanence. |
| `dfs.permissions.enabled` | `false` | Désactive les contrôles de droits sur HDFS. | Risque de `Permission denied` selon l'utilisateur qui écrit. (À garder activé en production.) |

### `mapred-site.xml`

| Propriété | Valeur | Rôle | Sans elle |
|-----------|--------|------|-----------|
| `mapreduce.framework.name` | `yarn` | Le job est envoyé au cluster YARN. | Défaut `local` : tout tourne dans un seul processus Java, dans le client. Ça marche, mais ce n'est plus du distribué et rien n'apparaît sur l'interface YARN. |
| `yarn.app.mapreduce.am.env` | `HADOOP_MAPRED_HOME=/opt/hadoop` | Dit à l'ApplicationMaster où est installé Hadoop. | Erreur `Could not find or load main class ...MRAppMaster`. |
| `mapreduce.map.env` | idem | Même chose pour les tâches map. | Les tâches map échouent au lancement. |
| `mapreduce.reduce.env` | idem | Même chose pour les tâches reduce. | Les tâches reduce échouent au lancement. |

### `yarn-site.xml`

| Propriété | Valeur | Rôle | Sans elle |
|-----------|--------|------|-----------|
| `yarn.resourcemanager.hostname` | `resourcemanager` | Adresse du ResourceManager, pour le NodeManager (qui s'y enregistre) et pour le client (qui y soumet le job). | Défaut `0.0.0.0` : personne ne trouve le ResourceManager. |
| `yarn.nodemanager.aux-services` | `mapreduce_shuffle` | Démarre dans le NodeManager le service qui **sert la sortie des maps aux reducers** : c'est le shuffle. | Les reducers ne peuvent rien récupérer : le job reste bloqué ou échoue. |
| `yarn.nodemanager.resource.memory-mb` | `4096` | Mémoire totale que le NodeManager peut distribuer. | Défaut 8192 Mo : YARN pourrait promettre plus que ce que Docker a réellement. |
| `yarn.scheduler.minimum-allocation-mb` | `512` | Plus petit conteneur YARN possible. | Défaut 1024 Mo : moins de tâches en parallèle. |
| `yarn.nodemanager.vmem-check-enabled` | `false` | Désactive le contrôle de mémoire **virtuelle**, souvent faux sous Docker. | Des tâches peuvent être tuées avec `running beyond virtual memory limits`. |

### `pom.xml`

| Élément | Valeur | Rôle |
|---------|--------|------|
| `maven.compiler.release` | `8` | Compile pour Java 8, la version de l'image. Sinon : `UnsupportedClassVersionError`. |
| `hadoop-client` | `3.4.1`, scope `provided` | L'API Hadoop, nécessaire pour compiler. `provided` = non incluse dans le jar, car le cluster l'a déjà. |
| `finalName` | `wordcount` | Nom fixe : `target/wordcount.jar`. |
| `mainClass` | `com.wordcount.WordCountDriver` | Écrite dans le manifeste du jar, ce qui permet `hadoop jar wordcount.jar <in> <out>` sans nommer la classe. |

---

## 5. Le workflow pas à pas

### Phase 0 — Démarrage du cluster (`docker compose up -d`)

1. Docker crée le réseau `hadoop` et monte les dossiers.
2. Dans chaque conteneur, le point d'entrée de l'image (`/opt/starter.sh`) s'exécute **avant** la commande :
   - il ouvre les droits sur `/data` ;
   - sur le `namenode` uniquement : `ENSURE_NAMENODE_DIR` n'existe pas → **formatage** de HDFS (création d'un annuaire vide) ;
   - puis il lance la commande du service (`hdfs namenode`, …).
3. **NameNode** démarre, écoute sur `9000` (RPC) et `9870` (web).
4. **DataNode** démarre, lit `fs.defaultFS`, contacte `namenode:9000` et s'**enregistre**. Ensuite il envoie un heartbeat toutes les 3 s.
5. **ResourceManager** démarre, écoute sur `8032` (soumission de jobs) et `8088` (web).
6. **NodeManager** démarre, lit `yarn.resourcemanager.hostname`, s'enregistre en annonçant 4096 Mo, et lance le service `mapreduce_shuffle`.

Vérification : 9870 → *Live Nodes = 1*, 8088 → *Active Nodes = 1*.

### Phase 1 — Compilation (`mvn clean package`)

1. `clean` supprime `target/`.
2. Maven télécharge `hadoop-client` 3.4.1 (premier lancement uniquement, mis en cache dans `~/.m2`).
3. `javac --release 8` compile les 3 classes.
4. Le plugin jar assemble `target/wordcount.jar` avec `Main-Class: com.wordcount.WordCountDriver`.
5. Grâce au montage, le jar apparaît instantanément dans `/jobs/wordcount.jar` des conteneurs.

### Phase 2 — Chargement des données (`hdfs dfs -put`)

```
docker exec namenode hdfs dfs -put -f /data/input/sample.txt /user/hadoop/input/
```

1. Le client `hdfs` (dans le conteneur namenode) lit `/data/input/sample.txt` sur le **disque local**.
2. Il demande au **NameNode** : « je veux créer `/user/hadoop/input/sample.txt` ».
3. Le NameNode répond : « écris le bloc n°1 sur `datanode` ».
4. Le client envoie les octets **directement au DataNode** (les données ne passent jamais par le NameNode).
5. Le DataNode confirme ; le NameNode inscrit le fichier dans son annuaire.

Le fichier fait 67 octets, donc **1 seul bloc** (un bloc fait jusqu'à 128 Mo).

### Phase 3 — Soumission et exécution du job (`hadoop jar`)

```
docker exec namenode hadoop jar /jobs/wordcount.jar /user/hadoop/input /user/hadoop/output
```

#### 3.1 Côté client : le Driver

1. `hadoop jar` démarre une JVM, lit le manifeste et appelle `WordCountDriver.main()`.
2. `new Configuration()` charge les 4 fichiers XML.
3. Le Driver décrit le job : jar, Mapper, Reducer, types `Text`/`IntWritable`, entrée, sortie.
4. `job.waitForCompletion(true)` déclenche la soumission :
   - **vérifie que le dossier de sortie n'existe pas** (sinon erreur immédiate) ;
   - **calcule les splits** : découpe l'entrée en morceaux, un par tâche map. Ici 1 fichier de 1 bloc → **1 split → 1 tâche map** ;
   - **copie le jar et la config dans HDFS**, dans un dossier temporaire (`/tmp/hadoop-yarn/staging/...`), pour que le NodeManager puisse les récupérer ;
   - **envoie la demande au ResourceManager** (`resourcemanager:8032`). Le job reçoit un identifiant `job_..._0001`.

#### 3.2 Côté YARN : l'ApplicationMaster

5. Le ResourceManager choisit un NodeManager et lui demande de lancer un conteneur YARN pour le **MRAppMaster**.
6. Le NodeManager télécharge le jar depuis HDFS et démarre le MRAppMaster (c'est là que `yarn.app.mapreduce.am.env` sert).
7. Le MRAppMaster lit la liste des splits et demande au ResourceManager **1 conteneur par map** (ici 1), puis, plus tard, **1 par reduce** (par défaut 1).

#### 3.3 La phase MAP

8. Le NodeManager lance une tâche map (`YarnChild`).
9. Le **TextInputFormat** (format d'entrée par défaut) lit le split ligne par ligne et produit des paires `(position en octets, ligne)`.
10. Pour **chaque ligne**, Hadoop appelle `WordCountMapper.map()`, qui émet `(mot, 1)` pour chaque mot.
11. Les paires émises vont dans un **tampon mémoire**. Hadoop :
    - les **partitionne** : `hash(mot) % nombre de reducers` décide quel reducer recevra le mot (ici un seul reducer → tout va à la partition 0) ;
    - les **trie par mot** ;
    - les écrit sur le **disque local** du NodeManager (pas dans HDFS).
12. La tâche map se termine → console : `map 100% reduce 0%`.

#### 3.4 La phase SHUFFLE & SORT (faite par Hadoop, pas par notre code)

13. La tâche reduce démarre et **télécharge** sa partition depuis chaque map terminé, via le service `mapreduce_shuffle` du NodeManager (port 13562). C'est le **shuffle** : le transfert réseau map → reduce.
14. Elle **fusionne** toutes les partitions reçues en gardant l'ordre trié, puis **regroupe** les valeurs d'un même mot : `(hello, [1, 1])`.

#### 3.5 La phase REDUCE

15. Pour **chaque mot distinct**, dans l'ordre alphabétique, Hadoop appelle `WordCountReducer.reduce()`, qui fait la somme et émet `(mot, total)`.
16. Le **TextOutputFormat** écrit chaque paire sous la forme `mot<TAB>total` dans un fichier temporaire de HDFS.
17. Quand la tâche réussit, le fichier est **validé** (commit) et renommé en `/user/hadoop/output/part-r-00000` (`r` = reduce, `00000` = reducer n°0).
18. Console : `map 100% reduce 100%`.

#### 3.6 Fin du job

19. Le MRAppMaster écrit le fichier vide **`_SUCCESS`** dans le dossier de sortie, rend ses ressources au ResourceManager et s'arrête.
20. Le Driver affiche `Job job_..._0001 completed successfully` et les **compteurs** (lignes lues, paires émises, octets échangés…), puis `System.exit(0)`.

### Phase 4 — Lecture du résultat

```
docker exec namenode hdfs dfs -cat /user/hadoop/output/part-r-00000
```

Le client demande au NameNode où sont les blocs, puis lit les octets
directement sur le DataNode.

Pour récupérer le résultat sur le PC :

```
docker exec namenode hdfs dfs -getmerge /user/hadoop/output /data/output/wordcount.txt
```

`getmerge` concatène tous les `part-r-*` en un seul fichier, écrit dans
`/data/output`, c'est-à-dire dans `data/output/` sur ton PC.

---

## 6. L'exemple suivi à chaque étape

**Entrée** (`sample.txt`) :

```
hello hadoop
hello mapreduce
hadoop counts words
words words words
```

**Après le TextInputFormat** — paires `(position, ligne)` données au Mapper :

```
(0,  "hello hadoop")
(13, "hello mapreduce")
(29, "hadoop counts words")
(49, "words words words")
```

**Après MAP** — ce que `map()` émet, dans l'ordre :

```
(hello,1) (hadoop,1)
(hello,1) (mapreduce,1)
(hadoop,1) (counts,1) (words,1)
(words,1) (words,1) (words,1)
```

**Après le tri côté map** (avant le shuffle) :

```
(counts,1) (hadoop,1) (hadoop,1) (hello,1) (hello,1)
(mapreduce,1) (words,1) (words,1) (words,1) (words,1)
```

**Après SHUFFLE & regroupement** — ce que reçoit `reduce()` :

```
counts    -> [1]
hadoop    -> [1, 1]
hello     -> [1, 1]
mapreduce -> [1]
words     -> [1, 1, 1, 1]
```

**Après REDUCE** — contenu de `part-r-00000` :

```
counts	1
hadoop	2
hello	2
mapreduce	1
words	4
```

---

## 7. Ports et interfaces web

| Port | Service | Usage |
|------|---------|-------|
| [9870](http://localhost:9870) | NameNode (web) | État de HDFS, DataNodes, *Utilities → Browse the file system*. |
| 9000 | NameNode (RPC) | Adresse de `fs.defaultFS`, utilisée par les clients et le DataNode. |
| [9864](http://localhost:9864) | DataNode (web) | État du DataNode. |
| [8088](http://localhost:8088) | ResourceManager (web) | Liste des jobs, progression, **logs** des tâches. |
| 8032 | ResourceManager (RPC) | Soumission des jobs (interne au réseau Docker). |
| [8042](http://localhost:8042) | NodeManager (web) | Conteneurs YARN en cours sur ce nœud. |
| 13562 | Service shuffle | Transfert map → reduce (interne). |

---

## 8. Erreurs fréquentes

| Message | Cause | Solution |
|---------|-------|----------|
| `Output directory ... already exists` | Le dossier de sortie existe déjà. | `hdfs dfs -rm -r -f /user/hadoop/output` |
| `Input path does not exist` | Les données n'ont pas été envoyées dans HDFS. | Refaire la phase 2 (`hdfs dfs -put`). |
| `UnsupportedClassVersionError` | Jar compilé pour une version de Java plus récente que 8. | Garder `maven.compiler.release` = 8 et recompiler. |
| `Could not find or load main class ...MRAppMaster` | `HADOOP_MAPRED_HOME` manquant. | Vérifier les 3 propriétés `*.env` de `mapred-site.xml`. |
| `JAR does not exist ... C:/...` | Lancé depuis Git Bash, qui convertit `/jobs` en chemin Windows. | Utiliser PowerShell, ou préfixer par `MSYS_NO_PATHCONV=1`. |
| Job bloqué à `map 0% reduce 0%` | Aucun NodeManager actif, ou pas assez de mémoire. | Vérifier *Active Nodes* sur http://localhost:8088 et `docker compose ps`. |
| `Connection refused` vers `namenode:9000` | NameNode pas encore prêt. | Attendre ~30 s après `docker compose up`. |
