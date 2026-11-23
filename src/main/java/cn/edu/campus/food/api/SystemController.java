package cn.edu.campus.food.api;

import cn.edu.campus.food.service.CurrentUser;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final CurrentUser user;
    public SystemController(CurrentUser user) { this.user = user; }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status", "UP", "application", "food-diary"); }
    @GetMapping("/me") public Map<String,Object> me() { return Map.of("id", user.id(), "displayName", "本地体验用户", "mode", "SINGLE_USER_DEMO"); }
}
