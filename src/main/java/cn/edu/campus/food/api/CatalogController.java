package cn.edu.campus.food.api;

import cn.edu.campus.food.api.Requests.*;
import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/canteens") public List<Canteen> canteens(@RequestParam(defaultValue = "false") boolean includeArchived) { return service.canteens(includeArchived); }
    @GetMapping("/canteens/{id}") public Canteen canteen(@PathVariable long id) { return service.canteen(id); }
    @PostMapping("/canteens") @ResponseStatus(HttpStatus.CREATED)
    public Canteen createCanteen(@Valid @RequestBody CanteenInput input) { return service.createCanteen(input); }
    @PutMapping("/canteens/{id}") public Canteen updateCanteen(@PathVariable long id, @Valid @RequestBody CanteenInput input) { return service.updateCanteen(id, input); }
    @DeleteMapping("/canteens/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveCanteen(@PathVariable long id) { service.archiveCanteen(id); }
    @PostMapping("/canteens/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreCanteen(@PathVariable long id) { service.restoreCanteen(id); }

    @GetMapping("/stalls") public List<Stall> stalls(@RequestParam(required = false) Long canteenId, @RequestParam(defaultValue = "false") boolean includeArchived) { return service.stalls(canteenId, includeArchived); }
    @GetMapping("/stalls/{id}") public Stall stall(@PathVariable long id) { return service.stall(id); }
    @PostMapping("/stalls") @ResponseStatus(HttpStatus.CREATED)
    public Stall createStall(@Valid @RequestBody StallInput input) { return service.createStall(input); }
    @PutMapping("/stalls/{id}") public Stall updateStall(@PathVariable long id, @Valid @RequestBody StallInput input) { return service.updateStall(id, input); }
    @DeleteMapping("/stalls/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveStall(@PathVariable long id) { service.archiveStall(id); }
    @PostMapping("/stalls/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreStall(@PathVariable long id) { service.restoreStall(id); }

    @GetMapping("/dishes") public Page<Dish> dishes(@Valid @ModelAttribute DishQuery query) { return service.dishes(query); }
    @GetMapping("/dishes/{id}") public Dish dish(@PathVariable long id) { return service.dish(id); }
    @PostMapping("/dishes") @ResponseStatus(HttpStatus.CREATED)
    public Dish createDish(@Valid @RequestBody DishInput input) { return service.createDish(input); }
    @PutMapping("/dishes/{id}") public Dish updateDish(@PathVariable long id, @Valid @RequestBody DishInput input) { return service.updateDish(id, input); }
    @DeleteMapping("/dishes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveDish(@PathVariable long id) { service.archiveDish(id); }
    @PostMapping("/dishes/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreDish(@PathVariable long id) { service.restoreDish(id); }
    @PutMapping("/dishes/{id}/marks") public Dish mark(@PathVariable long id, @Valid @RequestBody MarkInput input) { return service.mark(id, input); }
    @GetMapping("/recommendations/random") public Recommendation random(@Valid @ModelAttribute DishQuery query) { return service.recommend(query); }
}
