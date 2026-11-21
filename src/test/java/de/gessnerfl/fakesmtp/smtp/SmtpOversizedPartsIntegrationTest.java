package de.gessnerfl.fakesmtp.smtp;

import de.gessnerfl.fakesmtp.model.Email;
import de.gessnerfl.fakesmtp.model.EmailPartProcessingStatus;
import de.gessnerfl.fakesmtp.repository.EmailAttachmentRepository;
import de.gessnerfl.fakesmtp.repository.EmailContentRepository;
import de.gessnerfl.fakesmtp.repository.EmailInlineImageRepository;
import de.gessnerfl.fakesmtp.repository.EmailRepository;
import de.gessnerfl.fakesmtp.smtp.client.SmartClient;
import de.gessnerfl.fakesmtp.smtp.server.SmtpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles({"integrationtest", "default"})
@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
@SpringBootTest(properties = "fakesmtp.max-attachment-size=16B")
class SmtpOversizedPartsIntegrationTest {

    private static final String FROM_ADDRESS = "sender@example.com";
    private static final String TO_ADDRESS = "receiver@example.com";

    @Autowired
    private SmtpServer smtpServer;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private EmailAttachmentRepository emailAttachmentRepository;

    @Autowired
    private EmailContentRepository emailContentRepository;

    @Autowired
    private EmailInlineImageRepository emailInlineImageRepository;

    @BeforeEach
    @AfterEach
    void clearEmailData() {
        emailAttachmentRepository.deleteAllInBatch();
        emailContentRepository.deleteAllInBatch();
        emailInlineImageRepository.deleteAllInBatch();
        emailRepository.deleteAllInBatch();
    }

    @Test
    void shouldPersistEmailWithOversizedPartsWithoutSequenceGapAndLogSkippedParts(CapturedOutput output) throws Exception {
        String uniqueToken = String.valueOf(System.currentTimeMillis());
        String beforeSubject = "Before oversized parts " + uniqueToken;
        String oversizedPartsSubject = "Oversized parts " + uniqueToken;
        String afterSubject = "After oversized parts " + uniqueToken;

        sendTextEmail(beforeSubject);
        sendOversizedMultipartEmail(oversizedPartsSubject);
        sendTextEmail(afterSubject);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(emailRepository.findBySubject(beforeSubject), hasSize(1));
            assertThat(emailRepository.findBySubject(oversizedPartsSubject), hasSize(1));
            assertThat(emailRepository.findBySubject(afterSubject), hasSize(1));
        });

        Email beforeEmail = emailRepository.findBySubject(beforeSubject).getFirst();
        Email oversizedPartsEmail = emailRepository.findBySubject(oversizedPartsSubject).getFirst();
        Email afterEmail = emailRepository.findBySubject(afterSubject).getFirst();

        assertEquals(beforeEmail.getId() + 1, oversizedPartsEmail.getId());
        assertEquals(oversizedPartsEmail.getId() + 1, afterEmail.getId());

        assertThat(emailAttachmentRepository.findAll(), hasSize(1));
        var attachment = emailAttachmentRepository.findAll().getFirst();
        assertThat(attachment.getProcessingStatus(), is(EmailPartProcessingStatus.SKIPPED_TOO_LARGE));
        assertEquals(0, attachment.getData().length);
        assertThat(attachment.getProcessingMessage(), containsString("SKIPPED_TOO_LARGE"));

        assertThat(emailInlineImageRepository.findAll(), hasSize(1));
        var inlineImage = emailInlineImageRepository.findAll().getFirst();
        assertThat(inlineImage.getProcessingStatus(), is(EmailPartProcessingStatus.SKIPPED_TOO_LARGE));
        assertEquals("", inlineImage.getData());
        assertThat(inlineImage.getProcessingMessage(), containsString("SKIPPED_TOO_LARGE"));

        assertThat(output.getAll(), containsString("SKIPPED_TOO_LARGE: Attachment 'oversized.bin'"));
        assertThat(output.getAll(), containsString("SKIPPED_TOO_LARGE: Inline image"));
    }

    private void sendTextEmail(String subject) throws IOException {
        String rawMessage = """
                From: %s
                To: %s
                Subject: %s
                Content-Type: text/plain; charset=utf-8

                Body
                """.formatted(FROM_ADDRESS, TO_ADDRESS, subject);
        sendRawEmail(rawMessage);
    }

    private void sendOversizedMultipartEmail(String subject) throws IOException {
        String base64Data = Base64.getEncoder().encodeToString(new byte[128]);
        String rawMessage = """
                From: %s
                To: %s
                Subject: %s
                MIME-Version: 1.0
                Content-Type: multipart/mixed; boundary="outer-boundary"

                --outer-boundary
                Content-Type: multipart/related; boundary="related-boundary"

                --related-boundary
                Content-Type: text/html; charset=utf-8

                <html><body><img src="cid:oversized-inline"></body></html>
                --related-boundary
                Content-Type: image/png
                Content-Transfer-Encoding: base64
                Content-ID: <oversized-inline>
                Content-Disposition: inline

                %s
                --related-boundary--
                --outer-boundary
                Content-Type: application/octet-stream; name="oversized.bin"
                Content-Transfer-Encoding: base64
                Content-Disposition: attachment; filename="oversized.bin"

                %s
                --outer-boundary--
                """.formatted(FROM_ADDRESS, TO_ADDRESS, subject, base64Data, base64Data);
        sendRawEmail(rawMessage);
    }

    private void sendRawEmail(String rawMessage) throws IOException {
        var client = new SmartClient("localhost", smtpServer.getPort(), "localhost");
        try {
            client.from(FROM_ADDRESS);
            client.to(TO_ADDRESS);
            client.dataStart();
            byte[] messageData = rawMessage.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
            client.dataWrite(messageData, messageData.length);
            client.dataEnd();
        } finally {
            client.quit();
        }
    }
}
