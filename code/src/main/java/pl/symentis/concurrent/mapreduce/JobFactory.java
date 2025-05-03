// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.mapreduce;

import java.util.Map;

public interface JobFactory {

    Job create(Map<String, String> context);
}
