package cn.edu.campus.food.service;

import cn.edu.campus.food.domain.Models.*;
import cn.edu.campus.food.repository.FoodRepository;
import cn.edu.campus.food.support.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class PhotoService {
    private static final Logger LOG = LoggerFactory.getLogger(PhotoService.class);
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final FoodRepository db;
    private final CurrentUser user;
    private final Path storage;
    public PhotoService(FoodRepository db, CurrentUser user, @Value("${app.storage-path}") String storage) {
        this.db = db; this.user = user; this.storage = Path.of(storage).toAbsolutePath().normalize();
    }
    public List<Photo> list(long dishId) {
        db.dish(user.id(), dishId); return db.photos(user.id(), dishId);
    }
    @Transactional(rollbackFor = IOException.class)
    public Photo upload(long dishId, MultipartFile file) throws IOException {
        db.dish(user.id(), dishId);
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw BusinessException.invalid("请选择非空且不超过 5MB 的图片");
        byte[] bytes = file.getBytes();
        String format = validateImage(bytes);
        String filename = UUID.randomUUID() + "." + format;
        Path target = storage.resolve(filename);
        Files.createDirectories(storage);
        // Register before writing, so transaction rollback also cleans a partially written file.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) removeFile(target);
            }
        });
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        String contentType = format.equals("png") ? "image/png" : "image/jpeg";
        long id = db.insert("INSERT INTO dish_photos(user_id,dish_id,filename,content_type) VALUES(?,?,?,?)",
                user.id(), dishId, filename, contentType);
        return new Photo(id, dishId, "/api/photos/" + id + "/content", contentType);
    }
    private String validateImage(byte[] bytes) throws IOException {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw BusinessException.invalid("仅支持真实的 PNG 或 JPEG 图片");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw BusinessException.invalid("仅支持 PNG 或 JPEG 图片");
                reader.setInput(stream);
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width * height > 12_000_000L) throw BusinessException.invalid("图片不能超过 1200 万像素，请先压缩");
                if (reader.read(0) == null) throw BusinessException.invalid("图片无法解码");
                return format.equals("png") ? "png" : "jpg";
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException ex) { throw BusinessException.invalid("图片损坏或格式不正确"); }
    }
    public record Download(Resource resource, String contentType) { }
    public Download download(long id) {
        var photo = db.photoFile(user.id(), id);
        var path = storage.resolve(photo.filename()).normalize();
        if (!path.startsWith(storage) || !Files.isRegularFile(path)) throw BusinessException.missing("照片文件");
        return new Download(new FileSystemResource(path), photo.contentType());
    }
    @Transactional
    public void delete(long id) {
        var photo = db.photoFile(user.id(), id);
        db.update("DELETE FROM dish_photos WHERE id=? AND user_id=?", id, user.id());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { removeFile(storage.resolve(photo.filename())); }
        });
    }
    private void removeFile(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ex) { LOG.warn("Unable to remove orphan photo {}", path, ex); }
    }
}
