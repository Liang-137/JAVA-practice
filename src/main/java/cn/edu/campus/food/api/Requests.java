package cn.edu.campus.food.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Requests {
    private Requests() { }

    public record CanteenInput(@NotBlank @Size(max = 80) String name,
                               @NotNull @Size(max = 200) String location) { }
    public record StallInput(@Positive long canteenId, @NotBlank @Size(max = 80) String name) { }
    public record DishInput(@Positive long stallId, @NotBlank @Size(max = 80) String name,
                            @NotNull @DecimalMin("0.00") @DecimalMax("999999.99")
                            @Digits(integer = 6, fraction = 2) BigDecimal price,
                            @NotBlank @Size(max = 30) String category,
                            @NotNull @Size(max = 8) List<@NotBlank @Size(max = 30)
                            @Pattern(regexp = "[^,]+", message = "口味标签不能包含逗号") String> flavors) { }
    public record MarkInput(@NotNull Boolean favorite, @NotNull Boolean avoided) { }
    public record RatingInput(@NotNull @Min(1) @Max(5) Integer taste,
                              @NotNull @Min(1) @Max(5) Integer valueScore,
                              @NotNull @Min(1) @Max(5) Integer portion,
                              @NotNull @Min(1) @Max(5) Integer recommendation) { }
    public record MealItemInput(@Positive long dishId, @Min(1) @Max(100) int quantity,
                                @NotNull @DecimalMin("0.00") @DecimalMax("999999.99")
                                @Digits(integer = 6, fraction = 2) BigDecimal paidAmount,
                                @Valid RatingInput rating,
                                @NotNull @Size(max = 1000) String comment) { }
    public record MealInput(@NotNull @PastOrPresent LocalDateTime eatenAt,
                            @NotNull MealType mealType, @NotNull @Size(max = 1000) String note,
                            @NotEmpty @Size(max = 30) List<@NotNull @Valid MealItemInput> items) { }
    public enum MealType { BREAKFAST, LUNCH, DINNER, SNACK }
}
