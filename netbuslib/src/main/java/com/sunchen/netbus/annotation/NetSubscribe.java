package com.sunchen.netbus.annotation;

import com.sunchen.netbus.type.NetType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by 「孙晨」 on 2019/4/2   22:13.
 * <p>
 * God bless me only
 * <p>
 * NetSubscribe
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NetSubscribe {
    NetType netType() default NetType.AUTO;
}
