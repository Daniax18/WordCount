package com.wordcount;

import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

/**
 * ÉTAPE REDUCE : additionne les "1" de chaque mot.
 *
 * Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT> :
 *   - entrée : (Text mot, liste de IntWritable [1, 1, 1, ...])
 *              -> déjà regroupés et triés par mot grâce au shuffle & sort de Hadoop
 *   - sortie : (Text mot, IntWritable total)
 *
 * Exemple : reçoit (hello, [1, 1])  ->  émet (hello, 2)
 */
public class WordCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {

    // Objet réutilisé pour le résultat (même raison que dans le Mapper).
    private final IntWritable total = new IntWritable();

    /**
     * Appelée par Hadoop UNE fois par mot distinct.
     *
     * @param key     le mot
     * @param values  toutes les valeurs émises par les Mappers pour ce mot
     * @param context permet d'écrire le résultat dans le fichier de sortie
     */
    @Override
    protected void reduce(Text key, Iterable<IntWritable> values, Context context)
            throws IOException, InterruptedException {

        int sum = 0;
        for (IntWritable value : values) {
            sum += value.get();   // .get() convertit IntWritable -> int
        }

        total.set(sum);

        // Chaque write produit une ligne "mot<TAB>total" dans le fichier part-r-00000.
        context.write(key, total);
    }
}
