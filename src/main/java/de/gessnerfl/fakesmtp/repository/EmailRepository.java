package de.gessnerfl.fakesmtp.repository;

import de.gessnerfl.fakesmtp.model.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long>, JpaSpecificationExecutor<Email> {

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM email_content WHERE email IN (SELECT id FROM email ORDER BY received_on DESC OFFSET ?1)", nativeQuery = true)
    int deleteEmailContentExceedingLimit(int maxNumber);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM email_attachment WHERE email IN (SELECT id FROM email ORDER BY received_on DESC OFFSET ?1)", nativeQuery = true)
    int deleteEmailAttachmentsExceedingLimit(int maxNumber);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM email_inline_image WHERE email IN (SELECT id FROM email ORDER BY received_on DESC OFFSET ?1)", nativeQuery = true)
    int deleteEmailInlineImagesExceedingLimit(int maxNumber);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM email WHERE id IN (SELECT id FROM email ORDER BY received_on DESC OFFSET ?1)", nativeQuery = true)
    int deleteEmailsExceedingDateRetentionLimit(int maxNumber);

    @Transactional
    default int deleteEmailsExceedingDateRetentionLimitWithCascade(int maxNumber) {
        deleteEmailContentExceedingLimit(maxNumber);
        deleteEmailAttachmentsExceedingLimit(maxNumber);
        deleteEmailInlineImagesExceedingLimit(maxNumber);
        return deleteEmailsExceedingDateRetentionLimit(maxNumber);
    }

    @Transactional
    @Query
    List<Email> findBySubject(String subject);

}
