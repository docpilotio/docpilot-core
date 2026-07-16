# DocPilot Plugin Packaging

A DocPilot plugin is a JAR containing an implementation of:

```text
io.docpilot.core.api.DocPilotPlugin
```

The JAR must also contain the service-provider configuration file:

```text
META-INF/services/io.docpilot.core.api.DocPilotPlugin
```

The file contains one fully qualified implementation class name per line.

Example:

```text
com.example.docpilot.MyOutputPlugin
```

At runtime, `ServiceLoaderPluginLoader` discovers all providers available
on the application classpath. Discovered plugins are validated and placed
into `InMemoryPluginRegistry` in deterministic plugin-ID order.

Dynamic loading from arbitrary file-system paths is intentionally deferred.
