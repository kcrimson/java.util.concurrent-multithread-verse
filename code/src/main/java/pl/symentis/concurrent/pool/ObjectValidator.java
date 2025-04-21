// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

@FunctionalInterface
public interface ObjectValidator<T> {
    boolean isValid(T object);
}
