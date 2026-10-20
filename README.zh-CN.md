# NetStatusBus

[![CI](https://github.com/sunyatas/NetStatusBus/actions/workflows/ci.yml/badge.svg)](https://github.com/sunyatas/NetStatusBus/actions/workflows/ci.yml)

[English](README.md) | 简体中文

NetStatusBus 是一个轻量的 Android 网络状态监听库。用 `@NetSubscribe` 注解一个方法并注册所在对象，网络发生变化时该方法就会在主线程被回调。

## 特性

- 基于注解的订阅方式：`@NetSubscribe(mode = Mode.X)`，无需自己写监听器或广播接收器
- 多种订阅模式：任意变化、仅 Wi-Fi、仅移动网络、仅连接时、仅断网时
- 回调始终在主线程执行，可以直接更新 UI
- 注册后立即回调一次当前网络状态
- 基于 `ConnectivityManager.NetworkCallback` 实现（不使用已废弃的 `CONNECTIVITY_ACTION` 广播）
- Android 6.0 及以上使用 `NetworkCapabilities` 判断网络类型
- `register` / `unregister` 线程安全；`init` 可重复调用
- `register(subscriber, lifecycleOwner)` 在生命周期销毁时自动注销
- `getNetTypeLiveData()` 以 `LiveData` 形式提供网络类型，无需注解和反射（Kotlin 可用 `asFlow()` 转成 `Flow`）
- 订阅者以弱引用保存（忘记 `unregister` 不会泄漏 Activity/Fragment）；会查找并按 Class 缓存父类上的 `@NetSubscribe` 方法
- 库中自带 `ACCESS_NETWORK_STATE` 权限声明和 R8/ProGuard 混淆规则

## 环境要求

- `minSdk` 21（Android 5.0）及以上
- AndroidX

## 安装

### 方式一：JitPack

在 `settings.gradle` 中添加 JitPack 仓库：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

在 app 模块的 `build.gradle` 中添加依赖：

```groovy
dependencies {
    implementation 'com.github.sunyatas:NetStatusBus:master-SNAPSHOT'
}
```

> 目前还没有打正式版本标签，所以这里使用 `master-SNAPSHOT`（即 `master` 最新代码）。也可以换成某个提交的短哈希来固定版本，例如 `com.github.sunyatas:NetStatusBus:329c932`。发布正式版本后，请改用对应的版本标签。

### 方式二：从源码构建

```bash
git clone https://github.com/sunyatas/NetStatusBus.git
```

把 `netbuslib` 模块复制到你的项目中，然后在 `settings.gradle` 和 app 的依赖里加入：

```groovy
// settings.gradle
include ':netbuslib'

// app/build.gradle
dependencies {
    implementation project(':netbuslib')
}
```

也可以在本仓库中执行 `./gradlew :netbuslib:publishToMavenLocal`，再通过 `mavenLocal()` 依赖 `com.github.sunyatas:netstatusbus:master-SNAPSHOT`。

## 使用方法

### 1. 在 `Application` 中初始化

尽可能早地调用 `init`，建议放在 `Application.onCreate()` 中。重复调用不会有副作用，网络回调只会注册一次。

```java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NetStatusBus.getInstance().init(this);
    }
}
```

### 2. 注册和注销订阅者

根据组件的生命周期注册和注销。在 `init` 之前调用 `register` 会抛出异常。

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

调用 `NetStatusBus.getInstance().unregisterAllObserver()` 可以一次性移除所有订阅者。

#### 绑定生命周期自动注销

传入一个 `LifecycleOwner`，当它的生命周期进入 `DESTROYED` 时会自动注销订阅者，不必再写对应的 `unregister`。需要在主线程调用。

```java
// Activity：在 onDestroy 时自动注销
NetStatusBus.getInstance().register(this, this);

// Fragment：在 onViewCreated 中注册，onDestroyView 时自动注销
NetStatusBus.getInstance().register(this, getViewLifecycleOwner());
```

订阅者和 owner 可以是不同的对象（例如绑定到 Fragment 视图生命周期的 presenter）。如果 owner 已经销毁，则不会注册。提前手动调用 `unregister` 也没有问题。

### 3. 声明订阅方法

```java
@NetSubscribe(mode = Mode.AUTO)
public void onNetChanged(NetType netType) {
    switch (netType) {
        case WIFI:   tvStatus.setText("Wi-Fi");   break;
        case MOBILE: tvStatus.setText("移动网络"); break;
        case NONE:   tvStatus.setText("无网络");   break;
    }
}

@NetSubscribe(mode = Mode.NONE)
public void onNetLost() {
    Toast.makeText(this, "网络已断开", Toast.LENGTH_SHORT).show();
}
```

订阅方法必须满足：

- 是 `public` 方法，返回值为 `void`
- 没有参数，或只有一个 `NetType` 类型的参数
- 声明在被注册对象的类或其任意父类中（子类覆盖了带注解的方法时，只回调子类的版本）

如果带注解的方法不符合以上规则（例如参数是 `String`，或返回值不是 `void`），`register` 会直接抛出 `IllegalArgumentException` 并指出是哪个方法，而不是注册成功后一直不回调。

Kotlin：

```kotlin
@NetSubscribe(mode = Mode.WIFI)
fun onWifiChanged(netType: NetType) {
    binding.status.text = if (netType == NetType.WIFI) "Wi-Fi 已连接" else "Wi-Fi 已断开"
}
```

### 4. 另一种方式：用 LiveData 观察

如果不想用注解，可以观察 `getNetTypeLiveData()`。`init` 之后它会立即持有当前的 `NetType`，之后每次变化都在主线程更新。遵循标准的 `LiveData` 规则：只有 owner 至少处于 `STARTED` 时观察者才会收到值，owner 销毁时自动移除。

```java
NetStatusBus.getInstance().getNetTypeLiveData().observe(this, netType -> {
    tvStatus.setText(netType == NetType.NONE ? "无网络" : netType.name());
});
```

Kotlin，以及借助 `androidx.lifecycle:lifecycle-livedata-ktx` 转成 `Flow`：

```kotlin
NetStatusBus.getInstance().netTypeLiveData.observe(viewLifecycleOwner) { netType ->
    binding.status.text = netType.name
}

val netTypes: Flow<NetType> = NetStatusBus.getInstance().netTypeLiveData.asFlow()
```

`LiveData` 只保留最新值，观察者不活跃期间的中间变化会被合并，适合驱动 UI 状态。需要 `Mode` 过滤或不能错过任何一次变化时，请使用 `@NetSubscribe`。

## 订阅模式

`@NetSubscribe` 默认使用 `Mode.AUTO`。

| 模式 | 回调时机 |
| --- | --- |
| `Mode.AUTO` | 网络类型发生任何变化 |
| `Mode.WIFI` | Wi-Fi 本身连上或断开 |
| `Mode.WIFI_CONNECT` | 网络变为 Wi-Fi |
| `Mode.MOBILE` | 移动网络本身连上或断开 |
| `Mode.MOBILE_CONNECT` | 网络变为移动网络 |
| `Mode.NONE` | 网络断开 |

`Mode.WIFI` 和 `Mode.MOBILE` 只响应各自对应的网络。例如移动网络断开不会通知 `Mode.WIFI` 订阅者；从 Wi-Fi 切换到移动网络时两者都会被通知（`Mode.WIFI` 收到 `MOBILE`，表示 Wi-Fi 已断开）。

> 行为变更：旧版本中，只要网络变为 `NONE`，无论断开的是哪种网络，`Mode.WIFI` / `Mode.MOBILE` 订阅者都会被回调。

## 网络类型

`NetType` 有四个值：

| NetType | 含义 |
| --- | --- |
| `NetType.WIFI` | 通过 Wi-Fi 连接 |
| `NetType.MOBILE` | 通过移动网络连接 |
| `NetType.ETHERNET` | 通过有线网络（以太网）连接 |
| `NetType.NONE` | 没有可用网络 |

## 工具方法

`com.sunchen.netbus.utils.NetworkUtils`（需要先调用 `init`）：

```java
NetType type = NetworkUtils.getNetType();            // 当前网络类型
boolean online = NetworkUtils.isNetworkAvailable();  // 是否有已连接的网络
boolean vpn = NetworkUtils.isVpnActive();            // 是否连着 VPN（6.0+）
MobileGeneration g = NetworkUtils.getMobileGeneration(); // G2/G3/G4/G5/UNKNOWN，需要 READ_PHONE_STATE
NetworkUtils.openSetting(context);                   // 打开系统 Wi-Fi 设置
NetworkUtils.openSetting(activity, REQUEST_WIFI);    // 同上，返回时回调 onActivityResult
```

`openSetting(context)` 可以传入任意 `Context`；传入非 Activity（例如 `Application`）时会自动加上 `FLAG_ACTIVITY_NEW_TASK`。`openSetting(context, requestCode)` 在 `context` 是（或包装了）`Activity` 且 `requestCode >= 0` 时使用 `startActivityForResult`，用户返回时会回调你的 `onActivityResult`。系统设置页通常返回 `RESULT_CANCELED`，所以请在回调里调用 `NetworkUtils.getNetType()` 读取最新状态。`requestCode` 传负数表示不需要返回结果。如果设备上没有 Wi-Fi 设置页，会退回到系统设置首页。

## 实现原理

1. `init` 记录当前网络类型并注册回调：Android 7.0+ 使用默认网络回调（`registerDefaultNetworkCallback`），Android 5.0–6.0 使用监听所有网络的 `ConnectivityManager.NetworkCallback`。
2. Android 7.0+ 直接根据回调带来的系统默认网络的 `NetworkCapabilities` 计算类型，WIFI 与移动网络之间切换会立即回调；已不是默认网络的迟到 `onLost` 会被忽略。旧系统上则重新计算当前活动网络的类型（忽略刚刚断开的网络）。两种情况下都只有类型真正变化时才分发。
3. `register` 通过反射查找对象中带 `@NetSubscribe` 的方法，保存在线程安全的 Map 中；之后根据每个方法的模式以及变化前后的网络类型，在主线程回调对应的订阅者。

## 权限与 R8/ProGuard

- `android.permission.ACCESS_NETWORK_STATE` 已在库的清单中声明，会自动合并到你的应用中。
- 库自带消费者混淆规则，保留 `@NetSubscribe` 方法以及 `Mode`/`NetType` 枚举，开启代码压缩时无需额外配置。

## 常见问题

**为什么 `register` 之后没有立即收到回调？**
请确认已先调用 `init`。另外检查订阅模式：注册时只有当前状态符合模式的方法才会被回调（例如 `Mode.NONE` 只有在设备当前无网络时才会在注册时被回调）。

**有线网络和 VPN。**
有线网络报告为 `NetType.ETHERNET`。VPN 不是单独的类型，报告的是它底层网络的类型（例如通过 Wi-Fi 连接的 VPN 报告为 `WIFI`）。可以用 `NetworkUtils.isVpnActive()` 判断当前是否连着 VPN（Android 6.0+）。

**2G/3G/4G/5G。**
`NetworkUtils.getMobileGeneration()` 是可选功能：库本身不声明 `READ_PHONE_STATE`。宿主 App 声明并获得该权限（6.0+ 需运行时申请）后，可返回当前移动网络制式；没有权限或当前不是移动网络时返回 `UNKNOWN`。5G NSA 系统通常报告为 LTE，因此返回 `G4`。

**需要注销吗？**
建议注销。订阅者以弱引用保存，忘记 `unregister` 不会泄漏 Activity 或 Fragment，但已销毁的对象在被垃圾回收之前仍可能收到回调。可以在对应的生命周期回调中调用 `unregister`，或者使用 `register(subscriber, lifecycleOwner)` 让库自动注销。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

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
