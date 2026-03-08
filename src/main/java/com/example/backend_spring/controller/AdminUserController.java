package com.example.backend_spring.controller;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.backend_spring.domain.Participant;
import com.example.backend_spring.domain.UserAccount;
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.UserAccountRepository;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAccountRepository userRepo;
    private final ParticipantRepository participantRepo;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(
            UserAccountRepository userRepo,
            ParticipantRepository participantRepo,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.participantRepo = participantRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String users(Model model) {
        List<UserRow> rows = userRepo.findAll().stream()
                .map(u -> new UserRow(
                        u.getId(),
                        u.getUsername(),
                        u.isEnabled(),
                        userRepo.findRoleNamesByUserId(u.getId())))
                .toList();
        model.addAttribute("users", rows);
        return "admin/users";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            RedirectAttributes redirectAttributes) {
        var user = userRepo.findById(id).orElseThrow();
        user.setEnabled(enabled);
        userRepo.save(user);
        redirectAttributes.addFlashAttribute("message", "ユーザー状態を更新しました。");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String setRole(
            @PathVariable Long id,
            @RequestParam String role,
            @RequestParam String action,
            RedirectAttributes redirectAttributes) {
        if (!"ROLE_ADMIN".equals(role) && !"ROLE_USER".equals(role)) {
            redirectAttributes.addFlashAttribute("error", "不正なロールです。");
            return "redirect:/admin/users";
        }

        if ("grant".equals(action)) {
            jdbcTemplate.update("""
                    INSERT INTO user_roles(user_id, role_id, created_at)
                    SELECT ?, r.id, CURRENT_TIMESTAMP(6)
                    FROM roles r
                    WHERE r.name = ?
                      AND NOT EXISTS (
                        SELECT 1 FROM user_roles ur WHERE ur.user_id = ? AND ur.role_id = r.id
                      )
                    """, id, role, id);
        } else if ("revoke".equals(action)) {
            jdbcTemplate.update("""
                    DELETE ur
                    FROM user_roles ur
                    JOIN roles r ON r.id = ur.role_id
                    WHERE ur.user_id = ? AND r.name = ?
                    """, id, role);
        }

        redirectAttributes.addFlashAttribute("message", "ロールを更新しました。");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var user = userRepo.findById(id).orElseThrow();
        userRepo.delete(user);
        redirectAttributes.addFlashAttribute("message", "ユーザーを削除しました。");
        return "redirect:/admin/users";
    }

    public record UserRow(Long id, String username, boolean enabled, List<String> roles) {
    }

    @PostMapping("/create")
    @Transactional
    public String createUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String enabled,
            RedirectAttributes redirectAttributes) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        boolean enabledFlag = "true".equalsIgnoreCase(enabled);

        if (normalizedUsername.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Username is required.");
            return "redirect:/admin/users";
        }
        if (password == null || password.length() < 8) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters.");
            return "redirect:/admin/users";
        }
        if (userRepo.findByUsername(normalizedUsername).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Username already exists.");
            return "redirect:/admin/users";
        }

        UserAccount saved = userRepo.save(new UserAccount(
                normalizedUsername,
                passwordEncoder.encode(password),
                enabledFlag));

        String participantDisplayName = normalizedDisplayName.isEmpty() ? normalizedUsername : normalizedDisplayName;

        jdbcTemplate.update("""
                INSERT INTO roles(name)
                SELECT 'ROLE_USER'
                WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER')
                """);

        jdbcTemplate.update("""
                INSERT INTO user_roles(user_id, role_id, created_at)
                SELECT ?, r.id, CURRENT_TIMESTAMP(6)
                FROM roles r
                WHERE r.name = 'ROLE_USER'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM user_roles ur
                      WHERE ur.user_id = ?
                        AND ur.role_id = r.id
                  )
                """, saved.getId(), saved.getId());

        participantRepo.findByParticipantTypeAndUserId("USER", saved.getId())
                .orElseGet(() -> participantRepo.save(new Participant("USER", saved.getId(), participantDisplayName)));

        redirectAttributes.addFlashAttribute(
                "message",
                "User created: " + normalizedUsername + " (participant: " + participantDisplayName + ")");
        return "redirect:/admin/users";
    }
}
