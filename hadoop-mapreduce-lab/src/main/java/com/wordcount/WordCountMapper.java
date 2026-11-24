package com.wordcount;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

/**
 * ÉTAPE MAP : transforme chaque ligne de texte en une série de paires (mot, 1).
 *
 * Les 4 types génériques de Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT> :
 *   - LongWritable : clé d'entrée    = position (en octets) de la ligne dans le fichier
 *   - Text         : valeur d'entrée = le contenu de la ligne
 *   - Text         : clé de sortie   = un mot
 *   - IntWritable  : valeur de sortie = 1
 *
 * Pourquoi LongWritable / Text / IntWritable et pas long / String / int ?
 * Ce sont les types "sérialisables" de Hadoop : ils savent s'écrire et se relire
 * en binaire, ce qui est nécessaire pour les envoyer sur le réseau pendant le shuffle.
 *
 * Exemple : la ligne "hello hadoop hello" produit
 *   (hello, 1)  (hadoop, 1)  (hello, 1)
 * Le Mapper ne fait PAS la somme : c'est le travail du Reducer.
 */
public class WordCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    // La valeur "1" est toujours la même : on crée l'objet une seule fois.
    private static final IntWritable ONE = new IntWritable(1);

    // Objet réutilisé pour chaque mot émis. map() est appelée une fois PAR LIGNE,
    // potentiellement des millions de fois : éviter un "new Text()" à chaque mot
    // économise beaucoup de mémoire. C'est sans risque car context.write()
    // sérialise (copie) immédiatement la valeur.
    private final Text word = new Text();

    /**
     * Appelée par Hadoop pour chaque ligne du fichier d'entrée.
     *
     * @param key     position de la ligne dans le fichier (on ne s'en sert pas)
     * @param value   la ligne de texte
     * @param context l'objet qui permet d'émettre des paires (clé, valeur) vers Hadoop
     */
    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {

        // Découpe la ligne sur les espaces / tabulations ("hello hadoop" -> "hello", "hadoop").
        StringTokenizer tokenizer = new StringTokenizer(value.toString());

        while (tokenizer.hasMoreTokens()) {
            word.set(tokenizer.nextToken());

            // Émet (mot, 1). Hadoop stocke ces paires, puis pendant le SHUFFLE & SORT
            // il les trie par clé et regroupe toutes les paires d'un même mot
            // pour les envoyer au même Reducer.
            context.write(word, ONE);
        }
    }
}
