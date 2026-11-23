package cn.edu.campus.food.service;

import cn.edu.campus.food.api.Requests.*;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.repository.FoodRepository.MealHeader;
import cn.edu.campus.food.support.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class MealService {
    private final FoodRepository db;
    private final CurrentUser user;
    public MealService(FoodRepository db, CurrentUser user) { this.db = db; this.user = user; }

    public Meal get(long id) { return db.meal(user.id(), id); }
    public Meal create(MealInput input) {
        long id = db.insert("INSERT INTO meals(user_id,eaten_at,meal_type,note) VALUES(?,?,?,?)",
                user.id(), input.eatenAt(), input.mealType().name(), input.note().strip());
        saveItems(id, input.items(), Map.of());
        return get(id);
    }
    public Meal update(long id, MealInput input) {
        var old = get(id);
        Map<Long,String> originalNames = old.items().stream().collect(Collectors.toMap(MealItem::dishId, MealItem::dishName));
        db.update("UPDATE meals SET eaten_at=?,meal_type=?,note=? WHERE id=? AND user_id=?",
                input.eatenAt(), input.mealType().name(), input.note().strip(), id, user.id());
        db.update("DELETE FROM meal_items WHERE meal_id=?", id);
        saveItems(id, input.items(), originalNames);
        return get(id);
    }
    private void saveItems(long mealId, List<MealItemInput> items, Map<Long,String> originalNames) {
        Set<Long> seen = new HashSet<>();
        for (var item : items) {
            if (!seen.add(item.dishId())) throw BusinessException.invalid("同一餐中菜品不能重复，请通过 quantity 合并份数");
            var dish = db.dish(user.id(), item.dishId());
            if (!dish.available() && !originalNames.containsKey(dish.id())) throw BusinessException.invalid("不能为已归档的食堂、档口或菜品新增用餐记录");
            var rating = item.rating();
            db.insert("""
                INSERT INTO meal_items(meal_id,dish_id,dish_name,quantity,paid_amount,taste,value_score,portion,recommendation,comment)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, mealId, dish.id(), originalNames.getOrDefault(dish.id(), dish.name()), item.quantity(), item.paidAmount(),
                    rating == null ? null : rating.taste(), rating == null ? null : rating.valueScore(),
                    rating == null ? null : rating.portion(), rating == null ? null : rating.recommendation(), item.comment().strip());
        }
    }
    public void delete(long id) {
        get(id); db.update("DELETE FROM meals WHERE id=? AND user_id=?", id, user.id());
    }
    public Page<Meal> list(YearMonth month, Long dishId, Integer page, Integer size) {
        var args = new ArrayList<Object>(); args.add(user.id());
        String sql = "SELECT e.id,e.eaten_at,e.meal_type,e.note FROM meals e WHERE e.user_id=?";
        if (month != null) {
            sql += " AND e.eaten_at>=? AND e.eaten_at<?";
            args.add(month.atDay(1).atStartOfDay()); args.add(month.plusMonths(1).atDay(1).atStartOfDay());
        }
        if (dishId != null) {
            db.dish(user.id(), dishId);
            sql += " AND EXISTS(SELECT 1 FROM meal_items i WHERE i.meal_id=e.id AND i.dish_id=?)";
            args.add(dishId);
        }
        var headers = db.query(sql + " ORDER BY e.eaten_at DESC,e.id DESC", MealHeader.class, args.toArray());
        var selected = CatalogService.page(headers, page, size);
        return new Page<>(selected.items().stream().map(h -> get(h.id())).toList(), selected.page(), selected.size(), selected.total());
    }
    public static YearMonth parseMonth(String value) {
        try { return YearMonth.parse(value); }
        catch (DateTimeException ex) { throw BusinessException.invalid("月份格式应为 yyyy-MM，例如 2026-11"); }
    }
}
