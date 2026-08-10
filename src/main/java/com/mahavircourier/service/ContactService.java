package com.mahavircourier.service;

import com.mahavircourier.dao.ContactMessageDao;
import com.mahavircourier.dto.ContactForm;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    private final ContactMessageDao contactMessageDao;

    public ContactService(ContactMessageDao contactMessageDao) {
        this.contactMessageDao = contactMessageDao;
    }

    public void saveMessage(ContactForm form) {
        contactMessageDao.save(form);
    }
}
