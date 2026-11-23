package cn.edu.campus.food.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Query parameters are optional. A missing boolean imposes no corresponding filter. */
public record DishQuery(@Positive Long canteenId, @Positive Long stallId,
                        @Size(max = 80) String keyword, @Size(max = 30) String category,
                        @Size(max = 30) String flavor,
                        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal minPrice,
                        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal maxPrice,
                        Boolean favorite, Boolean avoided, Boolean eaten, Boolean includeArchived,
                        @Min(1) Integer page, @Min(1) @Max(100) Integer size) { }
