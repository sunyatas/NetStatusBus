package com.sunchen.netbus.annotation;

import com.sunchen.netbus.type.Mode;
import com.sunchen.netbus.type.NetType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives network changes after its object is passed to
 * {@link com.sunchen.netbus.NetStatusBus#register(Object)}.
 * <p>
 * The method must return {@code void} and take either no parameter or one {@link NetType}
 * parameter; any other signature makes {@code register} throw
 * {@link IllegalArgumentException}. Any visibility works, and annotated methods declared in
 * superclasses are found too (an override is called once). Methods are called on the main
 * thread: once right after {@code register} with the current type, then on every change
 * that matches {@link #mode()}.
 * <pre>
 * &#64;NetSubscribe(mode = Mode.WIFI_CONNECT)
 * public void onWifiConnected() { ... }
 *
 * &#64;NetSubscribe
 * public void onNetChanged(NetType type) { ... }
 * </pre>
 * <p>
 * 标记接收网络变化的方法：返回 void，无参数或只有一个 NetType 参数，否则 register 时抛出异常；
 * 父类中的注解方法也会被找到；在主线程回调，注册后立即回调一次当前类型，之后按 mode 过滤回调每次变化。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NetSubscribe {
    /**
     * Which changes this method receives; {@link Mode#AUTO} (every change) by default.
     */
    Mode mode() default Mode.AUTO;
}
