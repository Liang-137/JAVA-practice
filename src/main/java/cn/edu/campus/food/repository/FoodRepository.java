package cn.edu.campus.food.repository;

import cn.edu.campus.food.api.DishQuery;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.support.BusinessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class FoodRepository {
    private final JdbcTemplate jdbc;
    public FoodRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public long insert(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"ID"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    public <T> List<T> query(String sql, Class<T> type, Object... args) {
        return jdbc.query(sql, DataClassRowMapper.newInstance(type), args);
    }
    public <T> T require(String sql, Class<T> type, String resource, Object... args) {
        return query(sql, type, args).stream().findFirst().orElseThrow(() -> BusinessException.missing(resource));
    }

    public List<Canteen> canteens(long user, boolean archived) {
        return query("SELECT id,name,location,active FROM canteens WHERE user_id=? "
                + (archived ? "" : "AND active=TRUE ") + "ORDER BY id", Canteen.class, user);
    }
    public Canteen canteen(long user, long id) {
        return require("SELECT id,name,location,active FROM canteens WHERE user_id=? AND id=?",
                Canteen.class, "食堂", user, id);
    }
    public Stall stall(long user, long id) {
        return require("SELECT id,canteen_id,name,active FROM stalls WHERE user_id=? AND id=?",
                Stall.class, "档口", user, id);
    }
    public List<Stall> stalls(long user, Long canteen, boolean archived) {
        var args = new ArrayList<Object>();
        args.add(user);
        String sql = "SELECT s.id,s.canteen_id,s.name,s.active FROM stalls s JOIN canteens c ON c.id=s.canteen_id WHERE s.user_id=?";
        if (canteen != null) { sql += " AND s.canteen_id=?"; args.add(canteen); }
        if (!archived) sql += " AND s.active=TRUE AND c.active=TRUE";
        return query(sql + " ORDER BY s.id", Stall.class, args.toArray());
    }

    private static final String DISH_SELECT = """
        SELECT d.id,d.stall_id,s.canteen_id,d.name,d.price,d.category,d.flavors,d.active,
          (d.active AND s.active AND c.active) AS available,
          COALESCE(m.favorite,FALSE) AS favorite, COALESCE(m.avoided,FALSE) AS avoided,
          (SELECT ROUND(AVG((i.taste+i.value_score+i.portion+i.recommendation)/4.0),2)
             FROM meal_items i JOIN meals e ON e.id=i.meal_id
             WHERE i.dish_id=d.id AND e.user_id=d.user_id AND i.taste IS NOT NULL) AS score,
          (SELECT p.id FROM dish_photos p WHERE p.dish_id=d.id AND p.user_id=d.user_id ORDER BY p.id DESC LIMIT 1) AS photo_id
        FROM dishes d JOIN stalls s ON s.id=d.stall_id JOIN canteens c ON c.id=s.canteen_id
        LEFT JOIN dish_marks m ON m.dish_id=d.id AND m.user_id=d.user_id
        WHERE d.user_id=?
        """;

    private final RowMapper<Dish> dishMapper = (rs, row) -> {
        Long photo = rs.getObject("photo_id", Long.class);
        String flavors = rs.getString("flavors");
        return new Dish(rs.getLong("id"), rs.getLong("stall_id"), rs.getLong("canteen_id"),
                rs.getString("name"), rs.getBigDecimal("price"), rs.getString("category"),
                flavors.isEmpty() ? List.of() : List.of(flavors.split(",")), rs.getBoolean("active"),
                rs.getBoolean("available"), rs.getBoolean("favorite"), rs.getBoolean("avoided"),
                rs.getBigDecimal("score"), photo == null ? null : "/api/photos/" + photo + "/content");
    };

    public Dish dish(long user, long id) {
        return jdbc.query(DISH_SELECT + " AND d.id=?", dishMapper, user, id).stream().findFirst()
                .orElseThrow(() -> BusinessException.missing("菜品"));
    }
    public List<Dish> dishes(long user, DishQuery q) {
        var sql = new StringBuilder(DISH_SELECT);
        var args = new ArrayList<Object>(); args.add(user);
        if (!Boolean.TRUE.equals(q.includeArchived())) sql.append(" AND d.active=TRUE AND s.active=TRUE AND c.active=TRUE");
        filter(sql, args, " AND s.canteen_id=?", q.canteenId());
        filter(sql, args, " AND d.stall_id=?", q.stallId());
        if (q.keyword() != null && !q.keyword().isBlank()) {
            sql.append(" AND (LOCATE(LOWER(?),LOWER(d.name))>0 OR LOCATE(LOWER(?),LOWER(s.name))>0 OR LOCATE(LOWER(?),LOWER(c.name))>0)");
            Collections.addAll(args, q.keyword().strip(), q.keyword().strip(), q.keyword().strip());
        }
        filter(sql, args, " AND d.category=?", trim(q.category()));
        filter(sql, args, " AND LOCATE(CONCAT(',',?,','),CONCAT(',',d.flavors,','))>0", trim(q.flavor()));
        filter(sql, args, " AND d.price>=?", q.minPrice());
        filter(sql, args, " AND d.price<=?", q.maxPrice());
        filter(sql, args, " AND COALESCE(m.favorite,FALSE)=?", q.favorite());
        filter(sql, args, " AND COALESCE(m.avoided,FALSE)=?", q.avoided());
        if (q.eaten() != null) {
            sql.append(q.eaten() ? " AND EXISTS" : " AND NOT EXISTS");
            sql.append(" (SELECT 1 FROM meal_items i JOIN meals e ON e.id=i.meal_id WHERE i.dish_id=d.id AND e.user_id=d.user_id)");
        }
        return jdbc.query(sql + " ORDER BY d.id DESC", dishMapper, args.toArray());
    }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.strip(); }
    private static void filter(StringBuilder sql, List<Object> args, String clause, Object value) {
        if (value != null) { sql.append(clause); args.add(value); }
    }

    public List<Photo> photos(long user, long dishId) {
        return jdbc.query("SELECT id,dish_id,content_type FROM dish_photos WHERE user_id=? AND dish_id=? ORDER BY id DESC",
                (rs, row) -> new Photo(rs.getLong("id"), rs.getLong("dish_id"),
                        "/api/photos/" + rs.getLong("id") + "/content", rs.getString("content_type")), user, dishId);
    }
    public PhotoFile photoFile(long user, long photoId) {
        return require("SELECT id,filename,content_type FROM dish_photos WHERE user_id=? AND id=?",
                PhotoFile.class, "照片", user, photoId);
    }

    public record MealHeader(long id, java.time.LocalDateTime eatenAt, String mealType, String note) { }
    public MealHeader mealHeader(long user, long id) {
        return require("SELECT id,eaten_at,meal_type,note FROM meals WHERE user_id=? AND id=?",
                MealHeader.class, "用餐记录", user, id);
    }
    public List<MealItem> mealItems(long id) {
        return jdbc.query("SELECT * FROM meal_items WHERE meal_id=? ORDER BY id", (rs, row) -> {
            Integer taste = rs.getObject("taste", Integer.class);
            Rating rating = taste == null ? null : new Rating(taste, rs.getObject("value_score", Integer.class),
                    rs.getObject("portion", Integer.class), rs.getObject("recommendation", Integer.class));
            return new MealItem(rs.getLong("dish_id"), rs.getString("dish_name"), rs.getInt("quantity"),
                    rs.getBigDecimal("paid_amount"), rating, rs.getString("comment"));
        }, id);
    }
    public Meal meal(long user, long id) {
        var header = mealHeader(user, id);
        var items = mealItems(id);
        var total = items.stream().map(MealItem::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Meal(id, header.eatenAt(), header.mealType(), header.note(), total, items);
    }
}
