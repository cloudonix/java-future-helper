# FutureHelper

FutureHelper is a utility library that contains useful methods that handle Java 8's `CompletableFuture` and to integrate them with Vert.x `AsyncResult` type handlers and Java 8's streams. An additional facility of FutureHelper is a simplified timer setup and invocation using Java 8's `Timer` class.

## Installation

FutureHelper is accessible using [JitPack](https://jitpack.io/#cloudonix/java-future-helper). To use it, in your `pom.xml` file add the JitPack repository: 

```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```

Then add the dependency with the version you want to use:

```xml
	<dependency>
	    <groupId>com.github.cloudonix</groupId>
	    <artifactId>java-future-helper</artifactId>
	    <version>1.14.3</version>
	</dependency>
```

## Usage

## `CompletableFuture` helper class - `Futures`

All of the methods are static methods in the class named `Futures`. 

### Synchronous completion helpers

These helpers help to generate `CompletableFuture` instances that are already completed synchronously. This is useful to propagate synchronous failures from an async chain and in a few other cases. Newer Java releases also offer similar helpers, but even working with newer Java versions you might still be intersted in using these less verbose helpers:

#### `Futures.failedFuture(Throwable error)`

Create a `CompletableFuture<T>` (for any required `T`) that has already "completed exceptionally" with the specified error.

#### `Futures.completedFuture()`

Create a `CompletableFuture<Void>` that has already completed with the `Void` value.

#### `Futures.completedFuture(T value)`

Create a `CompletableFuture<T>` that has already completed with the specified value.

### Execution and Error Handling Helpers 

#### `Futures.executeBlocking(ThrowingSupplier<T> supplier)`

Create a `CompletableFuture<T>` that will complete in the future with the value returned by the specified supplier, or complete exceptionally with the `Throwable` thrown from the supplier. The supplier will be run in the default async executor (the common fork-join pool).

The `ThrowingSupplier` interface is a functional interface that behaves like a Java 8 `Supplier` but allows the `get()` method to throw any exception so it is easier to propagate failures.

####  `Futures.on(Class<E> errType, ThrowingFunction<E, ? extends T> handler)`

The missing "catch" syntax for Java 8 asynchronous completions - this helper that is intended to be used in `CompletableFuture.exceptionally()` async error handlers to easily capture and handle (recover from) known exception types - exactly as the `throw...catch` syntax is used in synchronous Java to capture specific errors while letting other errors propagate easily.

The parameters passed would be the type of exception to capture and a handler that receives the captured exception (if such an exception type is thrown) and is expected to recover with the value type the completion stage is expected to return. The handler can throw any exception to signal that it wants to propagate another (or the same) error up the stack (or down the chain).

An example of using this functionality to return a default value from a supplier that failed to find a value using an IO-bound data access might look like this:

```Java
public String readValue(String defValue) {
   return tryToGetValue()
       .exceptionally(Futures.on(DataAccessException.class, e -> defValue));
}
```

An example of how to convert a mechanic error thrown by an underlying library to a logical exception might look like this:

```Java
public String readValue() {
   return tryToGetValue()
       .exceptionally(Futures.on(DataAccessException.class, e -> {
           throw new FailedToRetrieveValue("Data access failed", e);
       });
}
```

#### `Futures.delay(long delay)`

Allows to add a delay in the middle of an asynchronous chain of completions. This method generates a function that can be used in `CompletableFuture.thenCompose()` to forward a value from one completion to another, inducing a specified delay (in milliseconds).

Example usage:

```java
api.createSomeResource()
    // give the resource some time to complete initialization
    .thenCompose(Futures.delay(500))
    .thenCompose(resource -> api.useResource(resource));
```

### List and Stream Helpers

#### `Futures.anyOf(Stream<CompletableFuture<G>> futures)`

Wrapper for `CompletableFuture.anyOf()` that takes a stream instead of array.

#### `Futures.allOf(Stream<CompletableFuture<G>> futures)`

Wrapper for `CompletableFuture.allOf()` that takes a stream instead of an array.

#### `Futures.allOf(List<CompletableFuture<G>> list)`

Wrapper for `CompletableFuture.allOf()` that takes a list instead of an array.

#### `Futures.resolveAny(Stream<CompletableFuture<G>> promises)`

A re-definition of `CompletableFuture.anyOf()` that operates on a stream, with slightly better semantics:

* The returned (typed) promise will resolve with the value of the first promise that completes successfully, if any, in chronological order.
* If any (but not all) promises fail (complete exceptionally), they will be ignored.
* If all promises fail, the last failure (chronologically) will be used to fail the returned promise.
* If the stream has no elements, then the return promise will fail with a `NoSuchElement` exception.

#### `Futures.resolveAny(List<CompletableFuture<G>> promises)`

The same as the above, but takes a list instead of a stream.

#### `Futures.resolveAll(Stream<CompletableFuture<G>> promises)`

A re-definition of `CompletableFuture.allOf()` that operats on a stream and returns a promise (completion stage) whose value is the list of the results (in order) of all the completions of promises in the stream. Like `CompletableFuture.allOf()`, if any of the promises fail to complete (completes exceptionally), the returned promise will fail (complete exceptionally) with the first exception encountered (chronologically, not in order).

#### `Futures.resolveAll(List<CompletableFuture<G>> promises)`

The same as the above, but takes a list instead of a stream.

#### `Futures.resolveAll(CompletableFuture<G>... promises)`

The same as the above, but takes an array instead of a stream or a list.

#### `Futures.executeAllAsync(List<T> list, Function<T, CompletableFuture<Void>> operation)`

Operate on a list of values that will be each submitted to the async function to perform some asynchrnonous operation and the returned promise will complete when all operations have completed. This method is essentially a wrapper on top of `CompletableFuture.allOf()` so its behavior in the face of failures is the same as that.

#### `Futures.executeAllAsyncWithResults(List<T> list, Function<T, CompletableFuture<G>> operation)`

Similar to `Futures.executeAllAsync()`, this method takes a list of values and an async function and feeds the list to the operation - but additionally this method returns a promise of a list of all the results of the operations in the order they were submitted.

#### `Futures.resolvingCollector()`

This method generates a `Collector` that can be used with `Stream.collect()` to convert a stream of promises (`CompletableFuture`) to a promise for a list of values returned from all of the successfully resolved promises. This method uses `Futures.resolveAll()` internally and has the same semantics in the face of failures - i.e. the returned `CompletableFuture` will complete exceptionally with the semantics specified for `Futures.resolveAll()`.

Example usage:

```java
IntStream.range(0, 10).mapToObj(dao::findNameById).collect(Futures.resolvingCollector())
    .thenAccept(listOfNames -> ...)
```

#### `Futures.resolvingCollectorToStream()`

This method generates a `Collector`, similar to `Futures.resolvingCollector()`, but resolving directly to a stream of values. This collector assumes that no promise in the incoming stream will fail, and if any do fail - trying to access the value of the failed promise will result in a `CompletionException` being thrown with the original failure set as its `cause()`. Note that the stream API does not allow one to recover other elements of a stream that has such failed.

### Vert.x `AsyncResult` Helpers

These methods help to interact with Vert.x asynchronous methods that take a callback in the form of `Handler<AsyncResult>` to integrate with code that uses `CompletableFuture` chaining.

#### `Futures.fromAsync(Consumer<Handler<AsyncResult<T>>> action)`

Wrap a Vert.x asynchronous call that takes a callback and convert it to a Completablefuture. 

Due to defficiencies in Java generic resolution, code that uses `fromAsync()` often has to specify the generic type. For example, getting a `Lock` from Vert.x shared data API might look like this:

```java
Futures.<Lock>fromAsync(cb -> Vertx.vertx().sharedData()
    .getLock("lock", cb)).thenCompose(v -> ...);
```

#### `Futures.retryAsyncIf(Consumer<Handler<AsyncResult<T>>> action, Predicate<Throwable> predicate, int tries)`

Similar to the simple `Futures.fromAsync()`, this method wraps a Vert.x asynchronous callback API but offers additional logic to retry the operation a few times if it fails.

In addition to the lambda, this method takes a predicate that can test exceptions and a retry limit. If the wrapped call fails, the predicate will be tested with the exception and if it returns `true` - the operation will be retries again, as many times as specified in the `tries` parameter.

#### `Futures.fromHandler(Consumer<Handler<T>> action)`

Similar to `Futures.fromAsync()` but meant to work with Vert.x APIs that need just a `Handler<T>` callback (i.e. they cannot fail).

#### `Futures.forward(Consumer<AsyncResult<T>> destination)`

This method generates a function that can forward completions (whether successful or exceptional) from `CompletableFuture.handle()` to Vert.x `Future.handle()` for cases when you want to propagate the results from a `CompletableFuture` chain back into a Vert.x set of callbacks implemented with `Future`.

Example usage (taken from [Vert.x core documentations](https://vertx.io/docs/vertx-core/java/#_future_composition)):

```java
FileSystem fs = vertx.fileSystem();

Future<Void> vertxFuture = fs
  .compose(data -> {
    // When data is available, write it to the file
    return fs.writeFile("/foo", data);
  })
  .compose(v -> {
    // When the file is written (fut2), execute this:
    return fs.move("/foo", "/bar");
  });

completableFutureAPI.readDataBuffer().handle(Futures.forward(vertxFuture::handle));
```

## `Timer` helper class - `Timers`

All of the methods are static methods in the class named `Timers`. 

### Timer Operations

All of the methods in the `Timers` class execute their scheduled tasks on a single Timer instance named `"cxlib-timer"`. This means that they will all run in a single thread so any operation performed must be short in order to not delay other operations - if a long running operation needs to be scheduled, instead schedule a call to start it on another thread, for example by running it in a `CompletableFuture.*Async()` method that executes on the common fork-join pool. The timer uses a "daemon thread" - i.e. it doesn't need to be explicitly shutdown and will not prevent the JVM from terminating.

Please note that there are currently no APIs to cancel schedule or recurring tasks.

#### `Timers.schedule(Runnable operation, long delay);

Schedule the specified operation to be executed after the specified delay in milliseconds.

#### `Timers.schedule(Runnable operation, TimeUnit timeUnit, int delay);

Schedule the specified operation to be executed after the specified delay in the specified time unit.

#### `Timers.setDailyOperation(Runnable operation)`

Schedule the specified operation to be executed every day at midnight of UTC.

#### `Timers.setDailyOperation(Runnable operation, LocalTime timeOfDay)`

Schedule the specified operation to be executed every day at the specified UTC time.

#### `Timers.setDailyOperation(Runnable operation, LocalTime timeOfDay, ZoneOffset timezone)`

Schedule the specified operation to be executed every day at the specified time in the specified time zone.

#### `Timers.setPeriodicOperation(Runnable operation, long firstTime, long recurrenceEvery)`

Schedule the specified operation to be executed recurrently, with the first ocurrence hapenning after the specified delay.

For example, to schedule an occurrence every hour from now on:

```java
Timers.setPeriodicOperation(() -> System.out.println("Hello"), 0, TimeUnit.HOURS.toMillis(1));
```

## Notes

As this library is composed of static methods, it is also possible to stream-line some references by statically importing them, or all references:

```java
import static io.cloudonix.lib.Futures.*;

class MyClass(){
    protected CompletableFuture<Void> promise() {
        return completedFuture();
    }
}
```

## Configuration

The following behavior configuration options are available by setting the specific Java system properties:

- `io.cloudonix.lib.futures.async_callsite_snapshots` - when using `fromAsync()` to convert Vert.x
async callbacks to `CompletableFuture`, in case of a failure the Java 8 runtime encodes its internal exception
encoding mechanism's stack trace into the generated `CompletionException` class. By setting this property to
`true`, the library will capture the `fromAsync()` call site and in case of a failure, will encode the original
call site stack trace into Java's `CompletionException` class. This should allow easier debugging of failed async
operations. This adds a non-trivial computational cost to every call to `fromAsync()` (even those that will not fail)
which may be considered expensive depending on your specific scenario.

```

```