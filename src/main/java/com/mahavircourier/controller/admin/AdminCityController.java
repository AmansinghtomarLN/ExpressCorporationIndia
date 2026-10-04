package com.mahavircourier.controller.admin;

import com.mahavircourier.model.City;
import com.mahavircourier.service.CityCategory;
import com.mahavircourier.service.CityService;
import com.mahavircourier.service.CustomUserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/cities")
public class AdminCityController {

    private final CityService cityService;

    public AdminCityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "state", required = false) String state,
                       @RequestParam(value = "category", required = false) String category,
                       @RequestParam(value = "editId", required = false) Long editId,
                       Model model) {
        model.addAttribute("cities", cityService.search(q, state, category));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("state", state == null ? "" : state);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("states", CityCategory.STATES);
        model.addAttribute("city", new City());
        if (editId != null) {
            cityService.findById(editId).ifPresent(c -> model.addAttribute("editCity", c));
        }
        return "admin/cities";
    }

    @PostMapping
    public String create(@ModelAttribute City city, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            City created = cityService.create(city);
            redirectAttributes.addFlashAttribute("successMessage", "City added.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cities";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute City city,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            city.setId(id);
            City updated = cityService.update(city);
            redirectAttributes.addFlashAttribute("successMessage", "City updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cities";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            cityService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "City deleted.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cities";
    }
}
