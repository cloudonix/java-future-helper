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
import io.cloudonix.future.helper.FutureHelper;

Class MyClass(){
    protected CompletableFuture<Void> getSomething(Boolean someCondition) {
    	if(someCondition)
    		return someAsyncOperation();
		return FutureHelper.completedFuture();
	}
}
```

It is also possible to stream-line some references by staticaly importing them, or all references:

```
import static io.cloudonix.future.helper.FutureHelper.*;

class MyClass(){
    protected CompletableFuture<Void> promise() {
        return completedFuture();
    }
}
```