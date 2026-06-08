package com.noya.payBridge;

import org.springframework.boot.SpringApplication;

public class TestPayBridgeApplication {

	public static void main(String[] args) {
		SpringApplication.from(PayBridgeApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
