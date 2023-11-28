package com.gr.common.rest.messagebox.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.gr.common.rest.messagebox.entity.RestMessage;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestMessageRepository extends CrudRepository<RestMessage, Long>{

    @Query("select m from RestMessage as m where m.type='OUTBOX' and m.status = 'NEW' AND m.retryCount <= 3")
    Page<RestMessage> findOutboxMessagesThatCouldNotBeSent(Pageable pageRequest);

    @Query("select m from RestMessage as m where m.type='OUTBOX' and m.status = 'FAILURE' AND m.retryCount <= 3" )
    Page<RestMessage> findOutboxMessagesThatFailedSending(Pageable pageRequest);


    @Query("select m from RestMessage as m where m.type='INBOX' and m.status = 'NEW'")
    Page<RestMessage> findInboxMessagesThatCouldNotBeSent(Pageable pageRequest);

    @Query("select m from RestMessage as m where m.type='INBOX' and m.status = 'FAILURE'")
    Page<RestMessage> findInboxMessagesThatFailedSending(Pageable pageRequest);


    @Query("select m from RestMessage as m where m.type='OUTBOX' and (m.status= 'NEW' OR m.status ='FAILURE') AND m.retryCount <= :retryCount")
    List<RestMessage> findInboxMessagesThatShouldBeProcessAgain(Integer retryCount);

    @Query("select m from RestMessage as m where m.type='OUTBOX' and (m.status= 'NEW' OR m.status ='FAILURE') AND m.serviceMethodName LIKE :methodName AND m.retryCount <= :retryCount")
    List<RestMessage> findInboxMessagesThatShouldBeProcessAgain(String methodName, Integer retryCount);

    @Query("select m from RestMessage as m where m.type='OUTBOX' and (m.status= 'NEW' OR m.status ='FAILURE') AND m.serviceMethodName LIKE :methodName AND m.contentHash LIKE :contentHash AND m.retryCount <= :retryCount")
    Optional<RestMessage> getExistingMessageBasedOnContentHash(String contentHash, String methodName, Integer retryCount);
}
