# Changelog

All notable changes to NetStatusBus are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project aims to follow [Semantic Versioning](https://semver.org/) from 1.0.0 on.

## [Unreleased]

### Added

- `NetStatusBus.addListener(OnNetTypeChangeListener)` / `removeListener(...)`: an annotation-free, reflection-free callback for Java and Kotlin. It receives the current type right away and then every change on the main thread, including while the UI is in the background (unlike `LiveData`). `addListener(listener, LifecycleOwner)` removes it automatically on `DESTROYED`. A listener that throws is logged and does not affect other listeners or subscribers.

- `NetworkUtils.getNetType(Context)`, `isNetworkAvailable(Context)`, `isVpnActive(Context)` and `getMobileGeneration(Context)`: the same checks without calling `init` first (for example in a `BroadcastReceiver`, a WorkManager worker, or another library). Only the application context is kept.

- `NetStatusBus.getCurrentNetType()`: the type the bus last delivered, read from a cached field without querying the system (any thread). `NetStatusBus.isInitialized()` tells whether `init` has been called.

### Changed

- Using the bus or a no-argument `NetworkUtils` method before `init` now throws `IllegalStateException` with a message that explains the fix, instead of a bare `RuntimeException("application is empty")`; `register` and `addListener` called before `init` now throw the same `IllegalStateException` instead of `IllegalArgumentException`. `IllegalStateException` is a `RuntimeException`, so existing `catch` blocks still work.
- `unregisterAllObserver()` also removes listeners added with `addListener`.

### Fixed

- Registering the same subscriber from several threads at the same time could add it twice, so every network change was delivered to it twice. Registration and unregistration now share a lock; dispatch stays lock-free.
- Calling `register` or `addListener` off the main thread while the network was changing could deliver the "current type" callback after a newer change had already been delivered, so the subscriber or listener ended up on an outdated type until the next change (`getNetTypeLiveData()` was not affected). The initial callback is now skipped when a newer change has already been delivered.

## [1.0.0] - 2026-10-23

First release published through JitPack:
`implementation 'com.github.sunyatas:NetStatusBus:1.0.0'`.

### Added
- `NetStatusBus.getNetTypeLiveData()`: observe the network type as `LiveData<NetType>` without
  annotations or reflection (pure Java; Kotlin users can call `asFlow()`).
- `register(subscriber, LifecycleOwner)`: registers a subscriber and unregisters it automatically
  when the owner reaches `DESTROYED`.
- `NetType.ETHERNET` for wired connections, and `NetworkUtils.isVpnActive()`. A VPN is reported as
  the type of its underlying network (#13).
- `NetworkUtils.getMobileGeneration()` returns 2G/3G/4G/5G. It is opt-in: the host app must hold
  `READ_PHONE_STATE`, otherwise it returns `UNKNOWN` (#12).
- Consumer R8/ProGuard rules that keep `@NetSubscribe` methods, so minified apps work without extra
  configuration.
- JitPack publishing (`jitpack.yml`, `maven-publish` release publication with sources jar).
- GitHub Actions CI: debug build, unit tests, a minified sample release build that checks the
  consumer rules, and a `publishToMavenLocal` check that mirrors the JitPack build.
- Unit tests for dispatching, mode filtering, lifecycle binding, weak references, method validation,
  subscriber exceptions and the default-network tracker.
- Apache-2.0 license, English README with a Chinese translation (`README.zh-CN.md`), and this changelog.

### Changed
- Migrated to AndroidX; `compileSdk` 35, Android Gradle Plugin 8, JDK 17 to build. `minSdk` stays 21.
- Network type is derived from `NetworkCapabilities` instead of the deprecated `NetworkInfo`.
- On Android 7.0+ the library follows the system default network via
  `registerDefaultNetworkCallback`, so a switch (for example Wi-Fi to mobile) is reported
  immediately and a late `onLost` for the old network is ignored (#4, #11).
- `Mode.WIFI` / `Mode.MOBILE` subscribers are only called when that network itself connects or
  disconnects, not when an unrelated network drops (#7, #9).
- Subscriber methods are always invoked on the main thread, and the subscriber registry is
  thread-safe (#5).
- Subscribers are held through weak references, so a forgotten `unregister` no longer leaks an
  Activity or Fragment.
- `@NetSubscribe` methods declared in superclasses are discovered, and the lookup is cached per class.
- `NetworkUtils.isNetworkAvailable()` no longer requires the network to be `VALIDATED` on API 23+.
- `NetworkUtils.openSetting()` honors its `requestCode` and also works with a non-Activity `Context`.
- Exceptions thrown by a subscriber are logged with `Log.e` (tag `NetStatusBus`) with the real cause
  and `Class#method`; other subscribers are still notified.

### Fixed
- After turning Wi-Fi off the library could still report `WIFI` (#6).
- No network callback was registered on API 21, and calling `init()` twice registered it twice.
- The library manifest now declares `ACCESS_NETWORK_STATE` itself.

### Behavior notes when upgrading from the 2019 Bintray/JCenter builds
- The Maven coordinates changed from the defunct Bintray artifact to JitPack
  (`com.github.sunyatas:NetStatusBus`).
- `register()` now throws `IllegalArgumentException` when a `@NetSubscribe` method's parameter is
  not `NetType` (or a supertype of it), including methods inherited from a superclass. Before, such
  a method was accepted and failed only when an event was dispatched. Methods with more than one
  parameter or a non-void return type were already rejected.
- Callbacks always arrive on the main thread.

[Unreleased]: https://github.com/sunyatas/NetStatusBus/compare/v1.0.0...master
[1.0.0]: https://github.com/sunyatas/NetStatusBus/compare/6aaadb4...v1.0.0
