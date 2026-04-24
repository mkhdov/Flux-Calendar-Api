package com.flux.notification_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class NotificationServiceApplicationTests {

	@MockitoBean
	private org.springframework.mail.javamail.JavaMailSender javaMailSender;

	@Test
	void contextLoads() {
	}

}
