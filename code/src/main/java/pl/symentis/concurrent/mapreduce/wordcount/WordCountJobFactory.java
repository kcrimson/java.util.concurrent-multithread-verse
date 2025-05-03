// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce.wordcount;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.Map;
import pl.symentis.concurrent.mapreduce.Job;
import pl.symentis.concurrent.mapreduce.JobFactory;

public class WordCountJobFactory implements JobFactory {

    @Override
    public Job create(Map<String, String> context) {
        var filename = context.get("filename");
        var wordCount = new WordCount.Builder()
                .withStopwords(ThreadLocalStopwords.class)
                .build();
        try {
            return new WordCountJob(wordCount.input(new File(filename)), wordCount);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }
}
