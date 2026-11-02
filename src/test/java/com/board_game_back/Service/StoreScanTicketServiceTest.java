package com.board_game_back.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.board_game_back.Exception.StoreErrorCode;
import com.board_game_back.Exception.StoreException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** QR 찍은 순간을 증명하는 티켓 — 위조·다른 매장 거부, 24시간 유효 */
class StoreScanTicketServiceTest {

    private static final LocalDateTime SCANNED = LocalDateTime.of(2026, 11, 2, 5, 0);

    private final StoreScanTicketService tickets = new StoreScanTicketService("test-jwt-secret");

    @Test
    void 발급한_티켓은_찍은_시각을_돌려준다() {
        String ticket = tickets.issue(7L, SCANNED);

        assertThat(tickets.verify(ticket, 7L, SCANNED.plusHours(23))).isEqualTo(SCANNED);
    }

    @Test
    void 위조하거나_다른_매장_티켓이면_거부한다() {
        String ticket = tickets.issue(7L, SCANNED);
        String forged = ticket.replaceFirst("\\.\\d+\\.", "." + (SCANNED.toEpochSecond(java.time.ZoneOffset.UTC) + 3600) + ".");

        assertCode(() -> tickets.verify(forged, 7L, SCANNED), StoreErrorCode.TICKET_INVALID);
        assertCode(() -> tickets.verify(ticket, 8L, SCANNED), StoreErrorCode.TICKET_INVALID);
        assertCode(() -> tickets.verify("abc", 7L, SCANNED), StoreErrorCode.TICKET_INVALID);
        assertCode(() -> tickets.verify(null, 7L, SCANNED), StoreErrorCode.TICKET_INVALID);
        assertCode(() -> new StoreScanTicketService("other-secret").verify(ticket, 7L, SCANNED), StoreErrorCode.TICKET_INVALID);
    }

    @Test
    void 이십사시간이_지나면_만료된다() {
        String ticket = tickets.issue(7L, SCANNED);

        assertCode(() -> tickets.verify(ticket, 7L, SCANNED.plusHours(24)), StoreErrorCode.TICKET_EXPIRED);
    }

    private static void assertCode(Runnable call, StoreErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(StoreException.class)
            .satisfies(e -> assertThat(((StoreException) e).code()).isEqualTo(code));
    }
}
