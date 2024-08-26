package com.example.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

class Utils {

    public static boolean isSseConnectLost(SseEmitter sseEmitter) {
        if (sseEmitter == null) {
            return true;
        }
        Object sendFailed = getFieldInstance(sseEmitter, "sendFailed");
        Object complete = getFieldInstance(sseEmitter, "complete");
        if (sendFailed != null && (Boolean) sendFailed == true)
            return true;
        if (complete != null && (Boolean) complete == true) {
            return true;
        }
        return false;
    }

    public static Object getFieldInstance(Object obj, String fieldPath) {
        String[] fields = fieldPath.split("#");
        for (String field : fields) {
            obj = getField(obj, obj.getClass(), field);
            if (obj == null) {
                return null;
            }
        }
        return obj;
    }

    public static Object getField(Object obj, Class<?> clazz, String fieldName) {
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                java.lang.reflect.Field field;
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e) {
                // Ignore exceptions
            }
        }
        return null;
    }
}

@RestController
@Slf4j
@RequestMapping("/api/sse")
public class SseControllerTest {
    private static Map<String, SseEmitter> sseCache = new ConcurrentHashMap<>();

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String id) throws IOException {
        // 设置超时时间为3秒，用于演示客户端自动重连
        SseEmitter sseEmitter = sseCache.get(id);
        if (sseEmitter == null) {
            sseEmitter = new SseEmitter();
            log.info("添加连接：" + id);
            sseCache.put(id, sseEmitter);

            sseEmitter.onTimeout(() -> {
                log.info(id + " timeout");
                closeEmitter(id);
            });

            sseEmitter.onCompletion(() -> {
                log.info("连接 completed！");
                sseCache.remove(id);
            });

            sseEmitter.onError((Throwable t) -> {
                log.info("连接 error");
                closeEmitter(id);
            });

            // 设置前端的重试时间为1秒
            sseEmitter.send(SseEmitter.event().reconnectTime(500));
            // process(sseEmitter, id, "data");
        }

        return sseEmitter;
    }

    void process(SseEmitter sseEmitter, String id, String content) {
        Thread t = new Thread(() -> {
            int i = 0;
            try {
                for (; i < 5; i++) {
                    if (Utils.isSseConnectLost(sseEmitter)) {
                        break;
                    }
                    try {
                        sseEmitter.send(
                                SseEmitter.event().id("" + i).data(String.format("%s-%s-%d\n\n", content, id, i))); // 发送事件
                        Thread.sleep(300);
                    } catch (Exception e) {

                    }
                }
                sseEmitter.send(SseEmitter.event().id("" + i).name("close").data("close"));
            } catch (Exception e) {
                sseCache.remove(id);
            }
            closeEmitter(id);
        });
        t.start();
    }

    @GetMapping(path = "/receive")
    public String push(@RequestParam String id, @RequestParam String content) {
        log.info("receive push message id={} content={}", id, content);
        SseEmitter sseEmitter = sseCache.get(id);
        if (sseEmitter != null) {
            process(sseEmitter, id, content);
            return "push";
        } else {
            return "null";
        }

    }

    @GetMapping(path = "/close")
    public String closeEmitter(@RequestParam String id) {
        SseEmitter sseEmitter = sseCache.get(id);
        if (sseEmitter != null) {
            sseEmitter.complete();
            sseCache.remove(id);
        }
        return "over";
    }
}
