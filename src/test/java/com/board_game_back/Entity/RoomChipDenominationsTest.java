package com.board_game_back.Entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoomChipDenominationsTest {

    @Test
    void 칩_종류는_중복을_빼고_큰_값부터_저장한다() {
        Room room = new Room("포커", "ABC123", 1L);
        room.changeChipDenominations(List.of(100, 5000, 10, 100));
        assertThat(room.getChipDenominations()).isEqualTo("5000,100,10");
        assertThat(room.getChipDenominationList()).containsExactly(5000, 100, 10);
    }

    @Test
    void 정하지_않으면_null() {
        assertThat(new Room("포커", "ABC123", 1L).getChipDenominationList()).isNull();
    }

    @Test
    void 비었거나_0_이하거나_7개_이상이면_거부한다() {
        Room room = new Room("포커", "ABC123", 1L);
        assertThatThrownBy(() -> room.changeChipDenominations(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> room.changeChipDenominations(List.of(100, 0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> room.changeChipDenominations(List.of(1, 2, 3, 4, 5, 6, 7)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
