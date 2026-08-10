package com.mahavircourier.service;

import com.mahavircourier.dao.ContactMessageDao;
import com.mahavircourier.dto.ContactForm;
import com.mahavircourier.model.ContactMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactMessageDao contactMessageDao;

    public ContactService(ContactMessageDao contactMessageDao) {
        this.contactMessageDao = contactMessageDao;
    }

    public void saveMessage(ContactForm form) {
        contactMessageDao.save(form);
    }

    public List<ContactMessage> list() {
        return contactMessageDao.findAll();
    }

    public Optional<ContactMessage> findById(Long id) {
        return contactMessageDao.findById(id);
    }

    @Transactional
    public void markRead(Long id) {
        contactMessageDao.markStatus(id, "READ");
    }

    @Transactional
    public void markStatus(Long id, String status) {
        contactMessageDao.markStatus(id, status);
    }

    @Transactional
    public void delete(Long id) {
        contactMessageDao.deleteById(id);
    }

    public long countUnread() {
        return contactMessageDao.countUnread();
    }
}
