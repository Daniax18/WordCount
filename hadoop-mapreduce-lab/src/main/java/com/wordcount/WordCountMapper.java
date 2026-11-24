package com.wordcount;

import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

/**
 * ÉTAPE MAP (squelette — sera complété à l'étape 2).
 *
 * Les 4 types génériques de Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT> :
 *   - LongWritable : clé d'entrée   = position (en octets) de la ligne dans le fichier
 *   - Text         : valeur d'entrée = le contenu de la ligne
 *   - Text         : clé de sortie   = un mot
 *   - IntWritable  : valeur de sortie = 1
 *
 * Pourquoi LongWritable / Text / IntWritable et pas long / String / int ?
 * Ce sont les types "sérialisables" de Hadoop : ils savent s'écrire et se relire
 * en binaire, ce qui est nécessaire pour les envoyer sur le réseau pendant le shuffle.
 */
public class WordCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {
        // TODO (étape 2) : découper la ligne en mots et émettre (mot, 1) pour chacun.
    }
}
