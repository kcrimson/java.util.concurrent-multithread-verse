// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

public interface Reducer<MK, MV, RK, RV> {
    void reduce(MK k, Iterable<MV> input, Output<RK, RV> output);
}
