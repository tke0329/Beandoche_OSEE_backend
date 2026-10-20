package com.beandoche_osee_backend.domain.profile;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProfileImageValidator {
    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean png = name.endsWith(".png") && "image/png".equals(file.getContentType());
        boolean jpeg = (name.endsWith(".jpg") || name.endsWith(".jpeg")) && "image/jpeg".equals(file.getContentType());
        if (file.getSize() > 1048576 || (!png && !jpeg)) throw invalid();
        try (var stream = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h > 16000000) throw invalid();
                if (!(png ? "PNG" : "JPEG").equalsIgnoreCase(reader.getFormatName())) throw invalid();
                var source = reader.read(0);
                double ratio = Math.min(1.0, 512.0 / Math.max(w, h));
                var image = new BufferedImage(Math.max(1, (int)(w * ratio)), Math.max(1, (int)(h * ratio)), BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                if (output.size() > 1048576) throw invalid();
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) { throw invalid(); }
    }

    private ProfileFailure invalid() {
        return new ProfileFailure("INVALID_PROFILE_IMAGE", "1MB 이하의 올바른 PNG 또는 JPEG 이미지를 선택해 주세요.", 400);
    }
}
