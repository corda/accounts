package net.corda.accounts.cordapp.client.webserver

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType.SERVLET
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Our Spring Boot application.
 */
@SpringBootApplication
open class Application

/**
 * Starts our Spring Boot application.
 */
fun main(args: Array<String>) {
    val app = SpringApplication(Application::class.java)
    app.setBannerMode(Banner.Mode.OFF)
    app.webApplicationType = SERVLET
    app.run(*args)
}
