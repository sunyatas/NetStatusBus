# NetStatusBus [![Download](https://api.bintray.com/packages/sunchen/maven/netstatusbus/images/download.svg)](https://bintray.com/sunchen/maven/netstatusbus/_latestVersion)

```
NetStateBus 是一个可以无缝进行网络状态监听的框架，使用简单，具有强解耦，高性能等特性。
```



## 快速体验

![](https://www.pgyer.com/app/qrcode/USYp)

如果二维码图片不可见，[点我下载Demo体验](https://www.pgyer.com/USYp)



## 通过以下方式来使用 NetStatusBus

1. 通过 Gradle 添加依赖：

```groovy
implementation 'com.sunchen:netstatusbus:0.1.5'
```



2. Application 中初始化 NetStatusBus：

```java
 // 尽可能早的进行这一步操作, 建议在 Application 中完成初始化操作
 NetStatusBus.getInstance().init(this);
```



3. 根据你的生命周期来注册和注销订阅者，例如：

```java
 @Override
 public void onStart() {
     super.onStart();
     NetStatusBus.getInstance().register(this);
 }

 @Override
 public void onStop() {
     super.onStop();
     NetStatusBus.getInstance().unregister(this);
 }
```



4. 声明你的订阅方法，在该方法中可以监听到网络状态的变更：
   比如想要监听 wifi 连接的情况

```java
@NetSubscribe(mode = Mode.WIFI_CONNECT)
 public void doSometing() {
      tvTips.setText("已连接到wifi");
 }
```



## 注意事项

订阅方法**可以选填**一个`NetType`参数，可以通过`NetType`的值来判断当前网络类型。

 `@NetSubscribe `中可以指定 `mode `用来设置订阅的模式，mode类型如下：

#### `Mode.AUTO`

 这是默认值，任何网络状态发生变化，该类型订阅者都会被调用。

```java
//所有网络变化都会被调用，可以通过 NetType 来判断当前网络具体状态
@NetSubscribe(mode = Mode.AUTO)
public void netChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```

#### `Mode.WIFI`

 由 WIFI 改变引发的网络状态变化的情况下（wifi连接和断开），该类型订阅者会被调用。

```java
// 当 wifi 连接和失去连接时都被调用
@NetSubscribe(mode = Mode.WIFI)
public void wifiChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```

#### `Mode.WIFI_CONNECT`

 仅在 WIFI 成功连接后，该类型订阅者会被调用。

```java
// 只有当 wifi 连接时都被调用
@NetSubscribe(mode = Mode.WIFI_CONNECT)
public void wifiChange() {
    Log.d(Constrants.LOG_TAG, "连接到wifi网络");
}
```

#### `Mode.MOBILE`

 由移动网络改变引发的网络状态变化的情况时（移动网络连接和断开），该类型订阅者会被回调。

```java
// 当移动网络连接和失去连接时都会被调用
@NetSubscribe(mode = Mode.MOBILE)
public void netChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```

#### `Mode.MOBILE _CONNECT`

 仅在移动网络成功连接后，会被回调。

```java
// 当移动网络连接时调用
@NetSubscribe(mode = Mode.MOBILE _CONNECT)
public void netChange() {
    Log.d(Constrants.LOG_TAG, "连接到移动网络");
}
```

#### `Mode.NONE`

 只有当网络丢失时，该类型订阅者才会被回调。

```java
// 只有当网络丢失时，该类型订阅者才会被回调。
@NetSubscribe(mode = Mode.NONE)
public void netChange() {
    Log.d(Constrants.LOG_TAG, "失去网络");
}
```

注意：由于Android 在7.0以后出于性能及安全的考虑对广播做了大量的限制，监听网络连接的广播在7.0以后的系统上也只有动态注册才能生效。
本库出于性能考虑决定使用 `NetworkCallback` 类来代替广播实现网络状态变化监听。因此需要您将`minSdkVersion` 升级为21及以上。

如果确实需要满足在 Android 5.0 以下机型中进行运行，请联系我，我者会根据反馈考虑是否重新加入广播进行监听网络事件。

## 联系方式
[微博](http://weibo.com/sunchen1996 )

QQ Email: [sunchen.cc@qq.com](sunchen.cc@qq.com)


适配targetSdk33