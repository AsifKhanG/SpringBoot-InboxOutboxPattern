package com.gr.common.rest.messagebox.repository;

import com.gr.common.rest.messagebox.entity.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboxRepository extends JpaRepository<Inbox, String> {


}
