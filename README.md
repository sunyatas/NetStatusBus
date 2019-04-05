花一分钟时间来了解下如何使用 NetStateBus 进行网络状态监听
# NetStateBus
     
![image](http://image.sunchen.cc/blog/imagenetstatebus_process.png)

NetStateBus 是一个可以无缝进行网络状态监听的框架，具有强解耦，高性能等特性。

1. 只需要在你的 Application 的 onCreate() 方法中进行初始化：

``` java
NetStateBus.getDefault().init(this);
```


2. 在 Activity 或者 Fragment 的生命周期中进行注册和注销订阅者，如下：
``` java
@Override
protected void onStart() {
    super.onStart();
    NetStateBus.getDefault().register(this);
}

@Override
protected void onStop() {
    super.onStop();
    NetStateBus.getDefault().unregister(this);
}
```


3. 声明你的订阅方法，你可以选择一种监听类型，NetType.AUTO 是指在任何情况下只要有网络发生变化即执行该方法，你也可以选择指定 NetType.WIFI 或者 NetType.MOBILE，即指定订阅 wifi 或者手机移动网络发生变化。

``` java
@NetSubscriber(netType = NetType.AUTO)
public void doSomething(NetType netType) {
    Log.e(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<activity");
}
````
当网络状态发生改变时，会自动执行使用 @NetSubscriber 进行注解的方法。

好了，这样就拥有了一个强解耦能力的网络监听框架，使用它我们可以避免复杂和容易出错的依赖关系以及各种生命周期问题，使代码变得更简单健壮。
