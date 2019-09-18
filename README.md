# FutureHelper

FutureHelper is a utility library that contains useful methods that handle Java 8's `CompletableFuture`

## Installation

In your `pom.xml` file, add the repository for FutureHelper (we are currently not hosted
in the public Maven repository) as an element under `<project>`:

```
<repositories>
  <repository>
    <id>cloudonix-dist</id>
    <url>http://cloudonix-dist.s3-website-us-west-1.amazonaws.com/maven2/releases</url>
  </repository>
</repositories>
```

Then add the library as a dependency:

```
<dependency>
	<groupId>io.cloudonix</groupId>
	<artifactId>future-helper</artifactId>
	<version>[0,)</version>
</dependency>
```


## Usage

All of the methods are static methods in the class named FutureHelper. For example:

```
import io.cloudonix.lib.Futures;

Class MyClass(){
    protected CompletableFuture<Void> getSomething(Boolean someCondition) {
    	if(someCondition)
    		return someAsyncOperation();
		return Futures.completedFuture();
	}
}
```

It is also possible to stream-line some references by statically importing them, or all references:

```
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
