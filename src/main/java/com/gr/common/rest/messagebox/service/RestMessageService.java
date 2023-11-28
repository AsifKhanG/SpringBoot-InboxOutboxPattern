package com.gr.common.rest.messagebox.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gr.common.rest.messagebox.entity.RestMessage;
import com.gr.common.rest.messagebox.repository.RestMessageRepository;

@Service
public class RestMessageService {


	@Autowired
	RestMessageRepository restMessageRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Long saveRestMessage(RestMessage restMessage) {
		return restMessageRepository.save(restMessage).getId();
	}


	public Optional<RestMessage> findById(Long id) {
		return restMessageRepository.findById(id);
	}

	public List<RestMessage> getPendingOutboxMessages() {
		return restMessageRepository.findInboxMessagesThatShouldBeProcessAgain(3);
	}

	public List<RestMessage> getPendingOutboxMessages(String methodName) {
		return restMessageRepository.findInboxMessagesThatShouldBeProcessAgain(methodName,3);
	}

    public Optional<RestMessage> getRestMessage(String contentHash, String methodName) {
		 return restMessageRepository.getExistingMessageBasedOnContentHash(contentHash, methodName,3);
    }
}
