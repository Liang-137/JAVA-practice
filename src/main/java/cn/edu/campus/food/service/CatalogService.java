package cn.edu.campus.food.service;

import cn.edu.campus.food.api.DishQuery;
import cn.edu.campus.food.api.Requests.*;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.support.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class CatalogService {
    private final FoodRepository db;
    private final CurrentUser user;
    public CatalogService(FoodRepository db, CurrentUser user) { this.db = db; this.user = user; }

    public List<Canteen> canteens(boolean archived) { return db.canteens(user.id(), archived); }
    public Canteen canteen(long id) { return db.canteen(user.id(), id); }
    public Canteen createCanteen(CanteenInput input) {
        long id = db.insert("INSERT INTO canteens(user_id,name,location) VALUES(?,?,?)", user.id(), input.name().strip(), input.location().strip());
        return canteen(id);
    }
    public Canteen updateCanteen(long id, CanteenInput input) {
        canteen(id);
        db.update("UPDATE canteens SET name=?,location=? WHERE id=? AND user_id=?", input.name().strip(), input.location().strip(), id, user.id());
        return canteen(id);
    }
    public void archiveCanteen(long id) {
        canteen(id); db.update("UPDATE canteens SET active=FALSE WHERE id=? AND user_id=?", id, user.id());
    }
    public void restoreCanteen(long id) {
        canteen(id); db.update("UPDATE canteens SET active=TRUE WHERE id=? AND user_id=?", id, user.id());
    }
    public List<Stall> stalls(Long canteenId, boolean archived) {
        if (canteenId != null) canteen(canteenId);
        return db.stalls(user.id(), canteenId, archived);
    }
    public Stall stall(long id) { return db.stall(user.id(), id); }
    private void requireActiveCanteen(long id) {
        if (!canteen(id).active()) throw BusinessException.invalid("食堂已归档，请先恢复");
    }
    private void requireActiveStall(long id) {
        Stall stall = stall(id);
        if (!stall.active()) throw BusinessException.invalid("档口已归档，请先恢复");
        requireActiveCanteen(stall.canteenId());
    }
    public Stall createStall(StallInput input) {
        requireActiveCanteen(input.canteenId());
        long id = db.insert("INSERT INTO stalls(user_id,canteen_id,name) VALUES(?,?,?)", user.id(), input.canteenId(), input.name().strip());
        return stall(id);
    }
    public Stall updateStall(long id, StallInput input) {
        var original = stall(id);
        if (original.canteenId() != input.canteenId()) throw BusinessException.invalid("不能更改档口所属食堂，请新建档口");
        db.update("UPDATE stalls SET name=? WHERE id=? AND user_id=?", input.name().strip(), id, user.id());
        return stall(id);
    }
    public void archiveStall(long id) { stall(id); db.update("UPDATE stalls SET active=FALSE WHERE id=? AND user_id=?", id, user.id()); }
    public void restoreStall(long id) {
        requireActiveCanteen(stall(id).canteenId());
        db.update("UPDATE stalls SET active=TRUE WHERE id=? AND user_id=?", id, user.id());
    }
    public Dish dish(long id) { return db.dish(user.id(), id); }
    public Dish createDish(DishInput input) {
        requireActiveStall(input.stallId());
        long id = db.insert("INSERT INTO dishes(user_id,stall_id,name,price,category,flavors) VALUES(?,?,?,?,?,?)",
                user.id(), input.stallId(), input.name().strip(), input.price(), input.category().strip(), flavors(input));
        return dish(id);
    }
    private String flavors(DishInput input) {
        return String.join(",", input.flavors().stream().map(String::strip).distinct().toList());
    }
    public Dish updateDish(long id, DishInput input) {
        if (dish(id).stallId() != input.stallId()) throw BusinessException.invalid("不能更改菜品所属档口，请新建菜品");
        db.update("UPDATE dishes SET name=?,price=?,category=?,flavors=? WHERE id=? AND user_id=?",
                input.name().strip(), input.price(), input.category().strip(), flavors(input), id, user.id());
        return dish(id);
    }
    public void archiveDish(long id) { dish(id); db.update("UPDATE dishes SET active=FALSE WHERE id=? AND user_id=?", id, user.id()); }
    public void restoreDish(long id) {
        requireActiveStall(dish(id).stallId());
        db.update("UPDATE dishes SET active=TRUE WHERE id=? AND user_id=?", id, user.id());
    }
    public Dish mark(long id, MarkInput input) {
        dish(id);
        if (input.favorite() && input.avoided()) throw BusinessException.invalid("收藏与踩雷不能同时开启");
        db.update("MERGE INTO dish_marks(user_id,dish_id,favorite,avoided) KEY(user_id,dish_id) VALUES(?,?,?,?)",
                user.id(), id, input.favorite(), input.avoided());
        return dish(id);
    }
    public List<Dish> candidates(DishQuery query) {
        if (query.minPrice() != null && query.maxPrice() != null && query.minPrice().compareTo(query.maxPrice()) > 0)
            throw BusinessException.invalid("最低价格不能大于最高价格");
        return db.dishes(user.id(), query);
    }
    public Page<Dish> dishes(DishQuery query, int page, int size) {
        return page(candidates(query), page, size);
    }
    public Recommendation recommend(DishQuery query) {
        var dishes = candidates(query).stream().filter(d -> d.available() && !d.avoided()).toList();
        if (dishes.isEmpty()) throw new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,
                "NO_CANDIDATE", "没有符合条件的菜品，请调整预算、口味或类别；踩雷和已归档菜品不会参与推荐");
        var selected = dishes.get(ThreadLocalRandom.current().nextInt(dishes.size()));
        return new Recommendation(selected, dishes.size(), "从符合筛选条件且未踩雷的可用菜品中等概率抽取");
    }
    public static <T> Page<T> page(List<T> all, Integer page, Integer size) {
        int p = page == null ? 1 : page, s = size == null ? 20 : size;
        if (p < 1 || s < 1 || s > 100) throw BusinessException.invalid("page 必须 >=1，size 必须在 1～100 之间");
        int start = (int) Math.min(((long) p - 1) * s, all.size());
        return new Page<>(all.subList(start, Math.min(start + s, all.size())), p, s, all.size());
    }
}
