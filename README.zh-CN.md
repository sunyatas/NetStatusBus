# NetStatusBus

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
- 声明在被注册对象自身的类中（父类中继承来的方法不会被找到）

Kotlin：

```kotlin
@NetSubscribe(mode = Mode.WIFI)
fun onWifiChanged(netType: NetType) {
    binding.status.text = if (netType == NetType.WIFI) "Wi-Fi 已连接" else "Wi-Fi 已断开"
}
```

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
NetworkUtils.openSetting(activity, 0);               // 打开系统 Wi-Fi 设置
```

`openSetting` 通过 `context.startActivity` 打开设置页面，请传入 `Activity`。`requestCode` 参数目前未使用。

## 实现原理

1. `init` 注册一个 `ConnectivityManager.NetworkCallback`，并记录当前网络类型。
2. 在 `onAvailable`、`onLost` 和 `onCapabilitiesChanged` 中，重新计算当前活动网络的类型（忽略刚刚断开的网络），只有类型真正变化时才分发。
3. `register` 通过反射查找对象中带 `@NetSubscribe` 的方法，保存在线程安全的 Map 中；之后根据每个方法的模式以及变化前后的网络类型，在主线程回调对应的订阅者。

## 权限与 R8/ProGuard

- `android.permission.ACCESS_NETWORK_STATE` 已在库的清单中声明，会自动合并到你的应用中。
- 库自带消费者混淆规则，保留 `@NetSubscribe` 方法以及 `Mode`/`NetType` 枚举，开启代码压缩时无需额外配置。

## 常见问题

**为什么 `register` 之后没有立即收到回调？**
请确认已先调用 `init`。另外检查订阅模式：注册时只有当前状态符合模式的方法才会被回调（例如 `Mode.NONE` 只有在设备当前无网络时才会在注册时被回调）。

**有线网络和 VPN。**
有线网络报告为 `NetType.ETHERNET`。VPN 不是单独的类型，报告的是它底层网络的类型（例如通过 Wi-Fi 连接的 VPN 报告为 `WIFI`）。可以用 `NetworkUtils.isVpnActive()` 判断当前是否连着 VPN（Android 6.0+）。

**需要注销吗？**
需要。订阅者在调用 `unregister` 之前一直被强引用持有，请在对应的生命周期回调中注销，避免 Activity 或 Fragment 泄漏。

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
