package cn.edu.campus.food.service;

import cn.edu.campus.food.api.Requests.*;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.support.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ExperienceService {
    public record Input(@Positive Long dishId, @Size(max=80) String dishName,
                        @NotNull @PastOrPresent LocalDateTime eatenAt, @NotNull MealType mealType,
                        @NotNull @DecimalMin("0") @Digits(integer=6,fraction=2) BigDecimal paidAmount,
                        @Valid RatingInput rating, @Size(max=1000) String comment) {}
    public record Id(long id) {}
    public record Entry(long mealId, LocalDateTime eatenAt, MealItem item, List<String> photos) {}
    public record ImageData(String contentType, byte[] imageData) {}

    private final FoodRepository db;
    private final CurrentUser user;
    private final CatalogService catalog;
    private final MealService meals;

    public ExperienceService(FoodRepository db, CurrentUser user, CatalogService catalog, MealService meals) {
        this.db = db;
        this.user = user;
        this.catalog = catalog;
        this.meals = meals;
    }

    @Transactional(rollbackFor=IOException.class)
    public Entry save(long stallId, Input input, List<MultipartFile> photos) throws IOException {
        db.require("SELECT id FROM stalls WHERE user_id=? AND id=? FOR UPDATE", Id.class, "档口", user.id(), stallId);
        long dishId = resolveDish(stallId, input);
        var item = new MealItemInput(dishId, 1, input.paidAmount(), input.rating(),
                input.comment() == null ? "" : input.comment());
        Meal meal = meals.create(new MealInput(input.eatenAt(), input.mealType(), "", List.of(item)));
        for (var photo : photos) {
            if (photo.isEmpty() || photo.getSize() > 5 * 1024 * 1024) throw BusinessException.invalid("图片必须非空且不超过 5MB");
            byte[] bytes = photo.getBytes();
            String type = PhotoService.validateImage(bytes).equals("png") ? "image/png" : "image/jpeg";
            db.update("INSERT INTO experience_photos(meal_id,dish_id,content_type,image_data) VALUES(?,?,?,?)",
                    meal.id(), dishId, type, bytes);
        }
        return entry(meal, dishId);
    }

    private long resolveDish(long stallId, Input input) {
        if (input.dishId() != null) {
            var selected = catalog.dish(input.dishId());
            if (selected.stallId() != stallId) throw BusinessException.invalid("该菜品不属于此档口");
            return selected.id();
        }
        if (input.dishName() == null || input.dishName().isBlank()) throw BusinessException.invalid("请填写菜名或选择已有菜品");
        String name = input.dishName().strip();
        var matches = db.query("SELECT id FROM dishes WHERE user_id=? AND stall_id=? AND name=?",
                Id.class, user.id(), stallId, name);
        return matches.isEmpty()
                ? catalog.createDish(new DishInput(stallId, name, input.paidAmount(), "未分类", List.of())).id()
                : matches.get(0).id();
    }

    private Entry entry(Meal meal, long dishId) {
        var item = meal.items().stream().filter(i -> i.dishId() == dishId).findFirst().orElseThrow();
        var photos = db.query("SELECT id FROM experience_photos WHERE meal_id=? AND dish_id=? ORDER BY id",
                Id.class, meal.id(), dishId);
        return new Entry(meal.id(), meal.eatenAt(), item,
                photos.stream().map(p -> "/api/experience-photos/" + p.id()).toList());
    }

    public Page<Entry> history(long dishId, int page, int size) {
        var result = meals.list(null, dishId, page, size);
        return new Page<>(result.items().stream().map(m -> entry(m, dishId)).toList(), page, size, result.total());
    }

    public ImageData image(long id) {
        return db.require("SELECT p.content_type,p.image_data FROM experience_photos p JOIN meals m ON p.meal_id=m.id WHERE p.id=? AND m.user_id=?",
                ImageData.class, "照片", id, user.id());
    }
}
