package com.example.demo

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.*

@SpringBootApplication
class SpringBootTutorialApplication {

	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			SpringApplication.run(SpringBootTutorialApplication::class.java, *args)
		}
	}
}
