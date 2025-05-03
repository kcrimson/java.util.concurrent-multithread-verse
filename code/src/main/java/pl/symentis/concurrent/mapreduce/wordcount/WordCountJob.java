// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce.wordcount;

import pl.symentis.concurrent.mapreduce.Input;
import pl.symentis.concurrent.mapreduce.Job;
import pl.symentis.concurrent.mapreduce.Mapper;
import pl.symentis.concurrent.mapreduce.Reducer;

public class WordCountJob implements Job {

    private Input<String> input;

    private WordCount wordCount;

    public WordCountJob(Input<String> input, WordCount wordCount) {
        this.input = input;
        this.wordCount = wordCount;
    }

    @Override
    public Input<String> input() {
        return input;
    }

    @Override
    public Mapper<String, String, Long> mapper() {
        return wordCount.mapper();
    }

    @Override
    public Reducer<String, Long, String, Long> reducer() {
        return wordCount.reducer();
    }
}
