// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

public interface Mapper<I, K, V> {

    void map(I in, Output<K, V> output);
}
