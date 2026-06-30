package com.apexupi.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
})
@EnableConfigurationProperties
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
public class OperationsDashboardApiApplication {


    public static void main(String[] args) {
        SpringApplication.run(OperationsDashboardApiApplication.class, args);
    }
}

