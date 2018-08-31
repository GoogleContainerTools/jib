# Proposal: Emit events from Jib Core

The tracking issue is at [#714](https://github.com/GoogleContainerTools/jib/issues/714).

## Motivation

Currently, Jib logs various log messages via injecting a [`JibLogger`](https://github.com/GoogleContainerTools/jib/blob/02f7f41874223e1e6acf2a40648b5b3695877397/jib-core/src/main/java/com/google/cloud/tools/jib/JibLogger.java) interface into the execution steps in the `builder` package. However, the data is not structured. The user (of Jib Core) needs to parse log messages in order to obtain useful information. The log messages are also catered towards the `jib-maven/gradle-plugin` execution output.

## Solution

Emit events with structured information.

## Goals

- Flexible to support different event payloads
- Extensible with different event types
- Minimal overhead if not used

## Proposal

An `EventHandlers` class holds handlers to pass into the execution steps. This is used by `ExecutionContext`.

```java
class EventHandlers {
  
  // Handles `E` event class to with `eventConsumer`.
  <E extends JibEvent> add(JibEventType<E> eventType, Consumer<E> eventConsumer);
  
  // Handles all events.
  add(Consumer<JibEvent> eventConsumer);
}
```

Emitted events will be matched to handlers by their exact type. `JibEvent`s should **not** inherit from each other. `JibEventType` defines constants for all the possible event types to add handlers for.

An example usage could look like this:

```java
// In Jib Core
class PushingBlobEvent implements JibEvent {
  
  DescriptorDigest getDigest();
  URL getUploadLocation();
}

// Called by user
ExecutionContext executionContext = 
    ExecutionContext.newContext()
                    .addEventHandler(JibEventType.PUSHING_BLOB, event -> {
                      // Do some processing on event, like:
                      System.out.println(
                          "Pushing blob " + event.getDigest() + " to " +
                              event.getUploadLocation());
                    })
                    .addEventHandler(allJibEvents -> {
                      System.out.println("Some event happened");
                    })
                    // Some class that has pre-defined handlers that print log messages.
                    .addEventHandlers(new LoggingEventHandlers(jibLogger));
Jib.from(...)
   ...
   .containerize(
       Containerizers.withExecutionContext(executionContext)
                     .to(RegistryImage.named(targetImage)));
```
