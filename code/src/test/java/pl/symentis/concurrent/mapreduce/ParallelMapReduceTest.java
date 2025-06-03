// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.symentis.concurrent.mapreduce.wordcount.ThreadLocalStopwords;
import pl.symentis.concurrent.mapreduce.wordcount.WordCount;
import pl.symentis.concurrent.mapreduce.wordcount.WordCountJob;

public class ParallelMapReduceTest {

    private MapReduce mapReduce;

    @BeforeEach
    public void setUp() {
        mapReduce = new ParallelMapReduce.Builder()
                .withThreadPoolSize(4)
                .withPhaserMaxTasks(100)
                .build();
    }

    @AfterEach
    public void tearDown() {
        mapReduce.shutdown();
    }

    @Test
    public void testWordCount() throws FileNotFoundException {
        // given
        var inputFile = new File("src/test/resources/big.txt");
        var wordCount = new WordCount.Builder()
                .withStopwords(ThreadLocalStopwords.class)
                .build();
        var job = new WordCountJob(wordCount.input(inputFile), wordCount);

        var results = new ConcurrentHashMap<String, Long>();
        Output<String, Long> output = results::put;

        // When
        mapReduce.run(job.input(), job.mapper(), job.reducer(), output);

        // then
        // verify at least some expected words are counted correctly
        assertThat(results.size()).isGreaterThan(100);
        assertThat(results).containsKey("holmes");
        assertThat(results).containsKey("sherlock");

        // The stopwords should not be present
        assertStopwordsRemoved(results);
    }

    @Test
    public void testParallelExecution() throws FileNotFoundException {
        // given
        var inputFile = new File("src/test/resources/big.txt");
        var wordCount = new WordCount.Builder()
                .withStopwords(ThreadLocalStopwords.class)
                .build();
        WordCountJob job = new WordCountJob(wordCount.input(inputFile), wordCount);

        var resultsParallel = new ConcurrentHashMap<String, Long>();
        Output<String, Long> outputParallel = resultsParallel::put;

        // Create a sequential implementation for comparison
        var sequentialMapReduce = new SequentialMapReduce.Builder().build();
        var resultsSequential = new HashMap<String, Long>();
        Output<String, Long> outputSequential = resultsSequential::put;

        // when
        mapReduce.run(job.input(), job.mapper(), job.reducer(), outputParallel);

        // Reset the input for sequential execution
        var sequentialJob = new WordCountJob(wordCount.input(inputFile), wordCount);
        sequentialMapReduce.run(
                sequentialJob.input(), sequentialJob.mapper(), sequentialJob.reducer(), outputSequential);

        // then
        assertThat(resultsParallel).isEqualTo(resultsSequential);
    }

    private void assertStopwordsRemoved(Map<String, Long> results) {
        String[] commonStopwords = {"the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for"};
        assertThat(results).doesNotContainKeys(commonStopwords);
    }
}
