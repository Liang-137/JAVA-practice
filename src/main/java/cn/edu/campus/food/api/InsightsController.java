package cn.edu.campus.food.api;

import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.service.*;
import cn.edu.campus.food.service.InsightsService.RankingMetric;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InsightsController {
    private final InsightsService service;
    public InsightsController(InsightsService service) { this.service = service; }
    @GetMapping("/rankings") public List<RankEntry> ranking(
            @RequestParam(defaultValue = "OVERALL") RankingMetric metric,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.ranking(metric, limit);
    }
    @GetMapping("/statistics/monthly") public MonthlyExpense monthly(@RequestParam String month) {
        return service.monthly(MealService.parseMonth(month));
    }
}
