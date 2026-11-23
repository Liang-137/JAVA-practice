package cn.edu.campus.food.support;

import cn.edu.campus.food.api.Requests.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DemoDataInitializer implements CommandLineRunner {
    private final FoodRepository db;
    private final CatalogService catalog;
    private final MealService meals;
    private final boolean demo;
    public DemoDataInitializer(FoodRepository db, CatalogService catalog, MealService meals,
                               @Value("${app.demo-data:true}") boolean demo) {
        this.db = db; this.catalog = catalog; this.meals = meals; this.demo = demo;
    }
    @Override @Transactional
    public void run(String... args) {
        if (!db.query("SELECT id FROM app_users WHERE id=1", UserId.class).isEmpty()) return;
        db.update("INSERT INTO app_users(id,display_name) VALUES(1,?)", "本地体验用户");
        if (!demo) return;
        var canteen = catalog.createCanteen(new CanteenInput("第一食堂", "校园东侧"));
        var stall = catalog.createStall(new StallInput(canteen.id(), "家常菜档口"));
        var beef = catalog.createDish(new DishInput(stall.id(), "番茄牛腩饭", new BigDecimal("15.00"), "米饭", List.of("酸甜", "不辣")));
        var noodle = catalog.createDish(new DishInput(stall.id(), "香菇鸡肉面", new BigDecimal("12.00"), "面食", List.of("清淡", "不辣")));
        catalog.createDish(new DishInput(stall.id(), "麻辣香锅", new BigDecimal("20.00"), "香锅", List.of("麻辣")));
        catalog.mark(beef.id(), new MarkInput(true, false));
        meals.create(new MealInput(LocalDateTime.now().minusDays(1), MealType.LUNCH, "首次体验记录",
                List.of(new MealItemInput(beef.id(), 1, new BigDecimal("15.00"), new RatingInput(5,4,5,5), "牛肉很软，下次还来"))));
        meals.create(new MealInput(LocalDateTime.now().minusDays(2), MealType.DINNER, "",
                List.of(new MealItemInput(noodle.id(), 1, new BigDecimal("12.00"), new RatingInput(4,5,4,4), "汤很鲜"))));
    }
    public record UserId(long id) { }
}
