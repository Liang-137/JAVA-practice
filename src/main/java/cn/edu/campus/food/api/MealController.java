package cn.edu.campus.food.api;

import cn.edu.campus.food.api.Requests.MealInput;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.service.MealService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals")
public class MealController {
    private final MealService service;
    public MealController(MealService service) { this.service = service; }
    @GetMapping public Page<Meal> list(@RequestParam(required = false) String month,
            @RequestParam(required = false) @Positive Long dishId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(month == null ? null : MealService.parseMonth(month), dishId, page, size);
    }
    @GetMapping("/{id}") public Meal get(@PathVariable long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Meal create(@Valid @RequestBody MealInput input) { return service.create(input); }
    @PutMapping("/{id}") public Meal update(@PathVariable long id, @Valid @RequestBody MealInput input) { return service.update(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
}
