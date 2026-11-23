package cn.edu.campus.food.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Immutable business results; JSON field names form the frontend API contract. */
public final class Models {
    private Models() { }

    public record Canteen(long id, String name, String location, boolean active) { }
    public record Stall(long id, long canteenId, String name, boolean active) { }
    public record Dish(long id, long stallId, long canteenId, String name, BigDecimal price,
                       String category, List<String> flavors, boolean active, boolean available,
                       boolean favorite, boolean avoided, BigDecimal score, String photoUrl) { }
    public record Photo(long id, long dishId, String url, String contentType) { }
    public record PhotoFile(long id, String filename, String contentType) { }
    public record Rating(Integer taste, Integer valueScore, Integer portion, Integer recommendation) { }
    public record MealItem(long dishId, String dishName, int quantity, BigDecimal paidAmount,
                           Rating rating, String comment) { }
    public record Meal(long id, LocalDateTime eatenAt, String mealType, String note,
                       BigDecimal totalAmount, List<MealItem> items) { }
    public record Page<T>(List<T> items, int page, int size, long total) { }
    public record RankEntry(int rank, long dishId, String name, BigDecimal score,
                            int ratingCount, String photoUrl) { }
    public record DailyExpense(LocalDate date, BigDecimal amount, int mealCount) { }
    public record MonthlyExpense(String month, BigDecimal totalAmount, int mealCount,
                                  BigDecimal averagePerMeal, List<DailyExpense> days) { }
    public record Recommendation(Dish dish, int candidateCount, String reason) { }
}
