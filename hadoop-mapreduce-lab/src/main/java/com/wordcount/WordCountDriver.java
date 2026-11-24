package com.wordcount;

/**
 * DRIVER (squelette — sera complété à l'étape 2).
 *
 * C'est le point d'entrée (méthode main) exécuté par "hadoop jar wordcount.jar".
 * Son rôle : décrire le job à Hadoop (quel Mapper, quel Reducer, quels types,
 * où lire, où écrire) puis le soumettre à YARN et attendre la fin.
 *
 * Usage prévu : hadoop jar /jobs/wordcount.jar <dossier entrée HDFS> <dossier sortie HDFS>
 */
public class WordCountDriver {

    public static void main(String[] args) throws Exception {
        // TODO (étape 2) : configurer et lancer le Job MapReduce.
        System.out.println("Squelette OK : le job sera implémenté à l'étape 2.");
    }
}
