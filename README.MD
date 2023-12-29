# Coverage Agent

This is a small Java agent which dumps a list of all classes within a specified package in which code is executed.

## Usage

```bash
java -javaagent:"<path to agent>"=package/to/monitor/ ...
```

See also the [example](https://github.com/LlamaLad7/JetBrainsProject-CoverageAgent/tree/main/example), notably its 
buildscript.

The agent will keep track of all executed classes within the specified package, and will dump a list of them to
`executedClasses.txt`, both every 5 seconds and on JVM shutdown.

The agent will not track calls to constructors and class initializers, since these are deemed not useful for monitoring
coverage. You can give other methods the same behaviour by annotating them with:

```java
@DoNotTrack
```

## Considerations for the future

- Ideally the agent would be configurable via a more robust config file which could be passed in
- A `HashSet` addition on each method call is fairly expensive, so ideally this would be guarded by a volatile boolean
  kept in each class