package com.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author huangsl
 * @Date 2021/1/11 18:24
 **/
@Target({ElementType.TYPE})// 接口、类、枚举、注解
@Retention(RetentionPolicy.RUNTIME)  // 注解会在class字节码文件中存在，在运行时可以通过反射获取到
@Documented //解包含在javadoc中
public @interface AnController {
    String value() default "";

}
