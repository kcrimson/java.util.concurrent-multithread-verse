// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

public interface Job {
    Input<String> input();

    Mapper<String, String, Long> mapper();

    Reducer<String, Long, String, Long> reducer();
}
