# WordCount — Hadoop MapReduce 3.4.1 (Java)

Compter les mots d'un ou plusieurs fichiers texte avec un job MapReduce
exécuté sur un mini-cluster Hadoop dans Docker.

> Explication complète (rôle de chaque composant, chaque config, déroulé
> détaillé d'un job) : [docs/WORKFLOW.md](docs/WORKFLOW.md)

## Structure du projet

```
WordCount/
├── docker-compose.yml          # Le cluster : namenode, datanode, resourcemanager, nodemanager
├── docs/WORKFLOW.md            # Workflow détaillé du projet
├── config/hadoop/              # Config Hadoop montée dans chaque conteneur
│   ├── core-site.xml           #   adresse de HDFS
│   ├── hdfs-site.xml           #   réplication = 1
│   ├── mapred-site.xml         #   MapReduce tourne sur YARN
│   └── yarn-site.xml           #   service de shuffle, mémoire
├── data/
│   ├── input/sample.txt        # Texte d'exemple  (-> /data/input dans les conteneurs)
│   └── output/                 # Résultats rapatriés depuis HDFS (ignoré par git)
└── hadoop-mapreduce-lab/       # Le code Java (projet Maven)
    ├── pom.xml
    └── src/main/java/com/wordcount/
        ├── WordCountMapper.java   # map    : ligne  -> (mot, 1)
        ├── WordCountReducer.java  # reduce : (mot, [1,1,1]) -> (mot, 3)
        └── WordCountDriver.java   # main   : configure et lance le job
```

Le `.jar` produit par Maven (`hadoop-mapreduce-lab/target/wordcount.jar`)
est monté dans les conteneurs sous `/jobs/wordcount.jar`.

## Prérequis

- Docker Desktop
- JDK (8 ou plus, le projet compile en Java 8 pour le cluster) et Maven

## Démarrer le cluster

```powershell
docker compose up -d
```

Vérifier : http://localhost:9870 (HDFS, onglet *Datanodes* = 1 noeud) et
http://localhost:8088 (YARN, *Active Nodes* = 1).

## Compiler

```powershell
mvn -f hadoop-mapreduce-lab/pom.xml clean package
```

## Lancer le job

Toutes les commandes Hadoop s'exécutent **dans** le conteneur `namenode`
(c'est lui qui joue le rôle de "client" du cluster).

1. Copier le texte du disque local vers HDFS (`/data` = le dossier `data/` du projet) :

```powershell
docker exec namenode hdfs dfs -mkdir -p /user/hadoop/input
```

```powershell
docker exec namenode hdfs dfs -put -f /data/input/sample.txt /user/hadoop/input/
```

2. Lancer le job (le dossier de sortie ne doit pas exister, on le supprime s'il y en a un) :

```powershell
docker exec namenode hdfs dfs -rm -r -f /user/hadoop/output
```

```powershell
docker exec namenode hadoop jar /jobs/wordcount.jar /user/hadoop/input /user/hadoop/output
```

Pendant l'exécution, le job est visible sur http://localhost:8088.

3. Lire le résultat :

```powershell
docker exec namenode hdfs dfs -cat /user/hadoop/output/part-r-00000
```

```
counts	1
hadoop	2
hello	2
mapreduce	1
words	4
```

Le dossier de sortie contient `_SUCCESS` (fichier vide = le job a réussi) et
un fichier `part-r-NNNNN` par reducer (ici un seul).

4. (Optionnel) Rapatrier le résultat dans `data/output/` sur ton PC :

```powershell
docker exec namenode hdfs dfs -getmerge /user/hadoop/output /data/output/wordcount.txt
```

## Ce qui se passe pendant le job

```
sample.txt ──► MAP (WordCountMapper)    "hello hadoop" ──► (hello,1) (hadoop,1)
           ──► SHUFFLE & SORT (Hadoop)  regroupe par mot ──► (hello,[1,1])
           ──► REDUCE (WordCountReducer) additionne      ──► (hello,2)
           ──► part-r-00000 dans HDFS
```

Pour compter d'autres textes : dépose des `.txt` dans `data/input/`,
envoie-les avec `hdfs dfs -put`, puis relance le job.

## Arrêter le cluster

```powershell
docker compose down
```
