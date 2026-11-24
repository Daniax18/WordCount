package com.wordcount;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * DRIVER : le point d'entrée (méthode main) exécuté par "hadoop jar wordcount.jar".
 *
 * Il ne traite aucune donnée lui-même : il DÉCRIT le job à Hadoop
 * (quel Mapper, quel Reducer, quels types, où lire, où écrire),
 * le soumet à YARN, puis attend la fin.
 *
 * Usage : hadoop jar /jobs/wordcount.jar <dossier entrée HDFS> <dossier sortie HDFS>
 */
public class WordCountDriver {

    public static void main(String[] args) throws Exception {

        // 1. Vérifier les arguments : un dossier d'entrée et un dossier de sortie.
        if (args.length != 2) {
            System.err.println("Usage : hadoop jar wordcount.jar <entrée> <sortie>");
            System.exit(2);
        }

        // 2. Charger la configuration Hadoop. Elle lit automatiquement les fichiers
        //    core-site.xml, mapred-site.xml, etc. du conteneur : c'est ainsi que le job
        //    sait que HDFS est sur namenode:9000 et qu'il doit tourner sur YARN.
        Configuration conf = new Configuration();

        // 3. Créer le job (le nom apparaît dans l'interface YARN, http://localhost:8088).
        Job job = Job.getInstance(conf, "word count");

        // 4. Indiquer quel .jar envoyer aux NodeManagers : Hadoop prend le .jar
        //    qui contient cette classe.
        job.setJarByClass(WordCountDriver.class);

        // 5. Brancher nos classes Map et Reduce.
        job.setMapperClass(WordCountMapper.class);
        job.setReducerClass(WordCountReducer.class);

        // 6. Déclarer les types de la sortie (clé = mot, valeur = nombre).
        //    Ici le Mapper et le Reducer ont les mêmes types de sortie,
        //    donc ces deux lignes suffisent pour les deux.
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        // 7. Chemins dans HDFS.
        //    - Entrée : un fichier ou un dossier (tous les fichiers du dossier sont lus).
        //    - Sortie : un dossier qui NE DOIT PAS exister, sinon Hadoop refuse de
        //      démarrer (protection contre l'écrasement de résultats).
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // 8. Soumettre le job et attendre. "true" affiche la progression
        //    (map 0% reduce 0% ... map 100% reduce 100%) dans la console.
        //    Code de sortie : 0 = succès, 1 = échec.
        boolean success = job.waitForCompletion(true);
        System.exit(success ? 0 : 1);
    }
}
