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

import com.example.backend_spring.domain.Participant;
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.UserAccountRepository;

@Controller
@RequestMapping("/users/me")
public class UserProfileController {

    private final UserAccountRepository userRepo;
    private final ParticipantRepository participantRepo;
    private final PasswordEncoder passwordEncoder;

    public UserProfileController(
            UserAccountRepository userRepo,
            ParticipantRepository participantRepo,
            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.participantRepo = participantRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String profile(Principal principal, Model model) {
        var user = userRepo.findByUsername(principal.getName()).orElseThrow();
        String currentDisplayName = participantRepo.findByParticipantTypeAndUserId("USER", user.getId())
                .map(Participant::getDisplayName)
                .orElse(user.getUsername());
        model.addAttribute("user", user);
        model.addAttribute("currentDisplayName", currentDisplayName);
        return "users/me";
    }

    @PostMapping("/display-name")
    public String changeDisplayName(
            Principal principal,
            @RequestParam String displayName,
            RedirectAttributes redirectAttributes) {
        var user = userRepo.findByUsername(principal.getName()).orElseThrow();
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();

        if (normalizedDisplayName.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "表示名を入力してください。");
            return "redirect:/users/me";
        }
        if (normalizedDisplayName.length() > 100) {
            redirectAttributes.addFlashAttribute("error", "表示名は100文字以内で入力してください。");
            return "redirect:/users/me";
        }

        Participant participant = participantRepo.findByParticipantTypeAndUserId("USER", user.getId())
                .orElseGet(() -> new Participant("USER", user.getId(), normalizedDisplayName));
        participant.updateDisplayName(normalizedDisplayName);
        participantRepo.save(participant);

        redirectAttributes.addFlashAttribute("message", "表示名を更新しました。");
        return "redirect:/users/me";
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
