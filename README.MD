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

## Implementation

Consider this class:

```java
public class DemoClass {
    public static void main(String[] args) {
        System.out.println("Hi!");
    }
}
```

The agent will transform the class so it becomes equivalent to:

```java
public class DemoClass {
    public static final AtomicBoolean coverageAgent$notified = new AtomicBoolean();

    public static void main(String[] args) {
        coverageAgent$notify();
        System.out.println("Hi!");
    }

    public static void coverageAgent$notify() {
        if (!coverageAgent$notified.getAndSet(true)) {
            CoverageAgentAPI.markExecutedClass(DemoClass.class);
        }
    }
}
```

(The actual names of the generated members are not legal Java/Kotlin identifiers to ensure there will be no conflicts.)

The use of an atomic boolean for each class means the overhead is very low since central synchronization and a `HashSet`
lookup is only needed once per class.

We ignore interfaces which were compiled with Java <8, since their methods cannot have bodies.

## Considerations for the future

- Ideally the agent would be configurable via a more robust config file which could be passed in
- Perhaps a `volatile` field would be cheaper than an `AtomicBoolean`, though that wouldn't work for interfaces whose
  fields must be final
- In fact, a better idea might be to use an `INVOKEDYNAMIC`. We could use a `MutableCallSite` and link it to a dummy method after the first execution. Hopefully this would be more transparent to the JVM and the JIT would be able to get rid of it entirely.
