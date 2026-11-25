package cn.edu.campus.food;

import cn.edu.campus.food.api.DishQuery;
import cn.edu.campus.food.api.Requests.*;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.service.*;
import cn.edu.campus.food.service.InsightsService.RankingMetric;
import cn.edu.campus.food.support.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:food-test;DB_CLOSE_DELAY=-1", "app.demo-data=false", "logging.file.name=./target/test.log"
})
@Import(FoodDiaryIntegrationTest.UserConfig.class)
class FoodDiaryIntegrationTest {
    @TempDir static Path uploads;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage-path", () -> uploads.toString());
    }
    static class TestUser implements CurrentUser {
        final AtomicLong current = new AtomicLong(1);
        @Override public long id() { return current.get(); }
    }
    @TestConfiguration static class UserConfig {
        @Bean @Primary TestUser testUser() { return new TestUser(); }
    }
    @Autowired FoodRepository db;
    @Autowired CatalogService catalog;
    @Autowired MealService meals;
    @Autowired InsightsService insights;
    @Autowired PhotoService photos;
    @Autowired TestUser user;
    @LocalServerPort int port;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    long canteenId, stallId;

    @BeforeEach void reset() {
        user.current.set(1);
        for (String table : List.of("meal_items", "meals", "dish_photos", "dish_marks", "dishes", "stalls", "canteens", "app_users"))
            db.update("DELETE FROM " + table);
        db.update("INSERT INTO app_users(id,display_name) VALUES(1,'测试用户一'),(2,'测试用户二')");
        canteenId = catalog.createCanteen(new CanteenInput("第一食堂", "东区")).id();
        stallId = catalog.createStall(new StallInput(canteenId, "家常菜")).id();
    }
    Dish dish(String name, String price, String flavor) {
        return catalog.createDish(new DishInput(stallId, name, new BigDecimal(price), "米饭", List.of(flavor)));
    }
    MealItemInput item(long id, String paid, Integer score) {
        return new MealItemInput(id, 1, new BigDecimal(paid), score == null ? null : new RatingInput(score, score, score, score), "");
    }
    MealInput meal(int day, MealItemInput... items) {
        return new MealInput(LocalDateTime.of(2020, 1, day, 12, 0), MealType.LUNCH, "", List.of(items));
    }
    DishQuery filter(BigDecimal max, String flavor, Boolean eaten, Boolean archived) {
        return new DishQuery(null,null,null,null,flavor,null,max,null,null,eaten,archived,null,null);
    }
    @Test void mealTotalsUseActualPaymentAndCountMealsInsteadOfDishes() {
        var rice = dish("牛肉饭", "15.00", "微辣");
        var soup = dish("汤", "5.00", "清淡");
        var saved = meals.create(meal(2, item(rice.id(), "13.10", 5), item(soup.id(), "4.20", null)));
        assertThat(saved.totalAmount()).isEqualByComparingTo("17.30");
        catalog.updateDish(rice.id(), new DishInput(stallId,"改名后的牛肉饭", new BigDecimal("99.00"),"米饭",List.of("微辣")));
        var stats = insights.monthly(YearMonth.of(2020, 1));
        assertThat(stats.totalAmount()).isEqualByComparingTo("17.30");
        assertThat(stats.mealCount()).isEqualTo(1);
        assertThat(stats.averagePerMeal()).isEqualByComparingTo("17.30");
        assertThat(stats.days()).hasSize(31);
        assertThat(meals.get(saved.id()).items().get(0).dishName()).isEqualTo("牛肉饭");
        meals.update(saved.id(), meal(2,item(rice.id(),"13.10",5)));
        assertThat(meals.get(saved.id()).items().get(0).dishName()).isEqualTo("牛肉饭");
    }
    @Test void rankingAveragesRepeatedRatingsAndIgnoresUnratedMeals() {
        var a = dish("A", "10.00", "清淡"); var b = dish("B", "12.00", "清淡");
        meals.create(meal(1,item(a.id(),"10",5)));
        var low = meals.create(meal(2,item(a.id(),"10",1)));
        meals.create(meal(3,item(a.id(),"10",null)));
        meals.create(meal(4,item(b.id(),"12",4)));
        var rank = insights.ranking(RankingMetric.OVERALL,10);
        assertThat(rank).extracting(RankEntry::dishId).containsExactly(b.id(),a.id());
        assertThat(rank.get(1).score()).isEqualByComparingTo("3.00");
        assertThat(rank.get(1).ratingCount()).isEqualTo(2);
        meals.delete(low.id());
        assertThat(insights.ranking(RankingMetric.TASTE,10).get(0).dishId()).isEqualTo(a.id());
    }
    @Test void invalidNewOrUpdatedMealRollsBackAllChanges() {
        var dish = dish("饭", "10.00", "清淡");
        assertThatThrownBy(() -> meals.create(meal(1,item(dish.id(),"10",5),item(999999,"4",1))))
                .isInstanceOf(BusinessException.class);
        assertThat(meals.list(null,null,1,20).total()).isZero();
        var saved = meals.create(meal(1,item(dish.id(),"10",5)));
        assertThatThrownBy(() -> meals.update(saved.id(),meal(2,item(dish.id(),"10",3),item(dish.id(),"20",2))))
                .isInstanceOf(BusinessException.class);
        assertThat(meals.get(saved.id()).totalAmount()).isEqualByComparingTo("10");
        assertThat(meals.get(saved.id()).items().get(0).rating().taste()).isEqualTo(5);
    }
    @Test void filtersAndRecommendationsExcludeAvoidedAndArchivedDishes() {
        var good = dish("清淡饭", "10", "清淡"); var bad = dish("踩雷饭", "11", "清淡");
        dish("辣饭", "30", "麻辣");
        catalog.mark(bad.id(),new MarkInput(false,true));
        var query = filter(new BigDecimal("15"),"清淡",null,false);
        assertThat(catalog.dishes(query).total()).isEqualTo(2);
        for (int i=0;i<10;i++) assertThat(catalog.recommend(query).dish().id()).isEqualTo(good.id());
        assertThatThrownBy(() -> catalog.mark(good.id(),new MarkInput(true,true))).isInstanceOf(BusinessException.class);
        meals.create(meal(1,item(good.id(),"9",5)));
        assertThat(catalog.dishes(filter(null,null,true,false)).total()).isEqualTo(1);
        catalog.archiveCanteen(canteenId);
        assertThat(catalog.dishes(query).total()).isZero();
        assertThatThrownBy(() -> catalog.recommend(filter(null,null,null,true))).isInstanceOf(BusinessException.class);
        assertThat(insights.monthly(YearMonth.of(2020,1)).totalAmount()).isEqualByComparingTo("9");
        catalog.restoreCanteen(canteenId);
        assertThat(catalog.dish(good.id()).available()).isTrue();
    }
    @Test void anotherUsersCatalogMealsRankingsAndPhotosAreNotVisible() throws Exception {
        var dish = dish("私有菜品", "10", "清淡");
        var meal = meals.create(meal(1,item(dish.id(),"10",5)));
        var photo = photos.upload(dish.id(),png());
        user.current.set(2);
        assertThat(catalog.canteens(true)).isEmpty();
        assertThat(catalog.dishes(filter(null,null,null,true)).total()).isZero();
        assertThatThrownBy(() -> catalog.dish(dish.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> catalog.mark(dish.id(),new MarkInput(true,false))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> meals.get(meal.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> photos.download(photo.id())).isInstanceOf(BusinessException.class);
        assertThat(insights.ranking(RankingMetric.OVERALL,10)).isEmpty();
        assertThat(insights.monthly(YearMonth.of(2020,1)).mealCount()).isZero();
    }
    @Autowired ExperienceService experiences;

    @Test void experiencesReuseOnlyTheSameStallDishAndKeepPhotosPerVisit() throws Exception {
        var input = new ExperienceService.Input(null, "拉面", meal(2).eatenAt(), MealType.LUNCH, BigDecimal.TEN, null, null);
        var first = experiences.save(stallId, input, List.of(png(), png()));
        var second = experiences.save(stallId, input, List.of(png()));
        long dishId = first.item().dishId();
        assertThat(second.item().dishId()).isEqualTo(dishId);
        var other = catalog.createStall(new StallInput(canteenId, "另一档口"));
        assertThat(experiences.save(other.id(), input, List.of()).item().dishId()).isNotEqualTo(dishId);
        var side = dish("汤", "2", "清淡");
        meals.update(first.mealId(), meal(3, item(dishId, "10", 5), item(side.id(), "2", null)));
        var history = experiences.history(dishId, 1, 20).items();
        assertThat(history).extracting(ExperienceService.Entry::mealId).containsExactly(first.mealId(), second.mealId());
        assertThat(history.get(0).photos()).hasSize(2).isEqualTo(first.photos());
        assertThat(history.get(1).photos()).hasSize(1).doesNotContainAnyElementsOf(first.photos());
        assertThat(experiences.history(side.id(), 1, 20).items().get(0).photos()).isEmpty();
        var url = first.photos().get(0);
        long photoId = Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        assertThat(experiences.image(photoId).imageData()).isEqualTo(png().getBytes());
        meals.delete(first.mealId());
        assertThatThrownBy(() -> experiences.image(photoId)).isInstanceOf(BusinessException.class);
    }

    @Test void invalidExperiencePhotoRollsBackNewDishAndMeal() {
        var input = new ExperienceService.Input(null, "拉面", meal(2).eatenAt(), MealType.LUNCH, BigDecimal.TEN, null, null);
        assertThatThrownBy(() -> experiences.save(stallId, input, List.of(png(), new MockMultipartFile("photos", new byte[0]))))
                .isInstanceOf(BusinessException.class);
        assertThat(meals.list(null, null, 1, 20).total()).isZero();
        assertThat(catalog.dishes(filter(null, null, null, null)).total()).isZero();
    }

    MockMultipartFile png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("file","../../photo.png","image/png",bytes.toByteArray());
    }
    @Test void realPhotoRoundTripsAndFakeImageIsRejected() throws Exception {
        var dish = dish("拍照菜品", "10", "清淡"); var file = png();
        var photo = photos.upload(dish.id(),file);
        assertThat(photos.list(dish.id())).hasSize(1);
        assertThat(photos.download(photo.id()).resource().getContentAsByteArray()).isEqualTo(file.getBytes());
        var response = request("GET",photo.url(),null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("image/png");
        assertThatThrownBy(() -> photos.upload(dish.id(),new MockMultipartFile("file","fake.png","image/png","not an image".getBytes())))
                .isInstanceOf(BusinessException.class);
        photos.delete(photo.id());
        assertThat(photos.list(dish.id())).isEmpty();
        assertThatThrownBy(() -> photos.download(photo.id())).isInstanceOf(BusinessException.class);
    }
    @Test void statisticsUseHalfOpenMonthBoundariesAndHandleEmptyMonths() {
        var dish = dish("饭", "10", "清淡");
        meals.create(meal(31,item(dish.id(),"10.01",null)));
        meals.create(new MealInput(LocalDateTime.of(2020,2,1,0,0),MealType.SNACK,"",List.of(item(dish.id(),"4.99",null))));
        assertThat(insights.monthly(YearMonth.of(2020,1)).totalAmount()).isEqualByComparingTo("10.01");
        assertThat(insights.monthly(YearMonth.of(2020,2)).totalAmount()).isEqualByComparingTo("4.99");
        var empty = insights.monthly(YearMonth.of(2020,3));
        assertThat(empty.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(empty.averagePerMeal()).isEqualByComparingTo("0.00");
    }
    @Test void httpContractRejectsInvalidRequestsAndReturnsUsefulStatusCodes() throws Exception {
        assertThat(request("GET","/api/health",null).statusCode()).isEqualTo(200);
        assertThat(request("POST","/api/canteens","{\"name\":\"\",\"location\":\"\"}").statusCode()).isEqualTo(400);
        assertThat(request("POST","/api/canteens","{").statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/dishes?size=0",null).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/dishes?minPrice=20&maxPrice=10",null).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/statistics/monthly?month=2020-99",null).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/rankings?metric=INVALID",null).statusCode()).isEqualTo(400);
        assertThat(request("GET","/api/dishes/999999",null).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/does-not-exist",null).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/recommendations/random",null).body()).contains("NO_CANDIDATE");
        var created = request("POST","/api/canteens","{\"name\":\"新食堂\",\"location\":\"西区\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("新食堂");
        assertThat(request("POST","/api/canteens","{\"name\":\"新食堂\",\"location\":\"西区\"}").statusCode()).isEqualTo(409);
    }
    @Test void httpCanCreateRatedMealAndReturnRankingsAndStatistics() throws Exception {
        var dish = dish("接口菜品","10","清淡");
        String payload = """
            {"eatenAt":"2020-01-02T12:00:00","mealType":"LUNCH","note":"接口测试",
             "items":[{"dishId":%d,"quantity":2,"paidAmount":18.50,"comment":"好吃",
              "rating":{"taste":5,"valueScore":4,"portion":4,"recommendation":5}}]}
            """.formatted(dish.id());
        var created = request("POST","/api/meals",payload);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("18.50","2020-01-02T12:00:00");
        assertThat(request("GET","/api/rankings",null).body()).contains("4.50","接口菜品");
        assertThat(request("GET","/api/statistics/monthly?month=2020-01",null).body()).contains("18.50","\"mealCount\":1");
        assertThat(request("POST","/api/meals",payload.replace("\"taste\":5","\"taste\":6")).statusCode()).isEqualTo(400);
        assertThat(request("POST","/api/meals",payload.replace("18.50","-1")).statusCode()).isEqualTo(400);
    }
    @Test void modestConcurrentRequestsCompleteWithoutServerErrors() throws Exception {
        dish("并发查询菜品","10","清淡");
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Integer>>();
            for (int i=0;i<32;i++) tasks.add(() -> request("GET","/api/dishes?size=10",null).statusCode());
            for (var result : executor.invokeAll(tasks)) assertThat(result.get()).isEqualTo(200);
        } finally { executor.shutdownNow(); }
    }
    HttpResponse<String> request(String method,String path,String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if (body != null) builder.header("Content-Type","application/json");
        builder.method(method,body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
