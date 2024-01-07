# Coverage Agent

This is a small Java agent which dumps a list of all classes within a specified package in which code is executed.

### Note: This is a new implementation using `INVOKEDYNAMIC`, my original one can be found [here](https://github.com/LlamaLad7/JetBrainsProject-CoverageAgent/tree/original-implementation).

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
    public static void main(String[] args) {
        coverageAgent$notify();
        System.out.println("Hi!");
    }

    public static void coverageAgent$notify() {
        IndySupport.<invokedynamic>notify(DemoClass.class);
    }
}
```

(The actual name of the generated method is not a legal Java/Kotlin identifier to ensure there will be no conflicts.)

The invokedynamic call will notify the agent of the class's execution, and then rebind itself to do nothing on 
subsequent executions. The JVM should be able to see through this and optimize the call out entirely in hot code.

The use of a generated method means there is only a single INDY call-site per class, so after any of the methods 
trigger it, it will become inactive.

We ignore interfaces which were compiled with Java <8, since their methods cannot have bodies.

## Considerations for the future

- Ideally the agent would be configurable via a more robust config file which could be passed in
- Maybe we should have some kind of system to ignore methods called from `<init>` or `<clinit>`, not just those methods
  themselves?
