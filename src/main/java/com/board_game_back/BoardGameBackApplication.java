package com.board_game_back;

import com.board_game_back.Config.LocalProfileGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BoardGameBackApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BoardGameBackApplication.class);
        app.addListeners(new LocalProfileGuard());
        app.run(args);
    }

}
