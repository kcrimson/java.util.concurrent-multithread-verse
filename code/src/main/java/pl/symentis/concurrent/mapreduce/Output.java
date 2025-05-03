// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

public interface Output<K, V> {
    void emit(K k, V v);
}
