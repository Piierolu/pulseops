package com.pulseops.controlplane.incident;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class DiscordIncidentNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordIncidentNotifier.class);

    private final RestClient restClient;
    private final String webhookUrl;

    DiscordIncidentNotifier(
            RestClient.Builder restClientBuilder,
            @Value("${pulseops.notifications.discord-webhook-url:}") String webhookUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void notifyDiscord(IncidentChangedEvent event) {
        if (webhookUrl.isBlank()) {
            return;
        }
        String marker = event.status() == IncidentStatus.OPEN ? "DOWN" : "RECOVERED";
        String content = "[PulseOps] " + marker + " | " + event.monitorName()
                + " | incident " + event.incidentId();
        try {
            restClient.post().uri(webhookUrl).body(Map.of("content", content)).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            LOGGER.error("Discord incident notification failed for {}", event.incidentId(), exception);
        }
    }
}
