# NetStatusBus

[![CI](https://github.com/sunyatas/NetStatusBus/actions/workflows/ci.yml/badge.svg)](https://github.com/sunyatas/NetStatusBus/actions/workflows/ci.yml)

English | [简体中文](README.zh-CN.md)

NetStatusBus is a lightweight Android library for observing network status changes. Annotate a method with `@NetSubscribe`, register the object, and the method is called on the main thread whenever the network changes.

## Features

- Annotation-based subscribers: `@NetSubscribe(mode = Mode.X)`, no listeners or broadcast receivers to write
- Several subscription modes: any change, Wi-Fi only, mobile only, connect-only, or disconnect-only
- Callbacks always run on the main thread, so you can update the UI directly
- The current network state is delivered as soon as you register
- Built on `ConnectivityManager.NetworkCallback` (no deprecated `CONNECTIVITY_ACTION` broadcast)
- Network type is detected with `NetworkCapabilities` on Android 6.0+
- Thread-safe `register` / `unregister`; `init` can be called more than once
- `register(subscriber, lifecycleOwner)` unregisters automatically when the lifecycle is destroyed
- `getNetTypeLiveData()` exposes the network type as `LiveData` for annotation-free, reflection-free observing (convert to a Kotlin `Flow` with `asFlow()`)
- `addListener(OnNetTypeChangeListener)` is a plain callback with no annotations or reflection that sees every change, also from a `Service` (optionally removed automatically with a `LifecycleOwner`)
- Subscribers are held with weak references (forgotten `unregister` will not leak the Activity/Fragment); `@NetSubscribe` methods on superclasses are discovered and cached per class
- `ACCESS_NETWORK_STATE` permission and R8/ProGuard rules are bundled with the library

## Requirements

- `minSdk` 21 (Android 5.0) or higher
- AndroidX

## Installation

### Option 1: JitPack

Add the JitPack repository to `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency to your app module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.sunyatas:NetStatusBus:1.0.0'
}
```

> `1.0.0` is the latest release (see [Releases](https://github.com/sunyatas/NetStatusBus/releases) and the [changelog](CHANGELOG.md)). To try unreleased changes, use `master-SNAPSHOT`, which tracks the latest `master`, or pin a short commit hash such as `com.github.sunyatas:NetStatusBus:329c932`.

### Option 2: Build from source

```bash
git clone https://github.com/sunyatas/NetStatusBus.git
```

Copy the `netbuslib` module into your project, then add it to `settings.gradle` and your app's dependencies:

```groovy
// settings.gradle
include ':netbuslib'

// app/build.gradle
dependencies {
    implementation project(':netbuslib')
}
```

Alternatively, run `./gradlew :netbuslib:publishToMavenLocal` in this repository and depend on `com.github.sunyatas:netstatusbus:1.0.0` from `mavenLocal()`.

## Usage

### 1. Initialize in `Application`

Call `init` as early as possible, ideally in `Application.onCreate()`. Calling it again is harmless: the network callback is only registered once.

```java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NetStatusBus.getInstance().init(this);
    }
}
```

### 2. Register and unregister subscribers

Register in line with your component's lifecycle. Calling `register` before `init` throws an exception.

```java
@Override
protected void onStart() {
    super.onStart();
    NetStatusBus.getInstance().register(this);
}

@Override
protected void onStop() {
    super.onStop();
    NetStatusBus.getInstance().unregister(this);
}
```

`NetStatusBus.getInstance().unregisterAllObserver()` removes every subscriber at once.

#### Automatic unregister with a lifecycle

Pass a `LifecycleOwner` and the subscriber is unregistered automatically when that lifecycle reaches `DESTROYED`, so you don't need a matching `unregister` call. Call it on the main thread.

```java
// Activity: unregistered in onDestroy
NetStatusBus.getInstance().register(this, this);

// Fragment: register in onViewCreated, unregistered in onDestroyView
NetStatusBus.getInstance().register(this, getViewLifecycleOwner());
```

The subscriber and the owner can be different objects (for example a presenter bound to a Fragment's view lifecycle). If the owner is already destroyed, nothing is registered. Calling `unregister` earlier yourself is still fine.

### 3. Declare subscriber methods

```java
@NetSubscribe(mode = Mode.AUTO)
public void onNetChanged(NetType netType) {
    switch (netType) {
        case WIFI:   tvStatus.setText("Wi-Fi");   break;
        case MOBILE: tvStatus.setText("Mobile");  break;
        case NONE:   tvStatus.setText("Offline"); break;
    }
}

@NetSubscribe(mode = Mode.NONE)
public void onNetLost() {
    Toast.makeText(this, "Network lost", Toast.LENGTH_SHORT).show();
}
```

A subscriber method must:

- be `public` and return `void`
- take either no parameters or a single `NetType` parameter
- be declared in the registered object's class or any of its superclasses (if a subclass overrides an annotated method, only the subclass version is called)

`register` throws `IllegalArgumentException` naming the offending method if an annotated method breaks these rules (for example a `String` parameter or a non-`void` return), instead of silently never calling it.

If a subscriber method throws, the exception is caught and logged to Logcat with tag `NetStatusBus` (naming `Class#method` and the real cause), and every other subscriber still receives the event. Filter Logcat by that tag when a callback seems to do nothing.

Kotlin:

```kotlin
@NetSubscribe(mode = Mode.WIFI)
fun onWifiChanged(netType: NetType) {
    binding.status.text = if (netType == NetType.WIFI) "Wi-Fi connected" else "Wi-Fi disconnected"
}
```

### 4. Alternative: observe with LiveData

If you'd rather not use annotations, observe `getNetTypeLiveData()`. It holds the current `NetType` right after `init` and updates on the main thread on every change. Standard `LiveData` rules apply: observers only receive values while their owner is at least `STARTED` and are removed automatically when it is destroyed.

```java
NetStatusBus.getInstance().getNetTypeLiveData().observe(this, netType -> {
    tvStatus.setText(netType == NetType.NONE ? "Offline" : netType.name());
});
```

Kotlin, including a `Flow` via `androidx.lifecycle:lifecycle-livedata-ktx`:

```kotlin
NetStatusBus.getInstance().netTypeLiveData.observe(viewLifecycleOwner) { netType ->
    binding.status.text = netType.name
}

val netTypes: Flow<NetType> = NetStatusBus.getInstance().netTypeLiveData.asFlow()
```

`LiveData` keeps only the latest value, so changes that happen while an observer is inactive are merged. That suits UI state. Use `@NetSubscribe` when you need `Mode` filtering or must see every single change.

### 5. Alternative: a plain listener

`addListener` takes an `OnNetTypeChangeListener`: no annotations, no reflection, and no R8 rules involved. The listener is called once with the current `NetType` right after it is added, then on every change, always on the main thread. Unlike `LiveData`, it keeps receiving changes while the UI is in the background, so it also works in a `Service` or a repository.

```java
private final OnNetTypeChangeListener listener = netType -> {
    if (netType == NetType.NONE) {
        pauseUploads();
    } else {
        resumeUploads();
    }
};

NetStatusBus.getInstance().addListener(listener);
// ...
NetStatusBus.getInstance().removeListener(listener);
```

The listener is held with a strong reference (so lambdas work), so remove it when you're done, or pass a `LifecycleOwner` to remove it automatically when the owner is destroyed:

```kotlin
NetStatusBus.getInstance().addListener({ netType ->
    binding.status.text = netType.name
}, viewLifecycleOwner)
```

Adding the same listener instance twice has no effect, and `unregisterAllObserver()` removes listeners as well as subscribers.

### Sample app

The [`app`](app) module shows every style side by side:

- `MainActivity` (via `BaseActivity`): `@NetSubscribe` methods filtered by `Mode`, registered in `onStart` and unregistered in `onStop`.
- `BlankFragment`: a `@NetSubscribe(NetType)` method registered with `register(this, getViewLifecycleOwner())`, so it unregisters itself.
- `Main2Activity`: `addListener(listener, this)` shows the current network in the action bar subtitle, and `getNetTypeLiveData()` logs each change; neither needs annotations.

## Subscription modes

`@NetSubscribe` defaults to `Mode.AUTO`.

| Mode | Called when |
| --- | --- |
| `Mode.AUTO` | The network type changes in any way |
| `Mode.WIFI` | Wi-Fi itself connects or disconnects |
| `Mode.WIFI_CONNECT` | The network becomes Wi-Fi |
| `Mode.MOBILE` | Mobile data itself connects or disconnects |
| `Mode.MOBILE_CONNECT` | The network becomes mobile data |
| `Mode.NONE` | The network is lost |

`Mode.WIFI` and `Mode.MOBILE` only react to their own network. For example, losing mobile data does not notify `Mode.WIFI` subscribers, and switching from Wi-Fi to mobile data notifies both (`Mode.WIFI` receives `MOBILE` because Wi-Fi went away).

> Behavior change: earlier versions also notified `Mode.WIFI` / `Mode.MOBILE` subscribers whenever the network became `NONE`, no matter which network was lost.

## Network types

`NetType` has four values:

| NetType | Meaning |
| --- | --- |
| `NetType.WIFI` | Connected through Wi-Fi |
| `NetType.MOBILE` | Connected through mobile data |
| `NetType.ETHERNET` | Connected through Ethernet |
| `NetType.NONE` | No usable network |

## Utilities

`com.sunchen.netbus.utils.NetworkUtils`:

```java
NetType type = NetworkUtils.getNetType();            // current network type
boolean online = NetworkUtils.isNetworkAvailable();  // whether any network is connected
boolean vpn = NetworkUtils.isVpnActive();            // whether a VPN is connected (6.0+)
MobileGeneration g = NetworkUtils.getMobileGeneration(); // G2/G3/G4/G5/UNKNOWN, needs READ_PHONE_STATE
NetworkUtils.openSetting(context);                   // open the system Wi-Fi settings
NetworkUtils.openSetting(activity, REQUEST_WIFI);    // same, but get onActivityResult on return
```

The no-argument methods use the `Application` passed to `init` and throw `IllegalStateException` if `init` has not been called yet. Each of them also has an overload that takes a `Context` (`getNetType(context)`, `isNetworkAvailable(context)`, `isVpnActive(context)`, `getMobileGeneration(context)`) and works without `init`, for example in a `BroadcastReceiver`, a WorkManager worker, or another library. Only the application context is used, so passing an `Activity` does not leak it.

`openSetting(context)` accepts any `Context`; with a non-Activity context (for example the `Application`) it adds `FLAG_ACTIVITY_NEW_TASK` for you. `openSetting(context, requestCode)` uses `startActivityForResult` when the context is (or wraps) an `Activity` and `requestCode >= 0`, so your `onActivityResult` runs when the user comes back. The settings screen normally returns `RESULT_CANCELED`, so call `NetworkUtils.getNetType()` there to read the new state. A negative `requestCode` means no result is needed. If the device has no Wi-Fi settings screen, the main settings screen opens instead.

## How it works

1. `init` records the current network type and registers a callback: on Android 7.0+ a default-network callback (`registerDefaultNetworkCallback`), on Android 5.0–6.0 a `ConnectivityManager.NetworkCallback` for all networks.
2. On Android 7.0+, the type is computed from the `NetworkCapabilities` delivered with the callback for the system's default network, so switching between Wi-Fi and mobile data is reported immediately; late `onLost` events for a network that is no longer the default are ignored. On older versions, the library recalculates the type of the active network (ignoring a network that was just lost). In both cases it only dispatches when the type actually changes.
3. `register` scans the object for `@NetSubscribe` methods with reflection and stores them in a thread-safe map. Each subscriber is then invoked on the main thread according to its mode and the previous/current network types.

## Permissions and R8/ProGuard

- `android.permission.ACCESS_NETWORK_STATE` is declared in the library manifest and merged into your app automatically.
- The library ships consumer R8/ProGuard rules that keep `@NetSubscribe` methods and the `Mode`/`NetType` enums, so no extra configuration is needed when minification is enabled.
- CI builds the sample app as a minified release with R8 and fails if any `@NetSubscribe` method is renamed or removed, so these rules are checked on every push.

## FAQ

**Why don't I get a callback right after `register`?**
Make sure `init` was called first. Also check the mode: on registration each method is called only if the current state matches its mode (for example, `Mode.NONE` is called on registration only when the device is offline).

**Ethernet and VPN.**
Ethernet is reported as `NetType.ETHERNET`. A VPN is not a separate type: the type of the network underneath it is reported (for example `WIFI` when the VPN runs over Wi-Fi). Use `NetworkUtils.isVpnActive()` to check whether a VPN is connected (Android 6.0+).

**2G/3G/4G/5G.**
`NetworkUtils.getMobileGeneration()` is opt-in: the library does not declare `READ_PHONE_STATE`. If your app declares and is granted it (runtime permission on 6.0+), the generation of the current mobile network is returned; otherwise, or when not on mobile data, it returns `UNKNOWN`. 5G NSA is usually reported by the system as LTE, so it returns `G4`.

**Do I need to unregister?**
It's recommended. Subscribers are held by weak references, so a forgotten `unregister` won't leak an Activity or Fragment, but a destroyed object may keep receiving callbacks until it is garbage collected. Either call `unregister` in the matching lifecycle callback, or use `register(subscriber, lifecycleOwner)` to have it done automatically.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for what changed since the 2019 Bintray builds and what is coming in 1.0.0.

## License

```
Copyright 2019-2026 sunyatas

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

See [LICENSE](LICENSE) for the full text.
