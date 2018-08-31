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

An `EventHandlers` class holds handlers to pass into the execution steps.

```java
class EventHandlers {
  <E extends JibEvent> add(Class<E> eventClass, Consumer<E> eventConsumer);
  <E extends JibEvent> remove(Consumer<E> eventConsumer);
}
```

`<E extends JibEvent> add(Class<E> eventClass, Consumer<E> eventConsumer)`
This adds an event handler for the `E` event class to handle that event with `eventConsumer`. For example, usage could look like this:

```java
EventHandlers jibEventHandlers = 
    EventHandlers.create()
                 .add(SomeJibEvent.class, someJibEvent -> {
                   // Do some processing on event, like:
                   System.out.println("Got event with string: " + event.getString());
                 });
jibEventHandlers.add();

ExecutionContext executionContext = 
    ExecutionContext.newContext()
                    .addEventHandlers(jibEventHandlers)
                    // Some class that has pre-defined handlers that print log messages.
                    .addEventHandlers(new LoggingEventHandlers(jibLogger));
Jib.from(...)
   ...
   .containerize(
       Containerizers.withExecutionContext(executionContext)
                     .to(RegistryImage.named(targetImage)));
```

`<E extends JibEvent> removeHandler(handler)`
Detaches the handler.

### Example `JibEvent` and a handler

```java
// In Jib Core
class PushingBlobEvent implements JibEvent {
  
  DescriptorDigest getDigest();
  URL getUploadLocation();
}

// Called by user
EventHandlers handlers = EventHandlers.create();
handlers.add(PushingBlobEvent.class, event -> {
  System.out.println("Pushing blob " + event.getDigest() + " to " + event.getUploadLocation());
});
... add other handlers ...
```
