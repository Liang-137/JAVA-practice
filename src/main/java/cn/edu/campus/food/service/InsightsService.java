package cn.edu.campus.food.service;

import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.support.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class InsightsService {
    private final FoodRepository db;
    private final CurrentUser user;
    public InsightsService(FoodRepository db, CurrentUser user) { this.db = db; this.user = user; }

    public enum RankingMetric { OVERALL, TASTE, VALUE, PORTION, RECOMMENDATION }
    public record RankRow(long dishId, String name, BigDecimal score, int ratingCount, Long photoId) { }
    public List<RankEntry> ranking(RankingMetric metric, int limit) {
        if (limit < 1 || limit > 100) throw BusinessException.invalid("limit 必须在 1～100 之间");
        String scoreExpression = switch (metric) {
            case OVERALL -> "(i.taste+i.value_score+i.portion+i.recommendation)/4.0";
            case TASTE -> "i.taste";
            case VALUE -> "i.value_score";
            case PORTION -> "i.portion";
            case RECOMMENDATION -> "i.recommendation";
        };
        String sql = """
            SELECT d.id AS dish_id,d.name,AVG(%s) AS score,COUNT(*) AS rating_count,
              (SELECT MAX(p.id) FROM dish_photos p WHERE p.dish_id=d.id AND p.user_id=d.user_id) AS photo_id
            FROM meal_items i JOIN meals e ON e.id=i.meal_id JOIN dishes d ON d.id=i.dish_id
            WHERE e.user_id=? AND d.user_id=? AND i.taste IS NOT NULL
            GROUP BY d.id,d.name,d.user_id
            ORDER BY score DESC,rating_count DESC,d.id ASC LIMIT ?
            """.formatted(scoreExpression);
        var rows = db.query(sql, RankRow.class, user.id(), user.id(), limit);
        return IntStream.range(0, rows.size()).mapToObj(i -> {
            var row = rows.get(i);
            return new RankEntry(i + 1, row.dishId(), row.name(), row.score().setScale(2, RoundingMode.HALF_UP),
                    row.ratingCount(), row.photoId() == null ? null : "/api/photos/" + row.photoId() + "/content");
        }).toList();
    }

    public record ExpenseRow(LocalDate date, BigDecimal amount, int mealCount) { }
    public MonthlyExpense monthly(YearMonth month) {
        var rows = db.query("""
            SELECT CAST(e.eaten_at AS DATE) AS date,SUM(t.amount) AS amount,COUNT(*) AS meal_count
            FROM meals e JOIN (SELECT meal_id,SUM(paid_amount) AS amount FROM meal_items GROUP BY meal_id) t
              ON t.meal_id=e.id
            WHERE e.user_id=? AND e.eaten_at>=? AND e.eaten_at<?
            GROUP BY CAST(e.eaten_at AS DATE) ORDER BY date
            """, ExpenseRow.class, user.id(), month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        Map<LocalDate, ExpenseRow> byDate = new HashMap<>();
        rows.forEach(row -> byDate.put(row.date(), row));
        var days = IntStream.rangeClosed(1, month.lengthOfMonth()).mapToObj(day -> {
            LocalDate date = month.atDay(day);
            var row = byDate.get(date);
            return new DailyExpense(date, row == null ? new BigDecimal("0.00") : row.amount(), row == null ? 0 : row.mealCount());
        }).toList();
        BigDecimal total = rows.stream().map(ExpenseRow::amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
        int count = rows.stream().mapToInt(ExpenseRow::mealCount).sum();
        BigDecimal average = count == 0 ? new BigDecimal("0.00") : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new MonthlyExpense(month.toString(), total, count, average, days);
    }
}
