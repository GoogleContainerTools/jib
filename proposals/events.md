# Proposal: Emit events from Jib Core

Implemented in: **v0.9.0**

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
  <E extends JibEvent> addHandler(JibEventHandler<E> handler);
  <E extends JibEvent> removeHandler(JibEventHandler<E> handler);
}
```

`<E extends JibEvent> addHandler(JibEventHandler<E> handler)`
This adds an event handler for the E event class to handle that event with handler. For example, a `JibEventHandler` could look like this:

```java
class SomeJibEventHandler implements JibEventHandler<SomeJibEvent> {
  
  @Override
  Class<SomeJibEvent> getEventClass() {
    return SomeJibEvent.class;
  }
  
  @Override
  void handle(SomeJibEvent event) {
    // Do some processing on event, like:
    System.out.println("Got event with string: " + event.getString());
  }
}

EventHandlers jibEventHandlers = EventHandlers.create();
jibEventHandlers.addHandler(new SomeJibEventHandler());
jibEventHandlers.add(EventHandlers.loggingHandlers(jibLogger));

Containerizers.toRegistry(targetImage).setEventHandlers(jibEventHandlers)
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
handlers.add(JibEventHandler.handle(PushingBlobEvent.class, event -> {
  System.out.println("Pushing blob " + event.getDigest() + " to " + event.getUploadLocation());
});
... add other handlers ...
```
