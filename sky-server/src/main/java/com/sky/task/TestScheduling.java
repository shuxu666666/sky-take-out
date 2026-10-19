package com.sky.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//@Component
@Slf4j
public class TestScheduling {

    // @Scheduled(cron = "0/5 * * * * ?")
    public void test1() {
        log.info("测试一直在执行");
    }

    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次
    public void processTimeOutOrder() {
        log.info("测试每分钟执行一次");
    }
}
