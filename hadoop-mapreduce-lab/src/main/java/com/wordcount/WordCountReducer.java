package com.wordcount;

import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

/**
 * ÉTAPE REDUCE (squelette — sera complété à l'étape 2).
 *
 * Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT> :
 *   - entrée : (Text mot, liste de IntWritable [1, 1, 1, ...])
 *              -> regroupés par le shuffle & sort de Hadoop
 *   - sortie : (Text mot, IntWritable total)
 */
public class WordCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {

    @Override
    protected void reduce(Text key, Iterable<IntWritable> values, Context context)
            throws IOException, InterruptedException {
        // TODO (étape 2) : additionner les valeurs et émettre (mot, total).
    }
}
