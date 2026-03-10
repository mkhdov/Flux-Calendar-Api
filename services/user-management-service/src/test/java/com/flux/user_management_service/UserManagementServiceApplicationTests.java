package com.flux.user_management_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.mail.username=test@flux.local",
		"app.frontend.url=http://localhost:3000"
})
class UserManagementServiceApplicationTests {

	@MockitoBean
	private org.springframework.mail.javamail.JavaMailSender javaMailSender;

	@Test
	void contextLoads() {
	}

}
