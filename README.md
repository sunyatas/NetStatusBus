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
implementation 'com.sunchen:netstatusbus:0.1.3'
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

```java
@NetSubscribe(netType = NetType.AUTO)
 public void doSometing(NetType netType) {
 			Log.d(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<activity1");
      tvTips.setText("MainActivit当前网络状态>>>>" + netType.name());
 }
```



## 注意事项

订阅方法必须填写一个`NetType`参数，可以通过`NetType`的值来判断当前网络类型。

#### `@NetSubscribe `中 `NetType`的类型： 

#### `NetType.AUTO`

 这是默认值，任何网络状态发生变化，该类型订阅者都会被回调。同时会传入`NetType`参数告知你当前的网络类型，示例如下：

```java
@NetSubscribe(netType = NetType.AUTO)
public void netChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```

#### `NetType.WIFI`

 只要当前是由 WIFI 改变引发的网络状态变化，该类型订阅者都会被回调。同时会传入`NetType`参数告知你当前的网络类型，示例如下：

```java
// 当 wifi 连接时，或者没有网络时会回调此方法
@NetSubscribe(netType = NetType.WIFI)
public void netChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```

#### `NetType.MOBILE`

 只要当前是由移动网络改变引发的网络状态变化，该类型订阅者都会被回调。同时会传入`NetType`参数告知你当前的网络类型，示例如下：

```java
// 当正在使用移动网络时，或者没有网络时会回调此方法
@NetSubscribe(netType = NetType.MOBILE)
public void netChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```

#### `NetType.NONE`

 只有当网络丢失时，该类型订阅者才会被回调。

```java
@NetSubscribe(netType = NetType.NONE)
public void netChange(NetType netType) {
    Log.d(Constrants.LOG_TAG, netType.name());
}
```



注意：由于Android 在7.0以后出于性能及安全的考虑对广播做了大量的限制，监听网络连接的广播在7.0以后的系统上也只有动态注册才能生效。
本库出于性能考虑决定使用 `NetworkCallback` 类来代替广播实现网络状态变化监听。因此需要您将`minSdkVersion` 升级为21及以上。

如果确实需要满足在 Android 5.0 以下机型中进行运行，请联系作者，作者会根据反馈考虑是否重新加入广播进行监听网络事件。


## 联系方式

QQ Email: [sunchen.cc@qq.com](mailto:	sunchen.cc@qq.com)
