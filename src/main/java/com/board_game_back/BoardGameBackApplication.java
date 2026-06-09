package com.board_game_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BoardGameBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoardGameBackApplication.class, args);
    }

}
