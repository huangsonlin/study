package com.spring.action;

import com.spring.annotation.AnAutoWired;
import com.spring.annotation.AnController;
import com.spring.annotation.AnRequestMapping;
import com.spring.annotation.AnRequestParam;
import com.spring.service.DemoApi;
import com.spring.service.DemoService;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Author yanGe
 * @Date 2021/1/11 18:34
 **/
@AnController
@AnRequestMapping("/demo")
public class DemoAction {

    @AnAutoWired
    private DemoApi demoService;

    @AnRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,
        @AnRequestParam("name") String name){
        String result = demoService.get(name);
//		String result = "My name is " + name;
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
