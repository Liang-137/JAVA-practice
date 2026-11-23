package cn.edu.campus.food.api;

import cn.edu.campus.food.domain.Models.Photo;
import cn.edu.campus.food.service.PhotoService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PhotoController {
    private final PhotoService service;
    public PhotoController(PhotoService service) { this.service = service; }
    @GetMapping("/dishes/{id}/photos") public List<Photo> list(@PathVariable long id) { return service.list(id); }
    @PostMapping(value = "/dishes/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Photo upload(@PathVariable long id, @RequestPart("file") MultipartFile file) throws IOException { return service.upload(id, file); }
    @GetMapping("/photos/{id}/content") public ResponseEntity<Resource> download(@PathVariable long id) {
        var file = service.download(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(file.resource());
    }
    @DeleteMapping("/photos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
}
