package com.flux.user_management_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class UserManagementServiceApplicationTests {

	@MockitoBean
	private org.springframework.mail.javamail.JavaMailSender javaMailSender;

	@Test
	void contextLoads() {
	}

}
