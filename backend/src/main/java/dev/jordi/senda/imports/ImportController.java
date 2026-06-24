package dev.jordi.senda.imports;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    public List<ImportPreviewRow> preview(@Valid @RequestBody ImportPreviewRequest request) {
        return importService.preview(CurrentUser.id(), request);
    }

    @PostMapping("/commit")
    public ImportCommitResponse commit(@Valid @RequestBody ImportCommitRequest request) {
        return importService.commit(CurrentUser.id(), request);
    }

    @ExceptionHandler(InvalidImportException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidImportException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
