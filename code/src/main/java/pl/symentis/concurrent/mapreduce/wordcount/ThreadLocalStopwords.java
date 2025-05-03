// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce.wordcount;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ThreadLocalStopwords implements Stopwords {

    private final ThreadLocal<Collator> threadLocalCollator = new ThreadLocal<>() {

        @Override
        protected Collator initialValue() {
            return (Collator) collator.clone();
        }
    };

    private final Collator collator;
    private final TreeSet<CollationKey> stopwords;

    public static Stopwords from(InputStream inputStream) {
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            var stopwords =
                    reader.lines().map(collator::getCollationKey).collect(Collectors.toCollection(TreeSet::new));
            return new ThreadLocalStopwords(collator, stopwords);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private ThreadLocalStopwords(Collator collator, TreeSet<CollationKey> stopwords) {
        this.collator = collator;
        this.stopwords = stopwords;
    }

    @Override
    public boolean contains(String str) {
        return stopwords.contains(threadLocalCollator.get().getCollationKey(str));
    }
}
