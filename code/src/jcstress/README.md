# JCStress Tests for Pool Implementation

This directory contains JCStress tests for the concurrent Pool implementation. JCStress is a tool for testing concurrency correctness in Java code.

## Running the Tests

To run the JCStress tests, use the following Gradle command:

```bash
./gradlew jcstress
```

This will compile and run all JCStress tests in this directory.

## Creating a Standalone JCStress JAR

You can also create a standalone JAR file that can be run directly:

```bash
./gradlew jcstressJar
```

Then run the JAR:

```bash
java -jar build/libs/code-jcstress.jar
```

## Test Options

You can pass additional options to JCStress:

```bash
./gradlew jcstress --args="-v -t pl.symentis.concurrent.pool.ValidResourceTest"
```

Common options:
- `-v`: Verbose output
- `-t <regex>`: Run tests matching the regex pattern
- `-f <N>`: Run for N forks
- `-i <N>`: Run for N iterations per fork
- `-r <dir>`: Set results directory

## Writing New Tests

To create a new JCStress test:

1. Create a new Java class in the `src/jcstress/java` directory
2. Annotate the class with `@JCStressTest`
3. Define expected outcomes with `@Outcome` annotations
4. Use `@Actor` methods to define concurrent operations
5. Use `@Arbiter` methods to verify results

See the existing tests for examples.

## Documentation

For more information about JCStress, visit:
https://wiki.openjdk.org/display/CodeTools/jcstress
```

Now you have JCStress support added to your project. You can run the tests with:

```bash
./gradlew jcstress
```

Or create a standalone JAR with:

```bash
./gradlew jcstressJar