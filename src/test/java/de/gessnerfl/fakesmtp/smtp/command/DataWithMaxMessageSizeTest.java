package de.gessnerfl.fakesmtp.smtp.command;

import de.gessnerfl.fakesmtp.repository.EmailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext
@ActiveProfiles({"integrationtest_with_max_size"})
@ExtendWith(OutputCaptureExtension.class)
class DataWithMaxMessageSizeTest extends AbstractCommandIntegrationTest {

    private static final String OVERSIZED_LINE = "a".repeat(8192);
    private static final int OVERSIZED_LINE_COUNT = 129;

    @Autowired
    private EmailRepository emailRepository;

    @Test
    void shouldRejectOversizedMessagesWithoutConsumingEmailIdAndLogReason(CapturedOutput output) throws Exception {
        this.expect("220");

        this.send("EHLO foo.com");
        this.expectContains("250-SIZE 1048576");

        sendValidMessage("Before oversized message");

        this.send("MAIL FROM:<validuser@example.com>");
        this.expect("250 Ok");

        this.send("RCPT TO:<success@example.com>");
        this.expect("250 Ok");

        this.send("DATA");
        this.expect("354 End data with <CR><LF>.<CR><LF>");

        for (int i = 0; i < OVERSIZED_LINE_COUNT; i++) {
            this.send(OVERSIZED_LINE);
        }
        this.send(".");

        this.expect("552 5.3.4 Message size exceeds fixed limit");

        this.send("NOOP");
        this.expect("250 Ok");

        sendValidMessage("After oversized message");

        var beforeEmail = emailRepository.findBySubject("Before oversized message").getFirst();
        var afterEmail = emailRepository.findBySubject("After oversized message").getFirst();

        assertEquals(beforeEmail.getId() + 1, afterEmail.getId());
        assertThat(output.getAll(), containsString("Rejected SMTP message"));
        assertThat(output.getAll(), containsString("configured maximum message size of 1048576 bytes"));
    }

    private void sendValidMessage(String subject) throws Exception {
        this.send("MAIL FROM:<validuser@example.com>");
        this.expect("250 Ok");
        this.send("RCPT TO:<success@example.com>");
        this.expect("250 Ok");
        this.send("DATA");
        this.expect("354 End data with <CR><LF>.<CR><LF>");
        this.send("From: validuser@example.com");
        this.send("To: success@example.com");
        this.send("Subject: " + subject);
        this.send("Content-Type: text/plain; charset=utf-8");
        this.send("");
        this.send("Body");
        this.send(".");
        this.expect("250 Ok");
    }
}
