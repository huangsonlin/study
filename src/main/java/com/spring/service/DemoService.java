package com.spring.service;

import com.spring.annotation.AnService;

/**
 * @Author huangsl
 * @Date 2021/1/11 18:35
 **/
@AnService
public class DemoService implements DemoApi {

    public String get(String name) {
        return "My name is" + name + "form service";
    }
}
