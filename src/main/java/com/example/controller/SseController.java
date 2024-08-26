package com.example.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping(path = "/api/sse")
public class SseController {

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@RequestParam("id") String id) {
        SseEmitter emitter = new SseEmitter();
        process(emitter, id);
        // 在这里添加数据到 SSE 通道
        return emitter;
    }

    public void process(SseEmitter emitter, String id) {
        // 在新线程中发送事件以避免阻塞主线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    emitter.send(SseEmitter.event().id("" + i).data(String.format("data-%s-%d\n\n", id, i))); // 发送事件
                    Thread.sleep(500); // 每秒发送一次
                }
                emitter.complete(); // 完成事件流
            } catch (Exception e) {
                emitter.completeWithError(e); // 发送错误
            }
        }).start();
    }

}
