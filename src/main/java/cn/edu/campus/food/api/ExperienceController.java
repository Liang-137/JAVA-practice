package cn.edu.campus.food.api;

import cn.edu.campus.food.domain.Models.Page;
import cn.edu.campus.food.service.ExperienceService;
import cn.edu.campus.food.service.ExperienceService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ExperienceController {
    private final ExperienceService service;

    public ExperienceController(ExperienceService service) { this.service = service; }

    @PostMapping(value="/stalls/{stallId}/experiences", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Entry save(@PathVariable long stallId, @RequestPart("record") @Valid Input input,
                      @RequestPart(value="photos", required=false) List<MultipartFile> photos) throws IOException {
        return service.save(stallId, input, photos == null ? List.of() : photos);
    }

    @GetMapping("/dishes/{dishId}/experiences")
    public Page<Entry> history(@PathVariable long dishId, @RequestParam(defaultValue="1") @Min(1) int page,
                               @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.history(dishId, page, size);
    }

    @GetMapping("/experience-photos/{id}")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        var image = service.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.imageData());
    }
}
