# 2. Configure the RestClient's request factory explicitly

- **Status:** Accepted
- **Date:** 2026-10-19
- **Milestone:** M2

## Context

The Binance `RestClient` had no connect or read timeout. With a scheduled poller added in
M2 this is a real liability: an upstream that accepts a connection and then goes silent
parks the calling thread indefinitely, and the symptom ("the app stopped responding")
points nowhere near the actual cause.

The usual Spring Boot answer is to set `spring.http.client.connect-timeout` /
`spring.http.client.read-timeout` and let Boot's auto-configured `RestClient.Builder`
apply them. That answer does not work in this project, for two independent reasons:

1. **The auto-configuration is not on the classpath.** Boot 4 split what used to be one
   `spring-boot-autoconfigure` jar into per-technology modules, and `RestClient`
   auto-configuration moved into its own module. Verified against this project's resolved
   dependencies: no `RestClientAutoConfiguration` class and no `spring.http.client.*`
   configuration metadata exists in any jar on the classpath. `spring-boot-starter-web`
   does not pull it in. So there is no `RestClient.Builder` bean to inject and the
   properties would be silently ignored — the worst failure mode, since the config looks
   present and does nothing.
2. **`RestClient.builder()` is a static factory, not the auto-configured bean.** Even in a
   project where the module *is* present, calling `RestClient.builder()` creates a builder
   from scratch that has never seen Boot's properties. Only an injected
   `RestClient.Builder` carries them.

## Decision

Build the request factory by hand in `BinanceClientConfig`:

```java
HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
requestFactory.setReadTimeout(readTimeout);
return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
```

Both durations come from configuration (`binance.connect-timeout`, `binance.read-timeout`).

`JdkClientHttpRequestFactory` was chosen because it is what `RestClient` was *already*
using implicitly: `DefaultRestClientBuilder.initRequestFactory()` probes the classpath in
the order Apache HttpComponents → Jetty → Reactor Netty → JDK `HttpClient` → `Simple`, and
this project has none of the first three. So this change adds bounds without swapping the
HTTP client underneath.

## Consequences

**Good**

- Timeouts are explicit, configurable, and in version control rather than implied by a
  property that may or may not be wired up.
- Verified empirically: with `binance.base-url` pointed at the black-hole address
  `10.255.255.1`, the request fails in ~2.1 s instead of hanging.
- Makes visible *why* the two timeouts live in different objects: connection
  establishment is a property of the client (which pools connections), while waiting for
  a response is per-request.

**Bad / accepted costs**

- More code than a property line, and it must be revisited if a real connection-pooling
  client (Apache HttpComponents) is adopted later for pool sizing and keep-alive.
- If the `spring-boot-restclient` module is added in a future milestone, this config should
  be reconsidered in favour of injecting the auto-configured `RestClient.Builder`.
