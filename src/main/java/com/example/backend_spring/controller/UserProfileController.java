package com.example.backend_spring.controller;

import java.security.Principal;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.backend_spring.repository.UserAccountRepository;

@Controller
@RequestMapping("/users/me")
public class UserProfileController {

    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserProfileController(UserAccountRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String profile(Principal principal, Model model) {
        var user = userRepo.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("user", user);
        return "users/me";
    }

    @PostMapping("/password")
    public String changePassword(
            Principal principal,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            RedirectAttributes redirectAttributes) {
        var user = userRepo.findByUsername(principal.getName()).orElseThrow();

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "現在のパスワードが一致しません。");
            return "redirect:/users/me";
        }
        if (newPassword == null || newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("error", "新しいパスワードは8文字以上で入力してください。");
            return "redirect:/users/me";
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        redirectAttributes.addFlashAttribute("message", "パスワードを更新しました。");
        return "redirect:/users/me";
    }
}
