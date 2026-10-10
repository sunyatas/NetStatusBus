# NetStatusBus

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
    implementation 'com.github.sunyatas:NetStatusBus:master-SNAPSHOT'
}
```

> No release has been tagged yet, so `master-SNAPSHOT` (latest `master`) is used here. You can also pin a specific commit by replacing it with a short commit hash, e.g. `com.github.sunyatas:NetStatusBus:329c932`. Once a release is tagged, use the tag instead.

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

Alternatively, run `./gradlew :netbuslib:publishToMavenLocal` in this repository and depend on `com.github.sunyatas:netstatusbus:master-SNAPSHOT` from `mavenLocal()`.

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
- be declared in the registered object's own class (methods inherited from a superclass are not found)

Kotlin:

```kotlin
@NetSubscribe(mode = Mode.WIFI)
fun onWifiChanged(netType: NetType) {
    binding.status.text = if (netType == NetType.WIFI) "Wi-Fi connected" else "Wi-Fi disconnected"
}
```

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

`NetType` has three values:

| NetType | Meaning |
| --- | --- |
| `NetType.WIFI` | Connected through Wi-Fi |
| `NetType.MOBILE` | Connected through mobile data |
| `NetType.NONE` | No usable network |

## Utilities

`com.sunchen.netbus.utils.NetworkUtils` (requires `init` to have been called):

```java
NetType type = NetworkUtils.getNetType();            // current network type
boolean online = NetworkUtils.isNetworkAvailable();  // whether any network is connected
NetworkUtils.openSetting(activity, 0);               // open the system Wi-Fi settings
```

`openSetting` starts the settings screen with `context.startActivity`, so pass an `Activity`. The `requestCode` argument is currently not used.

## How it works

1. `init` registers a `ConnectivityManager.NetworkCallback` and records the current network type.
2. On `onAvailable`, `onLost` and `onCapabilitiesChanged`, the library recalculates the type of the active network (ignoring a network that was just lost) and only dispatches when the type actually changes.
3. `register` scans the object for `@NetSubscribe` methods with reflection and stores them in a thread-safe map. Each subscriber is then invoked on the main thread according to its mode and the previous/current network types.

## Permissions and R8/ProGuard

- `android.permission.ACCESS_NETWORK_STATE` is declared in the library manifest and merged into your app automatically.
- The library ships consumer R8/ProGuard rules that keep `@NetSubscribe` methods and the `Mode`/`NetType` enums, so no extra configuration is needed when minification is enabled.

## FAQ

**Why don't I get a callback right after `register`?**
Make sure `init` was called first. Also check the mode: on registration each method is called only if the current state matches its mode (for example, `Mode.NONE` is called on registration only when the device is offline).

**Ethernet or VPN shows `NONE`.**
On Android 6.0+ only Wi-Fi and cellular transports are mapped to `WIFI` and `MOBILE`; other transports are reported as `NONE`.

**Do I need to unregister?**
Yes. Subscribers are held by strong references until `unregister` is called, so unregister in the matching lifecycle callback to avoid leaking Activities or Fragments.
