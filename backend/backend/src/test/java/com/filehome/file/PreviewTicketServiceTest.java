package com.filehome.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.filehome.common.ApiException;
import com.filehome.config.AppProperties;

class PreviewTicketServiceTest {

    private final PreviewTicketService service = new PreviewTicketService(
            new AppProperties(
                    new AppProperties.Jwt("j".repeat(32), 120),
                    new AppProperties.Auth(12, false, 60),
                    new AppProperties.Upload(
                            16 * 1024 * 1024,
                            1024L * 1024 * 1024,
                            48,
                            4),
                    new AppProperties.Storage(
                            "http://127.0.0.1:9000",
                            "http://127.0.0.1:9001",
                            "access",
                            "secret",
                            "bucket",
                            "storage"),
                    new AppProperties.Share(
                            "http://localhost",
                            "s".repeat(32)),
                    new AppProperties.Cors(List.of("http://localhost")),
                    new AppProperties.Cache(30, 60, 30)));

    @Test
    void verifiesIssuedTicketClaims() {
        UUID actorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        PreviewTicketService.Ticket ticket = service.issue(
                FilePreviewService.SCOPE_PERSONAL,
                actorId,
                ownerId,
                fileId);

        PreviewTicketService.Claims claims = service.verify(ticket.value());
        assertThat(claims.scope()).isEqualTo(FilePreviewService.SCOPE_PERSONAL);
        assertThat(claims.actorId()).isEqualTo(actorId);
        assertThat(claims.ownerId()).isEqualTo(ownerId);
        assertThat(claims.fileId()).isEqualTo(fileId);
    }

    @Test
    void rejectsTamperedTicket() {
        PreviewTicketService.Ticket ticket = service.issue(
                FilePreviewService.SCOPE_PERSONAL,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
        int separator = ticket.value().indexOf('.');
        char firstSignatureCharacter = ticket.value().charAt(separator + 1);
        String tampered = ticket.value().substring(0, separator + 1)
                + (firstSignatureCharacter == 'A' ? 'B' : 'A')
                + ticket.value().substring(separator + 2);

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(ApiException.class);
    }
}
