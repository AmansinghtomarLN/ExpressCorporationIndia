package com.mahavircourier.controller;

import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.dto.ContactForm;
import com.mahavircourier.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class HomeController {

    private final BranchDao branchDao;
    private final ContactService contactService;

    public HomeController(BranchDao branchDao, ContactService contactService) {
        this.branchDao = branchDao;
        this.contactService = contactService;
    }

    @GetMapping({"/", "/home"})
    public String home() {
        return "index";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/services")
    public String services() {
        return "services";
    }

    @GetMapping("/branches")
    public String branches(Model model) {
        model.addAttribute("branches", branchDao.findAll());
        return "branches";
    }

    @GetMapping("/contact")
    public String contactPage(Model model) {
        if (!model.containsAttribute("contactForm")) {
            model.addAttribute("contactForm", new ContactForm());
        }
        return "contact";
    }

    @PostMapping("/contact")
    public String submitContact(@Valid @ModelAttribute("contactForm") ContactForm form,
                                 BindingResult bindingResult,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            return "contact";
        }
        contactService.saveMessage(form);
        model.addAttribute("submitted", true);
        model.addAttribute("contactForm", new ContactForm());
        return "contact";
    }
}
