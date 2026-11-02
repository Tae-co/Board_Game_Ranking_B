package com.board_game_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 그룹이 바뀌었다는 신호만 보낸다 (plan-store-impl §3.4). 상태는 폰이 {@code GET /parties/current}로 다시 받는다.
 * 커밋 뒤에 보내야 폰이 옛 상태를 읽지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StorePartyNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public void changed(Long partyId) {
        if (partyId == null) return;
        Runnable send = () -> messagingTemplate.convertAndSend("/topic/store-party/" + partyId, "changed");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
