package com.yizhaoqi.smartpai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "elasticsearch.index-initializer.enabled=false")
class SmartPaiApplicationTests {

    @Test
    void contextLoads() {
    }

}
