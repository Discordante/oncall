package com.nextdigital.oncall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.nextdigital.oncall.config.TestcontainersConfig;

@SpringBootTest
@Import(TestcontainersConfig.class)
class OncallApplicationTests {

	@Test
	void contextLoads() {
	}

}
