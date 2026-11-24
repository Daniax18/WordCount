# WordCount — Hadoop MapReduce 3.4.1 (Java)

Compter les mots d'un ou plusieurs fichiers texte avec un job MapReduce
exécuté sur un mini-cluster Hadoop dans Docker.

## Structure du projet

```
WordCount/
├── docker-compose.yml          # Le cluster : namenode, datanode, resourcemanager, nodemanager
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

Voir l'étape 2 (le job sera complété et les commandes ajoutées ici).

## Arrêter le cluster

```powershell
docker compose down
```
