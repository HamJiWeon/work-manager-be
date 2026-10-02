package work.managerbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ManagerBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagerBeApplication.class, args);
    }

}
