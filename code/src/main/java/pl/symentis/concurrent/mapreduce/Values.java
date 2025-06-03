// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

import java.util.Iterator;
import java.util.Set;

public interface Values<K, V> {

    Set<K> keys();

    Iterator<V> values(K k);
}
